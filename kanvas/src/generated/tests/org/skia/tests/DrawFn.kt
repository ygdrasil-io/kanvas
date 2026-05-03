package org.skia.tests

import kotlin.Int
import kotlin.Unit
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp

public typealias DrawFn = (
  SkSp<SkImage>,
  SkCanvas?,
  Int,
  Int,
) -> Unit
