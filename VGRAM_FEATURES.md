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
- [ ] VGRAM-only profile badges: TESTER, OG/OLD, FRIENDS, TEAM (labels can be revised).
- [ ] Only the project owner can grant or revoke badges.
- [ ] Do not represent a client-side badge as a Telegram-verified identity.
- [ ] Store grants server-side if badges must be tamper-resistant; a client-only implementation can be modified by the user.

## Plugin system
- [ ] Versioned, permissioned plugin API.
- [ ] Start with declarative themes, settings and animation presets; do not execute arbitrary downloaded code.
- [ ] Plugin compatibility checks, explicit consent, disable/uninstall controls and documentation.
- [ ] See [PLUGIN_API.md](PLUGIN_API.md).

## Definition of done
A feature is done only when its source patch is committed, the CI build succeeds, and the relevant behavior is checked on a device. This repository's bootstrap build does not yet mean the full feature list is implemented.
