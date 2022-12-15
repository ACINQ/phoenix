package fr.acinq.phoenix.utils

import platform.Foundation.*

actual class PlatformContext

actual fun getApplicationFilesDirectoryPath(ctx: PlatformContext): String =
    NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)[0] as String

actual fun getApplicationCacheDirectoryPath(ctx: PlatformContext): String =
    NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)[0] as String

actual fun getDatabaseFilesDirectoryPath(ctx: PlatformContext): String? {
    return NSFileManager.defaultManager.containerURLForSecurityApplicationGroupIdentifier(
        groupIdentifier = "group.co.acinq.phoenix"
    )?.URLByAppendingPathComponent(
        pathComponent = "databases",
        isDirectory = true
    )?.path
}

actual fun getTemporaryDirectoryPath(ctx: PlatformContext): String =
    NSTemporaryDirectory()
