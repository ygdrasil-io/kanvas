package org.skia.core

import kotlin.Int
import kotlin.UByte
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkSpan

public typealias SkRasterPipelineStartPipelineFn = (
  ULong,
  ULong,
  ULong,
  ULong,
  Int,
  SkSpan<MemoryCtxPatch>,
  UByte?,
) -> Unit
