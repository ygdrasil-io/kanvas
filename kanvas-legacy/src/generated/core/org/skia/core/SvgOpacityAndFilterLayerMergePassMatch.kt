package org.skia.core

import Pattern
import org.skia.utils.ClipRect
import undefined.Restore
import undefined.Save
import undefined.SaveLayer

public typealias SvgOpacityAndFilterLayerMergePassMatch = Pattern<Is<SaveLayer>, Is<Save>, Is<ClipRect>, Is<SaveLayer>, Is<Restore>, Is<Restore>, Is<Restore>>
