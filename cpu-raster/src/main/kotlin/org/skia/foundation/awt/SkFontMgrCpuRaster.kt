package org.skia.foundation.awt

import org.skia.foundation.SkFontMgr
import org.skia.foundation.opentype.OpenTypeSystemFontMgr

/**
 * JVM default font manager backed by pure Kotlin OpenType system-font
 * scanning. This does not use AWT or `GraphicsEnvironment`.
 */
public fun SkFontMgr.Companion.RefDefault(): SkFontMgr = OpenTypeSystemFontMgrHolder.SINGLETON

private object OpenTypeSystemFontMgrHolder {
    val SINGLETON: SkFontMgr by lazy { OpenTypeSystemFontMgr.Create() }
}
