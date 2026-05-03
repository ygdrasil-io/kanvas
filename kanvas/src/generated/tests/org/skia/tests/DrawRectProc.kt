package org.skia.tests

import kotlin.Unit
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.math.SkRect

public typealias DrawRectProc = (
  SkCanvas?,
  SkRect,
  SkPaint,
) -> Unit
