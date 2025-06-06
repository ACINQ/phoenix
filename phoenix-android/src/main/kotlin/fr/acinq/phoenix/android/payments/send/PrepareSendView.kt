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

package fr.acinq.phoenix.android.payments.send

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.Screen
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Clickable
import fr.acinq.phoenix.android.components.DefaultScreenHeader
import fr.acinq.phoenix.android.components.DefaultScreenLayout
import fr.acinq.phoenix.android.components.dialogs.Dialog
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.components.contact.ContactPhotoView
import fr.acinq.phoenix.android.components.enableOrFade
import fr.acinq.phoenix.android.components.nfc.NfcReaderMonitor
import fr.acinq.phoenix.android.components.openLink
import fr.acinq.phoenix.android.components.scanner.ScannerView
import fr.acinq.phoenix.android.isDarkTheme
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.payments.send.bolt11.SendToBolt11View
import fr.acinq.phoenix.android.payments.send.lnurl.LnurlAuthView
import fr.acinq.phoenix.android.payments.send.lnurl.LnurlPayView
import fr.acinq.phoenix.android.payments.send.lnurl.LnurlWithdrawView
import fr.acinq.phoenix.android.payments.send.offer.SendToOfferView
import fr.acinq.phoenix.android.payments.send.spliceout.SendSpliceOutView
import fr.acinq.phoenix.android.popToHome
import fr.acinq.phoenix.android.utils.extensions.findActivitySafe
import fr.acinq.phoenix.android.utils.extensions.toLocalisedMessage
import fr.acinq.phoenix.android.utils.gray300
import fr.acinq.phoenix.android.utils.gray800
import fr.acinq.phoenix.android.utils.mutedTextColor
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.android.utils.readClipboard
import fr.acinq.phoenix.data.ContactInfo
import fr.acinq.phoenix.data.ContactPaymentCode
import fr.acinq.phoenix.data.lnurl.LnurlError
import fr.acinq.phoenix.managers.SendManager

/**
 * @param fromDeepLink Default false. If true, the back button always pops to Home.
 * @param forceNavOnBack Default false. If true, the back button always pops the backstack. Otherwise, be smart and maybe reset the parser instead.
 */
