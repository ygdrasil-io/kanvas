package org.skia.math

import kotlin.Any
import kotlin.Array
import kotlin.Int
import kotlin.Unit

public typealias SkMatrixMapPtsProc = (
  Any,
  Array<Any>,
  Array<Any>,
  Int,
) -> Unit
