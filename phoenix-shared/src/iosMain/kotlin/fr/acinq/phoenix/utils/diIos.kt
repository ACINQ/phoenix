package fr.acinq.phoenix.utils

import fr.acinq.phoenix.PhoenixBusiness
import kotlinx.cinterop.ObjCClass
import kotlinx.cinterop.ObjCProtocol
import kotlinx.cinterop.getOriginalKotlinClass
import org.kodein.di.DI
import org.kodein.di.Instance
import org.kodein.di.bindings.*
import org.kodein.di.direct
import org.kodein.di.provider
import org.kodein.type.erased
import org.kodein.type.erasedComp

private val ObjCClass.k get() =
    getOriginalKotlinClass(this)
        ?: error("ObjC class $this does not correspond to any Kotlin class")

fun DI.directInstance(of: ObjCClass, params: List<ObjCClass>): Any {
    val token = erasedComp(of.k, *params.map { erased(it.k) } .toTypedArray())
    return direct.Instance(token)
}

fun phoenixDI() = DI { importAll(PhoenixBusiness.diModules) }
