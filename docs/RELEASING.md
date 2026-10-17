# Releasing

Releases are built by GitHub Actions. You push a tag, the workflow builds a signed universal APK and
publishes a GitHub release with notes taken from `CHANGELOG.md`.

## One-time setup: a signing key

Android only lets users update an app if every version is signed with the same key, so create one
key and keep it safe. If you lose it, people will have to uninstall and reinstall to get updates.

```sh
keytool -genkeypair -v -keystore prismtv-release.jks -alias prismtv \
  -keyalg RSA -keysize 4096 -validity 10000
```

Then add four repository secrets under **Settings > Secrets and variables > Actions**:

| Secret | Value |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | the keystore file, base64-encoded (see below) |
| `SIGNING_STORE_PASSWORD` | the keystore password |
| `SIGNING_KEY_ALIAS` | `prismtv` (or whatever alias you used) |
| `SIGNING_KEY_PASSWORD` | the key password |

To get the base64 string:

```sh
# Linux / macOS
base64 -w0 prismtv-release.jks        # macOS: base64 -i prismtv-release.jks

# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("prismtv-release.jks"))
```

Never commit the `.jks` file. It is already in `.gitignore`.

If the secrets are missing, the workflow still produces an APK, signed with a throwaway debug key, and
prints a warning. That is fine for a test, but not for a real release.

### Signing locally

To build a signed release on your own machine, create `keystore.properties` in the project root
(also ignored by git):

```properties
storeFile=/absolute/path/to/prismtv-release.jks
storePassword=...
keyAlias=prismtv
keyPassword=...
```

and run `./gradlew assembleRelease`.

## Cutting a release

1. Move the items under `## [Unreleased]` in `CHANGELOG.md` into a new section, for example
   `## [1.1.0] - 2026-11-02`, and update the links at the bottom.
2. Commit that change.
3. Tag and push:

   ```sh
   git tag v1.1.0
   git push origin main --tags
   ```

The **Release** workflow then:

- sets `versionName` to `1.1.0` and `versionCode` to `10100` (major * 10000 + minor * 100 + patch)
- builds `PrismTV-1.1.0-universal.apk` with R8 and signs it
- writes `SHA256SUMS.txt`
- creates the GitHub release with the changelog section as its notes, followed by GitHub's
  auto-generated list of merged pull requests

Tags with a suffix, such as `v1.1.0-beta.1`, are published as pre-releases.

You can also re-run a release for an existing tag from the Actions tab (**Release > Run workflow**).

## Other things to update when you fork

- `prism.repoUrl` in `gradle.properties` sets the "Source code" and "Report a problem" links in the app.
- The badge URLs and changelog links in `README.md` and `CHANGELOG.md` point at `ssuvamm/prism-tv`.
