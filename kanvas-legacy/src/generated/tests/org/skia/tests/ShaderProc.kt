package org.skia.tests

import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.foundation.SkTileMode

public typealias ShaderProc = (SkTileMode, SkTileMode) -> SkSp<SkShader>
