package fr.acinq.phoenix.android.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import fr.acinq.bitcoin.ByteVector
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.android.BuildConfig
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.utils.Notifications
import fr.acinq.phoenix.android.utils.Prefs
import fr.acinq.phoenix.data.Wallet
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.slf4j.LoggerFactory
import java.io.IOException
import java.lang.Runnable
import java.net.UnknownHostException
import java.util.concurrent.locks.ReentrantLock

class NodeService : Service() {

    private val log = LoggerFactory.getLogger(this::class.java)
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val binder = NodeBinder()

    /** True if the service is running headless (that is without a GUI). In that case we should show a notification */
    @Volatile
    private var isHeadless = true

    // Notifications
    private lateinit var notificationManager: NotificationManagerCompat
    private val notificationBuilder = NotificationCompat.Builder(this, Notifications.HEADLESS_NOTIF_CHANNEL_ID)

    /** State of the wallet, provides access to the business when started. Private so that it's not mutated from the outside. */
    private val _state = MutableLiveData<WalletState>(WalletState.Off)
    val state: LiveData<WalletState> get() = _state
    /** Lock for state updates */
    private val stateLock = ReentrantLock()

    /** List of payments received while the app is in the background */
    private val receivedInBackground: MutableLiveData<List<MilliSatoshi>> = MutableLiveData(emptyList())

    override fun onCreate() {
        super.onCreate()
        log.info("creating node service...")
        notificationManager = NotificationManagerCompat.from(this)
        refreshFcmToken()
        log.info("service created")
    }

