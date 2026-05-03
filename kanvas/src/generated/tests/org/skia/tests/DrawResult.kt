package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas

public enum class DrawResult {
  kOk,
  kFail,
  kSkip,
}

public typealias GMDrawResult = DrawResult

public typealias SimpleGMDrawProc = (SkCanvas?, String?) -> DrawResult
