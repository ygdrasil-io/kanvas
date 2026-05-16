package org.skia.tests

import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.gpu.Recorder

public typealias FactoryT = (Recorder?) -> SkSp<SkImage>
