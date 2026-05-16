package org.skia.core

import kotlin.Any
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSurfaceProps
import org.skia.memory.SkArenaAlloc

public typealias DrawBlitterChooser = (
  Any,
  Any,
  SkPaint,
  SkArenaAlloc?,
  Any,
  Any,
  SkSurfaceProps,
  Any,
) -> SkBlitter?
