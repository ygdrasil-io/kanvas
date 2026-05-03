package org.skia.modules

import VisitorInfo
import kotlin.Int
import kotlin.Unit

public typealias ParagraphVisitor = (Int, VisitorInfo?) -> Unit