    private fun refreshFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                log.warn("fetching FCM registration token failed", task.exception)
                return@OnCompleteListener
            }
            task.result?.let { serviceScope.launch { Prefs.saveFcmToken(applicationContext, it) } }
        })
    }

    // =========================================================== //
    //                      SERVICE LIFECYCLE                      //
    // =========================================================== //

    override fun onBind(intent: Intent?): IBinder? {
        log.info("binding node service from intent=$intent")
        // UI is binding to the service. The service is not headless anymore and we can remove the notification.
        isHeadless = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(Notifications.HEADLESS_NOTIF_ID)
        return binder
    }

    /** When unbound, the service is running headless. */
    override fun onUnbind(intent: Intent?): Boolean {
        isHeadless = true
        return false
    }

    private val shutdownHandler = Handler(Looper.getMainLooper())
    private val shutdownRunnable: Runnable = Runnable {
        if (isHeadless) {
            log.info("reached scheduled shutdown...")
            if (receivedInBackground.value == null || receivedInBackground.value!!.isEmpty()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(STOP_FOREGROUND_DETACH)
                notificationManager.notify(Notifications.HEADLESS_NOTIF_ID, notificationBuilder.setAutoCancel(true).build())
            }
            shutdown()
        }
    }

    /** Shutdown the node, close connections and stop the service */
    fun shutdown() {
        // closeConnections()
        log.info("shutting down service in state=${state.value?.name}")
        stopSelf()
        updateState(WalletState.Off)
    }

    // =========================================================== //
    //                    START COMMAND HANDLER                    //
    // =========================================================== //

    /** Called when an intent is called for this service. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        log.info("start service from intent [ intent=$intent, flag=$flags, startId=$startId ]")
        val reason = intent?.getStringExtra(EXTRA_REASON) // ?.also { spawnReason = it }

        val encryptedSeed = SeedManager.loadSeedFromDisk(applicationContext)
        when {
            state.value is WalletState.Started -> {
                notifyForegroundService(getString(R.string.notif__headless_title__default), null)
            }
            encryptedSeed is EncryptedSeed.V2.NoAuth -> {
                encryptedSeed.decrypt().let {
                    log.info("successfully decrypted seed in the background, starting wallet...")
                    notifyForegroundService(getString(R.string.notif__headless_title__default), null)
                    startBusiness(it)
                }
            }
            else -> {
                log.info("unhandled incoming payment with seed=${encryptedSeed?.name()}")
                if (reason == "IncomingPayment") {
                    notifyForegroundService(getString(R.string.notif__headless_title__missed_incoming), getString(R.string.notif__headless_message__app_locked))
                } else {
                    notifyForegroundService(getString(R.string.notif__headless_title__missed_fulfill), getString(R.string.notif__headless_message__pending_fulfill))
                }
            }
        }
        shutdownHandler.removeCallbacksAndMessages(null)
        shutdownHandler.postDelayed(shutdownRunnable, 60 * 1000L) // push back shutdown by 60s
        if (!isHeadless) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        return START_NOT_STICKY
    }

    // =========================================================== //
    //                      START BUSINESS                         //
    // =========================================================== //

    /**
     * Start the node business logic.
     * @param decryptedPayload Must be the decrypted payload of an [EncryptedSeed] object.
     */
    fun startBusiness(decryptedPayload: ByteArray) {
        // Check app state consistency. Use a lock because the [startKit] method can be called concurrently.
        // If the kit is already starting, started, or in error, the method returns.
        val canProceed = try {
            stateLock.lock()
            val state = _state.value
            if (state !is WalletState.Off) {
                log.warn("ignore attempt to start kit with app state=${state?.name}")
                false
            } else {
                updateState(WalletState.Bootstrap.Init, lazy = false)
                true
            }
        } catch (e: Exception) {
            log.error("error in state check when starting kit: ", e)
            false
        } finally {
            stateLock.unlock()
        }

        if (canProceed) {
            serviceScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
                log.info("aborted node startup with ${e.javaClass.simpleName}")
                when (e) {
                    is IOException, is IllegalAccessException -> {
                        log.error("seed file not readable: ", e)
                        updateState(WalletState.Error.UnreadableData)
                    }
                    else -> {
                        log.error("error when starting node: ", e)
                        updateState(WalletState.Error.Generic(e.localizedMessage ?: e.javaClass.simpleName))
                    }
                }
                if (isHeadless) {
                    shutdown()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
            }) {
                log.debug("initiating node startup from state=${_state.value?.name}")
                val state = doStartNode(decryptedPayload)
                updateState(state)
            }
        }
    }

    @WorkerThread
    private suspend fun doStartNode(decryptedPayload: ByteArray): WalletState.Started {
        log.info("starting up node...")
        val business = (applicationContext as? PhoenixApplication)?.business ?: throw RuntimeException("invalid context type, should be PhoenixApplication")
        val electrumServer = Prefs.getElectrumServer(applicationContext).first()
        val seed = business.prepWallet(EncryptedSeed.toMnemonics(decryptedPayload))
        business.loadWallet(seed)
        business.start()
        business.appConfigurationManager.updateElectrumConfig(electrumServer)
        return WalletState.Started.Kmm
    }

    // =========================================================== //
    //                 STATE UPDATE & NOTIFICATIONS                //
    // =========================================================== //

    /**
     * Update the app mutable [_state] and show a notification if the service is headless.
     * @param newState The new state of the app.
     * @param lazy `true` to update with postValue, `false` to commit the state directly. If not lazy, this method MUST be called from the main thread!
     */
    @Synchronized
    private fun updateState(newState: WalletState, lazy: Boolean = true) {
        log.info("updating state from {} to {} with headless={}", _state.value?.name, newState.name, isHeadless)
        if (_state.value != newState) {
            if (lazy) {
                _state.postValue(newState)
            } else {
                _state.value = newState
            }
        } else {
            log.debug("ignored attempt to update state=${_state.value} to state=$newState")
        }
    }

    /** Display a blocking notification and set the service as being foregrounded. */
    private fun notifyForegroundService(title: String?, message: String?) {
        log.debug("notifying foreground service with msg=$message")
        updateNotification(title, message).also { startForeground(Notifications.HEADLESS_NOTIF_ID, it) }
    }

    private fun updateNotification(title: String?, message: String?): Notification {
        title?.let {
            notificationBuilder.setContentTitle(it)
        }
        message?.let {
            notificationBuilder.setContentText(message)
            notificationBuilder.setStyle(NotificationCompat.BigTextStyle().bigText(message))
        }
        return notificationBuilder.build().apply {
            notificationManager.notify(Notifications.HEADLESS_NOTIF_ID, this)
        }
    }

    inner class NodeBinder : Binder() {
        fun getService(): NodeService = this@NodeService
    }

    companion object {
        const val EXTRA_REASON = "${BuildConfig.APPLICATION_ID}.SERVICE_SPAWN_REASON"
    }
}