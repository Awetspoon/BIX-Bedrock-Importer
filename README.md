<p align="center">
  <img src="play-store/icon-512.png" alt="BIX app logo" width="180">
</p>

<h1 align="center">BIX — Bedrock Import eXpress</h1>

<p align="center">A simple Android importer for Minecraft Bedrock add-ons, packs, ZIP downloads, folders, and worlds.</p>

<p align="center">
  <a href="https://github.com/Awetspoon/BIX-Bedrock-Importer/releases/latest"><strong>Download the latest BIX APK</strong></a>
</p>

## What BIX does

BIX is made for Minecraft Bedrock / Pocket Edition on Android.

It checks the Bedrock content you choose, prepares it when needed, then opens Minecraft so Minecraft can perform the final import.

For most downloads, choose **File / ZIP**. Choose **Extracted Folder** only when you already unzipped the add-on or world and can see `manifest.json` or `level.dat` inside.

## Features

- Import Bedrock add-ons, behaviour packs, resource packs, and worlds
- Choose `.mcaddon`, `.mcpack`, `.mcworld`, `.zip`, or extracted folders
- Automatically detect and prepare the selected content
- Prepare separate behaviour/resource pack folders as a standard `.mcaddon`
- Import world folders as `.mcworld`
- Reject Java Edition content with a clear message
- Import without changing Minecraft to External storage mode

## Safe by design

BIX does not write into Minecraft's data, global resource, pack, or world folders.

Selected files are processed locally on your device. BIX gives Minecraft temporary read access only when you tap **Import with Minecraft**.

## Not supported

Java Edition mods are not compatible with Minecraft Bedrock on Android. Files such as `.jar`, `pack.mcmeta`, `assets`, `data`, `META-INF`, or `net` are usually Java Edition content.

## Download

Go to [GitHub Releases](https://github.com/Awetspoon/BIX-Bedrock-Importer/releases/latest), download `BIX.apk`, then install it on your Android phone.

If Android warns you about installing from your browser or file manager, allow that source only if you trust this download.

## Build APK On Laptop

For developers, open the repo in Android Studio, wait for Gradle sync, then use:

```text
Build > Build APK(s)
```

The debug APK will be created at:

```text
app/build/outputs/apk/debug/BIX.apk
```

## Build with GitHub Actions

Run:

```text
Actions > Build Debug APK
```

Get the artifact:

```text
BIX-debug-apk
```

## Notes

This app deliberately does not request access to Minecraft's data folders. Minecraft performs the final import itself.

The separate level-folder import does not require access to Minecraft's storage. It reads a folder selected by the user, packages its contents with `level.dat` at the archive root, and hands the generated `.mcworld` to Minecraft.

For Google Play preparation, signing and policy steps, see `PLAY_STORE_RELEASE.md` and `PRIVACY_POLICY.md`.

BIX is an independent utility and is not affiliated with, endorsed by, or sponsored by Mojang Studios or Microsoft.
