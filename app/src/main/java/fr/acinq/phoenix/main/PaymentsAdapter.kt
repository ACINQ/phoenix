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
package fr.acinq.phoenix.main

import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import fr.acinq.eclair.db.PlainIncomingPayment
import fr.acinq.eclair.db.PlainOutgoingPayment
import fr.acinq.eclair.db.PlainPayment
import fr.acinq.phoenix.R
import fr.acinq.phoenix.utils.Prefs


class PaymentsAdapter : RecyclerView.Adapter<PaymentHolder>() {

  private val mPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _: SharedPreferences, key: String ->
    if (key == Prefs.PREFS_SHOW_AMOUNT_IN_FIAT) {
      notifyDataSetChanged()
    }
  }

  private val mDiffer: AsyncListDiffer<PlainPayment> = AsyncListDiffer(this, DIFF_CALLBACK)

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentHolder {
    val prefs = PreferenceManager.getDefaultSharedPreferences(parent.context)
    prefs.registerOnSharedPreferenceChangeListener(mPrefsListener)
    return PaymentHolder(LayoutInflater.from(parent.context).inflate(R.layout.holder_payment, parent, false))
  }

  override fun onBindViewHolder(holder: PaymentHolder, position: Int) {
    holder.bindPaymentItem(position, mDiffer.currentList[position])
  }

  override fun getItemCount(): Int = mDiffer.currentList.size

  fun submitList(list: List<PlainPayment>) {
    mDiffer.submitList(list)
  }

  companion object {
    val DIFF_CALLBACK: DiffUtil.ItemCallback<PlainPayment> = object : DiffUtil.ItemCallback<PlainPayment>() {
      override fun areItemsTheSame(oldItem: PlainPayment, newItem: PlainPayment): Boolean {
        return if (oldItem is PlainOutgoingPayment && newItem is PlainOutgoingPayment) {
          val sameId = oldItem.parentId().isDefined && newItem.parentId().isDefined && oldItem.parentId().get() == newItem.parentId().get()
          sameId
        } else if (oldItem is PlainIncomingPayment && newItem is PlainIncomingPayment) {
          val sameId = oldItem.paymentHash() == newItem.paymentHash()
          sameId
        } else {
          false
        }
      }

      override fun areContentsTheSame(oldItem: PlainPayment, newItem: PlainPayment): Boolean {
        return if (oldItem is PlainOutgoingPayment && newItem is PlainOutgoingPayment) {
          val sameAmount = (oldItem.finalAmount().isDefined && newItem.finalAmount().isDefined && oldItem.finalAmount().get().toLong() == newItem.finalAmount().get().toLong())
            || (oldItem.finalAmount().isEmpty && newItem.finalAmount().isEmpty)
          oldItem.status()::class == newItem.status()::class && sameAmount
        } else if (oldItem is PlainIncomingPayment && newItem is PlainIncomingPayment) {
          val sameAmount = (oldItem.finalAmount().isDefined && newItem.finalAmount().isDefined && oldItem.finalAmount().get().toLong() == newItem.finalAmount().get().toLong())
            || (oldItem.finalAmount().isEmpty && newItem.finalAmount().isEmpty)
          oldItem.status()::class == newItem.status()::class && sameAmount
        } else {
          false
        }
      }
    }
  }
}
