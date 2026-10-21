# VGRAM-Main

VGRAM is an independent Android client project based on the public Telegram for Android source code. It is intended to connect to Telegram's infrastructure, not to operate a separate messaging network.

## Current state

This repository contains the VGRAM build pipeline and project documentation. The workflow fetches the upstream Telegram Android source at build time, applies the VGRAM name/version/update-check branding changes, builds a debug APK, and publishes it as a GitHub Actions artifact.

**Important:** this first build pipeline is a bootstrap, not a claim that every planned VGRAM feature is already implemented. The custom theme, Uzi/Murder Drones icon, profile background, badges, in-app updates page, and safe plugin API still require source patches and device testing.

## Build an APK (phone-friendly)

1. Open the **Actions** tab.
2. Select **VGRAM Android Build**.
3. Press **Run workflow**.
4. Wait for the run to finish, then open its summary and download the `VGRAM-debug-apk` artifact.

The first build downloads the upstream source and its submodules, so it can take a while. GitHub-hosted runners may run out of memory while compiling Telegram's native libraries; if that happens, the workflow logs will show the failing step.

## Telegram API configuration

For a distributable client, obtain your own `api_id` and `api_hash` from https://my.telegram.org and add these repository Actions secrets:

- `TG_API_ID`
- `TG_API_HASH`

The workflow applies them to `BuildVars.java` when both are configured. Without them, the upstream source's development defaults are retained for a prototype build; do not treat that as a release configuration. Never commit API credentials, signing keys, or bot tokens.

## Project rules

- The old `VGRAM` repository is left untouched.
- Upstream Telegram source is fetched into the Actions runner and is not copied into this repository yet.
- VGRAM must be clearly identified as an unofficial third-party client and must use its own icon before public distribution.
- Keep Telegram's applicable licences and notices when redistributing source or binaries.
- Do not load arbitrary native/JAR code as plugins. The planned plugin system should use a permissioned, versioned API and declarative manifests.

## Planned VGRAM features

See [VGRAM_FEATURES.md](VGRAM_FEATURES.md) and [PLUGIN_API.md](PLUGIN_API.md) for the implementation plan and security constraints.
