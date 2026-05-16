package org.skia.tests

import graphite.TestOptions
import kotlin.Unit

public typealias GraphiteTestProc = (Reporter?, TestOptions) -> Unit
