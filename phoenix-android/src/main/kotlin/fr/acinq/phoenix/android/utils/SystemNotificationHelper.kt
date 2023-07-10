/*
 * Copyright 2021 ACINQ SAS
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

package fr.acinq.phoenix.android.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.net.toUri
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.LiquidityEvents
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.android.BuildConfig
import fr.acinq.phoenix.android.MainActivity
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.ExchangeRate
import fr.acinq.phoenix.data.FiatCurrency
import fr.acinq.phoenix.data.WalletPaymentId
import kotlinx.coroutines.flow.first
import org.slf4j.LoggerFactory
import java.text.DecimalFormat
import java.util.Random

object SystemNotificationHelper {
    private const val PAYMENT_FAILED_NOTIF_ID = 354319
    private const val PAYMENT_FAILED_NOTIF_CHANNEL = "${BuildConfig.APPLICATION_ID}.PAYMENT_FAILED_NOTIF"
    private const val PAYMENT_RECEIVED_NOTIF_CHANNEL = "${BuildConfig.APPLICATION_ID}.PAYMENT_RECEIVED_NOTIF"
    private const val SETTLEMENT_PENDING_NOTIF_ID = 354322
    private const val SETTLEMENT_PENDING_NOTIF_CHANNEL = "${BuildConfig.APPLICATION_ID}.SETTLEMENT_PENDING_NOTIF"

    const val HEADLESS_NOTIF_ID = 354321
    const val HEADLESS_NOTIF_CHANNEL = "${BuildConfig.APPLICATION_ID}.BACKGROUND_PROCESSING"

    private const val CHANNELS_WATCHER_ALERT_ID = 354324
    private const val CHANNELS_WATCHER_ALERT_CHANNEL = "${BuildConfig.APPLICATION_ID}.CHANNELS_WATCHER"

    private val log = LoggerFactory.getLogger(this::class.java)

    fun registerNotificationChannels(context: Context) {
        // notification channels (android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannels(
                listOf(
                    NotificationChannel(HEADLESS_NOTIF_CHANNEL, context.getString(R.string.notification_headless_title), NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = context.getString(R.string.notification_headless_desc)
                    },
                    NotificationChannel(PAYMENT_RECEIVED_NOTIF_CHANNEL, context.getString(R.string.notification_pending_settlement_title), NotificationManager.IMPORTANCE_HIGH).apply {
                        description = context.getString(R.string.notification_pending_settlement_desc)
                    },
                    NotificationChannel(PAYMENT_RECEIVED_NOTIF_CHANNEL, context.getString(R.string.notification_received_payment_title), NotificationManager.IMPORTANCE_LOW).apply {
                        description = context.getString(R.string.notification_received_payment_desc)
                    },
                    NotificationChannel(PAYMENT_FAILED_NOTIF_CHANNEL, context.getString(R.string.notification_missed_payment_title), NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = context.getString(R.string.notification_missed_payment_desc)
                    },
                )
            )
        }
    }

    fun notifyRunningHeadless(context: Context): Notification {
        return NotificationCompat.Builder(context, HEADLESS_NOTIF_CHANNEL).apply {
            setContentTitle(context.getString(R.string.notif_headless_title_default))
            setSmallIcon(R.drawable.ic_phoenix_outline)
        }.build().also {
            NotificationManagerCompat.from(context).notify(HEADLESS_NOTIF_ID, it)
        }
    }

    private fun notifyPaymentFailed(context: Context, title: String, message: String): Notification {
        return NotificationCompat.Builder(context, PAYMENT_FAILED_NOTIF_CHANNEL).apply {
            setContentTitle(title)
            setContentText(message)
            setStyle(NotificationCompat.BigTextStyle().bigText(message))
            setSmallIcon(R.drawable.ic_phoenix_outline)
            setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            setAutoCancel(true)
        }.build().also {
            NotificationManagerCompat.from(context).notify(PAYMENT_FAILED_NOTIF_ID, it)
        }
    }

    fun notifyPaymentRejectedPolicyDisabled(context: Context, source: LiquidityEvents.Source, amountIncoming: MilliSatoshi): Notification {
        return notifyPaymentFailed(
            context = context,
            title = context.getString(if (source == LiquidityEvents.Source.OnChainWallet) R.string.notif_rejected_deposit_title else R.string.notif_rejected_payment_title,
                amountIncoming.toPrettyString(BitcoinUnit.Sat, withUnit = true)),
            message = context.getString(R.string.notif_rejected_policy_disabled),
        )
    }

    fun notifyPaymentRejectedChannelsInitializing(context: Context, source: LiquidityEvents.Source, amountIncoming: MilliSatoshi): Notification {
        return notifyPaymentFailed(
            context = context,
            title = context.getString(if (source == LiquidityEvents.Source.OnChainWallet) R.string.notif_rejected_deposit_title else R.string.notif_rejected_payment_title,
                amountIncoming.toPrettyString(BitcoinUnit.Sat, withUnit = true)),
            message = context.getString(R.string.notif_rejected_channels_initializing),
        )
    }

    fun notifyPaymentRejectedOverAbsolute(context: Context, source: LiquidityEvents.Source, amountIncoming: MilliSatoshi, fee: MilliSatoshi, absoluteMax: Satoshi): Notification {
        return notifyPaymentFailed(
            context = context,
            title = context.getString(if (source == LiquidityEvents.Source.OnChainWallet) R.string.notif_rejected_deposit_title else R.string.notif_rejected_payment_title,
                amountIncoming.toPrettyString(BitcoinUnit.Sat, withUnit = true)),
            message = context.getString(R.string.notif_rejected_over_absolute,
                fee.toPrettyString(BitcoinUnit.Sat, withUnit = true),
                absoluteMax.toPrettyString(BitcoinUnit.Sat, withUnit = true)),
        )
    }

    fun notifyPaymentRejectedOverRelative(context: Context, source: LiquidityEvents.Source, amountIncoming: MilliSatoshi, fee: MilliSatoshi, percentMax: Int): Notification {
        return notifyPaymentFailed(
            context = context,
            title = context.getString(if (source == LiquidityEvents.Source.OnChainWallet) R.string.notif_rejected_deposit_title else R.string.notif_rejected_payment_title,
                amountIncoming.toPrettyString(BitcoinUnit.Sat, withUnit = true)),
            message = context.getString(R.string.notif_rejected_over_relative,
                fee.toPrettyString(BitcoinUnit.Sat, withUnit = true),
                DecimalFormat("0.##").format(percentMax.toDouble() / 100),
            ),
        )
    }

    fun notifyPaymentMissedAppUnavailable(context: Context): Notification {
        return notifyPaymentFailed(
            context = context,
            title = context.getString(R.string.notif_missed_title),
            message = context.getString(R.string.notif_missed_unavailable),
        )
    }

    fun notifyPendingSettlement(context: Context): Notification {
        return NotificationCompat.Builder(context, SETTLEMENT_PENDING_NOTIF_CHANNEL).apply {
            setContentTitle(context.getString(R.string.notif_pending_settlement_title))
            setContentText(context.getString(R.string.notif_pending_settlement_message))
            setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notif_pending_settlement_message)))
            setSmallIcon(R.drawable.ic_phoenix_outline)
            setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            setAutoCancel(true)
        }.build().also {
            NotificationManagerCompat.from(context).notify(SETTLEMENT_PENDING_NOTIF_ID, it)
        }
    }

    suspend fun notifyPaymentsReceived(
        context: Context,
        paymentHash: ByteVector32,
        amount: MilliSatoshi,
        rates: List<ExchangeRate>,
        isHeadless: Boolean,
    ): Notification {
        val isFiat = UserPrefs.getIsAmountInFiat(context).first() && rates.isNotEmpty()
        val unit = if (isFiat) {
            UserPrefs.getFiatCurrency(context).first()
        } else {
            UserPrefs.getBitcoinUnit(context).first()
        }
        val rate = if (isFiat) {
            when (val rate = rates.find { it.fiatCurrency == unit }) {
                is ExchangeRate.BitcoinPriceRate -> rate
                is ExchangeRate.UsdPriceRate -> {
                    (rates.find { it.fiatCurrency == FiatCurrency.USD } as? ExchangeRate.BitcoinPriceRate)?.let { usdRate ->
                        // create a BTC/Fiat price rate using the USD/BTC rate and the Fiat/USD rate.
                        ExchangeRate.BitcoinPriceRate(
                            fiatCurrency = rate.fiatCurrency,
                            price = rate.price * usdRate.price,
                            source = rate.source,
                            timestampMillis = rate.timestampMillis
                        )
                    }
                }
                else -> null
            }
        } else null

        return NotificationCompat.Builder(context, PAYMENT_RECEIVED_NOTIF_CHANNEL).apply {
            setContentTitle(context.getString(R.string.notif_headless_received, amount.toPrettyString(unit, rate, withUnit = true)))
            setSmallIcon(R.drawable.ic_phoenix_outline)
            setContentIntent(TaskStackBuilder.create(context).run {
                addNextIntentWithParentStack(
                    Intent(
                        Intent.ACTION_VIEW,
                        "phoenix:payments/${WalletPaymentId.DbType.INCOMING.value}/${paymentHash.toHex()}".toUri(),
                        context,
                        MainActivity::class.java
                    )
                )
                getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            })
            setAutoCancel(true)
        }.build().also {
            NotificationManagerCompat.from(context).notify(if (isHeadless) HEADLESS_NOTIF_ID else Random().nextInt(), it)
        }
    }

    fun notifyRevokedCommits(context: Context) {
        NotificationCompat.Builder(context, CHANNELS_WATCHER_ALERT_CHANNEL).apply {
            setContentTitle(context.getString(R.string.notif_watcher_revoked_commit_title))
            setContentText(context.getString(R.string.notif_watcher_revoked_commit_message))
            setSmallIcon(R.drawable.ic_phoenix_outline)
            setAutoCancel(true)
        }.let {
            NotificationManagerCompat.from(context).notify(CHANNELS_WATCHER_ALERT_ID, it.build())
        }
    }
}