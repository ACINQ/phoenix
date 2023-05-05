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

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.utils.*
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
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
        var previousBalance: WalletBalance? = null
        balanceManager.swapInWalletBalance.collect {
            if (previousBalance != null && it.total > 0.sat && it != previousBalance) {
                onSwapInReceived()
            } else {
                previousBalance = it
            }
        }
    }

    DefaultScreenLayout(horizontalAlignment = Alignment.CenterHorizontally, isScrollable = false) {
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

@OptIn(ExperimentalPagerApi::class)
@Composable
private fun ReceiveViewPages(
    vm: ReceiveViewModel,
    defaultInvoiceDescription: String,
    defaultInvoiceExpiry: Long,
) {
    val pagerState = rememberPagerState()
    HorizontalPager(
        modifier = Modifier.fillMaxHeight(),
        count = 2,
        state = pagerState,
        contentPadding = PaddingValues(horizontal = 44.dp),
        verticalAlignment = Alignment.Top
    ) { index ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (index) {
                0 -> LightningInvoiceView(vm = vm, defaultDescription = defaultInvoiceDescription, expiry = defaultInvoiceExpiry)
                1 -> BitcoinAddressView(state = vm.bitcoinAddressState)
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
) {
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

    val onEdit = { vm.isEditingLightningInvoice = true }

    InvoiceHeader(
        label = stringResource(id = R.string.receive_lightning_title),
        helpMessage = stringResource(id = R.string.receive_lightning_help),
        icon = R.drawable.ic_zap
    )

    val state = vm.lightningInvoiceState
    val isEditing = vm.isEditingLightningInvoice
    when {
        state is ReceiveViewModel.LightningInvoiceState.Init -> {
            LaunchedEffect(key1 = Unit) {
                vm.generateInvoice(amount = customAmount, description = customDesc, expirySeconds = expiry)
            }
            GeneratingLightningInvoice(bitmap = vm.lightningQRBitmap)
            CopyShareEditButtons(onCopy = { }, onShare = { }, onEdit = onEdit)
        }
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
        state is ReceiveViewModel.LightningInvoiceState.Generating -> {
            GeneratingLightningInvoice(bitmap = vm.lightningQRBitmap)
            CopyShareEditButtons(onCopy = { }, onShare = { }, onEdit = onEdit)
        }
        state is ReceiveViewModel.LightningInvoiceState.Show -> {
            DisplayLightningInvoice(
                paymentRequest = state.paymentRequest,
                bitmap = vm.lightningQRBitmap,
                onEdit = onEdit
            )
        }
        state is ReceiveViewModel.LightningInvoiceState.Error -> {
            ErrorMessage(
                errorHeader = "Failed to generate invoice",
                errorDetails = state.e.localizedMessage
            )
        }
    }
}

@Composable
private fun GeneratingLightningInvoice(bitmap: ImageBitmap?) {
    Box(contentAlignment = Alignment.Center) {
        QRCodeView(bitmap = bitmap, data = null)
        Card(shape = RoundedCornerShape(16.dp)) { ProgressView(text = stringResource(id = R.string.receive_lightning_generating)) }
    }
}

@Composable
private fun DisplayLightningInvoice(
    paymentRequest: PaymentRequest,
    bitmap: ImageBitmap?,
    onEdit: () -> Unit,
) {
    val context = LocalContext.current
    val prString = remember(paymentRequest) { paymentRequest.write() }
    val amount = paymentRequest.amount
    val description = paymentRequest.description.takeUnless { it.isNullOrBlank() }

    QRCodeView(data = prString, bitmap = bitmap)

    CopyShareEditButtons(
        onCopy = { copyToClipboard(context, data = prString) },
        onShare = { share(context, "lightning:$prString", context.getString(R.string.receive_lightning_share_subject), context.getString(R.string.receive_lightning_share_title)) },
        onEdit = onEdit
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
            if (amount != null) {
                QRCodeDetail(label = stringResource(id = R.string.receive_lightning_amount_label)) {
                    AmountWithFiatColumnView(
                        amount = amount,
                        amountTextStyle = MaterialTheme.typography.body2.copy(fontSize = 14.sp),
                        fiatTextStyle = MaterialTheme.typography.caption.copy(fontSize = 13.sp),
                    )
                }
            }
            if (!description.isNullOrBlank()) {
                QRCodeDetail(
                    label = stringResource(id = R.string.receive_lightning_desc_label),
                    value = description,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun BitcoinAddressView(
    state: ReceiveViewModel.BitcoinAddressState,
) {
    val context = LocalContext.current

    InvoiceHeader(
        label = stringResource(id = R.string.receive_bitcoin_title),
        helpMessage = stringResource(id = R.string.receive_bitcoin_help),
        icon = R.drawable.ic_chain
    )

    when (state) {
        is ReceiveViewModel.BitcoinAddressState.Init -> {
            QRCodeView(data = null, bitmap = null)
            CopyShareEditButtons(onCopy = { }, onShare = { }, onEdit = null)
        }
        is ReceiveViewModel.BitcoinAddressState.Show -> {
            QRCodeView(data = state.address, bitmap = state.image)
            CopyShareEditButtons(
                onCopy = { copyToClipboard(context, data = state.address) },
                onShare = { share(context, "bitcoin:${state.address}", context.getString(R.string.receive_bitcoin_share_subject), context.getString(R.string.receive_bitcoin_share_title)) },
                onEdit = null
            )
            Spacer(modifier = Modifier.height(24.dp))
            HSeparator(width = 50.dp)
            Spacer(modifier = Modifier.height(16.dp))
            QRCodeDetail(label = stringResource(id = R.string.receive_bitcoin_address_label), value = state.address)
        }
        is ReceiveViewModel.BitcoinAddressState.Error -> {
            ErrorMessage(errorHeader = stringResource(id = R.string.receive_bitcoin_error), errorDetails = state.e.localizedMessage)
        }
    }
}

@Composable
private fun QRCodeView(
    data: String?,
    bitmap: ImageBitmap?,
    details: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .width(270.dp)
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
    Row(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.subtitle1.copy(fontSize = 12.sp, textAlign = TextAlign.Start),
            modifier = Modifier.alignBy(FirstBaseline)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.alignBy(FirstBaseline)) {
            content()
        }
    }
}

@Composable
private fun CopyShareEditButtons(
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onEdit: (() -> Unit)?,
) {
    Spacer(modifier = Modifier.height(32.dp))
    Row(modifier = Modifier.padding(horizontal = 32.dp)) {
        BorderButton(icon = R.drawable.ic_copy, onClick = onCopy)
        Spacer(modifier = Modifier.width(16.dp))
        BorderButton(icon = R.drawable.ic_share, onClick = onShare)
        if (onEdit != null) {
            Spacer(modifier = Modifier.width(16.dp))
            BorderButton(
                text = stringResource(id = R.string.receive_lightning_edit_button),
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
                border = BorderStroke(1.dp, MaterialTheme.colors.primary),
                shape = RoundedCornerShape(16.dp)
            )
            .background(MaterialTheme.colors.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            AmountInput(
                initialAmount = amount,
                label = { Text(text = stringResource(id = R.string.receive_lightning_edit_amount_label)) },
                placeholder = { Text(text = stringResource(id = R.string.receive_lightning_edit_amount_placeholder)) },
                onAmountChange = { complexAmount ->
                    log.debug { "invoice amount update amount=$complexAmount" }
                    onAmountChange(complexAmount?.amount)
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            TextInput(
                text = description ?: "",
                onTextChange = onDescriptionChange,
                label = { Text(stringResource(id = R.string.receive_lightning_edit_desc_label)) },
                maxChars = 180,
                maxLines = Int.MAX_VALUE,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row {
            Button(
                icon = R.drawable.ic_arrow_back,
                onClick = onCancel,
            )
            Button(
                text = stringResource(id = R.string.receive_lightning_edit_generate_button),
                icon = R.drawable.ic_qrcode,
                modifier = Modifier.weight(1f),
                onClick = onSubmit,
            )
        }

    }
}
