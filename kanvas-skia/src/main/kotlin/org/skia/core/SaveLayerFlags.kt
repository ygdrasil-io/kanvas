package org.skia.core

/**
 * Mirrors Skia's `SkCanvas::SaveLayerFlags` (`uint32_t` low-bit-mask of
 * `SaveLayerFlagsSet` enumerants — `kPreserveLCDText_SaveLayerFlag`,
 * `kInitWithPrevious_SaveLayerFlag`, `kF16ColorType`, ...). Resolved here as
 * a plain [Int] so existing call sites that pass `0` for "no flags" work
 * unchanged. The raster path honours `kF16ColorType` (`1 shl 4`) and
 * accepts the remaining bits for source compatibility.
 */
public typealias SaveLayerFlags = Int
