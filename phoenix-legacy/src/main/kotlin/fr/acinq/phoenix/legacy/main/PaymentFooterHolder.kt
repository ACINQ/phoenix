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

package fr.acinq.phoenix.legacy.main

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.navigation.findNavController
import androidx.navigation.fragment.*
import androidx.recyclerview.widget.RecyclerView
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.db.IncomingPaymentStatus
import fr.acinq.eclair.db.OutgoingPaymentStatus
import fr.acinq.eclair.db.PlainIncomingPayment
import fr.acinq.eclair.db.PlainOutgoingPayment
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.phoenix.legacy.NavGraphMainDirections
import fr.acinq.phoenix.legacy.PaymentWithMeta
import fr.acinq.phoenix.legacy.R
import fr.acinq.phoenix.legacy.paymentdetails.PaymentDetailsFragment
import fr.acinq.phoenix.legacy.utils.Converter
import fr.acinq.phoenix.legacy.utils.Prefs
import fr.acinq.phoenix.legacy.utils.Transcriber
import fr.acinq.phoenix.legacy.utils.customviews.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class PaymentHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

class PaymentFooterHolder(itemView: View) : PaymentHolder(itemView) {

  val log: Logger = LoggerFactory.getLogger(this::class.java)

  @SuppressLint("SetTextI18n")
  fun bindPaymentItem() {
    val footerButton = itemView.findViewById<ButtonView>(R.id.footer_button)
    footerButton.setOnClickListener { itemView.findNavController().navigate(R.id.action_main_payments_history) }
  }
}
