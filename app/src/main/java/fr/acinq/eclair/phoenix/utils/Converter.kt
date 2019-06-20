/*
 * Copyright 2019 ACINQ SAS
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

package fr.acinq.eclair.phoenix.utils

import android.content.Context
import android.text.Html
import android.text.Spanned
import fr.acinq.bitcoin.BtcAmount
import fr.acinq.bitcoin.Satoshi
import fr.acinq.eclair.`CoinUtils$`
import fr.acinq.eclair.phoenix.R
import java.text.DecimalFormat
import java.util.regex.Pattern

object Converter {

  private val DECIMAL_SEPARATOR = DecimalFormat().decimalFormatSymbols.decimalSeparator.toString()

  fun formatAmount(amount: BtcAmount, context: Context, withUnit: Boolean = false, withSign: Boolean = false): Spanned {
    val formatted = `CoinUtils$`.`MODULE$`.formatAmountInUnit(amount, Prefs.prefCoin(context), withUnit)
    val formattedParts = formatted.split(Pattern.quote(DECIMAL_SEPARATOR).toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    return if (formattedParts.size == 2) {
      Html.fromHtml(context.getString(R.string.utils_pretty_amount, formattedParts[0] + DECIMAL_SEPARATOR, formattedParts[1]))
    } else {
      Html.fromHtml(formatted)
    }
  }

  fun sat2msat(amount: Satoshi) = fr.acinq.bitcoin.`package$`.`MODULE$`.satoshi2millisatoshi(amount)
}
