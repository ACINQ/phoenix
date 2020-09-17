package fr.acinq.phoenix.utils

import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.bindings.NoArgBindingDI
import org.kodein.di.bindings.NoArgDIBinding
import org.kodein.di.direct
import org.kodein.di.instance
import org.kodein.log.Logger
import org.kodein.log.newLogger


// Will be part of Kodein-DI in a future version (when first class support for iOS is released).
// Ensures that the same screen will always receive the same controller.
// I.e, in Android, the controller survives activity restarts.
expect inline fun <reified T: Any> DI.Builder.screenProvider(noinline creator: NoArgBindingDI<Any>.() -> T): NoArgDIBinding<*, T>

fun DIAware.newLogger(): Logger = this.newLogger(direct.instance())

// Tags
const val TAG_APPLICATION = "application"
const val TAG_IS_MAINNET = "is_mainnet"
const val TAG_MASTER_PUBKEY_PATH = "master_pubkey_path"
const val TAG_ONCHAIN_ADDRESS_PATH = "onchain_address_path"
const val TAG_ACINQ_ADDRESS = "peer_host"
