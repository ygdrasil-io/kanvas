package org.skia.tests

import kotlin.Any
import kotlin.Int
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkSp

public typealias FilterFactory = (Any, Int) -> SkSp<SkImageFilter>
