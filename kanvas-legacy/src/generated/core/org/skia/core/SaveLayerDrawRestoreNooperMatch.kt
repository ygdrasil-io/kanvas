package org.skia.core

import Pattern
import undefined.Restore
import undefined.SaveLayer

public typealias SaveLayerDrawRestoreNooperMatch = Pattern<Is<SaveLayer>, IsSingleDraw, Is<Restore>>
