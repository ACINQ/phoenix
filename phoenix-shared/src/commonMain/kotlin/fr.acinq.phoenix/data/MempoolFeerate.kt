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

package fr.acinq.phoenix.data

import fr.acinq.lightning.blockchain.fee.FeeratePerByte


/** Inspired from https://mempool.space/api/v1/fees/recommended */
data class MempoolFeerate(
    val fastest: FeeratePerByte,
    val halfHour: FeeratePerByte,
    val hour: FeeratePerByte,
    val economy: FeeratePerByte,
    val minimum: FeeratePerByte,
    val timestamp: Long,
)
