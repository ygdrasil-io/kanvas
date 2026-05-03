package org.skia.tests

import kotlin.Any
import kotlin.Array
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp

public typealias GradMaker = (
  Array<Any>,
  Any,
  Any,
) -> SkSp<SkShader>
