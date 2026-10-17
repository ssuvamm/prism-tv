# Contributing

Thanks for wanting to help. A few things make reviews quicker.

## Before you start

- For anything bigger than a small fix, open an issue first so we can agree on the approach.
- Problems with individual channels (dead links, wrong logos, wrong country) belong in
  [iptv-org/iptv](https://github.com/iptv-org/iptv). This app only displays that data.

## Working on the code

- JDK 17 and the Android SDK with platform 37 are all you need. Open the project in Android Studio or build with
  `./gradlew assembleDebug`.
- Keep the style of the surrounding code: Kotlin official style, Compose for all UI, no new dependencies unless
  there is a good reason.
- Performance matters here. The main lists hold over ten thousand items, so avoid work in composition,
  keep list items keyed, and do filtering off the main thread.
- Test on a real device if you touch the player. Emulators are fine for UI work.

## Pull requests

- One topic per pull request.
- Describe what changed and how you tested it.
- Add a line under `## [Unreleased]` in `CHANGELOG.md` if users will notice the change.

By contributing you agree that your work is released under the MIT License.
