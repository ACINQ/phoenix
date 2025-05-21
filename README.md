[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Testnet Build](https://github.com/ACINQ/phoenix/actions/workflows/testnet-build.yml/badge.svg)](https://github.com/ACINQ/phoenix/actions?query=testnet-build)
[![Download Testnet APK](https://img.shields.io/badge/Download-Testnet%20APK-green?style=flat&logo=android&logoColor=white)](https://acinq.co/pub/phoenix/phoenix-testnet-latest.apk)

![Phoenix Logo](.readme/phoenix_text.png)

Phoenix is a Bitcoin wallet developed by [ACINQ](https://acinq.co), that allows you to send and receive bitcoin securely over the Lightning Network. It is self-custodial, which means that **you hold the keys** of the wallet. It provides a simple and clean UX. Thanks to native Lightning support, payments are faster and cheaper.

---

:rotating_light: This wallet is self-custodial. It means that, when creating a new wallet, a 12-words recovery phrase is generated. Only you have it. It is your responsibility to make a backup of that recovery phrase. It gives full access to your funds, so do not share it with anyone. If you lose the recovery phrase, your funds are lost.

---

Head to our website for more information:
- [FAQ](https://phoenix.acinq.co/faq)
- [Terms](https://phoenix.acinq.co/terms)
- [Privacy](https://phoenix.acinq.co/privacy)

## Download

Phoenix is available for Android 8+ and iOS 15+:

&nbsp;&nbsp;&nbsp;[<img src="https://toolbox.marketingtools.apple.com/api/v2/badges/download-on-the-app-store/black/en-us"
  alt="Get it on iOS"
  height="53">](https://apps.apple.com/us/app/phoenix-wallet/id1544097028)
  
[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png"
    alt="Get it on Google Play"
    height="80">](https://play.google.com/store/apps/details?id=fr.acinq.phoenix.mainnet)
    
&nbsp;&nbsp;&nbsp;[<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png"
    alt="Get it on Obtainium"
    height="53">](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22fr.acinq.phoenix.mainnet%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FACINQ%2Fphoenix%22%2C%22author%22%3A%22ACINQ%22%2C%22name%22%3A%22Phoenix%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22filterReleaseNotesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22verifyLatestTag%5C%22%3Atrue%2C%5C%22sortMethodChoice%5C%22%3A%5C%22date%5C%22%2C%5C%22useLatestAssetDateAsReleaseDate%5C%22%3Afalse%2C%5C%22releaseTitleAsVersion%5C%22%3Afalse%2C%5C%22trackOnly%5C%22%3Afalse%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22matchGroupToUse%5C%22%3A%5C%22%5C%22%2C%5C%22versionDetection%5C%22%3Atrue%2C%5C%22releaseDateAsVersion%5C%22%3Afalse%2C%5C%22useVersionCodeAsOSVersion%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22appName%5C%22%3A%5C%22phoenix%5C%22%2C%5C%22appAuthor%5C%22%3A%5C%22ACINQ%5C%22%2C%5C%22shizukuPretendToBeGooglePlay%5C%22%3Afalse%2C%5C%22allowInsecure%5C%22%3Afalse%2C%5C%22exemptFromBackgroundUpdates%5C%22%3Afalse%2C%5C%22skipUpdateNotifications%5C%22%3Afalse%2C%5C%22about%5C%22%3A%5C%22%5C%22%2C%5C%22refreshBeforeDownload%5C%22%3Afalse%7D%22%2C%22overrideSource%22%3Anull%7D)
    
[<img src="https://github.com/machiav3lli/oandbackupx/blob/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png"
    alt="Get it on GitHub"
    height="80">](https://github.com/ACINQ/phoenix/releases)

Package ID:
```
fr.acinq.phoenix.mainnet
```
SHA-256 Fingerprint:
```
ED:55:0B:D5:D6:07:D3:42:B6:1B:BB:BB:94:FF:D4:DD:E4:3F:84:51:71:F6:3D:3A:E4:75:73:A9:5A:13:26:29
```

## Build and test Phoenix

Phoenix is separated in 3 modules:
- `phoenix-shared`: business logic written in Kotlin, shared between the iOS and the Android applications. Uses [lightning-kmp](https://github.com/ACINQ/lightning-kmp) for everything Lightning/Bitcoin related.
- `phoenix-android`: the UI for the new Android application, written in Kotlin and Jetpack Compose.
- `phoenix-ios`: the UI for the iOS application, written in Swift.

Instructions to build the iOS and the Android apps are provided [here](https://github.com/ACINQ/phoenix/blob/master/BUILD.md).

## Contribute

We use GitHub for bug tracking. Search [the existing issues](https://github.com/ACINQ/phoenix/issues) for your bug and create a new one if needed.

You can also contribute to the project by submitting pull requests to improve the codebase or bring new features. Pull requests will be reviewed by members of the ACINQ team.

To contribute to Lightning in general, take a look at the [Eclair repository](https://github.com/ACINQ/eclair) for routing nodes, or the [lightning-kmp repository](https://github.com/ACINQ/lightning-kmp) for mobile nodes.

## Translate the app

If you want to contribute to the translation effort, consult the guidelines provided in [TRANSLATION.md](https://github.com/ACINQ/phoenix/blob/master/TRANSLATION.md)

## Support

For troubleshooting and questions, visit [our support page](https://phoenix.acinq.co/support).

## License

Phoenix is released under the terms of the Apache 2.0 license. See LICENSE for more information.
