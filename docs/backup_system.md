# Backup System Documentation

## Overview

Pastiera implements two backup mechanisms:
1. **Manual Backup**: User-initiated backup/restore via UI, creates ZIP files
2. **Automatic Backup**: Android's Auto Backup (API 23+) and Cloud Backup (API 31+)

Both systems are aligned to exclude the same data types for consistency.

## Manual Backup System

### Architecture

Manual backups are handled by `BackupManager` and create ZIP archives containing:
- SharedPreferences (as JSON)
- Internal files (allowlist-based)
- Metadata (version, timestamp, component list)

### Backup Process

1. **Create working directory** in cache (`backup_${timestamp}`)
2. **Dump SharedPreferences** via `PreferencesBackupHelper.dumpSharedPreferences()`
   - Converts XML prefs to JSON format
   - Excludes entire preference files (not individual keys)
3. **Snapshot internal files** via `FileBackupHelper.snapshotInternalFiles()`
   - Uses allowlist approach (only specified files/directories)
4. **Create metadata** (`backup_meta.json`) with version info and component list
5. **Zip everything** and write to user-selected URI
6. **Cleanup** temporary directory

### Restore Process

1. **Extract ZIP** to temporary directory
2. **Read metadata** to validate backup version
3. **Restore preferences** via `PreferencesBackupHelper.restorePreferences()`
   - Validates keys against schema (`PreferenceSchemas`)
   - Coerces types if needed
   - Skips unknown/invalid keys
4. **Restore files** via `FileBackupHelper.restoreFiles()`
   - Validates JSON files
   - Special handling for `variations.json` (merge with defaults)
   - Creates backups of existing files before overwriting
5. **Cleanup** temporary directory

### Included Data

#### SharedPreferences
- All preference files except excluded ones
- Main preferences: `pastiera_prefs` (contains all app settings)
- User dictionary entries: `user_dictionary_entries` key in `pastiera_prefs`

#### Files (Allowlist)
- `ctrl_key_mappings.json` - Navigation mode key mappings
- `variations.json` - Character variations (merged on restore)
- `user_defaults.json` - User dictionary defaults
- `locale_layout_mapping.json` - Locale-to-layout mappings
- `keyboard_layouts/` - Custom keyboard layout directory

### Excluded Data

#### SharedPreferences
- `recent_emojis_prefs` - Recent emoji history (ephemeral data)

#### Files
- `dictionaries_serialized/` - All dictionary files (including custom imports)
- Clipboard database (`pastiera_clipboard.db` - stored in `databases/`, not `filesDir`)
- Any files not in the allowlist

## Automatic Backup System

### Android Auto Backup (API 23-30)

Configured via `backup_rules.xml`:
- Includes all SharedPreferences except `recent_emojis_prefs.xml`
- Includes specific files: `keyboard_layouts/`, `ctrl_key_mappings.json`, `variations.json`, `user_defaults.json`
- Excludes dictionaries and clipboard data

### Android Cloud Backup (API 31+)

Configured via `data_extraction_rules.xml`:
- Same inclusions/exclusions as Auto Backup
- Used for device-to-device restore via Google Drive

### Key Differences from Manual Backup

- **No `locale_layout_mapping.json`** in automatic backup
- **No explicit dictionary exclusion needed** (not included by default)
- **Simpler structure** (Android handles compression/encryption)

## Backup File Structure

```
backup.zip
├── backup_meta.json          # Metadata (version, timestamp, components)
├── prefs/
│   └── pastiera_prefs.json   # SharedPreferences as JSON
└── files/
    ├── ctrl_key_mappings.json
    ├── variations.json
    ├── user_defaults.json
    ├── locale_layout_mapping.json
    └── keyboard_layouts/
        └── ...
```

### Metadata Format

```json
{
  "versionCode": 1964,
  "versionName": "1.0.0",
  "timestamp": "2026-01-28T10:30:00Z",
  "components": [
    "files/ctrl_key_mappings.json",
    "files/keyboard_layouts/custom.xml",
    "files/variations.json",
    "prefs/pastiera_prefs.json"
  ]
}
```

### Preference JSON Format

```json
{
  "name": "pastiera_prefs",
  "entries": {
    "key_name": {
      "type": "string|boolean|int|long|float|string_set",
      "value": "..."
    }
  }
}
```

## Preference Schema Validation

The restore process validates preferences against `PreferenceSchemas`:

- **Fixed keys**: Known preference keys with expected types
- **Dynamic keys**: Pattern-based keys (e.g., `auto_correct_custom_*`)
- **Unknown keys**: Skipped during restore (logged as warnings)

This ensures:
- Type safety (coercion when possible)
- Forward compatibility (new keys in backups don't break older versions)
- Backward compatibility (old keys gracefully skipped if removed)

## Special Handling

### Variations.json Merge

On restore, `variations.json` is merged with current defaults:
1. Load current file (or fallback to assets)
2. Overlay backup values
3. Preserve default keys missing in backup

This ensures new variation types are preserved even from old backups.

### File Validation

- JSON files are validated before restore
- Invalid JSON files are skipped (logged)
- Existing files are backed up before overwriting
- Rollback on restore failure

## Data Categories

### User Customizations (Backed Up)
- Keyboard layouts
- SYM page mappings
- Auto-correction rules
- Status bar button configuration
- User dictionary entries
- Navigation mode mappings
- Variations customization
- All app settings/preferences

### Ephemeral Data (Not Backed Up)
- Recent emoji history
- Clipboard history
- Custom imported dictionaries

### System Data (Not Backed Up)
- Installed dictionaries (can be re-downloaded)
- Cache files
- Temporary files

## Implementation Details

### Key Classes

- `BackupManager`: Creates manual backups
- `RestoreManager`: Restores from backup files
- `PreferencesBackupHelper`: Handles SharedPreferences serialization
- `FileBackupHelper`: Handles file allowlist and validation
- `PreferenceSchemas`: Defines valid preference keys and types
- `BackupMetadata`: Backup versioning and component tracking

### Error Handling

- Backup failures return `BackupResult.Failure` with error message
- Restore failures return `RestoreResult.Failure` with error message
- Invalid files/keys are skipped (not fatal)
- Rollback mechanism for file restore failures
- Logging at appropriate levels (WARN for skips, ERROR for failures)

## Best Practices

1. **Always validate** backup metadata before restore
2. **Test restore** on different app versions
3. **Handle missing keys** gracefully (schema evolution)
4. **Merge, don't overwrite** for critical files like variations
5. **Exclude ephemeral data** to keep backups small and privacy-focused
6. **Document schema changes** when adding new preferences

## Future Considerations

- Version migration helpers for breaking changes
- Incremental backup support
- Backup encryption (currently relies on Android's ZIP handling)
- Backup size limits and compression optimization
