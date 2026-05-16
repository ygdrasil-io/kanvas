package org.skia.core

import kotlin.Any
import kotlin.Array
import kotlin.Int
import kotlin.UInt
import kotlin.Unit

public typealias SkBlitRowProc32 = (
  Array<UInt>,
  Array<Any>,
  Int,
  Any,
) -> Unit
