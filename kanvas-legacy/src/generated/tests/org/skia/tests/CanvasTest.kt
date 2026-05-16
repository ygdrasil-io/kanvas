package org.skia.tests

import kotlin.Unit
import org.skia.core.SkCanvas

public typealias CanvasTest = (SkCanvas?, Reporter?) -> Unit
