# Releasing TPlay and updating it with Obtainium

## Release
1. Make sure `main` is green (the CI badge workflow runs the unit tests).
2. Tag and push: `git tag v1.1.0 && git push origin v1.1.0`
3. GitHub Actions (`Release`) runs the tests, builds the signed APK and publishes it as a GitHub Release with the APK and a SHA-256 file.

`versionName` comes from the tag, `versionCode` is the number of commits, so it always grows: make at least one commit between two tags.

## Signing key (do not lose it)
The key lives in `~/.tplay-signing/tplay-release.jks` (password in `keystore-password.txt` next to it) and as GitHub secrets `TPLAY_KEYSTORE_B64` and `TPLAY_KEYSTORE_PASSWORD`. Android only installs an update when it is signed with the same key, so back this folder up somewhere safe (password manager or encrypted drive). It is never committed.

## Obtainium on the phone
1. Install Obtainium (github.com/ImranR98/Obtainium/releases).
2. Add app, source URL `https://github.com/Sinnv2710/TPlay`.
3. The repo is public, so no token is needed.
4. Obtainium installs the first release and then offers every new tag as an update.

First install note: a build signed with the debug key (adb installs) cannot be updated by the release-signed APK. Uninstall once, then install through Obtainium.
