package org.skia.tests

import kotlin.Any
import kotlin.Unit
import org.skia.foundation.SkRRect

public typealias InsetProc = (
  SkRRect,
  Any,
  Any,
  SkRRect?,
) -> Unit
