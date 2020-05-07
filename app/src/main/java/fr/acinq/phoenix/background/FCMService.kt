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
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import fr.acinq.eclair.io.Peer
import fr.acinq.phoenix.MainActivity
import fr.acinq.phoenix.R
import fr.acinq.phoenix.utils.Constants
import fr.acinq.phoenix.utils.Wallet
import org.greenrobot.eventbus.EventBus
import org.slf4j.LoggerFactory


class FCMService : FirebaseMessagingService() {
  private val log = LoggerFactory.getLogger(this::class.java)

  /**
   * Called when message is received.
   * See https://firebase.google.com/docs/cloud-messaging/concept-options
   *
   * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
   */
  override fun onMessageReceived(remoteMessage: RemoteMessage) {
    log.info("received fcm message=${remoteMessage} from ${remoteMessage.from}")
    remoteMessage.data.isNotEmpty().let {
      showNotification()
    }
  }

  /**
   * Called if the FCM token is updated. This may occur if the security of the previous token has been compromised.
   */
  override fun onNewToken(token: String) {
    log.debug("refresh fcm token=$token")
    EventBus.getDefault().post(Peer.SendFCMToken(Wallet.ACINQ.nodeId(), token))
  }

  private fun showNotification() {
    val intent = Intent(this, MainActivity::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    val builder = NotificationCompat.Builder(this, Constants.FCM_NOTIFICATION_CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_phoenix)
      .setContentTitle(getString(R.string.notif_fcm_title))
      .setContentText(getString(R.string.notif_fcm_message))
      .setContentIntent(PendingIntent.getActivity(this, Constants.FCM_REQUEST_CODE, intent, PendingIntent.FLAG_ONE_SHOT))
      .setAutoCancel(true)
    NotificationManagerCompat.from(this).notify(Constants.FCM_REQUEST_CODE, builder.build())
  }
}
