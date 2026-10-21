# VGRAM plugin API — initial safety specification

This is a design document, not an implemented runtime.

## Initial supported plugin types

The first plugin API should support declarative data only:
- Theme palette and typography presets.
- Animation presets selected from an allowlist of built-in transitions.
- Settings-page descriptors with validated labels and bounded values.

Do not support loading arbitrary Java, Kotlin, DEX, JAR, native libraries, scripts, or executable code from downloaded plugins in the initial version.

## Manifest

A future plugin manifest may be a JSON document with:
- `id`: reverse-domain identifier, unique and stable.
- `name`: display name.
- `version`: semantic version.
- `apiVersion`: supported VGRAM plugin API version.
- `description`: short description.
- `capabilities`: explicit allowlisted capabilities (initially only `theme`, `animationPreset`, and `settings`).

The manifest format alone does not grant permissions. Unknown fields and capabilities must be rejected. Validate lengths, ranges, color formats and animation identifiers. Never allow a plugin to request Telegram account data, messages, contacts, tokens, filesystem access or network access through this first API.

## Trust and distribution

- Show the source, version and requested capabilities before enabling a plugin.
- Keep plugins disabled by default and provide a one-tap disable/remove action.
- Verify signed release metadata and artifact hashes before install.
- Do not treat a checksum from the same untrusted download location as proof of publisher identity.
- Do not bundle secrets in the APK or plugin.
- Keep an audit log of owner-issued badge changes separately from plugin settings.

## Compatibility

Increment `apiVersion` only with documented migration behavior. Reject incompatible plugins safely and keep the base messenger functional if a plugin is invalid.
