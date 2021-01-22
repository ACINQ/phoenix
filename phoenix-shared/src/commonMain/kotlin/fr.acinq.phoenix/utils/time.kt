package fr.acinq.phoenix.utils

import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

@OptIn(ExperimentalTime::class)
val RETRY_DELAY = 0.5.seconds

@OptIn(ExperimentalTime::class)
fun increaseDelay(retryDelay: Duration) = when (val delay = retryDelay.inSeconds) {
    8.0 -> delay
    else -> delay * 2.0
}.seconds
