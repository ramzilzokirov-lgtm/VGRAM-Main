# VGRAM feature plan

This document tracks the requested product scope. A feature is not complete merely because it is listed here; mark it complete only after its code is integrated and tested on a device.

## Foundation
- [x] New repository, separate from the existing `VGRAM` repository.
- [x] GitHub Actions bootstrap that fetches Telegram Android source and attempts a debug APK build.
- [ ] Pin and audit an upstream Telegram source revision.
- [ ] Verify a successful build and install the artifact on a real Android device.
- [ ] Configure VGRAM's own Telegram API ID/hash and release signing.

## Branding and interface
- [ ] Rename the app and beta label to VGRAM (bootstrap workflow applies these changes at build time).
- [ ] Create and integrate an original VGRAM icon inspired by Uzi from *Murder Drones*; do not use Telegram's standard icon.
- [ ] Muted yellow accent, Material 3-inspired styling, selectable accent colors.
- [ ] Liquid-glass-inspired surfaces and smooth animations, with a reduced-motion option.
- [ ] Custom profile background.
- [ ] Check anti-burn-in behavior for static UI elements on OLED displays.
- [ ] Review login, phone-number, SMS/Telegram code and 2-step-verification flows without bypassing Telegram security.

## VGRAM pages and updates
- [ ] Developers page showing project owner `ramzilzokirov-lgtm` and the assistant role.
- [ ] VGRAM updates page backed by GitHub Releases, with checksum/signature verification before installation.
- [ ] Publish versioned APK releases and provide release notes and rollback guidance.
- [ ] Document update behavior and permissions in the app.

## Badges
- [x] Local badge manager UI: TESTER, OG/OLD, FRIENDS, TEAM can be manually assigned/revoked by Telegram user ID on this device.
- [ ] Secure owner-only grants: not complete until backed by a server that authenticates the project owner.
- [x] The badge manager warns that local badges are not Telegram-verified and are not tamper-resistant.
- [ ] Server-side badge storage and profile/name rendering: not implemented; local grants do not sync to other users or devices.

## Plugin system
- [ ] Versioned, permissioned plugin API.
- [ ] Start with declarative themes, settings and animation presets; do not execute arbitrary downloaded code.
- [ ] Plugin compatibility checks, explicit consent, disable/uninstall controls and documentation.
- [ ] See [PLUGIN_API.md](PLUGIN_API.md).

## Current status note\nThe badge manager is an initial local tool, not a completed public badge system. Profile background selection currently previews the selected image in VGRAM settings; applying it to Telegram's actual profile screen still needs integration. The Liquid Glass and reduced-motion controls are not yet verified to affect every Telegram screen.\n\n## Definition of done
A feature is done only when its source patch is committed, the CI build succeeds, and the relevant behavior is checked on a device. This repository's bootstrap build does not yet mean the full feature list is implemented.
