package org.skia.tests

import kotlin.FloatArray
import kotlin.Int
import kotlin.Unit

public typealias ComparePixmapsErrorReporter = (
  Int,
  Int,
  FloatArray,
) -> Unit
