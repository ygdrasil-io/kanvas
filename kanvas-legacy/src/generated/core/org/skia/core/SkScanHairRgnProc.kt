package org.skia.core

import kotlin.Unit
import org.skia.foundation.SkRegion
import org.skia.foundation.SkSpan
import org.skia.math.SkPoint

public typealias SkScanHairRgnProc = (
  SkSpan<SkPoint>,
  SkRegion?,
  SkBlitter?,
) -> Unit
