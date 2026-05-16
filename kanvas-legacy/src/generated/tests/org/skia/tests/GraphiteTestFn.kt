package org.skia.tests

import kotlin.Unit
import org.skia.gpu.Context
import org.skia.tools.GraphiteTestContext
import org.skia.tools.TestOptions

public typealias GraphiteTestFn = (
  Reporter?,
  Context?,
  GraphiteTestContext?,
  TestOptions,
) -> Unit
