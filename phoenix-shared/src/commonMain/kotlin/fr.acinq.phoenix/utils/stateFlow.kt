package fr.acinq.phoenix.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.reflect.KProperty

operator fun <T : Any?> MutableStateFlow<T>.setValue(thisRef: Any?, property: KProperty<*>, value: T) {
    this.value = value
}

operator fun <T : Any?> MutableStateFlow<T>.getValue(thisRef: Any?, property: KProperty<*>): T {
    return this.value
}

