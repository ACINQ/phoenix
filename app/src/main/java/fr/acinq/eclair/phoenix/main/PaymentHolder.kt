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
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import fr.acinq.eclair.CoinUnit
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.utils.Converter

class PaymentHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

  init {
    itemView.setOnClickListener(this)
  }

  override fun onClick(v: View) {
    // ????????
  }

  @SuppressLint("SetTextI18n")
  fun bindPaymentItem(position: Int, payment: Payment, fiatCode: String, coinUnit: CoinUnit, displayAmountAsFiat: Boolean) {
    itemView.findViewById<TextView>(R.id.amount).text = Converter.formatAmount(payment.amount, itemView.context, true)
    itemView.findViewById<TextView>(R.id.label).text = payment.id
  }
}
