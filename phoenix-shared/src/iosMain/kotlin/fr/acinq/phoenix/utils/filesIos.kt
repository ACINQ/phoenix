package fr.acinq.phoenix.utils

import org.kodein.di.DI
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask


actual fun getApplicationFilesDirectoryPath(di: DI): String =
    NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)[0] as String
