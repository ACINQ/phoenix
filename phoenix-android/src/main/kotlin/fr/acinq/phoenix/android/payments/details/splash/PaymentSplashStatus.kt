/*
 * Copyright 2024 ACINQ SAS
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

package fr.acinq.phoenix.android.payments.details.splash

import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.blockchain.electrum.ElectrumConnectionStatus
import fr.acinq.lightning.db.ChannelCloseOutgoingPayment
import fr.acinq.lightning.db.InboundLiquidityOutgoingPayment
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.db.SpliceCpfpOutgoingPayment
import fr.acinq.lightning.db.SpliceOutgoingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.Dialog
import fr.acinq.phoenix.android.components.FilledButton
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.components.openLink
import fr.acinq.phoenix.android.components.txUrl
import fr.acinq.phoenix.android.payments.cpfp.CpfpView
import fr.acinq.phoenix.android.utils.Converter.toRelativeDateString
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.mutedTextColor
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.android.utils.positiveColor
import fr.acinq.phoenix.utils.extensions.minDepthForFunding
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun PaymentStatus(
    payment: WalletPayment,
    fromEvent: Boolean,
    onCpfpSuccess: () -> Unit,
) {
    val peerManager = business.peerManager
    when (payment) {
        is LightningOutgoingPayment -> when (payment.status) {
            is LightningOutgoingPayment.Status.Pending -> PaymentStatusIcon(
                message = { Text(text = stringResource(id = R.string.paymentdetails_status_sent_pending)) },
                imageResId = R.drawable.ic_payment_details_pending_static,
                isAnimated = false,
                color = mutedTextColor
            )
            is LightningOutgoingPayment.Status.Completed.Failed -> PaymentStatusIcon(
                message = { Text(text = annotatedStringResource(id = R.string.paymentdetails_status_sent_failed), textAlign = TextAlign.Center) },
                imageResId = R.drawable.ic_payment_details_failure_static,
                isAnimated = false,
                color = negativeColor
            )
            is LightningOutgoingPayment.Status.Completed.Succeeded -> PaymentStatusIcon(
                message = {
                    Text(text = annotatedStringResource(id = R.string.paymentdetails_status_sent_successful, payment.completedAt?.toRelativeDateString() ?: ""))
                },
                imageResId = if (fromEvent) R.drawable.ic_payment_details_success_animated else R.drawable.ic_payment_details_success_static,
                isAnimated = fromEvent,
                color = positiveColor,
            )
        }
        is ChannelCloseOutgoingPayment -> when (payment.confirmedAt) {
            null -> {
                PaymentStatusIcon(
                    message = null,
                    imageResId = R.drawable.ic_payment_details_pending_onchain_static,
                    isAnimated = false,
                    color = mutedTextColor,
                )
                ConfirmationView(payment.txId, payment.channelId, isConfirmed = false, canBeBumped = false, onCpfpSuccess = onCpfpSuccess)
            }
            else -> {
                PaymentStatusIcon(
                    message = {
                        Text(text = annotatedStringResource(id = R.string.paymentdetails_status_channelclose_confirmed, payment.completedAt?.toRelativeDateString() ?: ""))
                    },
                    imageResId = if (fromEvent) R.drawable.ic_payment_details_success_animated else R.drawable.ic_payment_details_success_static,
                    isAnimated = fromEvent,
                    color = positiveColor,
                )
                ConfirmationView(payment.txId, payment.channelId, isConfirmed = true, canBeBumped = false, onCpfpSuccess)
            }
        }
        is SpliceOutgoingPayment -> when (payment.confirmedAt) {
            null -> {
                PaymentStatusIcon(
                    message = null,
                    imageResId = R.drawable.ic_payment_details_pending_onchain_static,
                    isAnimated = false,
                    color = mutedTextColor,
                )
                ConfirmationView(payment.txId, payment.channelId, isConfirmed = false, canBeBumped = true, onCpfpSuccess = onCpfpSuccess)
            }
            else -> {
                PaymentStatusIcon(
                    message = {
                        Text(text = annotatedStringResource(id = R.string.paymentdetails_status_sent_successful, payment.completedAt!!.toRelativeDateString()))
                    },
                    imageResId = if (fromEvent) R.drawable.ic_payment_details_success_animated else R.drawable.ic_payment_details_success_static,
                    isAnimated = fromEvent,
                    color = positiveColor,
                )
                ConfirmationView(payment.txId, payment.channelId, isConfirmed = true, canBeBumped = true, onCpfpSuccess = onCpfpSuccess)
            }
        }
        is SpliceCpfpOutgoingPayment -> when (payment.confirmedAt) {
            null -> {
                PaymentStatusIcon(
                    message = null,
                    imageResId = R.drawable.ic_payment_details_pending_onchain_static,
                    isAnimated = false,
                    color = mutedTextColor,
                )
                ConfirmationView(payment.txId, payment.channelId, isConfirmed = false, canBeBumped = true, onCpfpSuccess = onCpfpSuccess)
            }
            else -> {
                PaymentStatusIcon(
                    message = {
                        Text(text = annotatedStringResource(id = R.string.paymentdetails_status_sent_successful, payment.completedAt!!.toRelativeDateString()))
                    },
                    imageResId = if (fromEvent) R.drawable.ic_payment_details_success_animated else R.drawable.ic_payment_details_success_static,
                    isAnimated = fromEvent,
                    color = positiveColor,
                )
                ConfirmationView(payment.txId, payment.channelId, isConfirmed = true, canBeBumped = true, onCpfpSuccess = onCpfpSuccess)
            }
        }
        is IncomingPayment -> {
            val received = payment.received
            when {
                received == null -> {
                    PaymentStatusIcon(
                        message = { Text(text = stringResource(id = R.string.paymentdetails_status_received_pending)) },
                        imageResId = R.drawable.ic_payment_details_pending_static,
                        isAnimated = false,
                        color = mutedTextColor
                    )
                }
                received.receivedWith.isEmpty() -> {
                    PaymentStatusIcon(
                        message = { Text(text = stringResource(id = R.string.paymentdetails_status_received_paytoopen_pending)) },
                        isAnimated = false,
                        imageResId = R.drawable.ic_clock,
                        color = mutedTextColor,
                    )
                }
                received.receivedWith.any { it is IncomingPayment.ReceivedWith.OnChainIncomingPayment && it.lockedAt == null } -> {
                    PaymentStatusIcon(
                        message = {
                            Text(text = stringResource(id = R.string.paymentdetails_status_unconfirmed))
                        },
                        isAnimated = false,
                        imageResId = R.drawable.ic_clock,
                        color = mutedTextColor,
                    )
                }
                payment.completedAt == null -> {
                    PaymentStatusIcon(
                        message = {
                            Text(text = stringResource(id = R.string.paymentdetails_status_received_pending))
                        },
                        imageResId = R.drawable.ic_payment_details_pending_static,
                        isAnimated = false,
                        color = mutedTextColor
                    )
                }
                else -> {
                    PaymentStatusIcon(
                        message = {
                            Text(text = annotatedStringResource(id = R.string.paymentdetails_status_received_successful, payment.completedAt!!.toRelativeDateString()))
                        },
                        imageResId = if (fromEvent) R.drawable.ic_payment_details_success_animated else R.drawable.ic_payment_details_success_static,
                        isAnimated = fromEvent,
                        color = positiveColor,
                    )
                }
            }
            received?.receivedWith?.filterIsInstance<IncomingPayment.ReceivedWith.OnChainIncomingPayment>()?.firstOrNull()?.let {
                val nodeParams = business.nodeParamsManager.nodeParams.value
                val channelMinDepth by produceState<Int?>(initialValue = null, key1 = Unit) {
                    nodeParams?.let { params ->
                        val channelId = payment.received?.receivedWith?.filterIsInstance<IncomingPayment.ReceivedWith.OnChainIncomingPayment>()?.firstOrNull()?.channelId
                        value = channelId?.let { peerManager.getChannelWithCommitments(it)?.minDepthForFunding(params) }
                    }
                }
                ConfirmationView(it.txId, it.channelId, isConfirmed = it.confirmedAt != null, canBeBumped = false, onCpfpSuccess = onCpfpSuccess, channelMinDepth)
            }
        }
        is InboundLiquidityOutgoingPayment -> SplashLiquidityStatus(payment = payment, fromEvent = fromEvent)
    }
}

@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
fun PaymentStatusIcon(
    message: (@Composable ColumnScope.() -> Unit)?,
    isAnimated: Boolean,
    imageResId: Int,
    color: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val scope = rememberCoroutineScope()
        var atEnd by remember { mutableStateOf(false) }
        Image(
            painter = if (isAnimated) {
                rememberAnimatedVectorPainter(AnimatedImageVector.animatedVectorResource(imageResId), atEnd)
            } else {
                painterResource(id = imageResId)
            },
            contentDescription = null,
            colorFilter = ColorFilter.tint(color),
            modifier = Modifier.size(80.dp)
        )
        if (isAnimated) {
            LaunchedEffect(key1 = Unit) {
                scope.launch {
                    delay(150)
                    atEnd = true
                }
            }
        }
        message?.let {
            Spacer(Modifier.height(16.dp))
            Column { it() }
        }
    }

}

@Composable
private fun ConfirmationView(
    txId: TxId,
    channelId: ByteVector32,
    isConfirmed: Boolean,
    canBeBumped: Boolean,
    onCpfpSuccess: () -> Unit,
    minDepth: Int? = null, // sometimes we know how many confirmations are needed
) {
    val txUrl = txUrl(txId = txId)
    val context = LocalContext.current
    val electrumClient = business.electrumClient
    var showBumpTxDialog by remember { mutableStateOf(false) }

    if (isConfirmed) {
        FilledButton(
            text = stringResource(id = R.string.paymentdetails_status_confirmed),
            icon = R.drawable.ic_chain,
            backgroundColor = Color.Transparent,
            padding = PaddingValues(8.dp),
            textStyle = MaterialTheme.typography.button.copy(fontSize = 14.sp),
            iconTint = MaterialTheme.colors.primary,
            space = 6.dp,
            onClick = { openLink(context, txUrl) }
        )
    } else {

        suspend fun getConfirmations(): Int {
            val confirmations = electrumClient.getConfirmations(txId)
            return confirmations ?: run {
                delay(5_000)
                getConfirmations()
            }
        }

        val confirmations by produceState<Int?>(initialValue = null) {
            electrumClient.connectionStatus.filterIsInstance<ElectrumConnectionStatus.Connected>().first()
            val confirmations = getConfirmations()
            value = confirmations
        }
        confirmations?.absoluteValue?.let { conf ->
            if (conf == 0) {
                Card(
                    internalPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    onClick = if (canBeBumped) {
                        { showBumpTxDialog = true }
                    } else null,
                    backgroundColor = Color.Transparent,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TextWithIcon(
                        text = stringResource(R.string.paymentdetails_status_unconfirmed_zero),
                        icon = if (canBeBumped) R.drawable.ic_rocket else R.drawable.ic_clock,
                        textStyle = MaterialTheme.typography.button.copy(fontSize = 14.sp, color = MaterialTheme.colors.primary),
                        iconTint = MaterialTheme.colors.primary
                    )

                    if (canBeBumped) {
                        Text(
                            text = stringResource(id = R.string.paymentdetails_status_unconfirmed_zero_bump),
                            style = MaterialTheme.typography.button.copy(fontSize = 14.sp, color = MaterialTheme.colors.primary, fontWeight = FontWeight.Bold),
                        )
                    }
                }
            } else {
                FilledButton(
                    text = when (minDepth) {
                        null -> stringResource(R.string.paymentdetails_status_unconfirmed_default, conf)
                        else -> stringResource(R.string.paymentdetails_status_unconfirmed_with_depth, conf, minDepth)
                    },
                    icon = R.drawable.ic_chain,
                    onClick = { openLink(context, txUrl) },
                    backgroundColor = Color.Transparent,
                    padding = PaddingValues(8.dp),
                    textStyle = MaterialTheme.typography.button.copy(fontSize = 14.sp),
                    iconTint = MaterialTheme.colors.primary,
                    space = 6.dp,
                )
            }

            if (conf == 0 && showBumpTxDialog) {
                BumpTransactionDialog(channelId = channelId, onSuccess = onCpfpSuccess, onDismiss = { showBumpTxDialog = false })
            }
        } ?: ProgressView(
            text = stringResource(id = R.string.paymentdetails_status_unconfirmed_fetching),
            textStyle = MaterialTheme.typography.body1.copy(fontSize = 14.sp),
            padding = PaddingValues(8.dp),
            progressCircleSize = 16.dp,
        )
    }
}

@Composable
private fun BumpTransactionDialog(
    channelId: ByteVector32,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismiss = onDismiss,
        title = stringResource(id = R.string.cpfp_title),
        buttons = null,
    ) {
        CpfpView(channelId = channelId, onSuccess = onSuccess)
    }
}