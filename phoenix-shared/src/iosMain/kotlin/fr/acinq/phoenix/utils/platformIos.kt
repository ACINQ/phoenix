package fr.acinq.phoenix.utils

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask


actual class PlatformContext

actual fun getApplicationFilesDirectoryPath(ctx: PlatformContext): String =
    NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)[0] as String
