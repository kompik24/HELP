# HELP applications

Two installable clients for the existing HELP service:

- Android smartphone app: `apps/android` (minimum Android 7.0 / API 24).
- Windows desktop app: `apps/windows` (Electron installer).

Both clients open the existing HTTPS service at `https://help-apk-catalog.onrender.com/`. The browser application remains the source of the catalog and administration interface, so category, APK upload, delete, and download behavior stays shared.

## Build artifacts

GitHub Actions workflow `Build HELP apps` builds:
- Android debug APK (`app-debug.apk`).
- Windows NSIS installer (`HELP-Setup-1.0.0.exe`).

Run it from GitHub: Actions → Build HELP apps → Run workflow. Download the APK and EXE from the workflow run's Artifacts section.

## Branding

Both installable apps now use the shared HELP orange-and-charcoal icon. The vector source is `apps/branding/help-icon.svg`; the Android launcher uses native vector/adaptive resources, and the Windows build generates its `.ico` file automatically before packaging.

## Important

These are online clients: internet access and the HELP server are required. The Android build is a debug APK, suitable for initial installation/testing, not a signed Play Store release. A release signing key should be configured privately before publishing a production release.
