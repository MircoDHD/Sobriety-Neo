package com.katiearose.sobriety.shared

/** Android implementation of the (unused) [Platform] template, see commonMain. */
class AndroidPlatform : Platform {
    override val name: String = "Android ${android.os.Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()
