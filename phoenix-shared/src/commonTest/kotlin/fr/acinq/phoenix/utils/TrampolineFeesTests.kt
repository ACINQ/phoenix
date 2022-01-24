package fr.acinq.phoenix.utils

import fr.acinq.lightning.CltvExpiryDelta
import fr.acinq.lightning.TrampolineFees
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.controllers.payments.MaxFees
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrampolineFeesTests {

    @Test
    fun createTrampolineFees() {
        val defaultFees = listOf(
            TrampolineFees(0.sat, 0, CltvExpiryDelta(576)),
            TrampolineFees(1.sat, 100, CltvExpiryDelta(576)),
            TrampolineFees(3.sat, 100, CltvExpiryDelta(576)),
            TrampolineFees(5.sat, 500, CltvExpiryDelta(576)),
            TrampolineFees(5.sat, 1000, CltvExpiryDelta(576)),
            TrampolineFees(5.sat, 1200, CltvExpiryDelta(576))
        )
        run { // test: maxFee = item in default list
            val trampolineFees = createTrampolineFees(
                defaultFees = defaultFees,
                maxFees = MaxFees(
                    feeBase = 5.sat,
                    feeProportionalMillionths = 1200
                ),
                significantFeeBaseDiff = 300.sat,
                significantFeeProportionalDiff = 2000
            )
            assertEquals(trampolineFees, defaultFees)
        }
        run { // test: maxFee.feeBase truncates list
            val trampolineFees = createTrampolineFees(
                defaultFees = defaultFees,
                maxFees = MaxFees(
                    feeBase = 4.sat, // causes truncation
                    feeProportionalMillionths = 1200
                ),
                significantFeeBaseDiff = 300.sat,
                significantFeeProportionalDiff = 2000
            )
            assertEquals(trampolineFees, listOf(
                TrampolineFees(0.sat, 0, CltvExpiryDelta(576)),
                TrampolineFees(1.sat, 100, CltvExpiryDelta(576)),
                TrampolineFees(3.sat, 100, CltvExpiryDelta(576)),
                TrampolineFees(4.sat, 1200, CltvExpiryDelta(576)),
            ))
        }
        run { // test: maxFee.feeProportionalMillionths truncates list
            val trampolineFees = createTrampolineFees(
                defaultFees = defaultFees,
                maxFees = MaxFees(
                    feeBase = 5.sat,
                    feeProportionalMillionths = 750 // causes truncation
                ),
                significantFeeBaseDiff = 300.sat,
                significantFeeProportionalDiff = 2000
            )
            assertEquals(trampolineFees, listOf(
                TrampolineFees(0.sat, 0, CltvExpiryDelta(576)),
                TrampolineFees(1.sat, 100, CltvExpiryDelta(576)),
                TrampolineFees(3.sat, 100, CltvExpiryDelta(576)),
                TrampolineFees(5.sat, 500, CltvExpiryDelta(576)),
                TrampolineFees(5.sat, 750, CltvExpiryDelta(576)),
            ))
        }
        run { // test: maxFee under significantDiff
            val trampolineFees = createTrampolineFees(
                defaultFees = defaultFees,
                maxFees = MaxFees(
                    feeBase = 100.sat, // 100 - 5 = 95
                    feeProportionalMillionths = 2000 // 2000 - 1200 = 800
                ),
                significantFeeBaseDiff = 300.sat, // 300 > 95
                significantFeeProportionalDiff = 2000 // 2000 > 800
            )
            assertEquals(trampolineFees, defaultFees +
                TrampolineFees(100.sat, 2000, CltvExpiryDelta(576))
            )
        }
        run { // test: maxFee over significantDiff
            val trampolineFees = createTrampolineFees(
                defaultFees = defaultFees,
                maxFees = MaxFees(
                    feeBase = 95.sat, // 95 - 5 = 90
                    feeProportionalMillionths = 2100 // 2100 - 1200 = 900
                ),
                significantFeeBaseDiff = 90.sat, // 90 >= 90
                significantFeeProportionalDiff = 1000 // 1000 !>= 900
            )
            assertEquals(trampolineFees, defaultFees + listOf(
                TrampolineFees(35.sat, 1500, CltvExpiryDelta(576)),
                TrampolineFees(65.sat, 1800, CltvExpiryDelta(576)),
                TrampolineFees(95.sat, 2100, CltvExpiryDelta(576))
            ))
        }
    }
}