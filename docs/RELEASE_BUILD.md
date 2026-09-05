# SEAM Chat Release Build

The Android release workflow builds a debug APK for the initial beta and publishes it as a GitHub Actions artifact.

## Build

Run **SEAM Chat Android Release** manually from GitHub Actions, or push a `v*` tag.

The artifact is named `seam-chat-debug-apk`.

This is an initial beta/debug build, not a production-signed APK. Production release signing should be added only after the build and smoke tests pass.
