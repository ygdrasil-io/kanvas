package org.skia.core

import kotlin.Int
import kotlin.Unit
import org.skia.ports.ReleaseContext

public typealias SkSpecialImageRasterReleaseProc = (Int, ReleaseContext) -> Unit
