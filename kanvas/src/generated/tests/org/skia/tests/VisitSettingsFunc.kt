package org.skia.tests

import kotlin.Any
import kotlin.Int
import kotlin.Unit
import org.skia.gpu.PrecompileContext

public typealias VisitSettingsFunc = (
  PrecompileContext?,
  Any,
  (
    PrecompileContext?,
    PrecompileSettings,
    Int,
  ) -> Unit,
) -> Unit
