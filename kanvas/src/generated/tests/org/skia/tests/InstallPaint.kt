package org.skia.tests

import kotlin.UInt
import kotlin.Unit
import org.skia.foundation.SkPaint

public typealias InstallPaint = (
  SkPaint?,
  UInt,
  UInt,
) -> Unit
