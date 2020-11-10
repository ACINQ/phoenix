package fr.acinq.phoenix.utils

import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.bindings.NoArgBindingDI
import org.kodein.di.bindings.NoArgDIBinding
import org.kodein.di.direct
import org.kodein.di.instance
import org.kodein.log.Logger
import org.kodein.log.newLogger


fun DIAware.newLogger(): Logger = this.newLogger(direct.instance())

// Tags
const val TAG_APPLICATION = "application"
const val TAG_MASTER_PUBKEY_PATH = "master_pubkey_path"
const val TAG_ONCHAIN_ADDRESS_PATH = "onchain_address_path"
const val TAG_ACINQ_NODE_URI = "peer_host"
