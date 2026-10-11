package com.katiearose.sobriety.shared

import platform.UIKit.UIDevice

/** iOS implementation of the (unused) [Platform] template, see commonMain. */
class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()
