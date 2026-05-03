package org.skia.core

import kotlin.Array
import kotlin.Boolean
import org.skia.math.SkMatrix
import org.skia.math.SkPoint

public typealias PolyMapProc = (Array<SkPoint>, SkMatrix?) -> Boolean
