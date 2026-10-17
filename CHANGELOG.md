# Changelog

All notable changes to Prism TV are written down here. The format loosely follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow [Semantic Versioning](https://semver.org/).

When you cut a release, add a section with the version number in square brackets. The release workflow copies
that section into the GitHub release notes.

## [Unreleased]

## [1.1.0] - 2026-10-17

### Added
- A "Support Prism TV" section in Settings with PayPal and UPI donation options. Tapping UPI opens your UPI
  app and also copies the UPI ID, in case the app won't accept a payment started from another app

## [1.0.0] - 2026-10-13

First public release.

### Added
- Channel catalog built from the iptv-org database: about 12,000 channels with names, logos, countries,
  languages, categories and regions, refreshed quietly every 36 hours
- Search across names, countries, languages and genres, with filters and sorting
- Browse by category, country, language and region
- Favourites, playlists (with M3U import and export), watch history and hidden channels
- Long-press menu with play, VLC, open with, source picker, favourite, playlist, hide, copy and share
- Built-in Media3 player with an 8 second preload, brightness and volume swipe gestures, pinch to crop,
  lock, slide-in channel list, picture-in-picture, sleep timer and track selection
- Pause live TV for up to 5 minutes, recorded to local cache. While paused or behind live, 10 second
  skip buttons, double-tap seek and a seek bar are available, plus a Go live button
- Automatic fallback to a channel's other sources when one fails
- DNS over HTTPS for all traffic, Cloudflare by default
- Six visual styles (Material You, Glass, Liquid, AMOLED, Neon, Sunset), light and dark modes, accent colours
- Backup and restore of favourites, playlists and settings
- Android TV, Google TV and Fire TV support: TV launcher banner, full D-pad navigation with visible focus,
  remote keys in the player, tile layout by default
- Runs on Android 6.0 and newer, with bundled Let's Encrypt roots for Android 6 and 7.0
- Quality, audio, subtitle and source buttons directly in the player bar

[Unreleased]: https://github.com/ssuvamm/prism-tv/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/ssuvamm/prism-tv/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/ssuvamm/prism-tv/releases/tag/v1.0.0
