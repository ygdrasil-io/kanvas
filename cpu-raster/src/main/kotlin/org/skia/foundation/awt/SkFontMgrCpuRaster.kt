package org.skia.foundation.awt

import org.skia.foundation.SkFontMgr

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
public fun SkFontMgr.Companion.RefAwtDefault(): SkFontMgr = JvmAwtFontMgr.SINGLETON

/**
 * Compatibility alias for older JVM/cpu-raster call sites.
 */
@Deprecated(
    message = "Use RefAwtDefault() for the optional AWT system-font backend, or LiberationFontMgr.Make() for portable OpenType fonts.",
    replaceWith = ReplaceWith("RefAwtDefault()"),
)
public fun SkFontMgr.Companion.RefDefault(): SkFontMgr = RefAwtDefault()
