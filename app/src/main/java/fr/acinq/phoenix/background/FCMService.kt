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

import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import fr.acinq.eclair.io.Peer
import fr.acinq.phoenix.AppContext
import fr.acinq.phoenix.utils.Prefs
import fr.acinq.phoenix.utils.Wallet
import org.greenrobot.eventbus.EventBus
import org.slf4j.LoggerFactory


class FCMService : FirebaseMessagingService() {
  private val log = LoggerFactory.getLogger(this::class.java)

  /** When receiving a message over FCM, starts the eclair node service (in foreground). */
  override fun onMessageReceived(remoteMessage: RemoteMessage) {
    log.info("received fcm message=${remoteMessage} from ${remoteMessage.from}, starting eclair node service")
    ContextCompat.startForegroundService(
      applicationContext,
      Intent(applicationContext, EclairNodeService::class.java)
    )
  }

  /** Called if the FCM token is updated. This may occur if the security of the previous token has been compromised. */
  override fun onNewToken(token: String) {
    Prefs.saveFCMToken(applicationContext, token)
    EventBus.getDefault().post(Peer.SendFCMToken(Wallet.ACINQ.nodeId(), token))
  }

}
