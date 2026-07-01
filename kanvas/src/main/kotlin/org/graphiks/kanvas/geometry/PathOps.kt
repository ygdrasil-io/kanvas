package org.graphiks.kanvas.geometry

import org.graphiks.kanvas.types.Rect

enum class PathOp { DIFFERENCE, INTERSECT, UNION, XOR, REVERSE_DIFFERENCE }

object PathOps {
    fun op(path1: Path, path2: Path, op: PathOp): Path? {
        val r1 = Rect.EMPTY
        val r2 = Rect.EMPTY
        if (path1.isRect(r1) && path2.isRect(r2)) {
            val reg1 = Region(r1)
            val reg2 = Region(r2)
            val regionOp = when (op) {
                PathOp.DIFFERENCE -> RegionOp.DIFFERENCE
                PathOp.INTERSECT -> RegionOp.INTERSECT
                PathOp.UNION -> RegionOp.UNION
                PathOp.XOR -> RegionOp.XOR
                PathOp.REVERSE_DIFFERENCE -> RegionOp.REVERSE_DIFFERENCE
            }
            reg1.op(reg2, regionOp)
            if (reg1.isEmpty) return Path()
            if (reg1.isRect) {
                val b = reg1.bounds
                return Path().addRect(Rect.fromLTRB(b.left, b.top, b.right, b.bottom))
            }
            val result = Path()
            for (rect in reg1.rects) {
                result.addRect(rect)
            }
            return result
        }
        return null
    }

    fun simplify(path: Path): Path? {
        val result = Path()
        result.fillType = path.fillType
        result.apply {
            val v = path.verbs()
            val p = path.points()
            var pi = 0
            for (verb in v) {
                when (verb) {
                    PathVerb.MOVE -> { moveTo(p[pi].x, p[pi].y); pi++ }
                    PathVerb.LINE -> { lineTo(p[pi].x, p[pi].y); pi++ }
                    PathVerb.QUAD -> { quadTo(p[pi].x, p[pi].y, p[pi + 1].x, p[pi + 1].y); pi += 2 }
                    PathVerb.CUBIC -> { cubicTo(p[pi].x, p[pi].y, p[pi + 1].x, p[pi + 1].y, p[pi + 2].x, p[pi + 2].y); pi += 3 }
                    PathVerb.ARC_TO -> {
                        arcTo(p[pi].x, p[pi].y, p[pi + 1].x, p[pi + 1].y > 0f, p[pi + 2].x > 0f, p[pi + 3].x, p[pi + 3].y)
                        pi += 4
                    }
                    PathVerb.CLOSE -> close()
                }
            }
        }
        return result
    }

    fun asWinding(path: Path): Path? {
        val result = Path()
        result.fillType = FillType.WINDING
        val v = path.verbs()
        val p = path.points()
        var pi = 0
        for (verb in v) {
            when (verb) {
                PathVerb.MOVE -> { result.moveTo(p[pi].x, p[pi].y); pi++ }
                PathVerb.LINE -> { result.lineTo(p[pi].x, p[pi].y); pi++ }
                PathVerb.QUAD -> { result.quadTo(p[pi].x, p[pi].y, p[pi + 1].x, p[pi + 1].y); pi += 2 }
                PathVerb.CUBIC -> { result.cubicTo(p[pi].x, p[pi].y, p[pi + 1].x, p[pi + 1].y, p[pi + 2].x, p[pi + 2].y); pi += 3 }
                PathVerb.ARC_TO -> {
                    result.arcTo(p[pi].x, p[pi].y, p[pi + 1].x, p[pi + 1].y > 0f, p[pi + 2].x > 0f, p[pi + 3].x, p[pi + 3].y)
                    pi += 4
                }
                PathVerb.CLOSE -> result.close()
            }
        }
        return result
    }
}
