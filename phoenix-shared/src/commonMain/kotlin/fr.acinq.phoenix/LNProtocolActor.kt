package fr.acinq.phoenix

import kotlinx.coroutines.channels.ReceiveChannel


interface LNProtocolActor {
    fun openSubscription(): ReceiveChannel<String>

    fun start()
}
