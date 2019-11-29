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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import fr.acinq.eclair.db.PlainIncomingPayment
import fr.acinq.eclair.db.PlainOutgoingPayment
import fr.acinq.eclair.db.PlainPayment
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.utils.Prefs
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PaymentsAdapter(private var mPayments: MutableList<PlainPayment>) : RecyclerView.Adapter<PaymentHolder>() {

  val log: Logger = LoggerFactory.getLogger(this::class.java)

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentHolder {
    val prefs = PreferenceManager.getDefaultSharedPreferences(parent.context)
    prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    val view = LayoutInflater.from(parent.context).inflate(R.layout.holder_payment, parent, false)
    return PaymentHolder(view)
  }

  override fun onBindViewHolder(holder: PaymentHolder, position: Int) {
    val payment = this.mPayments[position]
    holder.bindPaymentItem(position, payment)
    log.info("bind payment view holder #$position")
  }

  override fun getItemCount(): Int {
    return this.mPayments.size
  }

  private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _: SharedPreferences, key: String ->
    if (key == Prefs.PREFS_SHOW_AMOUNT_IN_FIAT) {
      notifyDataSetChanged()
    }
  }

  fun update(payments: List<PlainPayment>) {
    log.debug("update payment adapter list=$payments")
    val diff = DiffUtil.calculateDiff(PaymentDiffCallback(mPayments, payments))
    this.mPayments.clear()
    this.mPayments.addAll(payments)
    diff.dispatchUpdatesTo(this)
    //    notifyDataSetChanged()
  }

  class PaymentDiffCallback(val oldList: MutableList<PlainPayment>, val newList: List<PlainPayment>) : DiffUtil.Callback() {
    val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
      val oldItem = oldList[oldItemPosition]
      val newItem = newList[newItemPosition]
      return if (oldItem is PlainOutgoingPayment && newItem is PlainOutgoingPayment) {
        val sameId = oldItem.parentId().isDefined && newItem.parentId().isDefined && oldItem.parentId().get() == newItem.parentId().get()
        log.debug("old outgoing parent_id=${oldItem.parentId()}, new outgoing parent_id=${newItem.parentId()} / same?=$sameId")
        sameId
      } else if (oldItem is PlainIncomingPayment && newItem is PlainIncomingPayment) {
        val sameId = oldItem.paymentHash() == newItem.paymentHash()
        log.debug("old incoming parent_id=${oldItem.paymentHash()}, new incoming parent_id=${newItem.paymentHash()} / same?=$sameId")
        sameId
      } else {
        false
      }
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
      val oldItem = oldList[oldItemPosition]
      val newItem = newList[newItemPosition]
      return if (oldItem is PlainOutgoingPayment && newItem is PlainOutgoingPayment) {
        val sameAmount = (oldItem.finalAmount().isDefined && newItem.finalAmount().isDefined && oldItem.finalAmount().get().toLong() == newItem.finalAmount().get().toLong())
          || (oldItem.finalAmount().isEmpty && newItem.finalAmount().isEmpty)
        log.debug("old outgoing status=${oldItem.status()}, new outgoing status=${newItem.status()} / same?=${oldItem.status()::class == newItem.status()::class}")
        oldItem.status()::class == newItem.status()::class && sameAmount
      } else if (oldItem is PlainIncomingPayment && newItem is PlainIncomingPayment) {
        val sameAmount = (oldItem.finalAmount().isDefined && newItem.finalAmount().isDefined && oldItem.finalAmount().get().toLong() == newItem.finalAmount().get().toLong())
          || (oldItem.finalAmount().isEmpty && newItem.finalAmount().isEmpty)
        log.debug("old incoming status=${oldItem.status()}, new incoming status=${newItem.status()} / same?=${oldItem.status()::class == newItem.status()::class}")
        oldItem.status()::class == newItem.status()::class && sameAmount
      } else {
        false
      }
    }

    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size
  }

}
