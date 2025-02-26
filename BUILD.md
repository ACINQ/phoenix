# Building

This document explains how to build Phoenix for iOS or for Android.

## Requirements

You'll need to install the following:
- [Xcode](https://developer.apple.com/xcode/): if you build the iOS app.
- [Android Studio](https://developer.android.com/studio): if you want to build the Android app. Android Studio is also the recommended IDE if you want to contribute to the `phoenix-shared` module, which contains shared code between iOS and Android. We recommend installing it even if you're only working on the iOS app.

## Build lightning-kmp

Phoenix is an actual lightning node running on your phone. It contains much of the bitcoin/lightning protocol, contained in the [lightning-kmp](https://github.com/ACINQ/lightning-kmp) library, also developed by ACINQ.

Tags of the lightning-kmp library are released on a public Maven repository. Sometimes during development, Phoenix uses a `snapshot`version ; this you will have to [build yourself](https://github.com/ACINQ/lightning-kmp/blob/master/BUILD.md).

## Build the application

Start by cloning the repository locally:

```sh
git clone git@github.com:ACINQ/phoenix.git
cd phoenix
```

### The `phoenix-shared` module

Like the lightning-kmp library, Phoenix shares some logic between the Android and the iOS application, thanks to [Kotlin Multiplatform](https://www.jetbrains.com/kotlin-multiplatform/). This includes database queries, or the some view controllers, and more (but not the UI!). The `phoenix-shared` module is where this cross-platform code is contained.

Development on this module should be done with Android Studio.

This module will be built automatically when you build the Android or the iOS app:

- on Android, because the `phoenix-android` module has a direct gradle dependency to this module;
- on iOS, because a build phase in iOS is set up to build a `PhoenixShared.framework` whenever needed.

### Building the iOS app

Open XCode, then open the `phoenix-ios` project. Once the project is properly imported, click on Product > Build.

If the project builds successfully, you can then run it on a device or an emulator.

### Building the Android app

Open the entire phoenix project in Android Studio, then build the `phoenix-android` application.

If you are only interested in building the iOS application, create a `local.properties` file at the root of the project, and add the following line:

```
skip.android=true
```

## Troubleshooting

### Lightning-kmp versions

The lightning-kmp version is set in  `gradle/libs.version.toml`. If this version is a `snapshot`, phoenix is using a development version of lightning-kmp. Not only will you need to build it yourself, but Phoenix may also need to be patched if there are API changes.

## Release the Android app

Phoenix releases are built using a dockerized Linux environment. The build is not deterministic yet, we are working on it.

Notes:

- This tool works on Macos, Linux and Windows.
- Following instructions only work for releases after v.1.3.1 (excluded).

### Prerequisites

You don't have to worry about installing any development tool, except:
1. Docker (Community Edition)

Note: on Windows at least, it is strongly recommended to bump the resources allocation settings from the default values, especially for Memory.

### Building the APK

1. Clone the phoenix project from https://github.com/ACINQ/phoenix ;

2. Open a terminal at the root of the cloned project ;

3. Checkout the tag you want to build, for example:

```shell
git checkout v1.4.0
```

4. Build the docker image mirroring the release environment (this typically takes ~20min):

```shell
docker build -t phoenix_build .
```

5. Build the APKs using the docker image (takes typically ~10min):

```shell
# If you're on macOS/linux:
docker run --rm -v $(pwd):/home/ubuntu/phoenix/phoenix-android/build/outputs -w /home/ubuntu/phoenix phoenix_build ./gradlew :phoenix-android:assembleRelease

# If you're on Windows:
docker run --rm -v ${pwd}:/home/ubuntu/phoenix/phoenix-android/build/outputs -w //home/ubuntu/phoenix phoenix_build ./gradlew :phoenix-android:assembleRelease
```

6. APK files are found in the `apk` folder.
