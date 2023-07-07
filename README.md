[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Testnet Build](https://github.com/ACINQ/phoenix/workflows/TESTNET%20Build/badge.svg)](https://github.com/ACINQ/phoenix/actions?query=workflow%3A%22TESTNET+Build%22)
[![Download Testnet APK](https://img.shields.io/badge/Download-Testnet%20APK-green?style=flat&logo=android&logoColor=white)](https://acinq.co/pub/phoenix/phoenix-testnet-latest.apk)

![Phoenix Logo](.readme/phoenix_text.png)

Phoenix is a Bitcoin wallet developed by [ACINQ](https://acinq.co), that allows you to send and receive bitcoins securely over the Lightning Network. It is self-custodial, which means that **you hold the keys** of the wallet. It provides a simple and clean UX. Thanks to native Lightning support, payments are faster and cheaper.

---

:rotating_light: This wallet is self-custodial. Your key, your bitcoins. But make sure you backup your key so you do not lose your money. Do not share your key with anyone.

---

Head to our website for more information:
- [FAQ](https://phoenix.acinq.co/faq)
- [Terms](https://phoenix.acinq.co/terms)
- [Privacy](https://phoenix.acinq.co/privacy)

## Download

Phoenix is available for Android and iOS:
- for iOS: [on the App Store](https://apps.apple.com/us/app/phoenix-wallet/id1544097028), requires iOS 14+.
- for Android: [on Google Play](https://play.google.com/store/apps/details?id=fr.acinq.phoenix.mainnet), or [get the APK from the releases](https://github.com/ACINQ/phoenix/releases)

## Build and test Phoenix

Phoenix is currently separated into two codebases:
- the legacy Android app, found in the `phoenix-legacy` folder, using our own Scala [eclair-core](https://github.com/ACINQ/eclair) library for Lightning. Instructions to build this app are provided [here](https://github.com/ACINQ/phoenix/blob/master/phoenix-legacy/BUILD.md)
- the iOS app found in the `phoenix-ios` folder, using our own Kotlin MultiPlatform [lightning-kmp](https://github.com/ACINQ/lightning-kmp) library for Lightning. Instructions to build this app are provided [here](https://github.com/ACINQ/phoenix/blob/master/BUILD.md)

The `phoenix-android` folder contains the new Android app that shares code with the iOS app and use lightning-kmp. This app is not ready yet. It will eventually replace the legacy Android app.

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
