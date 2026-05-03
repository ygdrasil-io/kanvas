package org.skia.core

import kotlin.Int
import kotlin.Unit

public typealias MergeAAProc = (
  Int,
  Int,
  Int,
  Int,
  Int,
) -> Unit
