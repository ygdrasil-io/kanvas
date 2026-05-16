package org.skia.core

import kotlin.Unit
import org.skia.foundation.SkSpan
import org.skia.math.SkPoint

public typealias SkScanHairRCProc = (
  SkSpan<SkPoint>,
  SkRasterClip,
  SkBlitter?,
) -> Unit
