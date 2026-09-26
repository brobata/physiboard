#!/usr/bin/env python3
"""Generates the two built-in typing-sound packs as tiny WAV files.

spec: settings-catalog.md SS2.13 ("typing_sound_mode"); expansion-clipboard-pickers-launcher.md
SS9.1 ("The two built-in packs are the raw resources typing_click_* and typing_typewriter_*,
each with five groups: normal, space, backspace, enter, modifier"). SS9.1's own shipped catalogue
has up to 24 files per group (98 files across both packs); this pass ships ONE file per group per
pack (10 files total) so `TypingSounds.resourceName(mode, group, index = 1)` always resolves,
deferring the full randomised catalogue to later work (see the fix-brief's own note on this).

Every file is synthesised, not recorded: a short sine tone with a fast exponential decay envelope,
mono 16-bit PCM at 22050 Hz, using only the Python standard library (wave, struct, math, random).
"click" uses a short, high-pitched, very-fast-decay tone (a key click); "typewriter" uses a
longer, lower-pitched tone with a touch of noise mixed into the attack (a mechanical thock). Each
of the five groups within a pack gets its own frequency and length so they are at least nominally
distinguishable by ear, matching `SoundGroup.forKey`'s five-way split (space, backspace, enter,
modifier, normal).

Usage: generate_sounds.py [<out-dir>]   (default out-dir: ime/src/main/res/raw relative to the
repo root this script lives under, i.e. tools/typing_sounds/../../ime/src/main/res/raw)
"""
import math
import os
import random
import struct
import sys
import wave

SAMPLE_RATE = 22050

# (frequency Hz, duration seconds, decay time-constant seconds), one row per SoundGroup.forKey
# group (expansion-clipboard-pickers-launcher.md SS9.1's five groups).
CLICK_GROUPS = {
    "normal": (1800.0, 0.025, 0.006),
    "space": (1200.0, 0.030, 0.008),
    "backspace": (900.0, 0.035, 0.009),
    "enter": (2200.0, 0.040, 0.010),
    "modifier": (1500.0, 0.020, 0.005),
}
TYPEWRITER_GROUPS = {
    "normal": (400.0, 0.060, 0.018),
    "space": (300.0, 0.070, 0.020),
    "backspace": (250.0, 0.080, 0.022),
    "enter": (500.0, 0.090, 0.024),
    "modifier": (350.0, 0.055, 0.016),
}


def synthesize(frequency: float, duration_s: float, decay_s: float, noise_amount: float) -> bytes:
    """One mono 16-bit PCM sample buffer: a sine tone times an exponential-decay envelope, with an
    optional splash of noise mixed into the first few milliseconds (the typewriter pack's "thock").
    """
    sample_count = max(1, int(SAMPLE_RATE * duration_s))
    frames = bytearray()
    rng = random.Random(round(frequency))  # deterministic per tone, not for cryptographic use
    for i in range(sample_count):
        t = i / SAMPLE_RATE
        envelope = math.exp(-t / decay_s)
        tone = math.sin(2.0 * math.pi * frequency * t)
        noise = (rng.random() * 2.0 - 1.0) if noise_amount > 0 and t < 0.006 else 0.0
        sample = envelope * (tone * (1.0 - noise_amount) + noise * noise_amount)
        value = max(-1.0, min(1.0, sample))
        frames += struct.pack("<h", int(value * 32000))
    return bytes(frames)


def write_wav(path: str, pcm: bytes) -> None:
    with wave.open(path, "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(SAMPLE_RATE)
        f.writeframes(pcm)


def main() -> None:
    default_out = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "..", "ime", "src", "main", "res", "raw"))
    out_dir = sys.argv[1] if len(sys.argv) > 1 else default_out
    os.makedirs(out_dir, exist_ok=True)

    for pack_name, groups, noise_amount in (("click", CLICK_GROUPS, 0.0), ("typewriter", TYPEWRITER_GROUPS, 0.35)):
        for group, (frequency, duration_s, decay_s) in groups.items():
            pcm = synthesize(frequency, duration_s, decay_s, noise_amount)
            out_path = os.path.join(out_dir, f"typing_{pack_name}_{group}_1.wav")
            write_wav(out_path, pcm)
            print(f"wrote {out_path} ({len(pcm) + 44} bytes)")


if __name__ == "__main__":
    main()
