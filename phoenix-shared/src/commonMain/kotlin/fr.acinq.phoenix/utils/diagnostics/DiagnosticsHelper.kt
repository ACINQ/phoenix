/*
 * Copyright 2026 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.utils.diagnostics

import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.blockchain.electrum.WalletState
import fr.acinq.lightning.blockchain.electrum.balance
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.ElectrumConfig
import fr.acinq.phoenix.data.Notification
import fr.acinq.phoenix.managers.phoenixFinalWallet
import fr.acinq.phoenix.managers.phoenixSwapInWallet
import fr.acinq.phoenix.shared.BuildVersions
import fr.acinq.phoenix.utils.extensions.confirmed
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

object DiagnosticsHelper {

    const val SEPARATOR = "-----------------------"
    const val TAB = "    "

    @OptIn(ExperimentalTime::class)
    suspend fun getDiagnostics(business: PhoenixBusiness): String {

        val peerManager = business.peerManager
        val balanceManager = business.balanceManager
        val nodeParams = business.nodeParamsManager.nodeParams.filterNotNull().first()
        val channels = peerManager.channelsFlow.filterNotNull().first()

        val result = StringBuilder()

        result.appendLine("node id: ${nodeParams.keyManager.nodeKeys.nodeKey.publicKey}")
        result.appendLine("legacy node id: ${nodeParams.keyManager.nodeKeys.legacyNodeKey.publicKey}")
        result.appendLine("chain: ${nodeParams.chain}")
        result.appendLine(SEPARATOR)
        result.appendLine("commit: ${BuildVersions.PHOENIX_COMMIT}")
        result.appendLine("lightning-kmp version: ${BuildVersions.LIGHTNING_KMP_VERSION}")
        result.appendLine(SEPARATOR)
        result.appendLine("Tor: ${business.appConfigurationManager.isTorEnabled.value}")
        result.appendLine(SEPARATOR)
        result.appendLine("electrum server: ${business.appConnectionsDaemon?.lastElectrumServerAddress?.first()}")
        result.appendLine("electrum connection: ${business.electrumClient.connectionStatus.first()}")
        result.appendLine("electrum config: ${business.appConfigurationManager.electrumConfig.filterNotNull().first().let { 
            when (it) {
                is ElectrumConfig.Random -> "random"
                is ElectrumConfig.Custom -> "custom(server=${it.server} onionIfTor=${it.requireOnionIfTorEnabled})"
            }
        }}")
        business.appConfigurationManager.electrumMessages.filterNotNull().let {
            result.appendLine("current block height: ${formatNumber(it.first().blockHeight)} (${Instant.fromEpochMilliseconds(it.first().header.time)})")
        }
        result.appendLine("mempoolspace feerate: ${business.phoenixGlobal.feerateManager.mempoolFeerate.first()}")

        // peer
        result.appendLine(SEPARATOR)
        result.appendLine("peer node id: ${peerManager.getPeer().remoteNodeId.toHex()}")
        result.appendLine("peer connection state: ${peerManager.getPeer().connectionState.first()}")
        result.appendLine("current tip: ${peerManager.getPeer().currentTipFlow.value?.let { formatNumber(it) }}")
        result.appendLine("onchain feerate: ${peerManager.getPeer().onChainFeeratesFlow.first()}")
        result.appendLine("peer feerate: ${peerManager.getPeer().peerFeeratesFlow.first()}")
        result.appendLine("remote funding rate: ${peerManager.getPeer().remoteFundingRates.first()}")
        result.appendLine("require upgrade: ${peerManager.upgradeRequired.first()}")
        result.appendLine("node params: ${peerManager.getPeer().nodeParams}")
        result.appendLine("wallet params: ${peerManager.getPeer().walletParams}")

        // channels
        result.appendLine(SEPARATOR)
        result.appendLine("balance_ln: ${printAmount(balanceManager.balance.filterNotNull().first())}")
        result.appendLine("may do payments: ${peerManager.mayDoPayments.first()}")
        result.appendLine("channels: [")
        channels.values.forEachIndexed { index, info ->
            if (index > 0) result.appendLine()
            result.appendLine("${TAB}channel id: ${info.channelId}")
            result.appendLine("${TAB}state: ${info.stateName}")
            result.appendLine("${TAB}available for send: ${info.localBalance?.let { printAmount(it) }}")
            result.appendLine("${TAB}available for receive: ${info.availableForReceive?.let { printAmount(it) }}")
            result.appendLine("${TAB}commitments: ${info.commitmentsInfo}")
            result.appendLine("${TAB}inactive commitments: ${info.inactiveCommitmentsInfo}")
            result.appendLine("${TAB}data: ${info.json.replace(Regex("\\s+"), " ")}")
        }
        result.appendLine("]")

        // swap-in wallet
        val swapInWallet = peerManager.swapInWallet.filterNotNull().first()
        val swapInWalletState = peerManager.getPeer().phoenixSwapInWallet.wallet.walletStateFlow.filterNotNull().first()
        val addresses = swapInWalletState.addresses.toList()
        val swapInLegacy = addresses.firstOrNull { it.second.meta is WalletState.AddressMeta.Single }
        val swapInTaproot = addresses.filter { it.second.meta is WalletState.AddressMeta.Derived }
            .sortedBy { it.second.meta.indexOrNull }
            .map { (address, state) ->
                "(${state.meta.indexOrNull}) $address already_used=${state.alreadyUsed} utxos=[${state.utxos}]"
            }
        result.appendLine(SEPARATOR)
        result.appendLine("swap-in wallet balance: [")
        result.appendLine("${TAB}unconfirmed: (${printAmount(swapInWallet.unconfirmed.balance)}) ${swapInWallet.unconfirmed}")
        result.appendLine("${TAB}weakly confirmed: (${printAmount(swapInWallet.weaklyConfirmed.balance)}) ${swapInWallet.weaklyConfirmed}")
        result.appendLine("${TAB}deeply confirmed: (${printAmount(swapInWallet.deeplyConfirmed.balance)}) ${swapInWallet.deeplyConfirmed}")
        result.appendLine("${TAB}locked until refund: (${printAmount(swapInWallet.lockedUntilRefund.balance)}) ${swapInWallet.lockedUntilRefund}")
        result.appendLine("${TAB}ready for refund: (${printAmount(swapInWallet.readyForRefund.balance)}) ${swapInWallet.readyForRefund}")
        result.appendLine("]")
        result.appendLine("swap-in address (legacy): ${swapInLegacy?.first}")
        result.appendLine("swap-in addresses (taproot): [")
        swapInTaproot.forEach {
            result.appendLine("${TAB}$it")
        }
        result.appendLine("]")

        // liquidity events
        val liquidityNotifs = business.notificationsManager.notifications.first()
        result.appendLine("liquidity events: [")
        liquidityNotifs.forEach { (_, notif) ->
            when (notif) {
                is Notification.PaymentRejected -> result.appendLine("${TAB}${notif::class.simpleName} source=${notif.source} amount=${printAmount(notif.amount)}} ${notif.createdAt}")
                else -> Unit
            }
        }
        result.appendLine("]")

        // final wallet
        result.appendLine(SEPARATOR)
        result.appendLine("final wallet balance: [")
        val finalWallet = peerManager.finalWallet.filterNotNull().first()
        result.appendLine("${TAB}unconfirmed: (${printAmount(finalWallet.unconfirmed.balance)}) ${finalWallet.unconfirmed}")
        result.appendLine("${TAB}confirmed: (${printAmount(finalWallet.confirmed.balance)}) ${finalWallet.confirmed}")
        result.appendLine("]")
        result.appendLine("final wallet address: ${peerManager.getPeer().phoenixFinalWallet.finalAddress}")

        return result.toString()
    }

    private fun printAmount(sat: Satoshi): String {
        return "${formatNumber(sat.sat)} sat"
    }

    private fun printAmount(msat: MilliSatoshi): String {
        return "${formatNumber(msat.msat)} msat"
    }

    /** print a number with a separator character for thousandths. */
    private fun formatNumber(number: Number, separator: Char = ','): String {
        val numberStr = number.toString()
        val reversed = numberStr.reversed()
        val grouped = reversed.chunked(3).joinToString(separator.toString())
        return grouped.reversed()
    }
}
