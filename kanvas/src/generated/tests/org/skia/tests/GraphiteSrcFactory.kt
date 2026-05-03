package org.skia.tests

import org.skia.foundation.SkPixmap
import org.skia.gpu.Recorder

public typealias GraphiteSrcFactory = (Recorder?, SkPixmap) -> T
