package org.skia.core

import kotlin.Any
import kotlin.Array
import kotlin.Int
import kotlin.UInt
import kotlin.Unit

public typealias SkBitmapProcStateSampleProc32 = (
  SkBitmapProcState,
  Array<UInt>,
  Int,
  Array<Any>,
) -> Unit
