package org.skia.tests

import kotlin.Boolean
import kotlin.Float

public typealias IsFiniteProc2 = (
  Float,
  Float,
  IsFiniteProc1,
) -> Boolean
