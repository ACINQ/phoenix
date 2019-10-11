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
import fr.acinq.bitcoin.Satoshi
import fr.acinq.eclair.CoinUnit
import fr.acinq.eclair.CoinUtils
import fr.acinq.eclair.db.Payment
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.utils.Prefs

class ClosingPayment(val txId: String, val amount: Satoshi, val timestamp: Long) : Payment

class PaymentsAdapter(private var payments: MutableList<Payment>?) : RecyclerView.Adapter<PaymentHolder>() {

  private var fiat = "usd"
  private var coin = CoinUtils.getUnitFromString("btc")
  private var displayAmountAsFiat = false // by default always show amounts in bitcoin

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentHolder {
    val prefs = PreferenceManager.getDefaultSharedPreferences(parent.context)
    prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    fiat = Prefs.getFiatCurrency(prefs)
    coin = Prefs.getCoinUnit(prefs)
    displayAmountAsFiat = Prefs.getShowAmountInFiat(prefs)
    val view = LayoutInflater.from(parent.context).inflate(R.layout.holder_payment, parent, false)
    return PaymentHolder(view)
  }

  override fun onBindViewHolder(holder: PaymentHolder, position: Int) {
    val payment = this.payments!![position]
    holder.bindPaymentItem(position, payment, fiat, coin, displayAmountAsFiat)
  }

  override fun getItemCount(): Int {
    return if (this.payments == null) 0 else this.payments!!.size
  }

  private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs: SharedPreferences, key: String ->
    when (key) {
      Prefs.PREFS_SHOW_AMOUNT_IN_FIAT -> {
        fiat = Prefs.getFiatCurrency(prefs)
        coin = Prefs.getCoinUnit(prefs)
        displayAmountAsFiat = Prefs.getShowAmountInFiat(prefs)
        update(this.payments) //fiat, coin, showAmountInFiat)
      }
      else -> {}
    }
  }

  fun update(payments: List<Payment>?) {
    if (payments == null) {
      this.payments = payments
    } else if (this.payments != payments) {
      this.payments!!.clear()
      this.payments!!.addAll(payments)
    }
    notifyDataSetChanged()
  }
}
