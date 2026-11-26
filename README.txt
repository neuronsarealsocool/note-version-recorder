Note Version Recorder 1.0.0

Android 8+ standalone foreground recorder for user-selected shared note folders.
Works with editors that save UTF-8 note files, including Zettel Notes. No Obsidian installation or network permission is needed on the phone.

Install the signed APK, select each vault root separately through Android's folder picker, grant notifications, and tap Start. Let the first baseline scan finish. Keep _mobile-edit-history included in Resilio Sync. Allow background battery use if your phone restricts the service. Stop recording before changing the list of folders. A selected folder can be removed without deleting its history.

It polls file metadata every 5 seconds by default, verifies unchanged contents about every minute, and stores full before/after text for versions it observes. Rapid overwritten saves can be missed. Phone clock and file timestamps determine source dates. Notes are never modified. Journal writes are queued in private storage and verified before the queue is acknowledged. The service uses Android's specialUse foreground-service type and can resume after a phone reboot when enabled. Android may still stop it.

Build on Windows:
  ./prepare-tools.ps1
  ./test.ps1 -Java <path-to-java.exe>
  ./build.ps1 -JavaBin <directory-containing-java-and-keytool>

The helper downloads Android Platform 35 and Build Tools 35 from Google's SDK repository and verifies their published SHA-1 values. It downloads Eclipse ECJ 3.40.0 from Maven Central to compile Java without a full JDK. No Gradle, external Android libraries, or internet access at app runtime is required. SDK packages include their upstream license files.

build.ps1 creates signing/note-recorder.p12 and signing/password.txt in the sibling build-tools folder when first used. Retain these private files for future APK updates; they are intentionally excluded from delivery bundles. They are not needed to install the signed APK. A fresh signing key cannot update an already-installed APK signed by the old key.

Validation performed: Java compile, DEX generation, APK manifest verification, v2/v3 signature verification, pure-Java recording-decision tests, and desktop journal/import simulations. The APK was installed and launched successfully over ADB on an Android phone. Folder-provider, notification, background, boot, battery-policy and Resilio end-to-end tests still require device setup. Perform the disposable-note check in the companion SETUP guide before relying on it for important notes.

Limits: UTF-8 notes up to 4 MiB, encoded journal events up to 12 MiB; extensions .md .txt .csv .htm .html. Hidden folders and the recorder's journal are excluded. Full histories have no automatic expiry. Use one recorder per shared folder to avoid re-recording another device's incoming changes as independent activity.
