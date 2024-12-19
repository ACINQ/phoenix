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
import fr.acinq.lightning.db.LegacyPayToOpenIncomingPayment
import fr.acinq.lightning.db.LegacySwapInIncomingPayment
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.payments.details.IncomingAmountSection
import fr.acinq.phoenix.android.payments.details.TechnicalCard
import fr.acinq.phoenix.android.payments.details.TechnicalRow
import fr.acinq.phoenix.data.ExchangeRate

@Suppress("DEPRECATION")
@Composable
fun TechnicalIncomingLegacySwapIn(
    payment: LegacySwapInIncomingPayment,
    originalFiatRate: ExchangeRate.BitcoinPriceRate?,
) {
    TechnicalCard {
        IncomingAmountSection(
            amountReceived = payment.amountReceived,
            minerFee = null,
            serviceFee = payment.fees,
            originalFiatRate = originalFiatRate
        )
        TechnicalRow(label = stringResource(id = R.string.paymentdetails_swapin_address_label)) {
            Text(payment.address ?: stringResource(id = R.string.utils_unknown))
        }
    }
}

@Suppress("DEPRECATION")
@Composable
fun TechnicalIncomingLegacyPayToOpen(
    payment: LegacyPayToOpenIncomingPayment,
    originalFiatRate: ExchangeRate.BitcoinPriceRate?,
) {
    TechnicalCard {
        IncomingAmountSection(
            amountReceived = payment.amountReceived,
            minerFee = null,
            serviceFee = payment.fees,
            originalFiatRate = originalFiatRate
        )
    }
}