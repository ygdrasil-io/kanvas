package org.skia.tests

import kotlin.Any
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkSize

public typealias MakeShaderProc = (Any, SkSize) -> SkSp<SkShader>
