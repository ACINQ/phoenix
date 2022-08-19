package fr.acinq.phoenix.utils

import platform.Foundation.*


actual class PlatformContext

actual fun getApplicationFilesDirectoryPath(ctx: PlatformContext): String =
    NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)[0] as String

actual fun getApplicationCacheDirectoryPath(ctx: PlatformContext): String =
    NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)[0] as String

actual fun getTemporaryDirectoryPath(ctx: PlatformContext): String =
    NSTemporaryDirectory()
