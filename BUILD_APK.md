# Build APK

## Android Studio

1. Open this folder in Android Studio.
2. Let Gradle sync.
3. Connect your Android phone with USB debugging enabled, or build the APK only.
4. Use `Build > Build APK(s)`.
5. Send `BIX.apk` to your phone and install it.

## GitHub Actions

1. Upload this repo to GitHub.
2. Go to the Actions tab.
3. Run `Build Debug APK`.
4. Download `BIX-debug-apk`.
5. Install the APK on your phone.

## First Test

1. Open the app.
2. Tap `Choose file, ZIP, or folder`.
3. Choose `File / ZIP` for normal downloads, or `Extracted Folder` for a folder containing `manifest.json` or `level.dat`.
4. Tap `Import with Minecraft`.
5. Confirm Minecraft reports a successful import before activating the pack.
6. If a pack itself is broken, remove it using Minecraft. The installer never writes into Minecraft's data folders.

## Level Folder Test

1. Keep Minecraft closed.
2. Tap `Choose file, ZIP, or folder`, then choose `Extracted Folder`.
3. Select a Bedrock world folder containing `level.dat`, then tap `Import with Minecraft`.
4. Minecraft should import the generated `.mcworld` without requiring External storage mode.
