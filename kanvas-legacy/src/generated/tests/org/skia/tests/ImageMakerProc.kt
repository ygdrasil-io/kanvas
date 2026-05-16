package org.skia.tests

import kotlin.Any
import kotlin.Array
import kotlin.Int
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp

public typealias ImageMakerProc = (
  Int,
  Int,
  Array<Any>,
) -> SkSp<SkImage>
