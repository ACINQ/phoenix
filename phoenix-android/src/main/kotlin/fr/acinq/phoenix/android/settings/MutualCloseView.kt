/*
 * Copyright 2021 ACINQ SAS
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


package fr.acinq.phoenix.android.settings


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.bitcoin.utils.Either
import fr.acinq.lightning.utils.msat
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.mvi.MVIView
import fr.acinq.phoenix.android.payments.ScannerView
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.controllers.config.CloseChannelsConfiguration
import fr.acinq.phoenix.data.BitcoinAddressError
import fr.acinq.phoenix.utils.Parser


@Composable
fun MutualCloseView() {
    val log = logger("MutualCloseView")
    val nc = navController
    val context = LocalContext.current
    val balance by business.peerManager.balance.collectAsState(0.msat)

    var address by remember { mutableStateOf("") }
    var addressErrorMessage by remember { mutableStateOf<String?>(null) }
    var showScannerView by remember { mutableStateOf(false) }
    var showConfirmationDialog by remember { mutableStateOf(false) }

    MVIView(CF::closeChannelsConfiguration) { model, postIntent ->
        if (showScannerView) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
            ) {
                ScannerView(
                    onScanViewBinding = { },
                    onScannedText = {
                        address = it
                        addressErrorMessage = ""
                        showScannerView = false
                    }
                )

                // buttons at the bottom of the screen
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colors.surface)
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(id = R.string.btn_cancel),
                        icon = R.drawable.ic_arrow_back,
                        onClick = { showScannerView = false }
                    )
                }
            }
        } else {
            DefaultScreenLayout {
                DefaultScreenHeader(
                    onBackClick = { nc.popBackStack() },
                    title = stringResource(id = R.string.mutualclose_title),
                )
                when (model) {
                    is CloseChannelsConfiguration.Model.Loading -> {
                        Card(internalPadding = PaddingValues(16.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(text = stringResource(id = R.string.mutualclose_loading))
                        }
                    }
                    is CloseChannelsConfiguration.Model.Ready -> {
                        Card(internalPadding = PaddingValues(16.dp), modifier = Modifier.fillMaxWidth()) {
                            balance?.let {
                                Text(
                                    annotatedStringResource(
                                        id = R.string.mutualclose_balance,
                                        it.toPrettyString(LocalBitcoinUnit.current, withUnit = true),
                                        it.toPrettyString(LocalFiatCurrency.current, fiatRate, withUnit = true)
                                    )
                                )
                            } ?: ProgressView(text = stringResource(R.string.mutualclose_checking_balance))
                        }
                        Card(internalPadding = PaddingValues(16.dp)) {
                            if (model.channels.isNotEmpty()) {
                                Text(text = stringResource(id = R.string.mutualclose_input_instructions))
                                Spacer(Modifier.height(16.dp))
                                TextInput(
                                    text = address,
                                    onTextChange = { addressErrorMessage = ""; address = it },
                                    label = { Text(text = stringResource(id = R.string.mutualclose_input_hint)) },
                                    placeholder = { Text(text = stringResource(id = R.string.mutualclose_input_placeholder)) },
                                    trailingIcon = {
                                        Button(
                                            onClick = { showScannerView = true },
                                            icon = R.drawable.ic_scan_qr,
                                            iconTint = MaterialTheme.colors.primary
                                        )
                                    },
                                    errorMessage = addressErrorMessage,
                                    maxLines = 3,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text(text = stringResource(id = R.string.mutualclose_no_channels))
                            }
                        }
                        Card {
                            val chain = business.chain
                            SettingButton(
                                text = R.string.mutualclose_button,
                                icon = R.drawable.ic_cross_circle,
                                enabled = address.isNotBlank() && model.channels.isNotEmpty(),
                                onClick = {
                                    when (val validation = Parser.readBitcoinAddress(chain, address)) {
                                        is Either.Left -> {
                                            val error = validation.value
                                            addressErrorMessage = when (error) {
                                                is BitcoinAddressError.ChainMismatch -> context.getString(R.string.mutualclose_error_chain_mismatch)
                                                is BitcoinAddressError.UnhandledRequiredParams -> context.getString(R.string.mutualclose_error_chain_reqparams)
                                                else -> context.getString(R.string.mutualclose_error_chain_generic)
                                            }
                                        }
                                        else -> {
                                            showConfirmationDialog = true
                                        }
                                    }
                                }
                            )
                            if (showConfirmationDialog) {
                                ConfirmDialog(
                                    message = stringResource(R.string.mutualclose_confirm),
                                    onDismiss = { showConfirmationDialog = false },
                                    onConfirm = {
                                        addressErrorMessage = ""
                                        postIntent(CloseChannelsConfiguration.Intent.MutualCloseAllChannels(address))
                                        showConfirmationDialog = false
                                    }
                                )
                            }
                        }
                    }
                    is CloseChannelsConfiguration.Model.ChannelsClosed -> {
                        Card(internalPadding = PaddingValues(16.dp)) {
                            Text(text = stringResource(R.string.mutualclose_done, model.channels.size))
                        }
                    }
                }
            }
        }
    }
}
