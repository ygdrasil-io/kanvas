package org.skia.core

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.Unit

public typealias SkStrokerPrivJoinProc = (
  Int,
  Int,
  Any,
  Any,
  Any,
  Any,
  Any,
  Boolean,
  Boolean,
) -> Unit
