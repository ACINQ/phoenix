[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Gitter chat](https://img.shields.io/badge/chat-on%20gitter-red.svg)](https://gitter.im/ACINQ/developers)

![Phoenix Logo](.readme/phoenix_text.png)

Phoenix is a Bitcoin wallet developed by [ACINQ](https://acinq.co), that allows you to send and receive bitcoins securely. It is non custodial and provides a simple and clean UX. Thanks to native Lightning support, payments are faster and cheaper.

This is a [Kotlin Multiplatform Mobile](https://kotlinlang.org/docs/mobile/home.html) application and can run on iOS and Android. It uses the [lightning-kmp](https://github.com/ACINQ/lightning-kmp) implementation to connect and interact with the Lightning Network. You can read more about the technological choices for this application in our [blog post](https://medium.com/@ACINQ/when-ios-cdf798d5f8ef).

---

:construction: Phoenix runs on Mainnet but is still experimental. Do not put too much money in the wallet. Backup your seed so you do not lose your bitcoins.

---

### Phoenix on iOS

Phoenix is available [in App Store](https://apps.apple.com/us/app/phoenix-wallet/id1544097028), and requires iOS 14+.

### Phoenix on Android

The KMM Android application is not ready yet. In the meantime, you can install the existing, battle-tested Phoenix Wallet application from the [GitHub repository](https://github.com/ACINQ/phoenix) or from [Google Play](https://play.google.com/store/apps/details?id=fr.acinq.phoenix.mainnet). Eventually, the KMM Android app will replace the existing Android application.

## Build and test Phoenix

See instructions [here](https://github.com/ACINQ/phoenix-kmm/blob/master/BUILD.md) to build and test the application.

## Contribute

We use GitHub for bug tracking. Search [the existing issues](https://github.com/ACINQ/phoenix-kmm/issues) for your bug and create a new one if needed.

You can also contribute to the project by submitting pull requests to improve the codebase or bring new features. Pull request will be reviewed by members of the ACINQ team.

To contribute to Lightning in general, take a look at the [Eclair repository](https://github.com/ACINQ/eclair) for routing nodes, or the [lightning-kmp repository](https://github.com/ACINQ/lightning-kmp) for mobile nodes.

## Support

For troubleshooting and questions, visit our support page: https://phoenix.acinq.co/support

## License

Phoenix is released under the terms of the Apache 2.0 license. See LICENSE for more information.
