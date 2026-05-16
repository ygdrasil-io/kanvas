package org.skia.foundation.awt

import org.skia.foundation.SkFontMgr

/**
 * Companion extension exposing the JVM's default font manager —
 * AWT-backed, enumerating every font available through
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
 * GPU module's classpath clean. Callers import this extension
 * (`import org.skia.foundation.awt.RefDefault`) to use the
 * familiar `SkFontMgr.RefDefault()` call site.
 */
public fun SkFontMgr.Companion.RefDefault(): SkFontMgr = JvmAwtFontMgr.SINGLETON
