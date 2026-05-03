package org.skia.core

import kotlin.Array
import kotlin.Int
import kotlin.Unit
import org.skia.foundation.SkPMColor

public typealias SkBitmapProcStateShaderProc32 = (
  Int,
  Int,
  Int,
  Array<SkPMColor>,
  Int,
) -> Unit
