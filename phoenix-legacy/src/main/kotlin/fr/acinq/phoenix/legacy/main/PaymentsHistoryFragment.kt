/*
 * Copyright 2023 ACINQ SAS
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

import android.os.*
import android.view.*
import androidx.lifecycle.*
import androidx.navigation.fragment.*
import androidx.recyclerview.widget.*
import fr.acinq.phoenix.legacy.*
import fr.acinq.phoenix.legacy.R
import fr.acinq.phoenix.legacy.databinding.*
import kotlinx.coroutines.*
import org.slf4j.*

class PaymentsHistoryFragment : BaseFragment() {
  override val log: Logger = LoggerFactory.getLogger(this::class.java)
  private lateinit var model: PaymentsHistoryViewModel
  private lateinit var mBinding: FragmentPaymentsHistoryBinding

  private lateinit var paymentsAdapter: PaymentsAdapter
  private lateinit var paymentsManager: RecyclerView.LayoutManager

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentPaymentsHistoryBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this

    paymentsManager = LinearLayoutManager(context)
    paymentsAdapter = PaymentsAdapter()
    mBinding.paymentList.apply {
      setHasFixedSize(true)
      layoutManager = paymentsManager
      adapter = paymentsAdapter
    }
    return mBinding.root
  }

  override fun onViewStateRestored(savedInstanceState: Bundle?) {
    super.onViewStateRestored(savedInstanceState)
    model = ViewModelProvider(this).get(PaymentsHistoryViewModel::class.java)
    app.payments.observe(this.viewLifecycleOwner, Observer { (paymentsCount, payments) ->
      context?.let { mBinding.actionBar.setTitle(it.getString(R.string.legacy_payments_history_title, paymentsCount)) }
      paymentsAdapter.submitList(showFooter = false, list = payments)
    })
    mBinding.actionBar.setOnBackAction { findNavController().popBackStack() }
  }

  override fun onStart() {
    super.onStart()
    getAllPayments()
  }

  private fun getAllPayments() {
    app.service?.let { service ->
      model.isFetching.value = true
      model.viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
        log.error("failed to retrieve payments: ", e)
        model.isFetching.value = false
      }) {
        val paymentsCount = service.getPaymentsCount()
        val allPayments = service.getPayments(limit = null)
        app.payments.postValue(paymentsCount to allPayments)
        model.isFetching.postValue(false)
      }
    }
  }
}

class PaymentsHistoryViewModel : ViewModel() {
  val isFetching = MutableLiveData(false)
}
