# Building

This document explains how to build Phoenix for iOS or for Android. Phoenix is a [Kotlin Multiplatform Mobile](https://kotlinlang.org/docs/mobile/home.html) application. It can run on many different platforms, including mobile devices (iOS and Android).

Note: that the legacy Android app is now a library within a new Android app. To build the old versions of this app, checkout one of the `android-legacy-vXX` tags, and follow the build instructions.

## Requirements

You'll need to install the following:
- [Xcode](https://developer.apple.com/xcode/): if you build the iOS app.
- [Android Studio](https://developer.android.com/studio): if you want to build the Android app. Android Studio is also the recommended IDE if you want to contribute to the `phoenix-shared` module, which contains shared code between iOS and Android. We recommend installing it even if you're only working on the iOS app.

## Build lightning-kmp

Phoenix is an actual lightning node running on your phone. It contains much of the bitcoin/lightning protocol. For that, Phoenix relies on the [lightning-kmp](https://github.com/ACINQ/lightning-kmp) library. Tags of this library are released on public maven repository, but this repository may from time to time use a SNAPSHOT development version.

When that happens, you will have to build this library yourself on your local machine. To do that, follow those [instructions](https://github.com/ACINQ/lightning-kmp/blob/master/BUILD.md). 

## Build the application

Start by cloning the repository locally:

```sh
git clone git@github.com:ACINQ/phoenix.git
cd phoenix
```

### The `phoenix-legacy` module

This module contains an older version of the Android application. This version is now an AAR, that is a Android library (and not a standalone application). It is embedded within the new `phoenix-android` app and must be built if you want to run the Android application.

Follow [those instructions](https://github.com/ACINQ/phoenix/blob/master/phoenix-legacy/BUILD.md) to build the legacy app.

Note:
- in the future, the legacy app code will be removed and the modern Android app won't have this dependency anymore.
- iOS developers who don't need the Android app and wish to save time can do so by [skipping the Android app altogether](#skipping-the-android-app).

### The `phoenix-shared` module

This module is where [Kotlin Multiplatform Mobile](https://kotlinlang.org/docs/mobile/home.html) is used. The code written in Kotlin is shared between the Android and the iOS application. It contains common logic, such as the database queries, or the MVI controllers. Development on this module should be done with Android Studio.

You do not need to run a command to build this module. It will be built automatically whether you're building the Android or the iOS app:

- the `phoenix-android` module has a direct gradle dependency to this module, so Android Studio will build it automatically when building the Android app.

- when building the iOS app, XCode will automatically call this command:

```
./gradlew :phoenix-shared:packForXCode -PXCODE_CONFIGURATION=Debug -PXCODE_PLATFORM_NAME=iphoneos -PskipAndroid=true
```

which will generate the required phoenix-ios-framework used by iOS.

### Skipping the Android app

If you are only interested in the iOS application, create a `local.properties` file at the root of the project, and add the following line:

```
skip.android=true
```

### Building the iOS app

Open XCode, then open the `phoenix-ios` project. Once the project is properly imported, click on Product > Build.

If the project builds successfully, you can then run it on a device or an emulator.

### Building the Android app

Open the entire phoenix project in Android Studio, then build the `phoenix-android` application.

## Troubleshooting

### Lightning-kmp versions

Make sure that the lightning-kmp version that phoenix depends on is the same that the lightning-kmp version you are building. Your local lightning-kmp repository may not be pointing to the version that `phoenix-android` requires.

Phoenix defines its lightning-kmp version in `buildSrc/src/main/kotlin/Versions.kt`, through the `val lightningKmp = "xxx"` field.

If this value is `snapshot` it means that the current phoenix `master` branch is using a development version of this library. In that case, there may be API changes in lightning-kmp which are not yet supported here.

## Release the Android app

Phoenix releases are built using a dockerized Linux environment. The build is not deterministic yet, we are working on it.

Notes:

- This tool works on Linux and Windows.
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
# If you're on linux:
docker run --rm -v $(pwd):/home/ubuntu/phoenix/phoenix-android/build/outputs -w /home/ubuntu/phoenix phoenix_build ./gradlew :phoenix-android:assembleRelease

# If you're on Windows:
docker run --rm -v ${pwd}:/home/ubuntu/phoenix/phoenix-android/build/outputs -w //home/ubuntu/phoenix phoenix_build ./gradlew :phoenix-android:assembleRelease
```

6. APK files are found in the `apk` folder.
