package fr.acinq.phoenix.io

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob

expect val Dispatchers.AppMain: MainCoroutineDispatcher



fun AppMainScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.AppMain)
