package org.skia.core

import Pattern
import undefined.Restore
import undefined.Save
import undefined.SaveLayer

public typealias SaveNoDrawsRestoreNooperMatch = Pattern<Is<Save>, Greedy<Not<Or<Is<Save>, Is<SaveLayer>, Is<Restore>, IsDraw>>>, Is<Restore>>
