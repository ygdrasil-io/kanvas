package org.skia.tests

import kotlin.Unit
import org.skia.gpu.ganesh.GrContextOptions

public typealias GaneshTestProc = (Reporter?, GrContextOptions) -> Unit
