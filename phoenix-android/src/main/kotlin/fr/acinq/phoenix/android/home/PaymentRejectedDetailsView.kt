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

package fr.acinq.phoenix.android.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.android.LocalNavController
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.Screen
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.utils.Converter.toAbsoluteDateTimeString
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.data.Notification

@Composable
fun PaymentRejectedDetailsView(
    id: UUID?,
    onBackClick: () -> Unit,
) {
    val log = logger("PaymentRejectedView")
    val nc = LocalNavController.current
    val notificationsManager = business.notificationsManager

    DefaultScreenLayout {
        DefaultScreenHeader(onBackClick = onBackClick, title = "Missed payment")
        Card(
            internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (id == null) {
                Text("no details available for that payment")
            } else {
                val notification by produceState<Notification.FeeTooExpensive?>(initialValue = null) {
                    value = when (val res = notificationsManager.getNotificationDetails(id)) {
                        is Notification.FeeTooExpensive -> res
                        else -> null
                    }
                }
                notification?.let {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // val paymentRejectedDetails = database.findPaymentRejected(id)
                        Text(text = "A ${it.amount} incoming payment was rejected on ${it.createdAt.toAbsoluteDateTimeString()}, because the fee would have exceeded the maximum fee in your incoming fee policy setting.")
                        Text("Network fees on ${it.createdAt.toAbsoluteDateTimeString()}:\n${it.expectedFee} msat", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Text("Your max fee setting then:\n${it.maxAllowedFee}", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Text(text = "To prevent future failures, tweak your fee policy in Phoenix settings.")
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Button(
                icon = R.drawable.ic_settings,
                text = "My fee policy setting",
                modifier = Modifier.fillMaxWidth(),
                onClick = { nc?.navigate(Screen.LiquidityPolicy.route) }
            )
        }
    }
}