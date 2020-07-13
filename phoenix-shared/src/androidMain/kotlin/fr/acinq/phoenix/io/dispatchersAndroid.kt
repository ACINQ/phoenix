package fr.acinq.phoenix.io

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainCoroutineDispatcher

actual val Dispatchers.AppMain: MainCoroutineDispatcher get() = Main
