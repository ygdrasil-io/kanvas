package org.skia.tests

import kotlin.Unit
import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint

public typealias Proc = (
  SkCanvas?,
  SkPaint,
  SkFont,
) -> Unit
