package org.graphiks.kanvas.canvas

import org.graphiks.kanvas.geometry.Path as GeometryPath
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.types.RRect as TypesRRect
import org.graphiks.kanvas.types.Rect as TypesRect

sealed interface ClipStack {
    data object WideOpen : ClipStack
    data class DeviceRect(val rect: TypesRect) : ClipStack
    data class Complex(val ops: List<ClipStackOp>) : ClipStack
}

sealed interface ClipStackOp {
    data class Rect(val rect: TypesRect, val op: ClipOp) : ClipStackOp
    data class RRect(val rrect: TypesRRect, val op: ClipOp) : ClipStackOp
    data class Path(val path: GeometryPath, val op: ClipOp) : ClipStackOp
}
