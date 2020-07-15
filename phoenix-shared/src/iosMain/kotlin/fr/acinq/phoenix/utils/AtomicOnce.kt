package fr.acinq.phoenix.utils

import kotlin.native.concurrent.AtomicReference
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal class AtomicOnce<T : Any> : ReadWriteProperty<Any?, T> {
    private val ref = AtomicReference<T?>(null)

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return ref.value ?: error("Property ${property.name} should be initialized before get.")
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        if (!ref.compareAndSet(null, value)) error("Property ${property.name} has already been initialized.")
    }
}