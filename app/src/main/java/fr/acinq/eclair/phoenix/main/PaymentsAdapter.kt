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

import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import fr.acinq.eclair.db.PlainPayment
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.utils.Prefs

class PaymentsAdapter(private var payments: MutableList<PlainPayment>?) : RecyclerView.Adapter<PaymentHolder>() {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentHolder {
    val prefs = PreferenceManager.getDefaultSharedPreferences(parent.context)
    prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    val view = LayoutInflater.from(parent.context).inflate(R.layout.holder_payment, parent, false)
    return PaymentHolder(view)
  }

  override fun onBindViewHolder(holder: PaymentHolder, position: Int) {
    val payment = this.payments!![position]
    holder.bindPaymentItem(position, payment)
  }

  override fun getItemCount(): Int {
    return this.payments?.size ?: 0
  }

  private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _: SharedPreferences, key: String ->
    if (key == Prefs.PREFS_SHOW_AMOUNT_IN_FIAT) {
      notifyDataSetChanged()
    }
  }

  fun update(payments: List<PlainPayment>?) {
    if (payments == null) {
      this.payments = payments
    } else if (this.payments != payments) {
      this.payments!!.clear()
      this.payments!!.addAll(payments)
    }
    notifyDataSetChanged()
  }
}
