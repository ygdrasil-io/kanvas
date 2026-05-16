package org.skia.core

import Pattern
import undefined.NoOp
import undefined.Restore
import undefined.Save

public typealias SaveOnlyDrawsRestoreNooperMatch = Pattern<Is<Save>, Greedy<Or<Is<NoOp>, IsDraw>>, Is<Restore>>
