package com.katiearose.sobriety.shared

/**
 * Template code from the Kotlin Multiplatform project wizard. It is not used by the Android app.
 * It exists so that the iOS port can show how platform specific code is declared
 * (`expect` here, `actual` in androidMain / iosMain).
 */
interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
