/*
 * Copyright 2022 ACINQ SAS
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

package fr.acinq.phoenix.android.payments

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.bitcoin.byteVector64
import fr.acinq.lightning.CltvExpiryDelta
import fr.acinq.lightning.Features
import fr.acinq.lightning.InvoiceDefaultRoutingFees
import fr.acinq.lightning.Lightning
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.NodeParams
import fr.acinq.lightning.ShortChannelId
import fr.acinq.lightning.TrampolineFees
import fr.acinq.lightning.crypto.LocalKeyManager
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.Either
import fr.acinq.lightning.utils.currentTimestampSeconds
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.toByteVector
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.AmountHeroInput
import fr.acinq.phoenix.android.components.BackButtonWithBalance
import fr.acinq.phoenix.android.components.FilledButton
import fr.acinq.phoenix.android.components.SplashLabelRow
import fr.acinq.phoenix.android.components.SplashLayout
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.safeLet
import fr.acinq.phoenix.controllers.payments.Scan
import fr.acinq.phoenix.managers.NodeParamsManager

@Composable
fun LnAddressOverDnsView(
    model: Scan.Model.LnAddressOverDns,
    trampolineFees: TrampolineFees?,
    onBackClick: () -> Unit,
    onSendClick: (Scan.Intent.InvoiceFlow.SendInvoicePayment) -> Unit
) {
    val log = logger("LnAddressOverDnsView")
    log.debug { "init ln-address view to =${model.userDomain}" }

    val context = LocalContext.current
    val mayDoPayments by business.peerManager.mayDoPayments.collectAsState()
    val balance = business.balanceManager.balance.collectAsState(null).value

    var amount by remember { mutableStateOf<MilliSatoshi?>(null) }
    var amountErrorMessage by remember { mutableStateOf("") }

    SplashLayout(
        header = { BackButtonWithBalance(onBackClick = onBackClick, balance = balance) },
        topContent = {
            AmountHeroInput(
                initialAmount = amount,
                onAmountChange = { newAmount ->
                    amountErrorMessage = ""
                    when {
                        newAmount == null -> {}
                        balance != null && newAmount.amount > balance -> {
                            amountErrorMessage = context.getString(R.string.send_error_amount_over_balance)
                        }
                    }
                    amount = newAmount?.amount
                },
                validationErrorMessage = amountErrorMessage,
                inputTextSize = 42.sp,
            )
        }
    ) {
        SplashLabelRow(label = "Payment to") {
            Text(text = "${model.username}@${model.domain}")
        }
        SplashLabelRow(label = "Address") {
            Text(text = "lno1pg257enxv4ezqcneype82um50ynhxgrwdajx293pqglnyxw6q0hzngfdusg8umzuxe8kquuz7pjl90ldj8wadwgs0xlmc")
        }
        SplashLabelRow(label = "Node id") {
            Text(text = model.nodeId.toHex())
        }
        Spacer(modifier = Modifier.height(32.dp))
        val nodeParams by business.nodeParamsManager.nodeParams.collectAsState()
        FilledButton(
            text = if (!mayDoPayments) stringResource(id = R.string.send_connecting_button) else stringResource(id = R.string.lnurl_pay_pay_button),
            icon = R.drawable.ic_send,
            enabled = mayDoPayments && amount != null && amountErrorMessage.isBlank() && trampolineFees != null,
            onClick = {
                val amt = amount ?: return@FilledButton
                val fees = trampolineFees ?: return@FilledButton
                val invoiceFeatures = nodeParams?.features?.invoiceFeatures() ?: return@FilledButton
                val invoice = when (model.username) {
                    "alice" -> {
//                        Alice.getInvoice(invoiceFeatures)
                        PaymentRequest.read(Alice.invoices.random())
                    }
                    "bob" -> {
                        Bob.getInvoice(invoiceFeatures)
                    }
                    else -> return@FilledButton
                }
                onSendClick(Scan.Intent.InvoiceFlow.SendInvoicePayment(paymentRequest = invoice, amount = amt, trampolineFees = fees))
            }
        )
    }
}

abstract class InvoiceMocker {
    abstract val seed: ByteVector

    private val keyManager by lazy {
        LocalKeyManager(
            seed = Alice.seed,
            chain = NodeParams.Chain.Testnet,
            remoteSwapInExtendedPublicKey = "tpubDDt5vQap1awkyDXx1z1cP7QFKSZHDCCpbU8nSq9jy7X2grTjUVZDePexf6gc6AHtRRzkgfPW87K6EKUVV6t3Hu2hg7YkHkmMeLSfrP85x41"
        )
    }
    val nodeId by lazy { keyManager.nodeKeys.nodeKey.publicKey }
    private val extraHops by lazy {
        val invoiceDefaultRoutingFees = InvoiceDefaultRoutingFees(
            feeBase = 1_000.msat,
            feeProportional = 100,
            cltvExpiryDelta = CltvExpiryDelta(144)
        )
        listOf(
            listOf(
                PaymentRequest.TaggedField.ExtraHop(
                    nodeId = NodeParamsManager.trampolineNodeId,
                    shortChannelId = ShortChannelId.peerId(nodeId),
                    feeBase = invoiceDefaultRoutingFees.feeBase,
                    feeProportionalMillionths = invoiceDefaultRoutingFees.feeProportional,
                    cltvExpiryDelta = invoiceDefaultRoutingFees.cltvExpiryDelta
                )
            )
        )
    }

    fun getInvoice(
        invoiceFeatures: Features,
    ): PaymentRequest {
        val preimage = Lightning.randomBytes32()
        return PaymentRequest.create(
            chainHash = NodeParams.Chain.Testnet.chainHash,
            amount = null,
            paymentHash = preimage.sha256(),
            privateKey = keyManager.nodeKeys.nodeKey.privateKey,
            description = Either.Left(""),
            minFinalCltvExpiryDelta = PaymentRequest.DEFAULT_MIN_FINAL_EXPIRY_DELTA,
            features = invoiceFeatures,
            paymentSecret = Lightning.randomBytes32(),
            paymentMetadata = ByteVector("2a"),
            expirySeconds = DateUtils.WEEK_IN_MILLIS / 1000,
            extraHops = extraHops,
            timestampSeconds = currentTimestampSeconds()
        )
    }
}

object Alice : InvoiceMocker() {
    val invoices = arrayOf(
        "lntb1pje6wu4pp5yypwwsx4fdzvxrlk0d7kz0rgytw9m4ln5wqn4qgx9q2cxxte9tsqcqpjsp5pqr7e5lzfwg60camf3hl5r0v5p2f03gj3z56rgmgwh0gzewgdznq9q7sqqqqqqqqqqqqqqqqqqqsqqqqqysgqdqqmqz9gxqyjw5qrzjqwfn3p9278ttzzpe0e00uhyxhned3j5d9acqak5emwfpflp8z2cnflu809qrhszp4sqqqqlgqqqqqeqqjqd0g3sz58dvy8rk3t5f64m5zt5csa3waq5dmexl23jtgnxngnxaknqk96d27gz0ytkfu44jqm9xc93f3peykjxssklwte3scyrvpj6ncp5dtxqa",
        "lntb1pje6wulpp5skcq38vnkh62tw7uzhcxf6dp0za4gx4ym836cd5awhpwpphzcn6scqpjsp54rmumlggpjmq3u8slw3p3qsy0kzej3yjah6uzdukqdlm5jfdz45s9q7sqqqqqqqqqqqqqqqqqqqsqqqqqysgqdqqmqz9gxqyjw5qrzjqwfn3p9278ttzzpe0e00uhyxhned3j5d9acqak5emwfpflp8z2cnflu809qrhszp4sqqqqlgqqqqqeqqjqdkw8tz3ylj9sme07cpwppf0z4aca5rtvw9s9zw5vz4eq0evj0u7ju7p7ea2k97jlwdnt2vz5a84c8vehts6qanrnktd4jstvdc56nesquy9ksf",
        "lntb1pje6wa8pp5tt6fhrdu8lw9k5hl5vf820km44ruvc7ay5phh3nqw9pdh4p0jzyscqpjsp5nky6p5eyee7a8wk2fv2q2gcv5krthn35rfchcwz8pmq5mnwxqr9s9q7sqqqqqqqqqqqqqqqqqqqsqqqqqysgqdqqmqz9gxqyjw5qrzjqwfn3p9278ttzzpe0e00uhyxhned3j5d9acqak5emwfpflp8z2cnflu809qrhszp4sqqqqlgqqqqqeqqjq4p2g9ne2ffev2gvjss46u9rffzz5u5xavfv4x64rpmp9p0tpgva87u24f8evts3mw92ahmvkezxwe0u03seyk0nt7j8pvtmsf8pcvecp0fc5nj",
        // "",
    )
    override val seed by lazy { MnemonicCode.toSeed("version place place weasel lamp east shiver filter camera vessel twin family", "").toByteVector() }
}

object Bob : InvoiceMocker() {
    override val seed by lazy { MnemonicCode.toSeed("just much soldier sight make then screen number helmet crush lift ice", "").toByteVector() }
}
