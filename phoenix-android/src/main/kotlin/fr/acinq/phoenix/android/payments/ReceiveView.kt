/*
 * Copyright 2020 ACINQ SAS
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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.payment.LiquidityPolicy
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.sum
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.LocalFiatCurrency
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.Screen
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.components.feedback.InfoMessage
import fr.acinq.phoenix.android.components.feedback.WarningMessage
import fr.acinq.phoenix.android.fiatRate
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.borderColor
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.safeLet
import fr.acinq.phoenix.android.utils.share
import fr.acinq.phoenix.legacy.utils.LegacyPrefsDatastore
import fr.acinq.phoenix.managers.WalletBalance

@Composable
fun ReceiveView(
    onBackClick: () -> Unit,
    onSwapInReceived: () -> Unit
) {
    val context = LocalContext.current
    val balanceManager = business.balanceManager
    val vm: ReceiveViewModel = viewModel(factory = ReceiveViewModel.Factory(business.peerManager))
    val defaultInvoiceExpiry by UserPrefs.getInvoiceDefaultExpiry(context).collectAsState(null)
    val defaultInvoiceDesc by UserPrefs.getInvoiceDefaultDesc(context).collectAsState(null)

    // When a on-chain payment has been received, go back to the home screen (via the onSwapInReceived callback)
    LaunchedEffect(key1 = Unit) {
        var previousBalance: WalletBalance = WalletBalance.empty()
        balanceManager.swapInWalletBalance.collect {
            if (previousBalance != WalletBalance.empty() && it.total > 0.sat && it != previousBalance) {
                onSwapInReceived()
            } else {
                previousBalance = it
            }
        }
    }

    DefaultScreenLayout(horizontalAlignment = Alignment.CenterHorizontally, isScrollable = true) {
        DefaultScreenHeader(
            title = if (vm.isEditingLightningInvoice) {
                stringResource(id = R.string.receive_lightning_edit_title)
            } else null,
            onBackClick = {
                if (vm.isEditingLightningInvoice) {
                    vm.isEditingLightningInvoice = false
                } else {
                    onBackClick()
                }
            },
        )
        safeLet(defaultInvoiceDesc, defaultInvoiceExpiry) { desc, exp ->
            ReceiveViewPages(vm = vm, defaultInvoiceDescription = desc, defaultInvoiceExpiry = exp)
        } ?: ProgressView(text = stringResource(id = R.string.utils_loading_prefs))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReceiveViewPages(
    vm: ReceiveViewModel,
    defaultInvoiceDescription: String,
    defaultInvoiceExpiry: Long,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    // we need to be responsive in some subcomponents, like the edit-invoice buttons
    BoxWithConstraints {
        HorizontalPager(
            modifier = Modifier.fillMaxHeight(),
            state = pagerState,
            contentPadding = PaddingValues(horizontal = when {
                maxWidth <= 240.dp -> 30.dp
                maxWidth <= 320.dp -> 40.dp
                maxWidth <= 480.dp -> 44.dp
                else -> 52.dp
            }),
            verticalAlignment = Alignment.Top
        ) { index ->
            val maxWidth = maxWidth
            Column(
                modifier = Modifier
                    .padding(
                        horizontal = when {
                            maxWidth <= 320.dp -> 6.dp
                            maxWidth <= 480.dp -> 8.dp
                            else -> 10.dp
                        },
                        vertical = when {
                            maxHeight <= 800.dp -> 32.dp
                            else -> 50.dp
                        }
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (index) {
                    0 -> LightningInvoiceView(vm = vm, defaultDescription = defaultInvoiceDescription, expiry = defaultInvoiceExpiry, maxWidth = maxWidth)
                    1 -> BitcoinAddressView(state = vm.bitcoinAddressState, maxWidth = maxWidth)
                }
            }
        }
    }
}

@Composable
private fun InvoiceHeader(
    label: String,
    helpMessage: String,
    icon: Int
) {
    Row {
        IconPopup(
            icon = icon,
            iconSize = 24.dp,
            iconPadding = 4.dp,
            colorAtRest = MaterialTheme.colors.primary,
            spaceRight = 8.dp,
            spaceLeft = null,
            popupMessage = helpMessage
        )
        Text(text = label)
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun LightningInvoiceView(
    vm: ReceiveViewModel,
    defaultDescription: String,
    expiry: Long,
    maxWidth: Dp,
) {
    val context = LocalContext.current
    val paymentsManager = business.paymentsManager
    var customDesc by remember { mutableStateOf(defaultDescription) }
    var customAmount by remember { mutableStateOf<MilliSatoshi?>(null) }

    // refresh LN invoice when it has been paid
    LaunchedEffect(key1 = Unit) {
        paymentsManager.lastCompletedPayment.collect {
            val state = vm.lightningInvoiceState
            if (state is ReceiveViewModel.LightningInvoiceState.Show && it is IncomingPayment && state.paymentRequest.paymentHash == it.paymentHash) {
                vm.generateInvoice(amount = customAmount, description = customDesc, expirySeconds = expiry)
            }
        }
    }

    val channels by business.peerManager.channelsFlow.collectAsState()
    val availableForReceive = remember(channels) { channels?.values?.map { it.availableForReceive ?: 0.msat }?.sum() }
    val mempoolFeerate by business.appConfigurationManager.mempoolFeerate.collectAsState()
    val liquidityPolicy by UserPrefs.getLiquidityPolicy(context).collectAsState(null)

    val onEdit = { vm.isEditingLightningInvoice = true }

    InvoiceHeader(
        label = stringResource(id = R.string.receive_lightning_title),
        helpMessage = stringResource(id = R.string.receive_lightning_help),
        icon = R.drawable.ic_zap
    )

    val navController = navController
    val state = vm.lightningInvoiceState
    val isEditing = vm.isEditingLightningInvoice
    when {
        isEditing -> {
            EditInvoiceView(
                amount = customAmount,
                description = customDesc,
                onAmountChange = { customAmount = it },
                onDescriptionChange = { customDesc = it },
                onCancel = { vm.isEditingLightningInvoice = false },
                onSubmit = { vm.generateInvoice(amount = customAmount, description = customDesc, expirySeconds = expiry) }
            )
        }
        state is ReceiveViewModel.LightningInvoiceState.Init || state is ReceiveViewModel.LightningInvoiceState.Generating -> {
            if (state is ReceiveViewModel.LightningInvoiceState.Init) {
                LaunchedEffect(key1 = Unit) {
                    vm.generateInvoice(amount = customAmount, description = customDesc, expirySeconds = expiry)
                }
            }
            Box(contentAlignment = Alignment.Center) {
                QRCodeView(bitmap = vm.lightningQRBitmap, data = null, maxWidth = maxWidth)
                Card(shape = RoundedCornerShape(16.dp)) { ProgressView(text = stringResource(id = R.string.receive_lightning_generating)) }
            }
            CopyShareEditButtons(onCopy = { }, onShare = { }, onEdit = onEdit, maxWidth = maxWidth)
        }
        state is ReceiveViewModel.LightningInvoiceState.Show -> {
            DisplayLightningInvoice(
                paymentRequest = state.paymentRequest,
                bitmap = vm.lightningQRBitmap,
                onEdit = onEdit,
                maxWidth = maxWidth,
            )
        }
        state is ReceiveViewModel.LightningInvoiceState.Error -> {
            ErrorMessage(
                header = stringResource(id = R.string.receive_lightning_error),
                details = state.e.localizedMessage
            )
        }
    }

    IncomingLiquidityWarning(
        swapFee = mempoolFeerate?.swapEstimationFee(hasNoChannels = channels.isNullOrEmpty()),
        liquidityPolicy = liquidityPolicy,
        availableForReceive = availableForReceive,
        hasChannels = channels?.isNotEmpty(),
        amount = customAmount,
        onMessageClick = { navController.navigate(Screen.LiquidityPolicy.route) },
    )

    if ((state is ReceiveViewModel.LightningInvoiceState.Init || state is ReceiveViewModel.LightningInvoiceState.Show) && !isEditing) {
        Spacer(modifier = Modifier.height(16.dp))
        HSeparator(width = 50.dp)
        Spacer(modifier = Modifier.height(24.dp))
        BorderButton(
            text = stringResource(id = R.string.receive_lnurl_button),
            icon = R.drawable.ic_scan,
            onClick = { navController.navigate(Screen.ScanData.route) },
        )
    }
}

@Composable
private fun DisplayLightningInvoice(
    paymentRequest: PaymentRequest,
    bitmap: ImageBitmap?,
    onEdit: () -> Unit,
    maxWidth: Dp,
) {
    val context = LocalContext.current
    val prString = remember(paymentRequest) { paymentRequest.write() }
    val amount = paymentRequest.amount
    val description = paymentRequest.description.takeUnless { it.isNullOrBlank() }

    QRCodeView(data = prString, bitmap = bitmap, maxWidth = maxWidth)

    CopyShareEditButtons(
        onCopy = { copyToClipboard(context, data = prString) },
        onShare = { share(context, "lightning:$prString", context.getString(R.string.receive_lightning_share_subject), context.getString(R.string.receive_lightning_share_title)) },
        onEdit = onEdit,
        maxWidth = maxWidth
    )

    if (amount != null || description != null) {
        Spacer(modifier = Modifier.height(24.dp))
        HSeparator(width = 50.dp)
        Column(
            modifier = Modifier
                .clickable(onClick = onEdit, role = Role.Button, onClickLabel = stringResource(id = R.string.receive_lightning_edit_title))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!description.isNullOrBlank()) {
                QRCodeDetail(
                    label = stringResource(id = R.string.receive_lightning_desc_label),
                    value = description,
                    maxLines = 2
                )
            }
            if (amount != null) {
                QRCodeDetail(label = stringResource(id = R.string.receive_lightning_amount_label)) {
                    AmountWithFiatBelow(
                        amount = amount,
                        amountTextStyle = MaterialTheme.typography.body2.copy(fontSize = 14.sp),
                        fiatTextStyle = MaterialTheme.typography.subtitle2,
                    )
                }
            }
        }
    }
}

@Composable
private fun BitcoinAddressView(
    state: ReceiveViewModel.BitcoinAddressState,
    maxWidth: Dp
) {
    val context = LocalContext.current

    InvoiceHeader(
        label = stringResource(id = R.string.receive_bitcoin_title),
        helpMessage = stringResource(id = R.string.receive_bitcoin_help),
        icon = R.drawable.ic_chain
    )

    when (state) {
        is ReceiveViewModel.BitcoinAddressState.Init -> {
            QRCodeView(data = null, bitmap = null, maxWidth = maxWidth)
            CopyShareEditButtons(onCopy = { }, onShare = { }, onEdit = null, maxWidth = maxWidth)
        }
        is ReceiveViewModel.BitcoinAddressState.Show -> {
            QRCodeView(data = state.address, bitmap = state.image, maxWidth = maxWidth)
            CopyShareEditButtons(
                onCopy = { copyToClipboard(context, data = state.address) },
                onShare = { share(context, "bitcoin:${state.address}", context.getString(R.string.receive_bitcoin_share_subject), context.getString(R.string.receive_bitcoin_share_title)) },
                onEdit = null,
                maxWidth = maxWidth,
            )
            Spacer(modifier = Modifier.height(24.dp))
            HSeparator(width = 50.dp)
            Spacer(modifier = Modifier.height(16.dp))
            QRCodeDetail(label = stringResource(id = R.string.receive_bitcoin_address_label), value = state.address)

            val isFromLegacy by LegacyPrefsDatastore.hasMigratedFromLegacy(context).collectAsState(initial = false)
            if (isFromLegacy) {
                Spacer(modifier = Modifier.height(24.dp))
                InfoMessage(
                    header = stringResource(id = R.string.receive_onchain_legacy_warning_title),
                    details = stringResource(id = R.string.receive_onchain_legacy_warning),
                    detailsStyle = MaterialTheme.typography.subtitle2,
                    alignment = Alignment.CenterHorizontally
                )
            }
        }
        is ReceiveViewModel.BitcoinAddressState.Error -> {
            ErrorMessage(
                header = stringResource(id = R.string.receive_bitcoin_error),
                details = state.e.localizedMessage
            )
        }
    }
}

@Composable
private fun QRCodeView(
    data: String?,
    bitmap: ImageBitmap?,
    details: @Composable () -> Unit = {},
    maxWidth: Dp,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .width(
                when {
                    maxWidth <= 240.dp -> 160.dp
                    maxWidth <= 320.dp -> 240.dp
                    maxWidth <= 480.dp -> 270.dp
                    else -> 320.dp
                }
            )
            .clip(RoundedCornerShape(16.dp))
            .border(
                border = BorderStroke(1.dp, MaterialTheme.colors.primary),
                shape = RoundedCornerShape(16.dp)
            )
            .background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        QRCodeImage(bitmap) { data?.let { copyToClipboard(context, it) } }
        details()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QRCodeImage(
    bitmap: ImageBitmap?,
    onLongClick: () -> Unit,
) {
    var showFullScreenQR by remember { mutableStateOf(false) }
    val image: @Composable () -> Unit = {
        if (bitmap == null) {
            Image(
                painter = painterResource(id = R.drawable.ic_white),
                contentDescription = null,
                alignment = Alignment.Center,
                contentScale = ContentScale.FillWidth,
            )
        } else {
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(id = R.string.receive_help_qr),
                alignment = Alignment.Center,
                contentScale = ContentScale.FillWidth,
            )
        }
    }
    Surface(
        Modifier
            .combinedClickable(
                role = Role.Button,
                onClick = { if (bitmap != null) showFullScreenQR = true },
                onLongClick = onLongClick,
            )
            .fillMaxWidth()
            .background(Color.White)
            .padding(24.dp)
    ) { image() }

    if (showFullScreenQR) {
        FullScreenDialog(onDismiss = { showFullScreenQR = false }) {
            Surface(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(16.dp)
            ) { image() }
        }
    }
}

@Composable
private fun QRCodeDetail(label: String, value: String, maxLines: Int = Int.MAX_VALUE) {
    QRCodeDetail(label) {
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.body2.copy(fontSize = 14.sp),
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun QRCodeDetail(label: String, content: @Composable () -> Unit) {
    Row(modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.subtitle1.copy(fontSize = 12.sp, textAlign = TextAlign.End),
            modifier = Modifier
                .alignBy(FirstBaseline)
                .width(80.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier
            .alignBy(FirstBaseline)
            .widthIn(min = 100.dp)) {
            content()
        }
    }
}

@Composable
private fun CopyShareEditButtons(
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onEdit: (() -> Unit)?,
    maxWidth: Dp,
) {
    Spacer(modifier = Modifier.height(32.dp))

    Row(modifier = Modifier.padding(horizontal = 4.dp)) {
        BorderButton(icon = R.drawable.ic_copy, onClick = onCopy)
        Spacer(modifier = Modifier.width(if (maxWidth <= 360.dp) 12.dp else 16.dp))
        BorderButton(icon = R.drawable.ic_share, onClick = onShare)
        if (onEdit != null) {
            Spacer(modifier = Modifier.width(if (maxWidth <= 360.dp) 12.dp else 16.dp))
            BorderButton(
                text = if (maxWidth <= 360.dp) null else stringResource(id = R.string.receive_lightning_edit_button),
                icon = R.drawable.ic_edit,
                onClick = onEdit
            )
        }
    }
}

@Composable
private fun EditInvoiceView(
    amount: MilliSatoshi?,
    description: String?,
    onDescriptionChange: (String) -> Unit,
    onAmountChange: (MilliSatoshi?) -> Unit,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
) {
    val log = logger("EditInvoiceView")
    Column(
        modifier = Modifier
            .width(320.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                border = BorderStroke(1.dp, borderColor),
                shape = RoundedCornerShape(16.dp)
            )
            .background(MaterialTheme.colors.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        TextInput(
            text = description ?: "",
            onTextChange = onDescriptionChange,
            staticLabel = stringResource(id = R.string.receive_lightning_edit_desc_label),
            placeholder = { Text(text = stringResource(id = R.string.receive_lightning_edit_desc_placeholder), maxLines = 2, overflow = TextOverflow.Ellipsis) },
            maxChars = 140,
            minLines = 2,
            maxLines = Int.MAX_VALUE,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        )
        Spacer(Modifier.height(16.dp))

        AmountInput(
            amount = amount,
            staticLabel = stringResource(id = R.string.receive_lightning_edit_amount_label),
            placeholder = { Text(text = stringResource(id = R.string.receive_lightning_edit_amount_placeholder), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            onAmountChange = { complexAmount -> onAmountChange(complexAmount?.amount) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        )

        Row {
            Button(
                icon = R.drawable.ic_arrow_back,
                onClick = onCancel,
            )
            Spacer(modifier = Modifier.weight(1f))
            Button(
                text = stringResource(id = R.string.receive_lightning_edit_generate_button),
                icon = R.drawable.ic_qrcode,
                onClick = onSubmit,
                horizontalArrangement = Arrangement.End,
            )
        }
    }
}

@Composable
private fun IncomingLiquidityWarning(
    swapFee: Satoshi?,
    liquidityPolicy: LiquidityPolicy?,
    availableForReceive: MilliSatoshi?,
    hasChannels: Boolean?,
    amount: MilliSatoshi?,
    onMessageClick: () -> Unit
) {
    val btcUnit = LocalBitcoinUnit.current
    val fiatUnit = LocalFiatCurrency.current
    Spacer(modifier = Modifier.height(8.dp))
    when {
        // strong warning => no channels + fee policy is disabled
        hasChannels == false && liquidityPolicy is LiquidityPolicy.Disable -> {
            Clickable(onClick = onMessageClick) {
                WarningMessage(
                    header = stringResource(id = R.string.receive_lightning_warning_title_surefail),
                    details = stringResource(id = R.string.receive_lightning_warning_fee_policy_disabled_insufficient_liquidity),
                    headerStyle = MaterialTheme.typography.body2.copy(fontSize = 15.sp),
                    alignment = Alignment.CenterHorizontally,
                )
            }
        }
        // warning: liquidity is short => message depends on the fee policy used
        hasChannels == false || (availableForReceive != null && amount != null && amount >= availableForReceive) -> {
            when {
                // no fee policy => strong warning
                liquidityPolicy is LiquidityPolicy.Disable -> {
                    Clickable(onClick = onMessageClick, internalPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                        InfoMessage(
                            header = stringResource(id = R.string.receive_lightning_warning_title_mayfail),
                            details = stringResource(id = R.string.receive_lightning_warning_fee_policy_disabled_insufficient_liquidity),
                            headerStyle = MaterialTheme.typography.body1.copy(fontSize = 15.sp),
                            alignment = Alignment.CenterHorizontally,
                        )
                    }
                }
                // no fee information available => basic warning
                swapFee == null -> {
                    Clickable(onClick = onMessageClick) {
                        InfoMessage(
                            header = stringResource(id = R.string.receive_lightning_warning_title_fee_expected),
                            details = stringResource(id = R.string.receive_lightning_warning_fee_insufficient_liquidity),
                            headerStyle = MaterialTheme.typography.body1.copy(fontSize = 15.sp),
                            alignment = Alignment.CenterHorizontally,
                        )
                    }
                }
                // fee policy is short => light warning
                liquidityPolicy is LiquidityPolicy.Auto && swapFee > liquidityPolicy.maxAbsoluteFee -> {
                    Clickable(onClick = onMessageClick) {
                        InfoMessage(
                            header = stringResource(id = R.string.receive_lightning_warning_title_mayfail),
                            details = stringResource(id = R.string.receive_lightning_warning_fee_exceeds_policy, liquidityPolicy.maxAbsoluteFee.toPrettyString(btcUnit, withUnit = true)),
                            headerStyle = MaterialTheme.typography.body1.copy(fontSize = 15.sp),
                            alignment = Alignment.CenterHorizontally,
                        )
                    }
                }
                // fee policy is within bounds => light warning
                liquidityPolicy is LiquidityPolicy.Auto -> {
                    Clickable(onClick = onMessageClick) {
                        InfoMessage(
                            header = stringResource(id = R.string.receive_lightning_warning_title_fee_expected),
                            details = stringResource(
                                id = R.string.receive_lightning_warning_fee_within_policy, swapFee.toPrettyString(btcUnit, withUnit = true),
                                swapFee.toPrettyString(fiatUnit, fiatRate, withUnit = true)
                            ),
                            headerStyle = MaterialTheme.typography.body1.copy(fontSize = 15.sp),
                            alignment = Alignment.CenterHorizontally,
                        )
                    }
                }
            }
        }
    }
}
