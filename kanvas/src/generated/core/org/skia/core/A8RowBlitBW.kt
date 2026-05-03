package org.skia.core

import kotlin.Array
import kotlin.Int
import kotlin.UByte
import kotlin.Unit

public typealias A8RowBlitBW = (
  Array<UByte>,
  UByte,
  Int,
) -> Unit
