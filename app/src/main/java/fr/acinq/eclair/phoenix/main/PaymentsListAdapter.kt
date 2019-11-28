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

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import fr.acinq.eclair.CoinUnit
import fr.acinq.eclair.CoinUtils
import fr.acinq.eclair.db.Payment
import fr.acinq.eclair.phoenix.R

class PaymentsListAdapter : ListAdapter<Payment, PaymentHolder>(PaymentDiffCallback()) {
  
//  private var fiatCode = "usd"
//  private var coinUnit = CoinUtils.getUnitFromString("btc")
//  private var displayAmountAsFiat = false // by default always show amounts in bitcoin

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentHolder {
    val view = LayoutInflater.from(parent.context).inflate(R.layout.holder_payment, parent, false)
    return PaymentHolder(view)
  }



  override fun onBindViewHolder(holder: PaymentHolder, position: Int) {
//    val payment = this.payments!![position]
    holder.bindPaymentItem(position, getItem(position))//, "usd", CoinUtils.getUnitFromString("btc"), false)
  }

//  override fun getItemCount(): Int {
//    return if (this.payments == null) 0 else this.payments!!.size
//  }

//  fun refresh(fiatCode: String, prefUnit: CoinUnit, displayAmountAsFiat: Boolean) {
//    update(this.payments, fiatCode, prefUnit, displayAmountAsFiat)
//  }
//
//  fun update(payments: List<Payment>?, fiatCode: String, prefUnit: CoinUnit, displayAmountAsFiat: Boolean) {
//    this.fiatCode = fiatCode
//    this.coinUnit = prefUnit
//    this.displayAmountAsFiat = displayAmountAsFiat
//    if (payments == null) {
//      this.payments = payments
//    } else if (this.payments != payments) {
//      this.payments!!.clear()
//      this.payments!!.addAll(payments)
//    }
//    notifyDataSetChanged()
//  }

  class PaymentDiffCallback : DiffUtil.ItemCallback<Payment>() {
    override fun areItemsTheSame(oldItem: Payment, newItem: Payment): Boolean {
      return true //oldItem.direction() == newItem.direction() && oldItem.paymentHash() == newItem.paymentHash()
    }

    override fun areContentsTheSame(oldItem: Payment, newItem: Payment): Boolean {
      return true //oldItem.status() == newItem.status()
    }
  }
}
