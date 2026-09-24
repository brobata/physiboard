/*
 * Vendored from Shizuku (https://github.com/RikkaApps/Shizuku) by RikkaApps.
 * Licensed under the Apache License, Version 2.0. See
 * app/src/main/java/moe/shizuku/manager/adb/NOTICE and third_party/licenses/Apache-2.0.txt.
 */
package moe.shizuku.manager.adb

@Suppress("NOTHING_TO_INLINE")
inline fun adbError(message: Any): Nothing = throw AdbException(message.toString())

open class AdbException : Exception {

    constructor(message: String, cause: Throwable?) : super(message, cause)
    constructor(message: String) : super(message)
    constructor(cause: Throwable) : super(cause)
    constructor()
}

class AdbInvalidPairingCodeException : AdbException()

class AdbKeyException : AdbException {
    constructor(cause: Throwable) : super(cause)
    constructor(message: String, cause: Throwable) : super(message, cause)
}
