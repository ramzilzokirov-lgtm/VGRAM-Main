# VGRAM Plugin API v1

This API is implemented as a **declarative JSON manifest**. It does not execute downloaded Java, Kotlin, DEX, JAR, native libraries, or scripts. Imported manifests are stored locally and can be disabled or removed from **Settings → VGRAM Studio → Plugins VGRAM**.

## Supported manifest

Save the following as `dusk-theme.json` and import it from the plugin manager:

```json
{
  "id": "org.vgram.theme.dusk",
  "name": "Dusk Gold",
  "version": "1.0.0",
  "apiVersion": 1,
  "description": "A muted gold accent for the VGRAM interface.",
  "capabilities": ["theme"],
  "accentColor": "#C5B878"
}
```

## Manifest fields

- `id`: stable identifier, 3–80 characters; letters, digits, `.`, `_`, and `-` only.
- `name`: required display name, up to 60 characters.
- `version`: required version label, up to 32 characters.
- `apiVersion`: must be `1`.
- `description`: optional, up to 240 characters.
- `capabilities`: non-empty list containing only `theme` or `animationPreset`.
- `accentColor`: optional `#RRGGBB` color for a theme manifest.
- `animationPreset`: optional built-in value `soft` or `none`.

Unknown fields and unsupported capabilities are rejected. Manifest files are limited to 64 KiB. Enabling a theme can change the VGRAM accent color; enabling an animation preset toggles the built-in soft animations. A plugin does not receive access to Telegram messages, contacts, account credentials, or network APIs.

## Trust and limitations

- Import only manifests from sources you trust. A checksum by itself does not prove who published a plugin.
- Plugins are local to this device and are not synchronized to other devices.
- This first API is not a marketplace and does not load executable code.
- Disabling or removing a plugin is available in the in-app plugin manager.
- VGRAM profile badges and owner-only badge issuance are separate features; a plugin cannot grant badges or claim Telegram verification.
- API changes must be versioned and documented. Incompatible API versions are rejected.
