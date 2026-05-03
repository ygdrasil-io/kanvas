package org.skia.gpu

import kotlin.Any
import kotlin.Unit

public typealias GpuFinishedWithStatsProc = (
  Any,
  CallbackResult,
  GpuStats,
) -> Unit
