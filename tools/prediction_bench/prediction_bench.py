#!/usr/bin/env python3
import argparse
import json
import os
import re
import statistics
import time
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any


WORD_RE = re.compile(r"[A-Za-zÀ-ÖØ-öø-ÿ0-9]+(?:['’-][A-Za-zÀ-ÖØ-öø-ÿ0-9]+)?")
PUNCT_RE = re.compile(r"^[\s]*([,.;:!?])")


@dataclass(frozen=True)
class BenchCase:
    fixture_id: str
    language: str
    context: str
    target: str
    position: int


def load_jsonl(path: Path) -> list[dict[str, Any]]:
    rows = []
    with path.open("r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, start=1):
            stripped = line.strip()
            if not stripped:
                continue
            try:
                rows.append(json.loads(stripped))
            except json.JSONDecodeError as exc:
                raise SystemExit(f"{path}:{line_number}: invalid JSON: {exc}") from exc
    return rows


def load_models(path: Path) -> list[dict[str, Any]]:
    with path.open("r", encoding="utf-8") as handle:
        data = json.load(handle)
    if not isinstance(data, list):
        raise SystemExit("models file must contain a JSON array")
    return data


def build_cases(fixtures: list[dict[str, Any]], min_context_words: int) -> list[BenchCase]:
    cases: list[BenchCase] = []
    for fixture in fixtures:
        text = fixture["text"]
        matches = list(WORD_RE.finditer(text))
        for index, match in enumerate(matches):
            if index < min_context_words:
                continue
            context = text[: match.start()]
            target = match.group(0)
            if not target:
                continue
            cases.append(
                BenchCase(
                    fixture_id=fixture["id"],
                    language=fixture["language"],
                    context=context,
                    target=target,
                    position=index,
                )
            )
    return cases


def normalize_word(word: str) -> str:
    return word.casefold().strip()


def first_word(text: str) -> str | None:
    match = WORD_RE.search(text.strip())
    if match is None:
        return None
    if match.start() > 2:
        return None
    return match.group(0)


def first_punctuation(text: str) -> str | None:
    match = PUNCT_RE.match(text)
    return match.group(1) if match else None


class TransformersModel:
    def __init__(self, model_id: str, cache_dir: Path, device: str | None) -> None:
        import torch
        from transformers import AutoModelForCausalLM, AutoTokenizer

        self.torch = torch
        self.tokenizer = AutoTokenizer.from_pretrained(model_id, cache_dir=cache_dir)
        self.model = AutoModelForCausalLM.from_pretrained(model_id, cache_dir=cache_dir)
        if self.tokenizer.pad_token_id is None and self.tokenizer.eos_token_id is not None:
            self.tokenizer.pad_token = self.tokenizer.eos_token

        if device is None:
            if getattr(torch.backends, "mps", None) and torch.backends.mps.is_available():
                device = "mps"
            elif torch.cuda.is_available():
                device = "cuda"
            else:
                device = "cpu"
        self.device = device
        self.model.to(self.device)
        self.model.eval()

    def predict_words(
        self,
        context: str,
        max_new_tokens: int,
        beams: int,
        candidates: int,
    ) -> tuple[list[str], list[str]]:
        inputs = self.tokenizer(context, return_tensors="pt", truncation=True, max_length=256)
        inputs = {key: value.to(self.device) for key, value in inputs.items()}
        with self.torch.inference_mode():
            output_ids = self.model.generate(
                **inputs,
                do_sample=False,
                num_beams=beams,
                num_return_sequences=candidates,
                max_new_tokens=max_new_tokens,
                early_stopping=True,
                pad_token_id=self.tokenizer.pad_token_id,
                eos_token_id=self.tokenizer.eos_token_id,
            )

        prompt_length = inputs["input_ids"].shape[-1]
        words: list[str] = []
        punctuation: list[str] = []
        seen_words: set[str] = set()
        seen_punctuation: set[str] = set()
        for sequence in output_ids:
            continuation = self.tokenizer.decode(sequence[prompt_length:], skip_special_tokens=True)
            punct = first_punctuation(continuation)
            if punct is not None and punct not in seen_punctuation:
                punctuation.append(punct)
                seen_punctuation.add(punct)
            word = first_word(continuation)
            if word is None:
                continue
            key = normalize_word(word)
            if key in seen_words:
                continue
            words.append(word)
            seen_words.add(key)
        return words[:3], punctuation[:3]


def run_model(
    model_config: dict[str, Any],
    cases: list[BenchCase],
    args: argparse.Namespace,
) -> list[dict[str, Any]]:
    model_id = model_config["id"]
    label = model_config.get("label", model_id)
    supported_languages = set(model_config.get("languages", []))
    selected_cases = [
        case for case in cases
        if not supported_languages or case.language in supported_languages
    ]
    if args.languages:
        selected = set(args.languages)
        selected_cases = [case for case in selected_cases if case.language in selected]
    if args.limit_cases is not None:
        selected_cases = selected_cases[: args.limit_cases]

    if not selected_cases:
        return []

    print(f"loading {label} ({model_id})")
    model = TransformersModel(model_id, Path(args.cache_dir), args.device)
    rows: list[dict[str, Any]] = []
    for index, case in enumerate(selected_cases, start=1):
        started = time.perf_counter()
        predictions, punctuation = model.predict_words(
            case.context,
            max_new_tokens=args.max_new_tokens,
            beams=args.beams,
            candidates=args.candidates,
        )
        elapsed_ms = (time.perf_counter() - started) * 1000.0
        normalized_target = normalize_word(case.target)
        normalized_predictions = [normalize_word(item) for item in predictions]
        row = {
            "model": label,
            "model_id": model_id,
            "fixture": case.fixture_id,
            "language": case.language,
            "position": case.position,
            "context": case.context,
            "target": case.target,
            "predictions": predictions,
            "punctuation": punctuation,
            "latency_ms": round(elapsed_ms, 2),
            "hit_top1": bool(normalized_predictions[:1] == [normalized_target]),
            "hit_top3": normalized_target in normalized_predictions[:3],
        }
        rows.append(row)
        print(
            f"{label} {index:>3}/{len(selected_cases)} "
            f"{case.language} target={case.target!r} pred={predictions} "
            f"{elapsed_ms:.0f}ms"
        )
    return rows


def summarize(rows: list[dict[str, Any]]) -> None:
    groups: dict[tuple[str, str], list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        groups[(row["model"], row["language"])].append(row)

    print("\nsummary")
    for (model, language), group in sorted(groups.items()):
        top1 = sum(1 for row in group if row["hit_top1"]) / len(group)
        top3 = sum(1 for row in group if row["hit_top3"]) / len(group)
        latencies = [float(row["latency_ms"]) for row in group]
        median_latency = statistics.median(latencies)
        print(
            f"{model:18s} {language:2s} "
            f"cases={len(group):3d} top1={top1:.2%} top3={top3:.2%} "
            f"median={median_latency:.0f}ms"
        )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Benchmark small causal LMs for keyboard next-word prediction.")
    parser.add_argument("--models", type=Path, required=True, help="JSON array of model configs.")
    parser.add_argument("--fixtures", type=Path, required=True, help="JSONL fixture texts.")
    parser.add_argument("--out", type=Path, required=True, help="JSONL result path.")
    parser.add_argument("--cache-dir", default=".prediction-bench/hf-cache", help="Hugging Face cache directory.")
    parser.add_argument("--languages", nargs="*", default=None, help="Optional language filter, e.g. en de fr.")
    parser.add_argument("--limit-cases", type=int, default=None, help="Limit cases per model after filtering.")
    parser.add_argument("--min-context-words", type=int, default=2, help="Skip early words with too little context.")
    parser.add_argument("--max-new-tokens", type=int, default=8, help="Tokens to generate while extracting first word.")
    parser.add_argument("--beams", type=int, default=8, help="Beam count for deterministic alternatives.")
    parser.add_argument("--candidates", type=int, default=8, help="Returned continuations before dedupe.")
    parser.add_argument("--device", default=None, help="torch device override: cpu, mps, or cuda.")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    os.environ.setdefault("TOKENIZERS_PARALLELISM", "false")
    Path(args.cache_dir).mkdir(parents=True, exist_ok=True)
    args.out.parent.mkdir(parents=True, exist_ok=True)

    fixtures = load_jsonl(args.fixtures)
    models = load_models(args.models)
    cases = build_cases(fixtures, min_context_words=args.min_context_words)
    if not cases:
        raise SystemExit("no benchmark cases generated")

    all_rows: list[dict[str, Any]] = []
    for model_config in models:
        rows = run_model(model_config, cases, args)
        all_rows.extend(rows)
        with args.out.open("w", encoding="utf-8") as handle:
            for row in all_rows:
                handle.write(json.dumps(row, ensure_ascii=False) + "\n")

    summarize(all_rows)
    print(f"\nwrote {len(all_rows)} rows to {args.out}")


if __name__ == "__main__":
    main()
