package org.skia.foundation.awt

import org.skia.foundation.SkFontMgr
import org.skia.foundation.opentype.OpenTypeSystemFontMgr

/**
 * Companion extension exposing the JVM AWT font manager.
 *
 * This is an explicit optional backend: it is AWT-backed, enumerating every
 * font available through
 * `java.awt.GraphicsEnvironment.getAvailableFontFamilyNames()`.
 *
 * Mirrors upstream Skia's platform-default font manager
 * (`SkFontMgr_Mac` on macOS, `SkFontMgr_Fontconfig` on Linux, …)
 * without binding to a native library. See [JvmAwtFontMgr] for
 * backend specifics.
 *
 * Lives in `:cpu-raster` (not `:kanvas-skia` like the rest of
 * [SkFontMgr]) so that the foundation layer remains free of
 * AWT-backed implementation dependencies — this keeps the
 * GPU module's classpath clean. Callers must opt in to this package
 * explicitly; portable font paths should use
 * [org.skia.foundation.LiberationFontMgr.Make] instead.
 */
@Deprecated(
    message = "Use RefDefault() for the pure Kotlin OpenType system-font manager. RefAwtDefault() is a legacy AWT backend scheduled for removal.",
    replaceWith = ReplaceWith("RefDefault()"),
)
public fun SkFontMgr.Companion.RefAwtDefault(): SkFontMgr = JvmAwtFontMgr.SINGLETON

/**
 * JVM default font manager backed by pure Kotlin OpenType system-font
 * scanning. This does not use AWT or `GraphicsEnvironment`.
 */
public fun SkFontMgr.Companion.RefDefault(): SkFontMgr = OpenTypeSystemFontMgrHolder.SINGLETON

private object OpenTypeSystemFontMgrHolder {
    val SINGLETON: SkFontMgr by lazy { OpenTypeSystemFontMgr.Create() }
}
