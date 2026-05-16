package org.skia.core

import kotlin.Array
import kotlin.Int
import kotlin.UInt
import kotlin.Unit

public typealias SkBitmapProcStateMatrixProc = (
  SkBitmapProcState,
  Array<UInt>,
  Int,
  Int,
  Int,
) -> Unit
