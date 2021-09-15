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

import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import fr.acinq.eclair.db.PlainIncomingPayment
import fr.acinq.eclair.db.PlainOutgoingPayment
import fr.acinq.phoenix.legacy.PaymentWithMeta
import fr.acinq.phoenix.legacy.R
import fr.acinq.phoenix.legacy.utils.Prefs


class PaymentsAdapter : RecyclerView.Adapter<PaymentHolder>() {

  private val mPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _: SharedPreferences, key: String ->
    if (key == Prefs.PREFS_SHOW_AMOUNT_IN_FIAT) {
      notifyDataSetChanged()
    }
  }

  private val mDiffer: AsyncListDiffer<PaymentWithMeta> = AsyncListDiffer(this, DIFF_CALLBACK)

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentHolder {
    val prefs = PreferenceManager.getDefaultSharedPreferences(parent.context)
    prefs.registerOnSharedPreferenceChangeListener(mPrefsListener)
    return PaymentHolder(LayoutInflater.from(parent.context).inflate(R.layout.holder_payment, parent, false))
  }

  override fun onBindViewHolder(holder: PaymentHolder, position: Int) {
    holder.bindPaymentItem(position, mDiffer.currentList[position])
  }

  override fun getItemCount(): Int = mDiffer.currentList.size

  fun submitList(list: List<PaymentWithMeta>) {
    mDiffer.submitList(list)
  }

  companion object {
    val DIFF_CALLBACK: DiffUtil.ItemCallback<PaymentWithMeta> = object : DiffUtil.ItemCallback<PaymentWithMeta>() {
      override fun areItemsTheSame(oldItem: PaymentWithMeta, newItem: PaymentWithMeta): Boolean {
        return if (oldItem.payment is PlainOutgoingPayment && newItem.payment is PlainOutgoingPayment) {
          val sameId = oldItem.payment.parentId().isDefined && newItem.payment.parentId().isDefined && oldItem.payment.parentId().get() == newItem.payment.parentId().get()
          sameId
        } else if (oldItem.payment is PlainIncomingPayment && newItem.payment is PlainIncomingPayment) {
          val sameId = oldItem.payment.paymentHash() == newItem.payment.paymentHash()
          sameId
        } else {
          false
        }
      }

      override fun areContentsTheSame(oldItem: PaymentWithMeta, newItem: PaymentWithMeta): Boolean {
        val sameCustomDesc = oldItem.meta?.custom_desc == newItem.meta?.custom_desc
        return if (oldItem.payment is PlainOutgoingPayment && newItem.payment is PlainOutgoingPayment) {
          val sameAmount = (oldItem.payment.finalAmount().isDefined && newItem.payment.finalAmount().isDefined && oldItem.payment.finalAmount().get().toLong() == newItem.payment.finalAmount().get().toLong())
            || (oldItem.payment.finalAmount().isEmpty && newItem.payment.finalAmount().isEmpty)
          oldItem.payment.status()::class == newItem.payment.status()::class && sameAmount && sameCustomDesc
        } else if (oldItem.payment is PlainIncomingPayment && newItem.payment is PlainIncomingPayment) {
          val sameAmount = (oldItem.payment.finalAmount().isDefined && newItem.payment.finalAmount().isDefined && oldItem.payment.finalAmount().get().toLong() == newItem.payment.finalAmount().get().toLong())
            || (oldItem.payment.finalAmount().isEmpty && newItem.payment.finalAmount().isEmpty)
          oldItem.payment.status()::class == newItem.payment.status()::class && sameAmount && sameCustomDesc
        } else {
          false
        }
      }
    }
  }
}
