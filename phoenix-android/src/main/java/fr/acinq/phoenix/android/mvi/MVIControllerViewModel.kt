package fr.acinq.phoenix.android.mvi

import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import fr.acinq.phoenix.ctrl.MVI
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.type.TypeToken


class MVIControllerViewModel<C: MVI.Controller<*, *>>(val controller: C) : ViewModel(), State<C> {

    override val value: C get() = controller

    override fun onCleared() {
        controller.stop()
    }

    class Factory(private val di: DI, private val controllerType: TypeToken<out Any>) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return MVIControllerViewModel(di.direct.Instance(controllerType) as MVI.Controller<*, *>) as T
        }
    }

}
