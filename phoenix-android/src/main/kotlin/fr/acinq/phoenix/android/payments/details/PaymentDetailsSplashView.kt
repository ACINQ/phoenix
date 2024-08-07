/*
 * Copyright 2023 ACINQ SAS
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

package fr.acinq.phoenix.android.payments.details

import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PublicKey
import fr.acinq.bitcoin.TxId
import fr.acinq.bitcoin.utils.Either
import fr.acinq.lightning.blockchain.electrum.ElectrumConnectionStatus
import fr.acinq.lightning.db.*
import fr.acinq.lightning.payment.FinalFailure
import fr.acinq.lightning.payment.OutgoingPaymentFailure
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sum
import fr.acinq.lightning.wire.LiquidityAds
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.contact.ContactCompactView
import fr.acinq.phoenix.android.components.contact.ContactOrOfferView
import fr.acinq.phoenix.android.components.contact.OfferContactState
import fr.acinq.phoenix.android.payments.cpfp.CpfpView
import fr.acinq.phoenix.android.utils.*
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.Converter.toRelativeDateString
import fr.acinq.phoenix.data.LnurlPayMetadata
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.data.lnurl.LnurlPay
import fr.acinq.phoenix.utils.extensions.WalletPaymentState
import fr.acinq.phoenix.utils.extensions.minDepthForFunding
import fr.acinq.phoenix.utils.extensions.incomingOfferMetadata
import fr.acinq.phoenix.utils.extensions.outgoingInvoiceRequest
import fr.acinq.phoenix.utils.extensions.state
import io.ktor.http.Url
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Composable
fun PaymentDetailsSplashView(
    onBackClick: () -> Unit,
    data: WalletPaymentInfo,
    onDetailsClick: (WalletPaymentId) -> Unit,
    onMetadataDescriptionUpdate: (WalletPaymentId, String?) -> Unit,
    fromEvent: Boolean,
) {
    val payment = data.payment
    SplashLayout(
        header = { DefaultScreenHeader(onBackClick = onBackClick) },
        topContent = { PaymentStatus(data.payment, fromEvent, onCpfpSuccess = onBackClick) }
    ) {
        AmountView(
            amount = when (payment) {
                is InboundLiquidityOutgoingPayment -> payment.amount
                is OutgoingPayment -> payment.amount - payment.fees
                is IncomingPayment -> payment.amount
            },
            amountTextStyle = MaterialTheme.typography.body1.copy(fontSize = 30.sp),
            separatorSpace = 4.dp,
            prefix = stringResource(id = if (payment is OutgoingPayment) R.string.paymentline_prefix_sent else R.string.paymentline_prefix_received)
        )

        Spacer(modifier = Modifier.height(36.dp))
        PrimarySeparator(
            height = 6.dp,
            color = when (payment.state()) {
                WalletPaymentState.Failure -> negativeColor
                WalletPaymentState.SuccessOffChain, WalletPaymentState.SuccessOnChain -> positiveColor
                else -> mutedBgColor
            }
        )
        Spacer(modifier = Modifier.height(36.dp))

        if (data.payment is LightningOutgoingPayment && data.metadata.lnurl != null) {
            LnurlPayInfoView(data.payment as LightningOutgoingPayment, data.metadata.lnurl!!)
        }

        payment.incomingOfferMetadata()?.let { meta ->
            meta.payerNote?.takeIf { it.isNotBlank() }?.let {
                OfferPayerNote(payerNote = it)
                Spacer(modifier = Modifier.height(8.dp))
            }
            OfferSentBy(payerPubkey = meta.payerKey, !meta.payerNote.isNullOrBlank())
        }

        payment.outgoingInvoiceRequest()?.payerNote?.takeIf { it.isNotBlank() }?.let {
            OfferPayerNote(payerNote = it)
        }

        PaymentDescriptionView(data = data, onMetadataDescriptionUpdate = onMetadataDescriptionUpdate)
        PaymentDestinationView(data = data)
        PaymentFeeView(payment = payment)
        if (payment is InboundLiquidityOutgoingPayment) {
            InboundLiquidityLeaseDetails(lease = payment.lease)
        }

        if (payment is LightningOutgoingPayment) {
            (payment.status as? LightningOutgoingPayment.Status.Completed.Failed)?.let { status ->
                PaymentErrorView(status = status, failedParts = payment.parts.map { it.status }.filterIsInstance<LightningOutgoingPayment.Part.Status.Failed>())
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
        BorderButton(
            text = stringResource(id = R.string.paymentdetails_details_button),
            borderColor = borderColor,
            textStyle = MaterialTheme.typography.caption,
            icon = R.drawable.ic_tool,
            iconTint = MaterialTheme.typography.caption.color,
            onClick = { onDetailsClick(data.id()) },
        )
    }
}

@Composable
private fun PaymentStatus(
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
        is InboundLiquidityOutgoingPayment -> when (val lockedAt = payment.lockedAt) {
            null -> {
                PaymentStatusIcon(
                    message = null,
                    imageResId = R.drawable.ic_payment_details_pending_onchain_static,
                    isAnimated = false,
                    color = mutedTextColor,
                )
            }
            else -> {
                PaymentStatusIcon(
                    message = {
                        Text(text = annotatedStringResource(id = R.string.paymentdetails_status_inbound_liquidity_success, lockedAt.toRelativeDateString()))
                    },
                    imageResId = if (fromEvent) R.drawable.ic_payment_details_success_animated else R.drawable.ic_payment_details_success_static,
                    isAnimated = fromEvent,
                    color = positiveColor,
                )
            }
        }
    }
}

@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
private fun PaymentStatusIcon(
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
private fun LnurlPayInfoView(payment: LightningOutgoingPayment, metadata: LnurlPayMetadata) {
    Spacer(modifier = Modifier.height(8.dp))
    SplashLabelRow(label = stringResource(id = R.string.paymentdetails_lnurlpay_service)) {
        SelectionContainer {
            Text(text = metadata.pay.callback.host)
        }
    }
    metadata.successAction?.let {
        LnurlSuccessAction(payment = payment, action = it)
    }
}

@Composable
private fun LnurlSuccessAction(payment: LightningOutgoingPayment, action: LnurlPay.Invoice.SuccessAction) {
    Spacer(modifier = Modifier.height(8.dp))
    when (action) {
        is LnurlPay.Invoice.SuccessAction.Message -> {
            SplashLabelRow(label = stringResource(id = R.string.paymentdetails_lnurlpay_action_message_label)) {
                SelectionContainer {
                    Text(text = action.message)
                }
            }
        }
        is LnurlPay.Invoice.SuccessAction.Url -> {
            SplashLabelRow(label = stringResource(id = R.string.paymentdetails_lnurlpay_action_url_label)) {
                Text(text = action.description)
                WebLink(text = stringResource(id = R.string.paymentdetails_lnurlpay_action_url_button), url = action.url.toString())
            }
        }
        is LnurlPay.Invoice.SuccessAction.Aes -> {
            SplashLabelRow(label = stringResource(id = R.string.paymentdetails_lnurlpay_action_aes_label)) {
                val status = payment.status
                if (status is LightningOutgoingPayment.Status.Completed.Succeeded.OffChain) {
                    val deciphered by produceState<String?>(initialValue = null) {
                        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(status.preimage.toByteArray(), "AES"), IvParameterSpec(action.iv.toByteArray()))
                        value = String(cipher.doFinal(action.ciphertext.toByteArray()), Charsets.UTF_8)
                    }
                    Text(text = action.description)
                    when (deciphered) {
                        null -> ProgressView(text = stringResource(id = R.string.paymentdetails_lnurlpay_action_aes_decrypting), padding = PaddingValues(0.dp))
                        else -> {
                            val url = try {
                                Url(deciphered!!)
                            } catch (e: Exception) {
                                null
                            }
                            if (url != null) {
                                WebLink(text = stringResource(id = R.string.paymentdetails_lnurlpay_action_url_button), url = url.toString())
                            } else {
                                SelectionContainer {
                                    Text(text = deciphered!!)
                                }
                            }
                        }
                    }
                } else {
                    Text(text = stringResource(id = R.string.paymentdetails_lnurlpay_action_aes_decrypting))
                }
            }
        }
    }
}

@Composable
private fun OfferPayerNote(payerNote: String) {
    Spacer(modifier = Modifier.height(8.dp))
    SplashLabelRow(label = stringResource(id = R.string.paymentdetails_offer_note_label)) {
        Text(text = payerNote)
    }
}

@Composable
private fun OfferSentBy(payerPubkey: PublicKey?, hasPayerNote: Boolean) {
    val contactsManager = business.contactsManager
    val contactState = remember { mutableStateOf<OfferContactState>(OfferContactState.Init) }
    LaunchedEffect(Unit) {
        contactState.value = payerPubkey?.let {
            contactsManager.getContactForPayerPubkey(it)
        }?.let { OfferContactState.Found(it) } ?: OfferContactState.NotFound
    }

    SplashLabelRow(label = stringResource(id = R.string.paymentdetails_offer_sender_label)) {
        when (val res = contactState.value) {
            is OfferContactState.Init -> Text(text = stringResource(id = R.string.utils_loading_data))
            is OfferContactState.NotFound -> {
                Text(text = stringResource(id = R.string.paymentdetails_offer_sender_unknown))
                if (hasPayerNote) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = stringResource(id = R.string.paymentdetails_offer_sender_unknown_details), style = MaterialTheme.typography.subtitle2)
                }
            }
            is OfferContactState.Found -> {
                ContactCompactView(
                    contact = res.contact,
                    currentOffer = null,
                    onContactChange = { contactState.value = if (it == null) OfferContactState.NotFound else OfferContactState.Found(it) },
                )
            }
        }
    }
}

@Composable
private fun PaymentDescriptionView(
    data: WalletPaymentInfo,
    onMetadataDescriptionUpdate: (WalletPaymentId, String?) -> Unit,
) {
    var showEditDescriptionDialog by remember { mutableStateOf(false) }

    val peer by business.peerManager.peerState.collectAsState()
    val paymentDesc = data.metadata.lnurl?.description ?: data.payment.smartDescription(LocalContext.current)
    val customDesc = remember(data) { data.metadata.userDescription?.takeIf { it.isNotBlank() } }

    Spacer(modifier = Modifier.height(8.dp))
    SplashLabelRow(label = stringResource(id = R.string.paymentdetails_desc_label)) {
        val isLegacyMigration = data.isLegacyMigration(peer)
        val finalDesc = when (isLegacyMigration) {
            null -> stringResource(id = R.string.paymentdetails_desc_closing_channel) // not sure yet, but we still know it's a closing
            true -> stringResource(id = R.string.paymentdetails_desc_legacy_migration)
            false -> paymentDesc ?: customDesc
        }

        if (isLegacyMigration == false) {
            SplashClickableContent(onClick = { showEditDescriptionDialog = true }) {
                Text(
                    text = finalDesc ?: stringResource(id = R.string.paymentdetails_no_description),
                    style = if (finalDesc == null) MaterialTheme.typography.caption.copy(fontStyle = FontStyle.Italic) else MaterialTheme.typography.body1
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (paymentDesc != null && customDesc != null) {
                    HSeparator(width = 50.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = customDesc, style = MaterialTheme.typography.body1.copy(fontStyle = FontStyle.Italic))
                    Spacer(modifier = Modifier.height(8.dp))
                }
                TextWithIcon(
                    text = stringResource(
                        id = when (customDesc) {
                            null -> R.string.paymentdetails_attach_desc_button
                            else -> R.string.paymentdetails_edit_desc_button
                        }
                    ),
                    textStyle = MaterialTheme.typography.subtitle2,
                    icon = R.drawable.ic_edit,
                    iconTint = MaterialTheme.typography.subtitle2.color,
                    space = 6.dp,
                )
            }
        }
    }

    if (showEditDescriptionDialog) {
        CustomNoteDialog(
            initialDescription = data.metadata.userDescription,
            onConfirm = {
                onMetadataDescriptionUpdate(data.id(), it?.trim()?.takeIf { it.isNotBlank() })
                showEditDescriptionDialog = false
            },
            onDismiss = { showEditDescriptionDialog = false }
        )
    }
}

@Composable
private fun PaymentDestinationView(data: WalletPaymentInfo) {
    when (val payment = data.payment) {
        is InboundLiquidityOutgoingPayment -> {}
        is OnChainOutgoingPayment -> {
            Spacer(modifier = Modifier.height(8.dp))
            SplashLabelRow(label = stringResource(id = R.string.paymentdetails_destination_label), icon = R.drawable.ic_chain) {
                SelectionContainer {
                    Text(
                        text = when (payment) {
                            is SpliceOutgoingPayment -> payment.address
                            is ChannelCloseOutgoingPayment -> payment.address
                            is SpliceCpfpOutgoingPayment -> stringResource(id = R.string.paymentdetails_destination_cpfp_value)
                            else -> stringResource(id = R.string.utils_unknown)
                        }
                    )
                }
            }
        }
        is LightningOutgoingPayment -> {
            val lnId = data.metadata.lnurl?.pay?.metadata?.lnid?.takeIf { it.isNotBlank() }
            if (lnId != null) {
                Spacer(modifier = Modifier.height(8.dp))
                SplashLabelRow(label = stringResource(id = R.string.paymentdetails_destination_label), icon = R.drawable.ic_zap) {
                    SelectionContainer {
                        Text(text = lnId)
                    }
                }
            }

            val details = payment.details
            if (details is LightningOutgoingPayment.Details.Blinded) {
                val offer = details.paymentRequest.invoiceRequest.offer
                SplashLabelRow(label = stringResource(id = R.string.paymentdetails_destination_label)) {
                    ContactOrOfferView(offer = offer)
                }
            }
        }
        else -> Unit
    }
}

@Composable
private fun PaymentFeeView(payment: WalletPayment) {
    val btcUnit = LocalBitcoinUnit.current
    when {
        payment is LightningOutgoingPayment && (payment.state() == WalletPaymentState.SuccessOffChain) -> {
            Spacer(modifier = Modifier.height(8.dp))
            SplashLabelRow(label = stringResource(id = R.string.paymentdetails_fees_label)) {
                Text(text = payment.fees.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW_IF_ZERO_SATS))
            }
        }
        payment is SpliceOutgoingPayment -> {
            Spacer(modifier = Modifier.height(8.dp))
            SplashLabelRow(label = stringResource(id = R.string.paymentdetails_fees_label)) {
                Text(text = payment.fees.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW_IF_ZERO_SATS))
            }
        }
        payment is ChannelCloseOutgoingPayment -> {
            Spacer(modifier = Modifier.height(8.dp))
            SplashLabelRow(label = stringResource(id = R.string.paymentdetails_fees_label)) {
                Text(text = payment.fees.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW_IF_ZERO_SATS))
            }
        }
        payment is SpliceCpfpOutgoingPayment -> {
            Spacer(modifier = Modifier.height(8.dp))
            SplashLabelRow(label = stringResource(id = R.string.paymentdetails_fees_label)) {
                Text(text = payment.fees.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW_IF_ZERO_SATS))
            }
        }
        payment is InboundLiquidityOutgoingPayment -> {
            Spacer(modifier = Modifier.height(8.dp))
            SplashLabelRow(
                label = stringResource(id = R.string.paymentdetails_liquidity_miner_fee_label),
                helpMessage = stringResource(id = R.string.paymentdetails_liquidity_miner_fee_help)
            ) {
                Text(text = payment.miningFees.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW_IF_ZERO_SATS))
            }
            Spacer(modifier = Modifier.height(8.dp))
            SplashLabelRow(
                label = stringResource(id = R.string.paymentdetails_liquidity_service_fee_label),
                helpMessage = stringResource(id = R.string.paymentdetails_liquidity_service_fee_help)
            ) {
                Text(text = payment.lease.fees.serviceFee.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW_IF_ZERO_SATS))
            }
        }
        payment is IncomingPayment -> {
            val receivedWithNewChannel = payment.received?.receivedWith?.filterIsInstance<IncomingPayment.ReceivedWith.NewChannel>() ?: emptyList()
            val receivedWithSpliceIn = payment.received?.receivedWith?.filterIsInstance<IncomingPayment.ReceivedWith.SpliceIn>() ?: emptyList()
            if ((receivedWithNewChannel + receivedWithSpliceIn).isNotEmpty()) {
                val serviceFee = receivedWithNewChannel.map { it.serviceFee }.sum() + receivedWithSpliceIn.map { it.serviceFee }.sum()
                val fundingFee = receivedWithNewChannel.map { it.miningFee }.sum() + receivedWithSpliceIn.map { it.miningFee }.sum()
                Spacer(modifier = Modifier.height(8.dp))
                if (serviceFee > 0.msat) {
                    SplashLabelRow(
                        label = stringResource(id = R.string.paymentdetails_service_fees_label),
                        helpMessage = stringResource(R.string.paymentdetails_service_fees_desc)
                    ) {
                        Text(text = serviceFee.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                SplashLabelRow(
                    label = stringResource(id = R.string.paymentdetails_funding_fees_label),
                    helpMessage = stringResource(R.string.paymentdetails_funding_fees_desc)
                ) {
                    Text(text = fundingFee.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.HIDE))
                }
            }
        }
        else -> {}
    }
}

@Composable
private fun InboundLiquidityLeaseDetails(lease: LiquidityAds.Lease) {
    Spacer(modifier = Modifier.height(8.dp))
    SplashLabelRow(label = stringResource(id = R.string.paymentdetails_liquidity_lease_duration_label)) {
        Text(text = stringResource(id = R.string.paymentdetails_liquidity_lease_duration_value))
    }
}

@Composable
private fun PaymentErrorView(status: LightningOutgoingPayment.Status.Completed.Failed, failedParts: List<LightningOutgoingPayment.Part.Status.Failed>) {
    val failure = remember(status, failedParts) { OutgoingPaymentFailure(status.reason, failedParts) }
    translatePaymentError(failure).let {
        Spacer(modifier = Modifier.height(8.dp))
        SplashLabelRow(label = stringResource(id = R.string.paymentdetails_error_label)) {
            Text(text = it)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomNoteDialog(
    initialDescription: String?,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var description by rememberSaveable { mutableStateOf(initialDescription) }

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colors.surface,
        contentColor = MaterialTheme.colors.onSurface,
        scrimColor = MaterialTheme.colors.onBackground.copy(alpha = 0.1f),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(top = 0.dp, start = 24.dp, end = 24.dp, bottom = 70.dp),
        ) {
            Text(text = stringResource(id = R.string.paymentdetails_edit_dialog_title), style = MaterialTheme.typography.body2)
            Spacer(modifier = Modifier.height(16.dp))
            TextInput(
                modifier = Modifier.fillMaxWidth(),
                text = description ?: "",
                onTextChange = { description = it.takeIf { it.isNotBlank() } },
                minLines = 2,
                maxLines = 6,
                maxChars = 280,
                staticLabel = stringResource(id = R.string.paymentdetails_edit_dialog_input_label)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onDismiss, text = stringResource(id = R.string.btn_cancel), shape = CircleShape)
                Button(
                    onClick = { onConfirm(description) },
                    text = stringResource(id = R.string.btn_save),
                    icon = R.drawable.ic_check,
                    enabled = description != initialDescription,
                    space = 8.dp,
                    shape = CircleShape
                )
            }
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
        confirmations?.let { conf ->
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

@Composable
fun translatePaymentError(paymentFailure: OutgoingPaymentFailure): String {
    val context = LocalContext.current
    val errorMessage = remember(key1 = paymentFailure) {
        when (val result = paymentFailure.explain()) {
            is Either.Left -> {
                when (val partFailure = result.value) {
                    is LightningOutgoingPayment.Part.Status.Failure.Uninterpretable -> partFailure.message
                    LightningOutgoingPayment.Part.Status.Failure.ChannelIsClosing -> context.getString(R.string.outgoing_failuremessage_channel_closing)
                    LightningOutgoingPayment.Part.Status.Failure.ChannelIsSplicing -> context.getString(R.string.outgoing_failuremessage_channel_splicing)
                    LightningOutgoingPayment.Part.Status.Failure.NotEnoughFees -> context.getString(R.string.outgoing_failuremessage_not_enough_fee)
                    LightningOutgoingPayment.Part.Status.Failure.NotEnoughFunds -> context.getString(R.string.outgoing_failuremessage_not_enough_balance)
                    LightningOutgoingPayment.Part.Status.Failure.PaymentAmountTooBig -> context.getString(R.string.outgoing_failuremessage_too_big)
                    LightningOutgoingPayment.Part.Status.Failure.PaymentAmountTooSmall -> context.getString(R.string.outgoing_failuremessage_too_small)
                    LightningOutgoingPayment.Part.Status.Failure.PaymentExpiryTooBig -> context.getString(R.string.outgoing_failuremessage_expiry_too_big)
                    LightningOutgoingPayment.Part.Status.Failure.RecipientRejectedPayment -> context.getString(R.string.outgoing_failuremessage_rejected_by_recipient)
                    LightningOutgoingPayment.Part.Status.Failure.RecipientIsOffline -> context.getString(R.string.outgoing_failuremessage_recipient_offline)
                    LightningOutgoingPayment.Part.Status.Failure.RecipientLiquidityIssue -> context.getString(R.string.outgoing_failuremessage_not_enough_liquidity)
                    LightningOutgoingPayment.Part.Status.Failure.TemporaryRemoteFailure -> context.getString(R.string.outgoing_failuremessage_temporary_failure)
                    LightningOutgoingPayment.Part.Status.Failure.TooManyPendingPayments -> context.getString(R.string.outgoing_failuremessage_too_many_pending)
                }
            }
            is Either.Right -> {
                when (result.value) {
                    FinalFailure.InvalidPaymentId -> context.getString(R.string.outgoing_failuremessage_invalid_id)
                    FinalFailure.AlreadyPaid -> context.getString(R.string.outgoing_failuremessage_alreadypaid)
                    FinalFailure.ChannelClosing -> context.getString(R.string.outgoing_failuremessage_channel_closing)
                    FinalFailure.ChannelNotConnected -> context.getString(R.string.outgoing_failuremessage_not_connected)
                    FinalFailure.ChannelOpening -> context.getString(R.string.outgoing_failuremessage_channel_opening)
                    FinalFailure.FeaturesNotSupported -> context.getString(R.string.outgoing_failuremessage_unsupported_features)
                    FinalFailure.InsufficientBalance -> context.getString(R.string.outgoing_failuremessage_not_enough_balance)
                    FinalFailure.InvalidPaymentAmount -> context.getString(R.string.outgoing_failuremessage_invalid_amount)
                    FinalFailure.NoAvailableChannels -> context.getString(R.string.outgoing_failuremessage_no_available_channels)
                    FinalFailure.RecipientUnreachable -> context.getString(R.string.outgoing_failuremessage_noroutefound)
                    FinalFailure.RetryExhausted -> context.getString(R.string.outgoing_failuremessage_noroutefound)
                    FinalFailure.UnknownError -> context.getString(R.string.outgoing_failuremessage_unknown)
                    FinalFailure.WalletRestarted -> context.getString(R.string.outgoing_failuremessage_restarted)
                }
            }
        }
    }
    return errorMessage
}
