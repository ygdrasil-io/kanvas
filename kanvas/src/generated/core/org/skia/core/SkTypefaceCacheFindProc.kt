package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkTypeface

public typealias SkTypefaceCacheFindProc = (SkTypeface?, Int) -> Boolean
