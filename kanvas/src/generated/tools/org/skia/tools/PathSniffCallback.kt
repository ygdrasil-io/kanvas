package org.skia.tools

import kotlin.Unit
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.math.SkMatrix

public typealias PathSniffCallback = (
  SkMatrix,
  SkPath,
  SkPaint,
) -> Unit
