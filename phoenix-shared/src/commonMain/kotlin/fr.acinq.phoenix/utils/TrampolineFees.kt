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

package fr.acinq.phoenix.utils

import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.TrampolineFees
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.controllers.payments.MaxFees


/**
 * Creates a list of trampolineFees by taking into account both
 * WalletParams.trampolineFees and the optional SendPayment.maxFees.
 *
 * The maxFees generally comes from user input,
 * so it may either truncate the default list, or append to it.
 * If the maxFees are "significantly" higher than the last item in the filtered trampolineFees,
 * then the algorithm will inject additional steps to smoothly increment up to the maxFees.
 */
fun createTrampolineFees(
    defaultFees: List<TrampolineFees>,
    maxFees: MaxFees,
    significantFeeBaseDiff: Satoshi = 300.sat,
    significantFeeProportionalDiff: Long = 2000 // 0.2%
): List<TrampolineFees> {
    // Remove any items from default list that exceed the given maxFees
    val trampolineFees = defaultFees.filter {
        it.feeBase <= maxFees.feeBase &&
        it.feeProportional <= maxFees.feeProportionalMillionths
    }.toMutableList()
    // If the maxFees already exist in the list then we're done
    if (trampolineFees.any {
            it.feeBase == maxFees.feeBase &&
            it.feeProportional == maxFees.feeProportionalMillionths
        }) {
        return trampolineFees
    }
    // Check to see if there's a "significant jump" up to the user-defined maxFees
    val cltvExpiryDelta = defaultFees.last().cltvExpiryDelta
    val last = trampolineFees.lastOrNull() ?: TrampolineFees(0.sat, 0, cltvExpiryDelta)
    val baseDiff = maxFees.feeBase.sat - last.feeBase.sat
    val proportionalDiff = maxFees.feeProportionalMillionths - last.feeProportional
    if (baseDiff >= significantFeeBaseDiff.sat ||
        proportionalDiff >= significantFeeProportionalDiff
    ) {
        // It's a "significant jump" up to the user-provided max.
        // So we insert a few steps to smooth out the potential fees.
        val steps = 2
        val baseIncrement = if (baseDiff > 0) {
            baseDiff.toDouble() / (steps + 1).toDouble()
        } else 0.0
        val proportionalIncrement = if (proportionalDiff > 0) {
            proportionalDiff.toDouble() / (steps + 1).toDouble()
        } else 0.0
        for (i in 1..steps) {
            val base = last.feeBase.sat + (baseIncrement * i).toLong()
            val proportional = last.feeProportional + (proportionalIncrement * i).toLong()
            trampolineFees.add(TrampolineFees(
                feeBase = Satoshi(sat = base),
                feeProportional = proportional,
                cltvExpiryDelta = last.cltvExpiryDelta
            ))
        }
    }
    return trampolineFees + TrampolineFees(
        feeBase = maxFees.feeBase,
        feeProportional = maxFees.feeProportionalMillionths,
        cltvExpiryDelta = cltvExpiryDelta
    )
}