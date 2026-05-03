package org.skia.core

import kotlin.ULong
import undefined.SkDiscardableMemory

public typealias SkDiscardableFactoryProc = (ULong) -> SkDiscardableMemory?
