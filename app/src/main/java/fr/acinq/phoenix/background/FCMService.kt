/*
 * Copyright 2020 ACINQ SAS
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

package fr.acinq.phoenix.background

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import fr.acinq.eclair.io.Peer
import fr.acinq.phoenix.MainActivity
import fr.acinq.phoenix.R
import fr.acinq.phoenix.utils.Constants
import fr.acinq.phoenix.utils.Prefs
import fr.acinq.phoenix.utils.Wallet
import fr.acinq.phoenix.utils.crypto.EncryptedSeed
import fr.acinq.phoenix.utils.crypto.SeedManager
import org.greenrobot.eventbus.EventBus
import org.slf4j.LoggerFactory


class FCMService : FirebaseMessagingService() {
  private val log = LoggerFactory.getLogger(this::class.java)

  /** When receiving a message over FCM, starts the eclair node service (in foreground). */
  override fun onMessageReceived(remoteMessage: RemoteMessage) {
    log.info("received fcm message data=${remoteMessage.data} type=${remoteMessage.messageType} notif=${remoteMessage.notification} from=${remoteMessage.from}")
    val reason = remoteMessage.data["reason"]
    val encryptedSeed = SeedManager.getSeedFromDir(Wallet.getDatadir(applicationContext))
    when (encryptedSeed) {
      is EncryptedSeed.V2.NoAuth -> {
        log.info("start foreground service")
        ContextCompat.startForegroundService(applicationContext,
          Intent(applicationContext, EclairNodeService::class.java).apply { reason?.let { putExtra(EclairNodeService.EXTRA_REASON, it) } })
      }
      else -> {
        val intent = Intent(this, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) }
        log.info("ignored fcm wakeup with seed=${encryptedSeed?.name()}, notify user")
        NotificationCompat.Builder(this, Constants.NOTIF_CHANNEL_ID__HEADLESS).apply {
          setSmallIcon(R.drawable.ic_phoenix_outline)
          setOnlyAlertOnce(true)
          setAutoCancel(true)
          if (reason == "IncomingPayment") {
            setContentTitle(getString(R.string.notif__headless_title__missed_incoming))
            setContentText(getString(R.string.notif__headless_message__app_locked))
          } else {
            setContentTitle(getString(R.string.notif__headless_title__missed_fulfill))
            setContentText(getString(R.string.notif__headless_message__pending_fulfill))
          }
          setContentIntent(PendingIntent.getActivity(applicationContext, Constants.NOTIF_ID__HEADLESS, intent, PendingIntent.FLAG_ONE_SHOT))
        }.build().run {
          NotificationManagerCompat.from(applicationContext).notify(Constants.NOTIF_ID__HEADLESS, this)
        }
      }
    }
  }

  /** Called if the FCM token is updated. This may occur if the security of the previous token has been compromised. */
  override fun onNewToken(token: String) {
    Prefs.saveFCMToken(applicationContext, token)
    EventBus.getDefault().post(FCMToken(token))
  }

}
