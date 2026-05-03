package org.skia.core

import kotlin.ULong
import undefined.SkDiscardableMemory

public typealias SkResourceCacheDiscardableFactory = (ULong) -> SkDiscardableMemory?
