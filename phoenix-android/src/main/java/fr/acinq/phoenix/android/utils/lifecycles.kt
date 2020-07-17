package fr.acinq.phoenix.android.utils

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner


fun (() -> Unit).bindOnLifecycle(owner: LifecycleOwner) {
    owner.lifecycle.addObserver(LifecycleEventObserver { _, e ->
        if (e == Lifecycle.Event.ON_DESTROY) this()
    })
}
