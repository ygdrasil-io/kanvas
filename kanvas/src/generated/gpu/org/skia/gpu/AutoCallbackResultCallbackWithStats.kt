package org.skia.gpu

import kotlin.Unit

public typealias AutoCallbackResultCallbackWithStats = (
  Context,
  CallbackResult,
  GpuStats,
) -> Unit
