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

package fr.acinq.eclair.phoenix.main

import android.annotation.SuppressLint
import android.text.format.DateUtils
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import fr.acinq.eclair.CoinUnit
import fr.acinq.eclair.db.OutgoingPaymentStatus
import fr.acinq.eclair.db.Payment
import fr.acinq.eclair.db.`OutgoingPaymentStatus$`
import fr.acinq.eclair.db.`PaymentDirection$`
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.utils.Converter
import kotlinx.android.synthetic.main.holder_payment.view.*
import scala.Option

class PaymentHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

  init {
    itemView.setOnClickListener(this)
  }

  override fun onClick(v: View) {
    // ????????
  }

  @SuppressLint("SetTextI18n")
  fun bindPaymentItem(position: Int, payment: Payment, fiatCode: String, coinUnit: CoinUnit, displayAmountAsFiat: Boolean) {

    val isPaymentOutgoing = payment.direction() == `PaymentDirection$`.`MODULE$`.OUTGOING()
    val amountView = itemView.findViewById<TextView>(R.id.amount)
    val unitView = itemView.findViewById<TextView>(R.id.unit)

    // payment status ====> amount/colors/unit
    when (payment.status()) {
      `OutgoingPaymentStatus$`.`MODULE$`.SUCCEEDED() -> {
        // amount
        if (payment.finalAmount().isDefined) {
          amountView.text = Converter.formatAmount(payment.finalAmount().get(), itemView.context, true, isPaymentOutgoing)
        } else {
          amountView.text = itemView.context.getString(R.string.utils_unknown)
        }
        // color
        if (isPaymentOutgoing) {
          amountView.amount.setTextColor(ContextCompat.getColor(itemView.context, R.color.payment_holder_sent))
        } else {
          amountView.amount.setTextColor(ContextCompat.getColor(itemView.context, R.color.payment_holder_received))
        }
        // unit
        unitView.text = coinUnit.shortLabel().toUpperCase()
        unitView.visibility = View.VISIBLE
      }
      `OutgoingPaymentStatus$`.`MODULE$`.PENDING() -> {
        amountView.text = itemView.context.getString(R.string.paymentholder_processing)
        unitView.visibility = View.GONE
      }
      `OutgoingPaymentStatus$`.`MODULE$`.FAILED() -> {
        amountView.text = itemView.context.getString(R.string.paymentholder_failed)
        unitView.visibility = View.GONE
      }
    }

    // description
    if (payment.description().isDefined) {
      itemView.findViewById<TextView>(R.id.description).text = payment.description().get()
    } else {
      itemView.findViewById<TextView>(R.id.description).text = itemView.context.getString(R.string.utils_unknown)
    }

    // timestamp
    if (payment.completedAt().isDefined) {
      val l: Long = payment.completedAt().get() as Long
      val delaySincePayment: Long = l - System.currentTimeMillis()
      itemView.findViewById<TextView>(R.id.timestamp).text = DateUtils.getRelativeTimeSpanString(l, System.currentTimeMillis(), delaySincePayment)
    } else {
      itemView.findViewById<TextView>(R.id.timestamp).text = "pending!!!"
    }
  }
}
