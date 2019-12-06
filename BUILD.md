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
