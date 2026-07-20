# Device-Archive

Dieses Verzeichnis sammelt beobachtetes Verhalten physischer Tastaturen auf echten Geräten und konkreten Firmwareständen.

Die Snapshots sind keine Runtime-Assets der App. Sie dienen als Archiv, um spätere Änderungen an Gerätefirmware, Android-Keymaps oder Pastiera-Routing nachvollziehbar gegen einen bekannten Stand vergleichen zu können.

## Format

Jeder Snapshot besteht aus:

- einer Markdown-Datei für die menschlich lesbare Einordnung,
- einer JSON-Datei mit normalisierten Beobachtungen,
- optional einem Rohdump unter `raw/`, wenn die ursprüngliche Debug-Datei dauerhaft nachvollziehbar bleiben soll.

Die JSON-Dateien verwenden ein eigenes Archivschema mit `schema_version`. Es ist bewusst unabhängig von den importierbaren Pastiera-Layoutdateien unter `app/src/main/assets/common/layouts/`.

## Namenskonvention

Snapshot-Dateien folgen diesem Muster:

```text
YYYY-MM-DD-<gerät>-<firmware>-<layout-kontext>.md
YYYY-MM-DD-<gerät>-<firmware>-<layout-kontext>.json
```

Der Dateiname soll den Vergleichskontext erkennbar machen, aber nicht alle Metadaten enthalten. Details wie Android-Build, App-Version, Eingabegeräte und Settings stehen im Snapshot selbst.

## Bekannte Geräte

- [Unihertz Titan 2](unihertz-titan2/)
- [Unihertz Titan 2 Elite](unihertz-titan2elite/)
