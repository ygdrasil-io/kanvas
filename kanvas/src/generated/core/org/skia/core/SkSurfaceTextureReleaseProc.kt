package org.skia.core

import kotlin.Unit
import org.skia.ports.ReleaseContext

public typealias SkSurfaceTextureReleaseProc = (ReleaseContext) -> Unit
