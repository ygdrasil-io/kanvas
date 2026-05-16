package org.skia.core

import org.skia.math.SkColor
import org.skia.foundation.SkPath
import org.skia.math.SkPoint3
import org.skia.math.SkScalar

/**
 * SPI dispatcher used by [SkCanvas.drawShadow] when no override is
 * provided by a subclass.
 *
 * [SkCanvas] lives in `:kanvas-skia/foundation` (the shared
 * abstraction layer) ; the concrete shadow rendering algorithm
 * (`SkShadowUtils.DrawShadow` — ~821 LOC of tessellation + ambient
 * / spot mask compositing) lives in `:cpu-raster/utils`. Calling
 * the impl directly from foundation would re-introduce the
 * `:cpu-raster → :kanvas-skia` cycle we are trying to keep one-way.
 *
 * Cpu-raster registers a lambda here at module-load time (see
 * `SkShadowUtils` object init in `:cpu-raster`). If the registration
 * never fires (e.g. `:cpu-raster` is not on the classpath, or the
 * caller bypassed regular loading), [SkCanvas.drawShadow] throws.
 */
@Volatile
@JvmField
public var drawShadowDispatcher: ((SkCanvas, SkPath, SkPoint3, SkPoint3, SkScalar, SkColor, SkColor, Int) -> Unit)? = null
