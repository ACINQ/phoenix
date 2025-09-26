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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.bitcoin.BitcoinError
import fr.acinq.bitcoin.Satoshi
import fr.acinq.bitcoin.utils.Either
import fr.acinq.lightning.blockchain.fee.FeeratePerByte
import fr.acinq.lightning.blockchain.fee.FeeratePerKw
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.CF
import fr.acinq.phoenix.android.LocalBitcoinUnits
import fr.acinq.phoenix.android.LocalFiatCurrencies
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.components.buttons.Button
import fr.acinq.phoenix.android.components.layouts.Card
import fr.acinq.phoenix.android.components.layouts.DefaultScreenHeader
import fr.acinq.phoenix.android.components.layouts.DefaultScreenLayout
import fr.acinq.phoenix.android.components.inputs.FeerateSlider
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.inputs.TextInput
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.components.buttons.TransparentFilledButton
import fr.acinq.phoenix.android.components.buttons.SmartSpendButton
import fr.acinq.phoenix.android.components.dialogs.ModalBottomSheet
import fr.acinq.phoenix.android.components.mvi.MVIView
import fr.acinq.phoenix.android.components.scanner.ScannerView
import fr.acinq.phoenix.android.primaryFiatRate
import fr.acinq.phoenix.android.utils.converters.AmountFormatter.toPrettyString
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.extensions.safeLet
import fr.acinq.phoenix.android.utils.monoTypo
import fr.acinq.phoenix.controllers.config.CloseChannelsConfiguration
import fr.acinq.phoenix.data.BitcoinUriError
import fr.acinq.phoenix.utils.Parser
import kotlinx.coroutines.launch


