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
import android.content.res.ColorStateList
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.common.base.Strings
import fr.acinq.eclair.CoinUnit
import fr.acinq.eclair.db.Payment
import fr.acinq.eclair.db.`OutgoingPaymentStatus$`
import fr.acinq.eclair.db.`PaymentDirection$`
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.utils.Converter
import kotlinx.android.synthetic.main.holder_payment.view.*


class PaymentHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

  private fun getAttrColor(resId: Int): Int {
    val typedValue = TypedValue()
    itemView.context.theme.resolveAttribute(resId, typedValue, true)
    val ta = itemView.context.obtainStyledAttributes(typedValue.resourceId, intArrayOf(resId))
    val color = ta.getColor(0, 0)
    ta.recycle()
    return color
  }

  @SuppressLint("SetTextI18n")
  fun bindPaymentItem(position: Int, payment: Payment, fiatCode: String, coinUnit: CoinUnit, displayAmountAsFiat: Boolean) {

    val defaultTextColor: Int = getAttrColor(R.attr.defaultTextColor)
    val mutedTextColor: Int = getAttrColor(R.attr.defaultMutedTextColor)

    val isPaymentOutgoing = payment.direction() == `PaymentDirection$`.`MODULE$`.OUTGOING()
    val amountView = itemView.findViewById<TextView>(R.id.amount)
    val unitView = itemView.findViewById<TextView>(R.id.unit)
    val descriptionView = itemView.findViewById<TextView>(R.id.description)
    val timestampView = itemView.findViewById<TextView>(R.id.timestamp)
    val avatarBgView = itemView.findViewById<ImageView>(R.id.avatar_background)
    val avatarView = itemView.findViewById<ImageView>(R.id.avatar)

    // payment status ====> amount/colors/unit/description/avatar
    when (payment.status()) {
      `OutgoingPaymentStatus$`.`MODULE$`.SUCCEEDED() -> {
        // amount
        if (payment.finalAmount().isDefined) {
          amountView.text = Converter.formatAmount(payment.finalAmount().get(), itemView.context, false, true, isPaymentOutgoing)
        } else {
          amountView.text = itemView.context.getString(R.string.utils_unknown)
        }
        // color
        if (isPaymentOutgoing) {
          amountView.amount.setTextColor(ContextCompat.getColor(itemView.context, R.color.dark))
        } else {
          amountView.amount.setTextColor(ContextCompat.getColor(itemView.context, R.color.green))
        }
        amountView.visibility = View.VISIBLE
        // unit
        unitView.text = coinUnit.shortLabel().toUpperCase()
        unitView.visibility = View.VISIBLE
        // desc + avatar
        descriptionView.setTextColor(itemView.context.getColor(R.color.dark))
        avatarBgView.imageTintList = ColorStateList.valueOf(itemView.context.getColor(R.color.athens))
        avatarView.setImageDrawable(itemView.context.getDrawable(R.drawable.payment_holder_def_success))
      }
      `OutgoingPaymentStatus$`.`MODULE$`.PENDING() -> {
        amountView.visibility = View.VISIBLE
        amountView.text = itemView.context.getString(R.string.paymentholder_processing)
        unitView.visibility = View.GONE
        // desc + avatar
        descriptionView.setTextColor(itemView.context.getColor(R.color.dark))
        avatarBgView.imageTintList = ColorStateList.valueOf(itemView.context.getColor(R.color.transparent))
        avatarView.setImageDrawable(itemView.context.getDrawable(R.drawable.payment_holder_def_pending))
      }
      `OutgoingPaymentStatus$`.`MODULE$`.FAILED() -> {
        amountView.visibility = View.GONE
        unitView.visibility = View.GONE
        // desc + avatar
        descriptionView.setTextColor(itemView.context.getColor(R.color.brandy))
        avatarBgView.imageTintList = ColorStateList.valueOf(itemView.context.getColor(R.color.dawn))
        avatarView.setImageDrawable(itemView.context.getDrawable(R.drawable.payment_holder_def_failed))
      }
    }

    // description
    val desc = if (payment.paymentRequest().isDefined) PaymentRequest.readDescription(payment.paymentRequest().get()) else ""
    if (desc.isNullOrBlank()) {
      descriptionView.text = itemView.context.getString(R.string.paymentholder_no_desc)
      descriptionView.setTextColor(mutedTextColor)
    } else {
      descriptionView.text = desc
      descriptionView.setTextColor(defaultTextColor)
    }

    // timestamp
    if (payment.completedAt().isDefined) {
      val l: Long = payment.completedAt().get() as Long
      val delaySincePayment: Long = l - System.currentTimeMillis()
      timestampView.text = DateUtils.getRelativeTimeSpanString(l, System.currentTimeMillis(), delaySincePayment)
      timestampView.visibility = View.VISIBLE
    } else {
      timestampView.visibility = View.GONE
    }

    // clickable action
    itemView.setOnClickListener {
      val action = MainFragmentDirections.actionMainToPaymentDetails(payment.direction().toString(), if (isPaymentOutgoing) payment.id().get().toString() else payment.paymentHash().toString())
      itemView.findNavController().navigate(action)
    }
  }
}
