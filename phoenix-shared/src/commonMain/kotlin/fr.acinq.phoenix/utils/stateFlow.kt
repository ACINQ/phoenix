package fr.acinq.phoenix.utils

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.reflect.KProperty

@ExperimentalCoroutinesApi
operator fun <T : Any?> MutableStateFlow<T>.setValue(thisRef: Any?, property: KProperty<*>, value: T) {
    this.value = value
}

@ExperimentalCoroutinesApi
operator fun <T : Any?> MutableStateFlow<T>.getValue(thisRef: Any?, property: KProperty<*>): T {
    return this.value
}

