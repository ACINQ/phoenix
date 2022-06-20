This document is related to the legacy app, based on eclair-core.

🚧 Phoenix is currently in a transition phase between the old app and the new app. During that transition phase, to build the new app you'll need to build the old app as well.

# How-to build Phoenix (legacy)

First you'll have to build the various dependencies for the legacy application.

## Building eclair-core for Phoenix

1. Clone the eclair project from https://github.com/ACINQ/eclair
2. Checkout the `android-phoenix` branch
3. Follow the instructions provided in the [BUILD.md](https://github.com/ACINQ/eclair/blob/master/BUILD.md) file of the eclair project

## Building the TOR proxy library

Phoenix uses a library to manage the communication with the tor binary. This library must be built locally

1. Clone the library from https://github.com/ACINQ/Tor_Onion_Proxy_Library
2. At the root of this project, run:
```shell
./gradlew install
./gradlew :universal:build
./gradlew :android:build
./gradlew :android:publishToMaven
```

## Troubleshooting

- If you have an error mentioning that the `eclair-core` library could not be found, it's because you need to build it first (see above), with the correct version (see below).
- The version of eclair-core used by Phoenix often changes; tagged version of the app (aka releases) always depends on a tagged version of eclair-core. The current `android-phoenix` branch may be a SNAPSHOT version which may not correspond to what the current Phoenix `master` depends on.
- You can check what eclair-core `.jar` file you have built by checking your local maven repository (`path/to/repo/fr/acinq/eclair/eclair-core_2.11/<version>/`). Default repository is `~/.m2`.

# Starting or releasing Phoenix legacy

Phoenix legacy is now an AAR library embedded in the modern, KMM application (which is hosted in the `phoenix-android` module). Follow the instructions in the BUILD.md file located in the root folder.

If you want to build old, standalone versions of the legacy app, checkout the tag you want to build and follow the build instructions for that tag.
