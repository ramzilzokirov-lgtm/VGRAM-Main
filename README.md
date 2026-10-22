# VGRAM

VGRAM is an unofficial Android Telegram client based on the original Telegram for Android source.

- Upstream source: https://github.com/DrKLO/Telegram
- Upstream commit used for the last source import: see [TELEGRAM_UPSTREAM_COMMIT.txt](TELEGRAM_UPSTREAM_COMMIT.txt)
- VGRAM feature specification: [VGRAM_FULL_SPEC.md](VGRAM_FULL_SPEC.md)
- Plugin API draft: [PLUGIN_API.md](PLUGIN_API.md)

VGRAM is not affiliated with Telegram. Telegram API credentials and Firebase configuration must be configured for your own application before distributing builds. See the upstream README preserved as TELEGRAM_UPSTREAM_README.md and the Telegram source license.


## API credential and regeneration checks

- Run **Actions → VGRAM Telegram Integrity Check → Run workflow** to compare the configured `TG_API_ID` and `TG_API_HASH` repository secrets with the constants in the currently fetched official Telegram Android source.
- Configure both values under **Settings → Secrets and variables → Actions**. Never commit credentials into source files.
- The check hides the hash in logs. A mismatch only means the configured values differ from the current upstream source; it does not independently prove that the credentials are invalid.
- **VGRAM Android Build** fetches the official Telegram source afresh and applies the VGRAM bootstrap patch. If upstream source changes break a patch anchor, the workflow should fail rather than silently claim a complete regeneration.
- This is an unofficial client. Telegram server IP addresses are not hardcoded: the upstream client manages its data-center configuration. The API ID/hash are application credentials, not server IPs.
