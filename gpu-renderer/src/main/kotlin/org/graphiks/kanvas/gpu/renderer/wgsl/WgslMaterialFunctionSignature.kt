package org.graphiks.kanvas.gpu.renderer.wgsl

import org.graphiks.wgsl.arena.Handle
import org.graphiks.wgsl.ir.Module
import org.graphiks.wgsl.ir.ScalarKind
import org.graphiks.wgsl.ir.Type
import org.graphiks.wgsl.ir.TypeInner
import org.graphiks.wgsl.ir.VectorSize

/** Proves the canonical composable material signature from lowered wgsl4k IR. */
internal fun Module.hasMaterialColorFunctionSignature(functionName: String): Boolean {
    val function = functions.filter { candidate -> candidate.name == functionName }
        .singleOrNull()
        ?: return false
    return function.parameters.size == 1 &&
        isFloatVector(function.parameters.single().type, VectorSize.Bi) &&
        function.returnType?.let { returnType ->
            isFloatVector(returnType, VectorSize.Quad)
        } == true
}

private fun Module.isFloatVector(
    typeHandle: Handle<Type>,
    expectedSize: VectorSize,
): Boolean {
    val vector = types[typeHandle].inner as? TypeInner.Vector ?: return false
    val scalar = types[vector.scalar].inner as? TypeInner.Scalar ?: return false
    return vector.size == expectedSize &&
        scalar.kind == ScalarKind.F32 &&
        scalar.width == Float.SIZE_BYTES
}
