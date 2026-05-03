package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkSp

public typealias MakerT = (SkCanvas?, SkImageInfo) -> SkSp<SkImage>
