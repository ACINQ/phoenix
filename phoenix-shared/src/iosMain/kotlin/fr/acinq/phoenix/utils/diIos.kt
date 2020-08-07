package fr.acinq.phoenix.utils

import kotlinx.cinterop.ObjCClass
import kotlinx.cinterop.ObjCProtocol
import kotlinx.cinterop.getOriginalKotlinClass
import org.kodein.di.DI
import org.kodein.di.Instance
import org.kodein.di.bindings.NoArgDIBinding
import org.kodein.di.bindings.NoArgSimpleBindingDI
import org.kodein.di.bindings.NoScope
import org.kodein.di.bindings.Scope
import org.kodein.di.direct
import org.kodein.di.provider
import org.kodein.type.erased
import org.kodein.type.erasedComp

actual inline fun <reified T: Any> DI.Builder.screenProvider(noinline creator: NoArgSimpleBindingDI<Any>.() -> T): NoArgDIBinding<*, T> =
    provider(creator)

private val ObjCClass.k get() =
    getOriginalKotlinClass(this)
        ?: error("ObjC class $this does not correspond to any Kotlin class")

fun DI.directInstance(of: ObjCClass, params: List<ObjCClass>): Any {
    val token = erasedComp(of.k, *params.map { erased(it.k) } .toTypedArray())
    return direct.Instance(token)
}
