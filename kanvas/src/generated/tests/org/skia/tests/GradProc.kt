package org.skia.tests

import kotlin.Unit
import skiatest.`Reporter* reporter`

public typealias GradProc = (
  `Reporter* reporter`,
  GradRec,
  GradRec,
) -> Unit
