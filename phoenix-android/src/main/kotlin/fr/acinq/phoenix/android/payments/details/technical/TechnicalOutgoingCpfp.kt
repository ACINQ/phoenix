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

import androidx.compose.runtime.Composable
import fr.acinq.lightning.db.SpliceCpfpOutgoingPayment
import fr.acinq.phoenix.android.payments.details.OutgoingAmountSection
import fr.acinq.phoenix.android.payments.details.TechnicalCard
import fr.acinq.phoenix.android.payments.details.TransactionRow
import fr.acinq.phoenix.data.ExchangeRate

@Composable
fun TechnicalOutgoingSpliceCpfp(
    payment: SpliceCpfpOutgoingPayment,
    originalFiatRate: ExchangeRate.BitcoinPriceRate?,
) {
    TechnicalCard {
        OutgoingAmountSection(payment = payment, originalFiatRate = originalFiatRate)
        TransactionRow(payment.txId)
    }
}