package org.skia.core

/**
 * Mirrors Skia's `SkCanvas::SaveLayerFlags` (`uint32_t` low-bit-mask of
 * `SaveLayerFlagsSet` enumerants — `kPreserveLCDText_SaveLayerFlag`,
 * `kInitWithPrevious_SaveLayerFlag`, `kF16ColorType`, ...). Resolved here as
 * a plain [Int] so existing call sites that pass `0` for "no flags" work
 * unchanged. The current raster path silently ignores all flag bits — they
 * gate features (backdrop init, F16 layer storage, LCD-text preservation)
 * that aren't implemented yet.
 */
public typealias SaveLayerFlags = Int
