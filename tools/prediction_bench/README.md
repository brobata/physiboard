# Prediction Bench

Small local benchmark for trying next-word models before wiring an on-device
prediction backend into Pastiera.

The tool turns short fixture texts into word-level prediction prompts. For each
word it asks a causal language model for a few continuations, extracts the first
finished word, and checks whether the original word appears in the first three
slots. This is intentionally close to keyboard UX: fast, local, and focused on
three visible suggestions rather than long generated text.

## Setup

```sh
cd /Users/go96buh/gits/GitHub/pastiera
python3 -m venv .prediction-bench/venv
. .prediction-bench/venv/bin/activate
pip install -r tools/prediction_bench/requirements.txt
```

## Run

```sh
python tools/prediction_bench/prediction_bench.py \
  --models tools/prediction_bench/models.example.json \
  --fixtures tools/prediction_bench/fixtures.jsonl \
  --out .prediction-bench/results.jsonl
```

Useful faster smoke run:

```sh
python tools/prediction_bench/prediction_bench.py \
  --models tools/prediction_bench/models.example.json \
  --fixtures tools/prediction_bench/fixtures.jsonl \
  --languages en \
  --limit-cases 12 \
  --out .prediction-bench/smoke.jsonl
```

Downloads, model cache, and results live under `.prediction-bench/`, which is
gitignored.

## Output

Each JSONL row contains the model, fixture id, language, context, target word,
the three extracted candidates, optional punctuation candidates, latency, and
hit flags. The final terminal summary prints top-1/top-3 rates and median-ish
latency per model/language pair.
