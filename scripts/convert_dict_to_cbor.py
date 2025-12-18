#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Convert existing .dict files (JSON format) to CBOR format.

This script converts all *_base.dict files from JSON to CBOR format
for faster loading in the Android app.

Usage:
    python scripts/convert_dict_to_cbor.py

Requirements:
    pip install cbor2
"""

import os
import json
import sys
from pathlib import Path

try:
    import cbor2
except ImportError:
    print("ERROR: cbor2 not installed. Run: pip install cbor2")
    sys.exit(1)


def find_project_root():
    """Find project root directory."""
    script_dir = Path(__file__).parent
    return script_dir.parent


def convert_json_to_cbor(input_path: Path, output_path: Path) -> tuple[bool, str]:
    """
    Convert a JSON .dict file to CBOR format.
    
    Returns:
        (success: bool, message: str)
    """
    try:
        # Read JSON
        with open(input_path, "r", encoding="utf-8") as f:
            data = json.load(f)
        
        # Write CBOR
        with open(output_path, "wb") as f:
            cbor2.dump(data, f)
        
        # Calculate sizes
        json_size = input_path.stat().st_size / (1024 * 1024)
        cbor_size = output_path.stat().st_size / (1024 * 1024)
        reduction = (1 - cbor_size / json_size) * 100
        
        return True, f"JSON: {json_size:.2f} MB → CBOR: {cbor_size:.2f} MB ({reduction:.1f}% smaller)"
    
    except json.JSONDecodeError as e:
        return False, f"JSON parse error: {e}"
    except Exception as e:
        return False, f"Error: {e}"


def main():
    project_root = find_project_root()
    dict_dir = project_root / "app" / "src" / "main" / "assets" / "common" / "dictionaries_serialized"
    
    if not dict_dir.exists():
        print(f"ERROR: Directory not found: {dict_dir}")
        return 1
    
    # Find all .dict files
    dict_files = list(dict_dir.glob("*_base.dict"))
    
    if not dict_files:
        print(f"ERROR: No .dict files found in {dict_dir}")
        return 1
    
    print(f"Found {len(dict_files)} dictionary files to convert...\n")
    
    success_count = 0
    failed = []
    
    for dict_file in sorted(dict_files):
        language = dict_file.stem.replace("_base", "")
        
        # Check if it's already CBOR (doesn't start with '{')
        with open(dict_file, "rb") as f:
            first_byte = f.read(1)
        
        if first_byte != b'{':
            print(f"Skipping {language}: already in CBOR format")
            success_count += 1
            continue
        
        print(f"Converting {language}...")
        
        # Convert in place (same file)
        temp_path = dict_file.with_suffix(".cbor.tmp")
        success, message = convert_json_to_cbor(dict_file, temp_path)
        
        if success:
            # Replace original with CBOR version
            temp_path.replace(dict_file)
            print(f"  ✓ {message}")
            success_count += 1
        else:
            # Clean up temp file if exists
            if temp_path.exists():
                temp_path.unlink()
            print(f"  ✗ {message}")
            failed.append(language)
        
        print()
    
    print("=" * 60)
    print(f"Converted: {success_count}/{len(dict_files)} dictionaries")
    if failed:
        print(f"Failed: {', '.join(failed)}")
    else:
        print("All dictionaries converted successfully!")
    
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())

