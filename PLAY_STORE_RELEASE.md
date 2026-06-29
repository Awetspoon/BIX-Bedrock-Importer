# BIX Google Play Release Checklist

## Code and build status

- Package ID: `com.bix.importer` — treat this as permanent after the first Play upload.
- Target SDK: 35 (Android 15).
- Minimum SDK: 26 (Android 8).
- Minecraft compatibility: checked against the current Bedrock Android update line, including the 26.32 hotfix released on 25 June 2026. BIX reads the installed Minecraft version dynamically.
- Android permissions: internet only, used to check GitHub Releases for app updates.
- No advertising, analytics, accounts, or tracking SDKs.
- Release output: Android App Bundle (`app-release.aab`).
- Google Play App Signing is recommended; keep the upload keystore and passwords backed up securely.

## Before the first upload

1. Replace the contact placeholder in `PRIVACY_POLICY.md`.
2. Publish that policy at a public HTTPS URL, such as GitHub Pages, and enter the URL in Play Console.
3. Create the app in Play Console using package ID `com.bix.importer`.
4. Enrol in Play App Signing and generate an upload key.
5. Complete App content forms: Data safety, Ads, App access, Content rating, Target audience, and Government apps where applicable.
6. Add a support email, feature graphic, and real phone screenshots. The 512×512 Play icon is provided at `play-store/icon-512.png`.
7. Upload the signed `BIX.aab` to Internal testing first and run the Play pre-launch report.
8. Test behaviour pack, resource pack, combined `.mcaddon`, ZIP, extracted folder, and world imports on more than one Android version.

## Suggested Data safety declaration

BIX does not collect data for the developer and does not upload selected add-ons, worlds, or folders. It does make a simple HTTPS request to GitHub Releases to check for app updates. Its core user-requested import action also transfers the selected prepared file directly to the separately installed Minecraft app. Google Play's guidance says direct on-device transfer to another app can count as sharing. A conservative declaration is:

- Data collected: No.
- Data shared: Files and docs.
- Purpose: App functionality.
- User action: The user explicitly selects the content and taps Import.
- Retention: Android removes the temporary activity grant when Minecraft's receiving activity stack finishes. BIX deletes cached imports older than 24 hours on a later app run, and Android may clear cache sooner.
- Update check: BIX contacts GitHub Releases over HTTPS for app version information. No selected user files are included in that request.

The Play account owner remains responsible for the final declaration and should re-check it whenever app behaviour changes.

## Suggested store listing

App name: `BIX – Bedrock Import eXpress`

Short description:

`Safely prepare and import Bedrock packs, ZIPs, folders and worlds.`

Full description:

`BIX helps Android users prepare Minecraft Bedrock content for import. Choose a .mcpack, .mcaddon, .mcworld, ZIP, or extracted folder. BIX validates and packages the content locally, then hands it to Minecraft using temporary read access. It supports standalone behaviour packs, standalone resource packs, combined add-ons and worlds. BIX never writes directly into Minecraft's data folders and does not use ads, analytics or tracking. Internet access is used only to check GitHub Releases for BIX updates. Java Edition mods are detected and rejected with a clear explanation. BIX is independent and is not affiliated with Mojang Studios or Microsoft.`

Suggested category: Tools.

## GitHub release secrets

Add these repository Actions secrets before running **Build Play Release**:

- `BIX_KEYSTORE_BASE64` — base64-encoded upload keystore.
- `BIX_STORE_PASSWORD`
- `BIX_KEY_ALIAS`
- `BIX_KEY_PASSWORD`

The workflow tests, runs release lint, builds a signed bundle, renames it to `BIX.aab`, and uploads it as a private workflow artifact.
