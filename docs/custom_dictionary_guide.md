# Custom Dictionary Guide (Pastiera)

This guide explains what the `.dict` files are, how to get the English dictionary, and how to create your own custom dictionary (for example, a Shavian dictionary).

## Where dictionaries are hosted

Pastiera dictionary release assets are published in the separate repository:

- `pastiera-dict`: `https://github.com/palsoftware/pastiera-dict`

The dictionary manifest is here:

- `https://github.com/palsoftware/pastiera-dict/blob/main/docs/dicts-manifest.json`

The English dictionary entry is `en_base.dict`, and the direct download URL is listed in the manifest.

## What is the `.dict` file format?

`.dict` is **not a plain text dictionary**.

Current `.dict` files are stored as **CBOR (binary)** and contain a precomputed serialized dictionary index for fast loading in the app.

The structure includes these top-level fields:

- `normalizedIndex`
- `prefixCache`
- `symDeletes`
- `symMeta`

The app can still read an older JSON-serialized dictionary format, but the current distributed files in `pastiera-dict` are CBOR.

## Can I directly edit `en_base.dict`?

Not realistically.

Because it is CBOR binary, it is not intended for manual editing. The recommended workflow is:

1. Start from an editable JSON word list (`[{ "w": "...", "f": ... }]`)
2. Edit or transform that JSON (for example, convert English spellings to Shavian)
3. Build a new `.dict` file from the JSON

## Recommended editable source format

In the main `pastiera` repository, the backup dictionaries are plain JSON and easy to edit:

- `dict_backup/en_base.json`

Format example:

```json
[
  { "w": "you", "f": 28787591 },
  { "w": "i", "f": 27086011 },
  { "w": "the", "f": 22761659 }
]
```

- `w` = word
- `f` = frequency (used for ranking suggestions)

## How to build a `.dict` file from JSON

Use the build script in the main `pastiera` repo:

- `scripts/build_symspell_dict.py`

It accepts either:

- a base JSON word list (`[{w,f}]`)
- an existing serialized dictionary (`.dict`)

and writes an extended `.dict` file in CBOR format.

### Example

```bash
python3 scripts/build_symspell_dict.py \
  --input dict_backup/en_base.json \
  --output /tmp/en_base.dict
```

If needed, install the CBOR dependency first:

```bash
pip install cbor2
```

## Suggested workflow for a Shavian dictionary

1. Copy `dict_backup/en_base.json`
2. Convert each `w` value to Shavian (keep `f` as-is, at least initially)
3. Save as something like `en_shaw_base.json`
4. Run `build_symspell_dict.py` to generate `en_shaw_base.dict`
5. Test the dictionary in the app (local/custom dictionary import path)

## Notes

- Keep frequencies if you want useful ranking behavior.
- The build script preserves original letter casing in `word`, while generating normalized lookup keys internally.
- If you later want to publish the dictionary via `pastiera-dict`, you will also need a metadata entry in `docs/dicts-metadata.json` there.

