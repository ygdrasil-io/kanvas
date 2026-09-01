package org.graphiks.kanvas.geometry

import org.graphiks.math.geometry.PathBooleanOp
import org.graphiks.math.geometry.PathOpsF32

enum class PathOp { DIFFERENCE, INTERSECT, UNION, XOR, REVERSE_DIFFERENCE }

/** Compatibility facade that keeps the nullable Kanvas result contract. */
object PathOps {
    fun op(path1: Path, path2: Path, op: PathOp): Path? = runCatching {
        PathOpsF32.op(path1.toPathF32(), path2.toPathF32(), op.toPathBooleanOp()).toCompatibilityPath()
    }.getOrNull()

    fun simplify(path: Path): Path? = runCatching {
        PathOpsF32.simplify(path.toPathF32()).toCompatibilityPath()
    }.getOrNull()

    fun asWinding(path: Path): Path? = runCatching {
        PathOpsF32.asWinding(path.toPathF32()).toCompatibilityPath()
    }.getOrNull()
}

private fun PathOp.toPathBooleanOp(): PathBooleanOp = when (this) {
    PathOp.DIFFERENCE -> PathBooleanOp.DIFFERENCE
    PathOp.INTERSECT -> PathBooleanOp.INTERSECT
    PathOp.UNION -> PathBooleanOp.UNION
    PathOp.XOR -> PathBooleanOp.XOR
    PathOp.REVERSE_DIFFERENCE -> PathBooleanOp.REVERSE_DIFFERENCE
}