@Composable
fun MutualCloseView(
    walletId: WalletId,
    business: PhoenixBusiness,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    val balance by business.balanceManager.balance.collectAsState(0.msat)

    var address by remember { mutableStateOf("") }
    var addressErrorMessage by remember { mutableStateOf<String?>(null) }
    val mempoolFeerate by business.appConfigurationManager.mempoolFeerate.collectAsState()
    val recommendedFeerate by business.peerManager.recommendedFeerateFlow.collectAsState()
    var feerate by remember { mutableStateOf(recommendedFeerate.feerate) }

    var showScannerView by remember { mutableStateOf(false) }
    var showConfirmationDialog by remember { mutableStateOf(false) }

    MVIView(CF::closeChannelsConfiguration) { model, postIntent ->
        if (showScannerView) {
            Box(Modifier.fillMaxSize()) {
                ScannerView(
                    onScannedText = {
                        address = Parser.trimMatchingPrefix(Parser.removeExcessInput(it), Parser.bitcoinPrefixes)
                        addressErrorMessage = ""
                        showScannerView = false
                    },
                    isPaused = false,
                    onDismiss = { showScannerView = false }
                )
            }
        } else {
            DefaultScreenLayout {
                DefaultScreenHeader(
                    onBackClick = onBackClick,
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
                                        it.toPrettyString(LocalBitcoinUnits.current.primary, withUnit = true),
                                        it.toPrettyString(LocalFiatCurrencies.current.primary, primaryFiatRate, withUnit = true)
                                    )
                                )
                            } ?: ProgressView(text = stringResource(R.string.mutualclose_checking_balance))
                        }
                        Card(internalPadding = PaddingValues(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (model.channels.isNotEmpty()) {
                                Text(text = stringResource(id = R.string.mutualclose_input_instructions))
                                Spacer(Modifier.height(16.dp))
                                TextInput(
                                    text = address,
                                    onTextChange = { addressErrorMessage = ""; address = it },
                                    staticLabel = stringResource(id = R.string.mutualclose_input_label),
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

                                Spacer(modifier = Modifier.height(16.dp))
                                Row(modifier = Modifier) {
                                    Text(text = stringResource(R.string.send_spliceout_feerate_label), style = MaterialTheme.typography.body2, fontSize = 14.sp, modifier = Modifier.alignByBaseline())
                                    Spacer(modifier = Modifier.width(16.dp))
                                    FeerateSlider(
                                        modifier = Modifier.alignByBaseline(),
                                        feerate = feerate,
                                        onFeerateChange = { feerate = it },
                                        mempoolFeerate = mempoolFeerate,
                                        enabled = true
                                    )
                                }
                            } else {
                                Text(text = stringResource(id = R.string.mutualclose_no_channels))
                            }
                        }
                        Card {
                            var isEstimatingFee by remember { mutableStateOf(false) }

                            val scope = rememberCoroutineScope()
                            val chain = business.chain
                            val peerState = business.peerManager.peerState.collectAsState(null)
                            val peer = peerState.value
                            var totalFeeEstimate by remember { mutableStateOf<Satoshi?>(null) }
                            val isUsingLowFeerate by produceState(initialValue = false, mempoolFeerate, feerate) {
                                value = safeLet(mempoolFeerate, feerate) { mf, f -> FeeratePerByte(f).feerate < mf.hour.feerate } ?: false
                            }

                            Button(
                                text = stringResource(id = R.string.mutualclose_button),
                                icon = R.drawable.ic_inspect,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = peer != null && address.isNotBlank() && model.channels.isNotEmpty() && !isEstimatingFee,
                                onClick = {
                                    if (peer == null) return@Button
                                    val feeratePerKw = FeeratePerKw(FeeratePerByte(feerate))
                                    totalFeeEstimate = null
                                    isEstimatingFee = true

                                    scope.launch {
                                        when (val validation = Parser.parseBip21Uri(chain, address)) {
                                            is Either.Left -> {
                                                val error = validation.value
                                                addressErrorMessage = when (error) {
                                                    is BitcoinUriError.InvalidScript -> when (error.error) {
                                                        is BitcoinError.ChainHashMismatch -> context.getString(R.string.mutualclose_error_chain_mismatch)
                                                        else -> context.getString(R.string.mutualclose_error_chain_generic)
                                                    }
                                                    is BitcoinUriError.UnhandledRequiredParams -> context.getString(R.string.mutualclose_error_chain_reqparams)
                                                    else -> context.getString(R.string.mutualclose_error_chain_generic)
                                                }
                                            }
                                            else -> {
                                                totalFeeEstimate = model.channels.sumOf {
                                                    peer.estimateFeeForMutualClose(channelId = it.id, targetFeerate = feeratePerKw)?.total?.sat ?: 0.sat.sat
                                                }.sat
                                                showConfirmationDialog = true
                                            }
                                        }
                                        isEstimatingFee = false
                                    }
                                }
                            )

                            if (showConfirmationDialog) {
                                ModalBottomSheet(
                                    onDismiss = { showConfirmationDialog = false },
                                    internalPadding = PaddingValues(horizontal = 12.dp),
                                    containerColor = MaterialTheme.colors.background,
                                ) {
                                    Column(
                                        modifier = Modifier.background(color = MaterialTheme.colors.surface, shape = RoundedCornerShape(24.dp)).padding(16.dp)
                                    ) {
                                        Text(text = stringResource(R.string.mutualclose_confirm_title), style = MaterialTheme.typography.h4)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(text = stringResource(R.string.mutualclose_confirm_details))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = address, style = monoTypo.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
                                        Spacer(Modifier.height(24.dp))
                                        when (val fee = totalFeeEstimate) {
                                            null -> TextWithIcon(text = stringResource(R.string.mutualclose_confirm_fee_unknown), icon = R.drawable.ic_alert_triangle)
                                            else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(text = stringResource(R.string.mutualclose_confirm_fee))
                                                    Text(text = stringResource(R.string.utils_converted_amount,"${fee.toPrettyString(LocalBitcoinUnits.current.primary, withUnit = true)}."), style = MaterialTheme.typography.body2)
                                                }
                                                if (isUsingLowFeerate) {
                                                    Text(
                                                        text = stringResource(R.string.spliceout_low_feerate_dialog_body1),
                                                        style = MaterialTheme.typography.subtitle2,
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(24.dp))
                                        SmartSpendButton(
                                            walletId = walletId,
                                            text = stringResource(id = R.string.btn_confirm),
                                            icon = R.drawable.ic_check,
                                            onSpend = {
                                                addressErrorMessage = ""
                                                feerate.let { postIntent(CloseChannelsConfiguration.Intent.MutualCloseAllChannels(address, FeeratePerKw(FeeratePerByte(it)))) }
                                                showConfirmationDialog = false
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            enabled = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TransparentFilledButton(
                                        text = stringResource(id = R.string.btn_cancel),
                                        icon = R.drawable.ic_cross,
                                        onClick = { showConfirmationDialog = false },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
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
