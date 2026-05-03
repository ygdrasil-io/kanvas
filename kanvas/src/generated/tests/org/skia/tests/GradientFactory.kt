package org.skia.tests

import kotlin.Any
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp

public typealias GradientFactory = (Any) -> SkSp<SkShader>