@Composable
fun SendView(
    initialInput: String?,
    immediatelyOpenScanner: Boolean,
    fromDeepLink: Boolean,
    forceNavOnBack: Boolean,
) {
    val navController = navController
    val vm = viewModel<PrepareSendViewModel>(factory = PrepareSendViewModel.Factory(sendManager = business.sendManager))
    var showScanner by remember { mutableStateOf(immediatelyOpenScanner) }
    val keyboardManager = LocalSoftwareKeyboardController.current

    val onBackClick: () -> Unit = {
        when {
            fromDeepLink -> navController.popToHome()
            forceNavOnBack -> navController.popBackStack()
            vm.parsePaymentState is ParsePaymentState.Ready -> navController.popBackStack()
            else -> vm.resetParsing()
        }
    }

    LaunchedEffect(key1 = Unit) {
        if (!initialInput.isNullOrBlank()) {
            vm.parsePaymentData(initialInput)
        }
    }

    when (val parseState = vm.parsePaymentState) {
        // if payment data has been successfully parsed, redirect to the relevant payment screen
        is ParsePaymentState.Success -> {
            LocalViewModelStoreOwner.current?.viewModelStore?.clear()
            when (val data = parseState.data) {
                is SendManager.ParseResult.Bolt11Invoice -> {
                    SendToBolt11View(invoice = data.invoice, onBackClick = onBackClick, onPaymentSent = { navController.popToHome() })
                }
                is SendManager.ParseResult.Bolt12Offer -> {
                    SendToOfferView(offer = data.offer, onBackClick = onBackClick, onPaymentSent = { navController.popToHome() })
                }
                is SendManager.ParseResult.Uri -> {
                    SendSpliceOutView(requestedAmount = data.uri.amount, address = data.uri.address, onBackClick = onBackClick, onSpliceOutSuccess = { navController.popToHome() })
                }
                is SendManager.ParseResult.Lnurl.Pay -> {
                    LnurlPayView(pay = data, onBackClick = onBackClick, onPaymentSent = { navController.popToHome() })
                }
                is SendManager.ParseResult.Lnurl.Withdraw -> {
                    LnurlWithdrawView(withdraw = data.lnurlWithdraw, onBackClick = onBackClick, onFeeManagementClick = { navController.navigate(Screen.LiquidityPolicy.route) }, onWithdrawDone = { navController.popToHome() })
                }
                is SendManager.ParseResult.Lnurl.Auth -> {
                    LnurlAuthView(auth = data.auth, onBackClick = onBackClick, onChangeAuthSchemeSettingClick = { navController.navigate("${Screen.PaymentSettings.route}?showAuthSchemeDialog=true") },
                        onAuthDone = { navController.popToHome() },)
                }
            }
        }
        is ParsePaymentState.Ready, is ParsePaymentState.Processing, is ParsePaymentState.Error, is ParsePaymentState.ChoosePaymentMode -> {
            PrepareSendView(
                onBackClick = onBackClick,
                vm = vm,
                onShowScanner = {
                    vm.resetParsing()
                    keyboardManager?.hide()
                    showScanner = true
                },
            )

            // show dialogs when a parsing error occurs
            if (parseState is ParsePaymentState.Error && !showScanner) {
                Dialog(onDismiss = vm::resetParsing, buttons = null) {
                    PaymentDataError(
                        errorMessage = when (parseState) {
                            is ParsePaymentState.GenericError -> parseState.errorMessage
                            is ParsePaymentState.ParsingFailure -> parseState.error.toLocalisedMessage()
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Spacer(modifier = Modifier.weight(1f))
                        if (parseState is ParsePaymentState.ParsingFailure && parseState.error.reason is SendManager.BadRequestReason.ServiceError) {
                            val error = (parseState.error.reason as SendManager.BadRequestReason.ServiceError).error
                            if (error is LnurlError.RemoteFailure.IsWebsite) {
                                val context = LocalContext.current
                                Button(text = "Open link", icon = R.drawable.ic_external_link, onClick = { openLink(context, error.origin) })
                            }
                        }
                        Button(text = stringResource(id = R.string.btn_ok), onClick = vm::resetParsing)
                    }
                }
            }

            if (parseState is ParsePaymentState.ChooseOnchainOrBolt11) {
                ChoosePaymentModeDialog(
                    onPayOnchainClick = { vm.parsePaymentState = ParsePaymentState.Success(SendManager.ParseResult.Uri(parseState.uri)) },
                    onPayOffchainClick = { vm.parsePaymentState = ParsePaymentState.Success(SendManager.ParseResult.Bolt11Invoice(request = parseState.request, parseState.bolt11)) },
                    onDismiss = { vm.resetParsing() }
                )
            }

            if (parseState is ParsePaymentState.ChooseOnchainOrOffer) {
                ChoosePaymentModeDialog(
                    onPayOnchainClick = { vm.parsePaymentState = ParsePaymentState.Success(SendManager.ParseResult.Uri(parseState.uri)) },
                    onPayOffchainClick = { vm.parsePaymentState = ParsePaymentState.Success(SendManager.ParseResult.Bolt12Offer(offer = parseState.offer, lightningAddress = null)) },
                    onDismiss = { vm.resetParsing() }
                )
            }

            if (showScanner) {
                ScannerBox(state = vm.parsePaymentState, onDismiss = { vm.resetParsing() ; showScanner = false }, onReset = vm::resetParsing, onSubmit = vm::parsePaymentData)
            }
        }
    }
}

@Composable
private fun PrepareSendView(
    vm: PrepareSendViewModel,
    onBackClick: () -> Unit,
    onShowScanner: () -> Unit,
) {
    val context = LocalContext.current
    var freeFormInput by remember { mutableStateOf("") }
    val parsePaymentState = vm.parsePaymentState
    val isProcessingData = vm.parsePaymentState.isProcessing || vm.readImageState.isProcessing

    DefaultScreenLayout(isScrollable = false, navBarColor = MaterialTheme.colors.surface) {
        DefaultScreenHeader(title = stringResource(id = R.string.preparesend_title), onBackClick = onBackClick)

        // show error message when reading an image from disk fails
        when (vm.readImageState) {
            is ReadImageState.Error -> Dialog(onDismiss = { vm.readImageState = ReadImageState.Ready }) {
                Text(text = stringResource(id = R.string.preparesend_imagepicker_error), modifier = Modifier.padding(16.dp))
            }
            is ReadImageState.NotFound -> Dialog(onDismiss = { vm.readImageState = ReadImageState.Ready }) {
                Text(text = stringResource(id = R.string.preparesend_imagepicker_not_found), modifier = Modifier.padding(16.dp))
            }
            ReadImageState.Reading, ReadImageState.Ready -> Unit
        }

        SendSmartInput(
            onValueChange = {
                if (it.isBlank()) { vm.resetParsing() }
                freeFormInput = it
            },
            onValueSubmit = { vm.parsePaymentData(freeFormInput) },
            isProcessing = isProcessingData,
            isError = parsePaymentState.hasFailed,
        )

        // list of contacts
        val contactsState = business.databaseManager.contactsList.collectAsState(emptyList())
        val contacts = contactsState.value.filter { it.paymentCodes.isNotEmpty() }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (contacts.isEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                TextWithIcon(text = stringResource(id = R.string.preparesend_contacts_none), icon = R.drawable.ic_user, textStyle = MaterialTheme.typography.caption.copy(fontSize = 16.sp), iconTint = MaterialTheme.typography.caption.color, iconSize = 24.dp, space = 8.dp)
            } else {
                val filteredContacts by produceState(initialValue = emptyList(), key1 = contacts, key2 = freeFormInput) {
                    value = if (freeFormInput.isBlank() || freeFormInput.length < 2) {
                        contacts
                    } else {
                        contacts.filter { it.name.contains(freeFormInput, ignoreCase = true) }
                    }
                }
                if (freeFormInput.isNotBlank() && filteredContacts.isEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    TextWithIcon(text = stringResource(id = R.string.preparesend_contacts_no_matches), icon = R.drawable.ic_user_search, textStyle = MaterialTheme.typography.caption.copy(fontSize = 16.sp), iconTint = MaterialTheme.typography.caption.color, iconSize = 24.dp, space = 8.dp)
                } else {
                    LazyColumn {
                        item { Spacer(modifier = Modifier.height(12.dp)) }
                        items(filteredContacts) {
                            ContactRow(
                                contactInfo = it,
                                onSendClick = { vm.parsePaymentData(it.paymentCode) },
                                enabled = !isProcessingData
                            )
                        }
                        item { Spacer(modifier = Modifier.height(12.dp)) }
                    }
                }
            }
        }

        // bottom buttons
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = MaterialTheme.colors.surface,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                if (isProcessingData) {
                    ProgressView(text = when {
                        vm.parsePaymentState is ParsePaymentState.ResolvingBip353 -> stringResource(id = R.string.preparesend_bip353_resolving)
                        vm.parsePaymentState is ParsePaymentState.ResolvingLnurl -> stringResource(id = R.string.preparesend_lnurl_fetching)
                        else -> stringResource(id = R.string.preparesend_parsing)
                    }, modifier = Modifier.heightIn(min = 80.dp), padding = PaddingValues(20.dp))
                } else {
                    SendButtonsRow(
                        onSubmit = {
                            freeFormInput = ""
                            vm.parsePaymentData(it)
                        },
                        onReadImage = {
                            vm.resetParsing()
                            vm.readImage(context, it, onDataFound = vm::parsePaymentData)
                        },
                        onShowScanner = onShowScanner,
                        enabled = true
                    )
                }
            }
        }
    }

    NfcReaderMonitor()
}

