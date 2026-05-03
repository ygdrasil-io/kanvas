package org.skia.core

import kotlin.Any
import kotlin.Unit

public typealias PtProcRecProc = (
  PtProcRec,
  Any,
  SkBlitter?,
) -> Unit
