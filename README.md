# Note Version Recorder

A standalone Android app that preserves observed saved-note versions from Zettel Notes and other editors. Resilio Sync carries its immutable journal alongside the notes to desktop Obsidian revision-history plugins.

## Install and use

Download the signed APK from [Releases](https://github.com/neuronsarealsocool/note-version-recorder/releases). Android 8.0 or newer is required.

1. Select each shared vault root separately through Android's folder picker. Choose the same folder your note editor and Resilio use.
2. Allow folder access and notifications, then tap **Start recording**. Wait for the initial baseline scan before editing.
3. Include `_mobile-edit-history` and its JSON files in Resilio Sync. Allow background battery use if your phone restricts the recorder.
4. Open the matching desktop vault. Updated receivers import the journal automatically:
   - [Daily Statistics](https://github.com/neuronsarealsocool/obsidian-daily-statistics) 1.2.0+
   - [Track Changes](https://github.com/neuronsarealsocool/obsidian-track-changes) 0.10.0+
   - [Edit History](https://github.com/neuronsarealsocool/obsidian-edit-history) 0.5.0+

Use one recorder per shared folder. The app never modifies note files and has no network permission; Resilio performs the transfer. The app shows immediate start feedback and live scan/publication progress. A foreground notification provides recording status and a Stop action. Stop recording before changing the folder list.

## What gets recorded

UTF-8 `.md`, `.txt`, `.csv`, `.htm`, and `.html` files up to 4 MiB. Checks run every 5 seconds by default, optionally 2 or 15 seconds; a periodic content check also detects changes whose metadata stayed the same. Hidden/configuration folders, dependency caches such as node_modules, and the journal itself are excluded.

The first observation establishes a private baseline without exporting every existing note. Later changed versions include full before/after text, a unique ID, source file modification time when available, observation time, and the phone's local date and timezone offset. The journal lives at `_mobile-edit-history/v1/<device-id>/<event-id>.json` inside each selected vault. Incomplete writes use a `.part` suffix. Private pending records are retained until published contents have been verified.

This captures saved versions observed during checks, not every keystroke. Saves overwritten between checks, unsaved editor text, and intermediate versions lost while the recorder was stopped cannot be recovered. Android can restrict or stop background work. File timestamps and the phone clock affect date attribution. Full-text history has no automatic expiry and consumes storage; it can contain text later deleted from the notes.

## Build

Run PowerShell 7 on Windows with a Java 21 runtime that includes `keytool`:

```powershell
./prepare-tools.ps1
./test.ps1 -Java 'C:/path/to/java.exe'
./build.ps1 -JavaBin 'C:/path/to/java/bin'
./package.ps1
```

The helper downloads Android Platform 35, Build Tools 35 and Eclipse ECJ. SDK archives are checked against their published SHA-1 hashes. No Gradle or third-party Android runtime libraries are needed. Outputs are under `build/`.

Signing material is generated in the sibling `build-tools/signing` folder and is excluded from the repo and source bundles. Retain that private key for compatible future APK updates.

## Validation

The recording core has JVM regression checks for baselines, intermediate versions, duplicate suppression, dates, and safe paths. The APK has passed Java/DEX compilation, manifest and v2/v3 signature verification, and installation/launch over ADB on an Android 12 phone. Start/Stop recovery, retained access to two selected folders, actual before/after recording, and delivery of a test record through Resilio into desktop receivers were also checked on that phone. Desktop journal ordering, retry, idempotence, date correction and history preservation are covered by receiver tests.

Actual folder-provider access, notification permission, background/boot behavior and end-to-end Resilio delivery still need a device check. Before relying on important notes, save three versions of a disposable note with pauses longer than the check interval, sync, and confirm the intermediate version appears in desktop history.