@Composable
private fun RowScope.SendButtonsRow(
    onSubmit: (String) -> Unit,
    onReadImage: (Uri) -> Unit,
    onShowScanner: () -> Unit,
    enabled: Boolean,
) {
    val context = LocalContext.current
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val imagePickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) {
        imageUri = it
    }
    LaunchedEffect(key1 = imageUri) {
        imageUri?.let { onReadImage(it) ; imageUri = null }
    }
    ReadDataButton(label = stringResource(id = R.string.preparesend_imagepicker_button), icon = R.drawable.ic_image, onClick = { imagePickerLauncher.launch("image/*") }, enabled = enabled)
    ReadDataButton(label = stringResource(id = R.string.preparesend_paste_button), icon = R.drawable.ic_paste, onClick = { readClipboard(context)?.let { onSubmit(it) } }, enabled = enabled)
    if (context.findActivitySafe()?.isNfcReaderAvailable() == true) {
        ReadDataButton(label = stringResource(id = R.string.nfc_button), icon = R.drawable.ic_nfc, onClick = { context.findActivitySafe()?.startNfcReader() }, enabled = enabled)
    }
    ReadDataButton(label = stringResource(id = R.string.preparesend_scan_button), icon = R.drawable.ic_scan_qr, onClick = onShowScanner, enabled = enabled)
}

