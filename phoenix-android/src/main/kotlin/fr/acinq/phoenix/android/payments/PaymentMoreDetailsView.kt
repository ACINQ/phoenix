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

import android.content.ClipData
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Blue
import androidx.compose.ui.graphics.Color.Companion.Yellow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.logger

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PaymentMoreDetailsView() {
    val log = logger("PaymentMoreDetailsView")
    val nc = navController
    val context = LocalContext.current

    SettingScreen {
        SettingHeader(
            onBackClick = { nc.popBackStack() },
            title = stringResource(id = R.string.paymentdetails_title)
        )
        Card(internalPadding = PaddingValues(16.dp)) {

            Row {
                Column(
                    Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(id = R.string.paymentdetails_invoice_created),
                        style = MaterialTheme.typography.subtitle2
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(
                    Modifier.weight(3f)
                ) {
                    Text(text = stringResource(id = R.string.about_seed_content))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Column(
                    Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(id = R.string.paymentdetails_amount_requested),
                        style = MaterialTheme.typography.subtitle2
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(
                    Modifier.weight(3f)
                ) {
                    Text(text = stringResource(id = R.string.about_seed_content))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Column(
                    Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(id = R.string.paymentdetails_payment_hash),
                        style = MaterialTheme.typography.subtitle2
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(
                    Modifier.weight(3f)
                ) {
                    Text(text = stringResource(id = R.string.about_seed_content))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Column(
                    Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(id = R.string.paymentdetails_sent_at),
                        style = MaterialTheme.typography.subtitle2
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(
                    Modifier.weight(3f)
                ) {
                    Text(text = stringResource(id = R.string.about_seed_content))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Column(
                    Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(id = R.string.paymentdetails_elapsed),
                        style = MaterialTheme.typography.subtitle2
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(
                    Modifier.weight(3f)
                ) {
                    Text(text = stringResource(id = R.string.about_seed_content))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Column(
                    Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(id = R.string.paymentdetails_amount_sent),
                        style = MaterialTheme.typography.subtitle2
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(
                    Modifier.weight(3f)
                ) {
                    Text(text = stringResource(id = R.string.about_seed_content))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Column(
                    Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(id = R.string.paymentdetails_fees_paid),
                        style = MaterialTheme.typography.subtitle2
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(
                    Modifier.weight(3f)
                ) {
                    Text(text = stringResource(id = R.string.about_seed_content))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Column(
                    Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(id = R.string.paymentdetails_amount_requested),
                        style = MaterialTheme.typography.subtitle2
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(
                    Modifier.weight(3f)
                ) {
                    Text(text = stringResource(id = R.string.about_seed_content))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Column(
                    Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(id = R.string.paymentdetails_recipient_pubkey),
                        style = MaterialTheme.typography.subtitle2
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(
                    Modifier.weight(3f)
                ) {
                    Text(text = stringResource(id = R.string.about_seed_content))
                }
            }
        }
    }
}

@Preview(device = Devices.PIXEL_3A)
@Composable
private fun Preview() {
    PaymentMoreDetailsView()
}