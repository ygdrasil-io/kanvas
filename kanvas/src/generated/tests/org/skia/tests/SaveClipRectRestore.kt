package org.skia.tests

import Pattern
import org.skia.core.Is
import org.skia.utils.ClipRect
import undefined.Restore
import undefined.Save

public typealias SaveClipRectRestore = Pattern<Is<Save>, Is<ClipRect>, Is<Restore>>
