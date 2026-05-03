package org.skia.tests

import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkSp

public typealias ImageFilterFactory = () -> SkSp<SkImageFilter>
