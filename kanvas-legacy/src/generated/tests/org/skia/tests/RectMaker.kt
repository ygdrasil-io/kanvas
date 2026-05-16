package org.skia.tests

import org.skia.core.SkPathData
import org.skia.foundation.SkSp
import org.skia.math.SkPathDirection
import org.skia.math.SkRect

public typealias RectMaker = (SkRect, SkPathDirection) -> SkSp<SkPathData>
