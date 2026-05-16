package org.skia.core

import kotlin.Int

public typealias NextFn = (SkBlockAllocator.Block?, Int) -> Int
