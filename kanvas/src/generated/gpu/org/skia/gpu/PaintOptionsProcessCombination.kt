package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Unit

public typealias PaintOptionsProcessCombination = (
  Any,
  DrawTypeFlags,
  Boolean,
  Coverage,
  RenderPassDesc,
) -> Unit
