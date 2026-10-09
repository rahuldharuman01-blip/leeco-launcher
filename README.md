# LeEco Glass Launcher

Custom home launcher for the LeEco Super4 X40 (EUI 5.8, Android 5/6).
Works on Android 4.4 and newer.

## Remote control
- OK = open
- Long-press OK (or Menu) on an app = add / remove favorite
- Back = go to Home screen

## Get the APK (no Android Studio needed)
1. Upload everything in this folder (including the `.github` folder) to a GitHub repo.
2. Open the **Actions** tab, choose **Build APK**, click **Run workflow**.
3. When it turns green, open the run and download **LeEcoGlassLauncher-apk** under Artifacts.
4. Unzip it, copy `LeEcoGlassLauncher.apk` to a USB drive, open it on the TV, allow unknown sources.
5. Press Home on the remote, choose this launcher, tap **Always**.

## Set up updates
1. In `app/src/main/java/com/rahul/leecoglasslauncher/MainActivity.java`, change
   `UPDATE_URL` to:
   `https://raw.githubusercontent.com/rahuldharuman01-blip/leeco-launcher/main/update.json`
2. In `update.json`, replace `rahuldharuman01-blip` and `leeco-launcher` in `apkUrl` the same way.
3. Rebuild once and install. From then on: Settings > Launcher update.

## Releasing a new version
1. Make your changes and raise `versionCode` (and `versionName`) in `app/build.gradle`.
2. Raise `versionCode`, `versionName` and `notes` in `update.json` to match.
3. Upload the changed files to GitHub.
4. Releases > Draft a new release > create a tag like `v2.2.0` > Publish.
   The workflow builds the APK and attaches it to the release automatically.
5. On the TV: Settings > Launcher update > Download & install.

`update.json` fields: `versionCode` (a number, must be higher than the installed one),
`versionName` (text shown to you), `apkUrl` (direct download link), `notes` (shown in the dialog).

## Notes
- All builds are signed with `app/launcher.keystore` (password `android`) so updates install
  over older versions. Do not delete it.
- HDMI/AV switching only works if the LeEco firmware exposes its inputs to apps;
  otherwise the launcher tells you to use the remote's SOURCE button.
