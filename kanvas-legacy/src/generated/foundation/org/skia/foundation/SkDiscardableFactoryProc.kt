package org.skia.foundation

import kotlin.ULong
import undefined.SkDiscardableMemory

public typealias SkDiscardableFactoryProc = (ULong) -> SkDiscardableMemory?
