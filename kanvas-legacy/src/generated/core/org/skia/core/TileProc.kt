package org.skia.core

import kotlin.Int
import kotlin.UInt
import org.skia.math.SkFixed

public typealias TileProc = (SkFixed, Int) -> UInt
