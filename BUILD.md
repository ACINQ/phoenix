# How-to build Phoenix

This section explains how to install a development environment for Phoenix. If you simply want to build the Phoenix APK, check the [Release section below](#release-phoenix).

First you'll have to build the various dependencies for the application.

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

## Building the app proper

[Android Studio](https://developer.android.com/studio) is the recommended development environment.

1. Install Android Studio
2. Clone the phoenix project from https://github.com/ACINQ/phoenix
3. Checkout the `master` branch (this is the default development branch, using the `TESTNET` blockchain)
4. Open Android Studio, and click on `File` > `Open...`, and open the cloned folder
5. Project initialization will proceed.

Note:
- If you have an error mentioning that the `eclair-core` library could not be found, it's because you need to build it first (see above).
- The version of eclair-core used by Phoenix often changes; tagged version of the app (aka releases) always depends on a tagged version of eclair-core. The current `android-phoenix` branch may be a SNAPSHOT version which does not correspond to what the current Phoenix `master` depends on.
- You can check what eclair-core `.jar` file you have built by checking your local maven repository (`path/to/repo/fr/acinq/eclair/eclair-core_2.11/<version>/`). Default repository is `~/.m2`.

# Release Phoenix

Phoenix releases are deterministically built using a dockerized Linux environment. This allow anyone to recreate the same APK that is published in the release page (minus the release signing part which is obviously not public). 
Notes:
- This tool works on Linux and Windows.
- Following instructions only work for releases after v.1.3.1 (excluded).

### Prerequisites

 You don't have to worry about installing any development tool, except:

1. Docker (Community Edition)

Note: on Windows at least, it is strongly recommended to bump the resources allocation settings from the default values, especially for Memory.

## How to build phoenix deterministically

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
If you're on linux:
```shell
docker run --rm -v $(pwd):/home/ubuntu/phoenix/app/build/outputs -w /home/ubuntu/phoenix phoenix_build ./gradlew assemble
```
If you're on Windows:

```shell
docker run --rm -v ${pwd}:/home/ubuntu/phoenix/app/build/outputs -w //home/ubuntu/phoenix phoenix_build ./gradlew assemble
```
6. Built artifacts are in `.apk/release`.