@Composable
private fun PaymentDataError(errorMessage: String, modifier: Modifier = Modifier, textStyle: TextStyle = MaterialTheme.typography.body1) {
    Row(modifier) {
        PhoenixIcon(resourceId = R.drawable.ic_alert_triangle, tint = negativeColor, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = errorMessage, style = textStyle)
    }
}

@Composable
private fun ScannerBox(
    state: ParsePaymentState,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        ScannerView(
            onScannedText = onSubmit,
            isPaused = state !is ParsePaymentState.Ready,
            onDismiss = onDismiss
        )
        BackHandler(onBack = onDismiss)
        if (state is ParsePaymentState.Error) {
            Dialog(onDismiss = onReset) {
                BackHandler(onBack = onDismiss)
                PaymentDataError(
                    errorMessage = when (state) {
                        is ParsePaymentState.GenericError -> state.errorMessage
                        is ParsePaymentState.ParsingFailure -> state.error.toLocalisedMessage()
                    },
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun RowScope.ReadDataButton(
    label: String,
    icon: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Clickable(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .alpha(if (enabled) 1f else 0.35f),
        onClick = onClick,
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
    ) {
        Column(
            modifier = Modifier.padding(top = 12.dp, start = 8.dp, end = 8.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .indication(interactionSource, ripple(bounded = false, color = if (isDarkTheme) gray300 else gray800, radius = 32.dp))
                    .clip(CircleShape)
                    .border(width = 1.dp, color = MaterialTheme.colors.primary, shape = CircleShape)
                    .background(MaterialTheme.colors.surface)
                    .padding(14.dp),
            ) {
                PhoenixIcon(resourceId = icon, tint = MaterialTheme.colors.primary, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = label, style = MaterialTheme.typography.body2.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ContactRow(
    contactInfo: ContactInfo,
    onSendClick: (ContactPaymentCode) -> Unit,
    enabled: Boolean,
) {
    var showPaymentCodesList by remember { mutableStateOf(false) }

    Clickable(modifier = Modifier.fillMaxWidth(), onClick = {
        when {
            contactInfo.paymentCodes.isEmpty() -> Unit
            contactInfo.paymentCodes.size == 1 -> onSendClick(contactInfo.paymentCodes.first())
            else -> showPaymentCodesList = !showPaymentCodesList
        }
    }, enabled = enabled) {
        Column(
            modifier = Modifier
                .then(if (showPaymentCodesList) Modifier.background(mutedTextColor.copy(alpha = .1f)) else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.enableOrFade(enabled),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ContactPhotoView(photoUri = contactInfo.photoUri, name = contactInfo.name, onChange = null, imageSize = 38.dp, borderSize = 1.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = contactInfo.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 18.sp)
            }
            if (showPaymentCodesList) {
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(contactInfo.paymentCodes) { paymentCode ->
                        Clickable(onClick = { onSendClick(paymentCode) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp, 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                val (main, details) = remember(paymentCode) {
                                    if (paymentCode.label.isNullOrBlank()) {
                                        paymentCode.paymentCode to null
                                    } else {
                                        paymentCode.label!! to paymentCode.paymentCode
                                    }
                                }
                                PhoenixIcon(R.drawable.ic_send, tint = MaterialTheme.colors.primary, modifier = Modifier.size(14.dp).align(Alignment.CenterVertically))
                                Text(text = main, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alignByBaseline())
                                details?.let { Text(text = it, style = MaterialTheme.typography.subtitle2, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.alignByBaseline()) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoosePaymentModeDialog(
    onPayOnchainClick: () -> Unit,
    onPayOffchainClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismiss = onDismiss, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = true), isScrollable = true, buttons = null) {
        Clickable(onClick = onPayOnchainClick) {
            Row(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                PhoenixIcon(resourceId = R.drawable.ic_chain)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(text = stringResource(id = R.string.send_paymentmode_onchain), style = MaterialTheme.typography.body2)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = stringResource(id = R.string.send_paymentmode_onchain_desc), style = MaterialTheme.typography.caption.copy(fontSize = 14.sp))
                }
            }
        }
        Clickable(onClick = onPayOffchainClick) {
            Row(modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                PhoenixIcon(resourceId = R.drawable.ic_zap)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(text = stringResource(id = R.string.send_paymentmode_lightning), style = MaterialTheme.typography.body2)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = stringResource(id = R.string.send_paymentmode_lightning_desc), style = MaterialTheme.typography.caption.copy(fontSize = 14.sp))
                }
            }
        }
    }
}
