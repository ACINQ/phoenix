# Building Phoenix

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

## Building Phoenix

[Android Studio](https://developer.android.com/studio) is the recommended development environment.

1. Install Android Studio
2. Clone the phoenix project from https://github.com/ACINQ/phoenix
3. Checkout the `master` branch (this is the default development branch, using the `TESTNET` blockchain)
4. Open Android Studio, and click on `File` > `Open...`, and open the cloned folder
5. Project initialization will proceed.

Note that if you have an error mentioning that the `eclair-core` library could not be found, it's because you need to build it first (see above).
You can check that the corresponding `.jar` file is present in your local maven repository (`path/to/repo/fr/acinq/eclair/eclair-core_2.11/<version>/`).

## Deterministic build of Phoenix

Phoenix supports deterministic builds on Linux OSs, this allows anyone to recreate from the sources the exact same APK that was published in the release page.
The deterministic build uses a self-contained dockerized environment, to run the build you must supply via docker build args the branch of eclair-core
that is going to be used for the build. Each release of Phoenix is built against a specific revision of eclair-core, checkout the release notes to know
which revision you need to use in the deterministic build.

### Prerequisites

1. A linux machine running on x64 CPU.
2. docker-ce installed

### How to build phoenix deterministically

1. Clone the phoenix project from https://github.com/ACINQ/phoenix
3. Run `docker build -t phoenix_build .` to create the build environment
4. Run `docker run --rm -v $(pwd):/home/ubuntu/phoenix/app/build/outputs -w /home/ubuntu/phoenix phoenix_build ./gradlew assemble`
5. Built artifacts are in `.apk/release`
