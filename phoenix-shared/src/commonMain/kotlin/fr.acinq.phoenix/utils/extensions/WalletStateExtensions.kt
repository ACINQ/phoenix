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

package fr.acinq.phoenix.utils.extensions

import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.blockchain.electrum.WalletState
import fr.acinq.lightning.utils.sum

fun WalletState.WalletWithConfirmations.deeplyConfirmedBalance(): Satoshi = deeplyConfirmed.map { it.amount }.sum()
fun WalletState.WalletWithConfirmations.weaklyConfirmedBalance(): Satoshi = weaklyConfirmed.map { it.amount }.sum()
fun WalletState.WalletWithConfirmations.unconfirmedBalance(): Satoshi = unconfirmed.map { it.amount }.sum()
fun WalletState.WalletWithConfirmations.totalConfirmedBalance(): Satoshi = deeplyConfirmedBalance() + weaklyConfirmedBalance()
fun WalletState.WalletWithConfirmations.totalBalance(): Satoshi = unconfirmedBalance() + deeplyConfirmedBalance() + weaklyConfirmedBalance()
