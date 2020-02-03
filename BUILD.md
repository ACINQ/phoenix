# Building Phoenix

## Building eclair-core for Phoenix

1. Clone the eclair project from https://github.com/ACINQ/eclair
2. Checkout the `android-phoenix` branch
3. Follow the instructions provided in the [BUILD.md](https://github.com/ACINQ/eclair/blob/master/BUILD.md) file of the eclair project

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
The deterministic build uses a dockerized build environment and require you to have previously built (and published locally) the artifact for the `eclair-core` 
dependency, follow the instructions to build it.

### Prerequisites

1. A linux machine running on x64 CPU.
2. docker-ce installed
3. Eclair-core published in your local maven repo, check out the instructions to build it.

### How to build phoenix deterministically

1. Clone the phoenix project from https://github.com/ACINQ/phoenix
3. Run `docker build -t phoenix_build .` to create the build environment
4. Run `docker run --rm -v $HOME/.m2:/root/.m2 -v $(pwd):/home/ubuntu/phoenix/app/build -w /home/ubuntu/phoenix phoenix_build ./gradlew assemble`
5. Built artifacts are in $(pwd)/outputs/apk/release
