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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import fr.acinq.bitcoin.ByteVector32
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

object Notifications {
    const val MISSED_PAYMENT_NOTIF_ID = 354319
    const val MISSED_PAYMENT_NOTIF_CHANNEL = "${BuildConfig.APPLICATION_ID}.MISSED_PAYMENT_NOTIF"
    const val RECEIVED_PAYMENT_NOTIF_ID = 354320
    const val RECEIVED_PAYMENT_NOTIF_CHANNEL = "${BuildConfig.APPLICATION_ID}.INCOMING_PAYMENT_NOTIF"
    const val BACKGROUND_NOTIF_ID = 354321
    const val BACKGROUND_NOTIF_CHANNEL = "${BuildConfig.APPLICATION_ID}.FCM_NOTIF"

    fun registerNotificationChannels(context: Context) {
        // notification channels (android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannels(
                listOf(
                    NotificationChannel(BACKGROUND_NOTIF_CHANNEL, context.getString(R.string.notification_headless_title), NotificationManager.IMPORTANCE_HIGH).apply {
                        description = context.getString(R.string.notification_headless_desc)
                    },
                    NotificationChannel(RECEIVED_PAYMENT_NOTIF_CHANNEL, context.getString(R.string.notification_received_payment_title), NotificationManager.IMPORTANCE_HIGH).apply {
                        description = context.getString(R.string.notification_received_payment_desc)
                    },
                    NotificationChannel(MISSED_PAYMENT_NOTIF_CHANNEL, context.getString(R.string.notification_missed_payment_title), NotificationManager.IMPORTANCE_HIGH).apply {
                        description = context.getString(R.string.notification_missed_payment_desc)
                    },
                )
            )
        }
    }

    fun notifyPaymentMissedBelowMin(context: Context, amount: MilliSatoshi, minPayToOpen: MilliSatoshi) {
        NotificationCompat.Builder(context, MISSED_PAYMENT_NOTIF_CHANNEL).apply {
            setContentTitle(context.getString(R.string.notif__missed__title))
            setContentText(context.getString(R.string.notif__missed__below_min, amount.toPrettyString(BitcoinUnit.Sat), minPayToOpen.toPrettyString(BitcoinUnit.Sat)))
            setSmallIcon(R.drawable.ic_phoenix_outline)
            setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            setAutoCancel(true)
        }.let {
            NotificationManagerCompat.from(context).notify(MISSED_PAYMENT_NOTIF_ID, it.build())
        }
    }

    suspend fun notifyPaymentReceived(
        context: Context,
        paymentHash: ByteVector32,
        amount: MilliSatoshi,
        rates: List<ExchangeRate>
    ) {
        val isFiat = UserPrefs.getIsAmountInFiat(context).first()
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
        NotificationCompat.Builder(context, RECEIVED_PAYMENT_NOTIF_CHANNEL).apply {
            setContentTitle(context.getString(R.string.notif__headless_title__received, amount.toPrettyString(unit, rate, withUnit = true)))
            setSmallIcon(R.drawable.ic_phoenix_outline)
            setContentIntent(TaskStackBuilder.create(context).run {
                Intent(
                    Intent.ACTION_VIEW,
                    "phoenix:payment/${WalletPaymentId.DbType.INCOMING}/${paymentHash.toHex()}".toUri(),
                    context,
                    MainActivity::class.java
                ).let { addNextIntentWithParentStack(it) }
                getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE)
            })
            setAutoCancel(true)
        }.let {
            NotificationManagerCompat.from(context).notify(RECEIVED_PAYMENT_NOTIF_ID, it.build())
        }
    }
}

