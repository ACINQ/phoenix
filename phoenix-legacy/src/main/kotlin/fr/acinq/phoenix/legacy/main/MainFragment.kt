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

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.WatchListener
import fr.acinq.eclair.channel.HasCommitments
import fr.acinq.eclair.channel.`WAIT_FOR_FUNDING_CONFIRMED$`
import fr.acinq.phoenix.legacy.BaseFragment
import fr.acinq.phoenix.legacy.R
import fr.acinq.phoenix.legacy.background.ChannelStateChange
import fr.acinq.phoenix.legacy.background.KitState
import fr.acinq.phoenix.legacy.background.PaymentPending
import fr.acinq.phoenix.legacy.databinding.FragmentMainBinding
import fr.acinq.phoenix.legacy.utils.Constants
import fr.acinq.phoenix.legacy.utils.Converter
import fr.acinq.phoenix.legacy.utils.Prefs
import fr.acinq.phoenix.legacy.utils.Wallet
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.text.DateFormat
import java.util.*

class MainFragment : BaseFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

  override val log: Logger = LoggerFactory.getLogger(this::class.java)
  private lateinit var mBinding: FragmentMainBinding
  private lateinit var model: MainViewModel

  private lateinit var paymentsAdapter: PaymentsAdapter
  private lateinit var paymentsManager: RecyclerView.LayoutManager

  private lateinit var notificationsAdapter: NotificationsAdapter
  private lateinit var notificationsManager: RecyclerView.LayoutManager

  private lateinit var blinkingAnimation: Animation

  private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _: SharedPreferences, key: String ->
    if (key == Prefs.PREFS_SHOW_AMOUNT_IN_FIAT) {
      refreshIncomingFundsAmountField()
    }
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentMainBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this

    // init payment recycler view
    paymentsManager = LinearLayoutManager(context)
    paymentsAdapter = PaymentsAdapter()
    paymentsAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
      override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
        mBinding.paymentList.scrollToPosition(0)
      }
    })
    mBinding.paymentList.apply {
      setHasFixedSize(true)
      layoutManager = paymentsManager
      adapter = paymentsAdapter
    }
    // init notification recycler view
    notificationsManager = LinearLayoutManager(context)
    notificationsAdapter = NotificationsAdapter(mutableListOf())
    mBinding.notificationList.apply {
      setHasFixedSize(true)
      layoutManager = notificationsManager
      adapter = notificationsAdapter
    }
    blinkingAnimation = AnimationUtils.loadAnimation(context, R.anim.blinking)
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProvider(this).get(MainViewModel::class.java)
    context?.let { ctx ->
      appContext(ctx).notifications.observe(viewLifecycleOwner) {
        notificationsAdapter.update(it)
      }
      app.networkInfo.observe(viewLifecycleOwner) {
        if (it.electrumServer == null || !it.lightningConnected) {
          if (mBinding.connectivityButton.animation == null || !mBinding.connectivityButton.animation.hasStarted()) {
            mBinding.connectivityButton.startAnimation(blinkingAnimation)
          }
          mBinding.connectivityButton.visibility = View.VISIBLE
          mBinding.torConnectedButton.visibility = View.GONE
        } else if (Prefs.isTorEnabled(ctx)) {
          if (it.torConnections.isEmpty()) {
            if (mBinding.connectivityButton.animation == null || !mBinding.connectivityButton.animation.hasStarted()) {
              mBinding.connectivityButton.startAnimation(blinkingAnimation)
            }
            mBinding.connectivityButton.visibility = View.VISIBLE
            mBinding.torConnectedButton.visibility = View.GONE
          } else {
            mBinding.connectivityButton.clearAnimation()
            mBinding.connectivityButton.visibility = View.GONE
            mBinding.torConnectedButton.visibility = View.VISIBLE
          }
        } else {
          mBinding.connectivityButton.clearAnimation()
          mBinding.connectivityButton.visibility = View.GONE
          mBinding.torConnectedButton.visibility = View.GONE
        }
      }
      appContext(ctx).balance.observe(viewLifecycleOwner) {
        mBinding.balance.setAmount(it.sendable)
      }
    }
    app.pendingSwapIns.observe(viewLifecycleOwner) {
      refreshIncomingFunds()
    }
    app.payments.observe(viewLifecycleOwner) { (paymentsCount, payments) ->
      paymentsAdapter.submitList(
        showFooter = paymentsCount > Constants.LATEST_PAYMENTS_COUNT,
        list = payments.take(Constants.LATEST_PAYMENTS_COUNT)
      )
    }
    model.incomingFunds.observe(viewLifecycleOwner) { amount ->
      if (amount.`$greater`(MilliSatoshi(0))) {
        refreshIncomingFundsAmountField()
        mBinding.incomingFundsNotif.visibility = View.VISIBLE
      } else {
        mBinding.incomingFundsNotif.visibility = View.INVISIBLE
      }
    }
  }

  override fun onStart() {
    super.onStart()
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this)
    }
    Wallet.hideKeyboard(context, mBinding.main)
    PreferenceManager.getDefaultSharedPreferences(context).registerOnSharedPreferenceChangeListener(prefsListener)

    context?.let {
      refreshNotifications(it)
      refreshBalanceDisplay(it)
    }

    mBinding.settingsButton.setOnClickListener { findNavController().navigate(R.id.action_main_to_settings) }
    mBinding.receiveButton.setOnClickListener { findNavController().navigate(R.id.action_main_to_receive) }
    mBinding.sendButton.setOnClickListener { findNavController().navigate(R.id.action_main_to_read_input) }
    mBinding.helpButton.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://phoenix.acinq.co/faq"))) }
    mBinding.torConnectedButton.setOnClickListener { findNavController().navigate(R.id.global_action_any_to_tor) }
    mBinding.connectivityButton.setOnClickListener { findNavController().navigate(R.id.action_main_to_connectivity) }
  }

  override fun onStop() {
    super.onStop()
    EventBus.getDefault().unregister(this)
    PreferenceManager.getDefaultSharedPreferences(context).unregisterOnSharedPreferenceChangeListener(prefsListener)
  }

  override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
    context?.let {
      when (key) {
        Prefs.PREFS_SHOW_BALANCE_HOME -> refreshBalanceDisplay(it)
        else -> refreshNotifications(it)
      }
    }
  }

  private fun refreshBalanceDisplay(context: Context) {
    if (Prefs.showBalanceHome(context)) {
      mBinding.balance.visibility = View.VISIBLE
    } else {
      mBinding.balance.visibility = View.GONE
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: PaymentPending) {
    log.debug("pending payment, refreshing list...")
    app.refreshLatestPayments()
  }

  @Subscribe(threadMode = ThreadMode.BACKGROUND)
  fun handleEvent(event: ChannelStateChange) {
    log.debug("channel state changed, refreshing incoming funds...")
    refreshIncomingFunds()
  }

  private fun refreshIncomingFunds() {
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("error when refreshing the incoming funds notification: ", exception)
    }) {
      val totalSwapIns = app.pendingSwapIns.value?.values?.map { s -> Converter.any2Msat(s.amount()) } ?: emptyList()
      val totalChannelsWaitingConf = app.service?.getChannels(`WAIT_FOR_FUNDING_CONFIRMED$`.`MODULE$`)?.map { c ->
        if (c.data() is HasCommitments) {
          (c.data() as HasCommitments).commitments().availableBalanceForSend()
        } else {
          MilliSatoshi(0)
        }
      } ?: emptyList()
      val total = totalSwapIns + totalChannelsWaitingConf
      model.incomingFunds.postValue(if (total.isEmpty()) {
        MilliSatoshi(0)
      } else {
        total.reduce { acc, amount -> acc.`$plus`(amount) }
      })
    }
  }

  private fun refreshIncomingFundsAmountField() {
    model.incomingFunds.value?.let { amount ->
      context?.let { ctx ->
        mBinding.incomingFundsNotif.text = getString(R.string.main_swapin_incoming,
          if (Prefs.getShowAmountInFiat(ctx)) {
            Converter.printFiatPretty(ctx, amount, withUnit = true)
          } else {
            Converter.printAmountPretty(amount, ctx, withUnit = true)
          })
      }
    }
  }

  private fun refreshNotifications(context: Context) {
    checkMnemonics(context)
    checkBackgroundWorkerCanRun(context)
  }

  /**
   * If the background channels watcher has not run since (now) - (DELAY_BEFORE_BACKGROUND_WARNING), we consider that the device is
   * blocking this application from working in background, and show a notification.
   *
   * Some devices vendors are known to aggressively kill applications (including background jobs) in order to save battery,
   * unless the app is whitelisted by the user in a custom OS setting page. This behaviour is hard to detect and not
   * standard, and does not happen on a stock android. In this case, the user has to whitelist the app.
   */
  private fun checkBackgroundWorkerCanRun(context: Context) {
    val channelsWatchOutcome = Prefs.getWatcherLastAttemptOutcome(context)
    if (channelsWatchOutcome.second > 0 && System.currentTimeMillis() - channelsWatchOutcome.second > Constants.DELAY_BEFORE_BACKGROUND_WARNING) {
      log.warn("watcher has not run since {}", DateFormat.getDateTimeInstance().format(Date(channelsWatchOutcome.second)))
      appContext(context).notifications.value?.add(InAppNotifications.BACKGROUND_WORKER_CANNOT_RUN)
      if (app.state.value is KitState.Started) {
        // the user has been notified once, but since the node has started he is safe anyway
        // the background watcher notification countdown can be reset so that it does not spam the user
        Prefs.saveWatcherAttemptOutcome(context, WatchListener.`Ok$`.`MODULE$`)
      }
    } else {
      appContext(context).notifications.value?.remove(InAppNotifications.BACKGROUND_WORKER_CANNOT_RUN)
    }
  }

  private fun checkMnemonics(context: Context) {
    val timestamp = Prefs.getMnemonicsSeenTimestamp(context)
    if (timestamp == 0L) {
      appContext(context).notifications.value?.add(InAppNotifications.MNEMONICS_NEVER_SEEN)
    } else {
      appContext(context).notifications.value?.remove(InAppNotifications.MNEMONICS_NEVER_SEEN)
    }
  }

}

class MainViewModel : ViewModel() {
  val incomingFunds = MutableLiveData(MilliSatoshi(0))
}

