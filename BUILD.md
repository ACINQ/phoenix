# Building

This document explains how to build the new Phoenix application using the Kotlin MultiPlatform stack. For the legacy version, see [here](https://github.com/ACINQ/phoenix/blob/master/phoenix-legacy/BUILD.md).

The new Phoenix app is a [Kotlin Multiplatform Mobile](https://kotlinlang.org/docs/mobile/home.html) application.
It can run on many different platforms, including mobile devices (iOS and Android).

## Requirements

You'll need to install the following:
- [Git LFS](https://git-lfs.github.com/): large files like png are stored using the Large File Storage Git extension.
- [Xcode](https://developer.apple.com/xcode/): if you build the iOS app.
- [Android Studio](https://developer.android.com/studio): if you want to build the Android app. It is also recommended if you want to contribute to the `phoenix-shared` module which contains shared code used by both iOS and Android apps.

## Build lightning-kmp

First you must build the lightning-kmp library. See the build instructions [here](https://github.com/ACINQ/lightning-kmp/blob/master/BUILD.md).

## Build the application

Start by cloning the repository locally:

```sh
git clone git@github.com:ACINQ/phoenix-kmm.git
cd phoenix-kmm
```

### The phoenix-shared module

This module is where [Kotlin Multiplatform Mobile](https://kotlinlang.org/docs/mobile/home.html) is used. The code written in Kotlin is shared between the Android and the iOS application. It contains common logic, such as the database queries, or the MVI controllers. Open this project with IntelliJ or Android Studio.

You do not need to build this module yourself:

- For Android, the Android app has a direct dependency to this module and Android Studio will build it automatically when building the Android app.

- For iOS, when building the iOS app, XCode will automatically call this command:

```
./gradlew :phoenix-shared:packForXCode -PXCODE_CONFIGURATION=Debug -PXCODE_PLATFORM_NAME=iphoneos -PskipAndroid=true
```

Which generates the phoenix-ios-framework for iOS.

#### Skip the Android app

If you are only interested in the iOS application, create a `local.properties` file at the root of the project, and add the following line:

```
skip.android=true
```

### Build the iOS app

Open XCode, then open the `phoenix-ios` project. Once the project is properly imported, click on Product > Build.

If the project builds successfully, you can then run it on a device or an emulator.

### Build the Android app

Open the entire phoenix-kmm project in Android Studio, then build the application. Note that the Android app uses Jetpack compose, which currently requires Android Studio Canary. Using a regular Android Studio release may cause issues.

## Troubleshooting

### lightning-kmp versions

Make sure that the lightning-kmp version that phoenix-kmm depends on is the same that the lightning-kmp version you are building. Your local lightning-kmp repository may not be pointing to the version that phoenix-kmm requires.

Phoenix-kmm defines its lightning-kmp version in `phoenix-shared/build.gradle.kts`, with `val lightningkmpVersion = "xxx"`.

If this value is `snapshot` it means that the current phoenix `master` branch is in development and depends on a floating version of lightning-kmp. In that case, there may be API changes in lightning-kmp and build may fail. In that case, you can checkout a tag of phoenix-kmm, which will always use a stable, tagged version of lightning-kmp.
