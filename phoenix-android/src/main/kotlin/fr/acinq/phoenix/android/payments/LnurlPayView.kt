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

import androidx.compose.runtime.Composable
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.controllers.payments.MaxFees
import fr.acinq.phoenix.controllers.payments.Scan

@Composable
fun LnurlPayView(
    model: Scan.Model.LnurlPayFlow,
    trampolineMaxFees: MaxFees?,
    onBackClick: () -> Unit,
    onSendLnurlPayClick: (Scan.Intent.LnurlPayFlow) -> Unit
) {
    // wip
}


