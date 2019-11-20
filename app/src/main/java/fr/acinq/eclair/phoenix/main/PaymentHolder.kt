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
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.common.base.Strings
import fr.acinq.eclair.CoinUnit
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.db.*
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.phoenix.NavGraphMainDirections
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.utils.Converter
import fr.acinq.eclair.phoenix.utils.Transcriber

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

    val primaryColor: Int = getAttrColor(R.attr.colorPrimary)
    val positiveColor: Int = getAttrColor(R.attr.positiveColor)
    val negativeColor: Int = getAttrColor(R.attr.negativeColor)
    val context = itemView.context

    val amountView = itemView.findViewById<TextView>(R.id.amount)
    val unitView = itemView.findViewById<TextView>(R.id.unit)
    val descriptionView = itemView.findViewById<TextView>(R.id.description)
    val detailsView = itemView.findViewById<TextView>(R.id.details)
    val iconBgView = itemView.findViewById<ImageView>(R.id.icon_background)
    val iconView = itemView.findViewById<ImageView>(R.id.icon)

    if (payment is LightningPayment) {
      when {

        // ------------ OUTGOING PAYMENTS ------------ //

        payment.status() is OutgoingPaymentStatus.`Pending$` -> {
          amountView.visibility = View.GONE
          unitView.visibility = View.GONE
          handleDescription(payment, descriptionView)
          detailsView.text = context.getString(R.string.paymentholder_processing)
          iconBgView.imageTintList = ColorStateList.valueOf(context.getColor(R.color.transparent))
          iconView.setImageDrawable(context.getDrawable(R.drawable.payment_holder_def_pending))
        }

        payment.status() is OutgoingPaymentStatus.Succeeded -> {
          amountView.visibility = View.VISIBLE
          unitView.visibility = View.VISIBLE
          amountView.text = if (payment.finalAmount().isDefined) {
            printAmount(payment.finalAmount().get(), true, displayAmountAsFiat)
          } else {
            context.getString(R.string.utils_unknown)
          }
          amountView.setTextColor(negativeColor)
          handleDescription(payment, descriptionView)
          detailsView.text = Transcriber.relativeTime(context, (payment.status() as OutgoingPaymentStatus.Succeeded).completedAt())
          iconBgView.imageTintList = ColorStateList.valueOf(primaryColor)
          iconView.setImageDrawable(context.getDrawable(R.drawable.payment_holder_def_success))
        }

        payment.status() is OutgoingPaymentStatus.Failed -> {
          amountView.visibility = View.GONE
          unitView.visibility = View.GONE
          handleDescription(payment, descriptionView)
          detailsView.text = Transcriber.relativeTime(context, (payment.status() as OutgoingPaymentStatus.Failed).completedAt())
          iconBgView.imageTintList = ColorStateList.valueOf(context.getColor(R.color.transparent))
          iconView.setImageDrawable(context.getDrawable(R.drawable.payment_holder_def_failed))
        }

        // ------------ INCOMING PAYMENTS ------------ //

        payment.status() is IncomingPaymentStatus.Received -> {
          amountView.visibility = View.VISIBLE
          unitView.visibility = View.VISIBLE
          amountView.text = if (payment.finalAmount().isDefined) {
            printAmount(payment.finalAmount().get(), false, displayAmountAsFiat)
          } else {
            itemView.context.getString(R.string.utils_unknown)
          }
          amountView.setTextColor(positiveColor)
          handleDescription(payment, descriptionView)
          detailsView.text = Transcriber.relativeTime(context, (payment.status() as IncomingPaymentStatus.Received).receivedAt())
          iconBgView.imageTintList = ColorStateList.valueOf(primaryColor)
          iconView.setImageDrawable(itemView.context.getDrawable(R.drawable.payment_holder_def_success))
        }

        payment.status() is IncomingPaymentStatus.`Pending$` -> {
          amountView.visibility = View.GONE
          unitView.visibility = View.GONE
          handleDescription(payment, descriptionView)
          detailsView.text = context.getString(R.string.paymentholder_waiting)
          iconBgView.imageTintList = ColorStateList.valueOf(context.getColor(R.color.transparent))
          iconView.setImageDrawable(context.getDrawable(R.drawable.payment_holder_def_pending))
        }

        payment.status() is IncomingPaymentStatus.`Expired$` -> {
          amountView.visibility = View.GONE
          unitView.visibility = View.GONE
          handleDescription(payment, descriptionView)
          detailsView.text = context.getString(R.string.paymentholder_failed)
          iconBgView.imageTintList = ColorStateList.valueOf(context.getColor(R.color.transparent))
          iconView.setImageDrawable(context.getDrawable(R.drawable.payment_holder_def_pending))
        }
      }
    }
    unitView.text = if (displayAmountAsFiat) fiatCode else coinUnit.code()

    // clickable action
    if (payment is LightningPayment) {
      itemView.setOnClickListener {
        val action = NavGraphMainDirections.globalActionAnyToPaymentDetails(
          payment.direction().toString(),
          if (payment.direction() is PaymentDirection.`OutgoingPaymentDirection$` && payment.parentId().isDefined) payment.parentId().get().toString() else payment.paymentHash().toString(),
          fromEvent = false)
        it.findNavController().navigate(action)
      }
    } else {
      itemView.setOnClickListener { Unit }
    }
  }

  private fun handleDescription(payment: LightningPayment, descriptionView: TextView) {
    val defaultTextColor: Int = getAttrColor(R.attr.textColor)
    val mutedTextColor: Int = getAttrColor(R.attr.mutedTextColor)
    val desc = if (payment.paymentRequest().isDefined) {
      PaymentRequest.fastReadDescription(payment.paymentRequest().get())
    } else if (payment.externalId().isDefined && payment.externalId().get().startsWith("closing-")) {
      descriptionView.context.getString(R.string.paymentholder_closing_desc, payment.externalId().get().split("-").last())
    } else {
      null
    }
    if (Strings.isNullOrEmpty(desc)) {
      descriptionView.text = descriptionView.context.getString(R.string.paymentholder_no_desc)
      descriptionView.setTextColor(mutedTextColor)
    } else {
      descriptionView.text = desc
      descriptionView.setTextColor(defaultTextColor)
    }
  }

  private fun printAmount(amount: MilliSatoshi, isOutgoing: Boolean, displayAmountAsFiat: Boolean): CharSequence {
    return if (displayAmountAsFiat) {
      Converter.printFiatPretty(itemView.context, amount = amount, withUnit = false, withSign = true, isOutgoing = isOutgoing).toString()
    } else {
      Converter.printAmountPretty(amount = amount, context = itemView.context, withUnit = false, withSign = true, isOutgoing = isOutgoing).toString()
    }
  }
}
