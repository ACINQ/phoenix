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

package fr.acinq.phoenix.android.payments.details.technical

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.acinq.lightning.db.LegacySwapInIncomingPayment
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.payments.details.TechnicalRow
import fr.acinq.phoenix.android.payments.details.TechnicalRowAmount
import fr.acinq.phoenix.android.payments.details.TimestampSection
import fr.acinq.phoenix.android.utils.MSatDisplayPolicy
import fr.acinq.phoenix.data.ExchangeRate

@Suppress("DEPRECATION")
@Composable
fun TechnicalIncomingLegacySwapIn(
    payment: LegacySwapInIncomingPayment,
    originalFiatRate: ExchangeRate.BitcoinPriceRate?,
) {
    Card {
        TechnicalRow(label = stringResource(id = R.string.paymentdetails_payment_type_label)) {
            Text(text = stringResource(R.string.paymentdetails_type_incoming_legacy_swapin))
        }
        TechnicalRow(label = stringResource(id = R.string.paymentdetails_status_label)) {
            Text(
                text = when (payment.completedAt) {
                    null -> stringResource(id = R.string.paymentdetails_status_pending)
                    else -> stringResource(R.string.paymentdetails_status_success)
                }
            )
        }
    }

    Card {
        TimestampSection(payment = payment)
    }

    Card {
        TechnicalRowAmount(
            label = stringResource(R.string.paymentdetails_amount_received_label),
            amount = payment.amountReceived,
            rateThen = originalFiatRate,
            mSatDisplayPolicy = MSatDisplayPolicy.SHOW
        )
        TechnicalRowAmount(
            label = stringResource(id = R.string.paymentdetails_funding_fees_label),
            amount = payment.fees,
            rateThen = originalFiatRate,
            mSatDisplayPolicy = MSatDisplayPolicy.SHOW
        )
        TechnicalRow(label = stringResource(id = R.string.paymentdetails_swapin_address_label)) {
            Text(payment.address ?: stringResource(id = R.string.utils_unknown))
        }
    }
}
