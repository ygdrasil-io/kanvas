package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.Unit

public typealias PrePostProc = (
  Int,
  Int,
  Boolean,
) -> Unit
