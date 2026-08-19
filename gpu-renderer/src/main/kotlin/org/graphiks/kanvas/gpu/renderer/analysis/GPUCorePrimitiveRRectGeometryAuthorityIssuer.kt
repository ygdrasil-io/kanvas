package org.graphiks.kanvas.gpu.renderer.analysis

import org.graphiks.kanvas.gpu.renderer.commands.GPURRect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectNormalizationResult
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformType
import org.graphiks.kanvas.gpu.renderer.commands.matchesSource
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRRectRawFacts
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRRectTransformRawFacts
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRectTransformType

/** Analysis-owned result of attempting to sign one exact, total device RRect. */
internal sealed interface GPUCorePrimitiveRRectGeometryAuthorityIssue {
    data class Issued(
        val authority: GPUCorePrimitiveRRectGeometryAuthority,
    ) : GPUCorePrimitiveRRectGeometryAuthorityIssue

    data class Refused(
        val code: String,
    ) : GPUCorePrimitiveRRectGeometryAuthorityIssue {
        init {
            require(code.isNotBlank()) { "RRect geometry authority refusal code must not be blank" }
        }
    }
}

/** Seals one raw FillRRect, its single accepted normalization, and its analyzed transform. */
internal fun corePrimitiveRRectGeometryAuthority(
    source: GPURRect,
    accepted: GPURRectNormalizationResult.Accepted,
    transform: GPUTransformFacts,
): GPUCorePrimitiveRRectGeometryAuthorityIssue {
    if (!accepted.matchesSource(source)) {
        return GPUCorePrimitiveRRectGeometryAuthorityIssue.Refused(
            "invalid.core_primitive.rrect.normalization_provenance",
        )
    }
    val exactTransform = when (val result = transform.exactRRectTransformOrRefusal()) {
        is GPUCorePrimitiveRRectExactTransformResult.Accepted -> result.transform
        is GPUCorePrimitiveRRectExactTransformResult.Refused -> {
            return GPUCorePrimitiveRRectGeometryAuthorityIssue.Refused(result.code)
        }
    }
    if (exactTransform.hasSkew) {
        return GPUCorePrimitiveRRectGeometryAuthorityIssue.Refused(
            "unsupported.transform.rrect_affine_unproven",
        )
    }
    val device = accepted.rrect.sealedDeviceRRectFacts(exactTransform)
        ?: return GPUCorePrimitiveRRectGeometryAuthorityIssue.Refused(
            "invalid.core_primitive.rrect.device_geometry",
        )
    val authority = GPUCorePrimitiveRRectGeometryAuthority.issue(
        source = source.rawRRectFacts(),
        normalized = accepted.rrect.rawRRectFacts(),
        transform = transform.rawRRectTransformFacts(),
        device = device,
    ) ?: return GPUCorePrimitiveRRectGeometryAuthorityIssue.Refused(
        "invalid.core_primitive.rrect.device_geometry",
    )
    return GPUCorePrimitiveRRectGeometryAuthorityIssue.Issued(authority)
}

/** Verifies the raw RRect and transform against analysis without repeating normalization. */
fun GPUCorePrimitiveRRectGeometryAuthority.matchesCorePrimitiveRRectGeometry(
    rrect: GPURRect,
    transform: GPUTransformFacts,
): Boolean = GPUCorePrimitiveRRectGeometryAuthority.matchesRawSource(
    authority = this,
    source = rrect.rawRRectFacts(),
    transform = transform.rawRRectTransformFacts(),
)

private sealed interface GPUCorePrimitiveRRectExactTransformResult {
    data class Accepted(
        val transform: GPUCorePrimitiveRRectExactTransform,
    ) : GPUCorePrimitiveRRectExactTransformResult

    data class Refused(
        val code: String,
    ) : GPUCorePrimitiveRRectExactTransformResult
}

private data class GPUCorePrimitiveRRectExactTransform(
    val translateX: Float,
    val translateY: Float,
    val scaleX: Float,
    val scaleY: Float,
    val skewX: Float,
    val skewY: Float,
) {
    val hasSkew: Boolean get() = skewX != 0f || skewY != 0f

    fun hasFiniteNonZeroDeterminant(): Boolean {
        val determinant = scaleX * scaleY - skewX * skewY
        return determinant.isFinite() && determinant != 0f
    }
}

private fun GPUTransformFacts.exactRRectTransformOrRefusal(): GPUCorePrimitiveRRectExactTransformResult {
    val exact = GPUCorePrimitiveRRectExactTransform(
        translateX = translateX,
        translateY = translateY,
        scaleX = scaleX,
        scaleY = scaleY,
        skewX = skewX,
        skewY = skewY,
    )
    if (!translateX.isFinite() || !translateY.isFinite() ||
        !scaleX.isFinite() || !scaleY.isFinite() || !skewX.isFinite() || !skewY.isFinite()
    ) {
        return GPUCorePrimitiveRRectExactTransformResult.Refused(
            "unsupported.transform.non_finite",
        )
    }
    val positiveZeroBits = 0f.toRawBits()
    val positiveOneBits = 1f.toRawBits()
    val coherent = when (type) {
        GPUTransformType.Identity ->
            translateX.toRawBits() == positiveZeroBits &&
                translateY.toRawBits() == positiveZeroBits &&
                scaleX.toRawBits() == positiveOneBits &&
                scaleY.toRawBits() == positiveOneBits &&
                skewX.toRawBits() == positiveZeroBits &&
                skewY.toRawBits() == positiveZeroBits
        GPUTransformType.Translate ->
            scaleX.toRawBits() == positiveOneBits &&
                scaleY.toRawBits() == positiveOneBits &&
                skewX.toRawBits() == positiveZeroBits &&
                skewY.toRawBits() == positiveZeroBits
        GPUTransformType.Scale ->
            translateX.toRawBits() == positiveZeroBits &&
                translateY.toRawBits() == positiveZeroBits &&
                skewX.toRawBits() == positiveZeroBits &&
                skewY.toRawBits() == positiveZeroBits &&
                exact.hasFiniteNonZeroDeterminant()
        GPUTransformType.Affine -> exact.hasFiniteNonZeroDeterminant()
        GPUTransformType.Perspective -> return GPUCorePrimitiveRRectExactTransformResult.Refused(
            "unsupported.transform.perspective",
        )
        GPUTransformType.Singular -> return GPUCorePrimitiveRRectExactTransformResult.Refused(
            "unsupported.transform.singular",
        )
    }
    return if (coherent) {
        GPUCorePrimitiveRRectExactTransformResult.Accepted(exact)
    } else {
        GPUCorePrimitiveRRectExactTransformResult.Refused(
            "invalid.core_primitive.rrect.transform_facts",
        )
    }
}

private fun GPURRect.sealedDeviceRRectFacts(
    transform: GPUCorePrimitiveRRectExactTransform,
): GPUCorePrimitiveRRectRawFacts? {
    val bounds = listOf(rect.left, rect.top, rect.right, rect.bottom)
    val radii = listOf(
        topLeft.x,
        topLeft.y,
        topRight.x,
        topRight.y,
        bottomRight.x,
        bottomRight.y,
        bottomLeft.x,
        bottomLeft.y,
    )
    if (bounds.any { !it.isFinite() } || rect.left >= rect.right || rect.top >= rect.bottom ||
        radii.any { !it.isFinite() || it < 0f } || transform.hasSkew
    ) return null

    val firstX = transform.scaleX * rect.left + transform.translateX
    val firstY = transform.scaleY * rect.top + transform.translateY
    val secondX = transform.scaleX * rect.right + transform.translateX
    val secondY = transform.scaleY * rect.bottom + transform.translateY
    if (!firstX.isFinite() || !firstY.isFinite() || !secondX.isFinite() || !secondY.isFinite()) {
        return null
    }
    val deviceLeft = minOf(firstX, secondX)
    val deviceTop = minOf(firstY, secondY)
    val deviceRight = maxOf(firstX, secondX)
    val deviceBottom = maxOf(firstY, secondY)
    if (deviceLeft >= deviceRight || deviceTop >= deviceBottom) return null

    fun sourceCorner(deviceIsLeft: Boolean, deviceIsTop: Boolean): Int {
        val sourceIsLeft = if (transform.scaleX < 0f) !deviceIsLeft else deviceIsLeft
        val sourceIsTop = if (transform.scaleY < 0f) !deviceIsTop else deviceIsTop
        return when {
            sourceIsTop && sourceIsLeft -> 0
            sourceIsTop -> 1
            !sourceIsLeft -> 2
            else -> 3
        }
    }
    val scaleXAbs = kotlin.math.abs(transform.scaleX)
    val scaleYAbs = kotlin.math.abs(transform.scaleY)
    val deviceRadii = listOf(
        sourceCorner(true, true),
        sourceCorner(false, true),
        sourceCorner(false, false),
        sourceCorner(true, false),
    ).flatMap { corner ->
        listOf(
            radii[corner * 2] * scaleXAbs,
            radii[corner * 2 + 1] * scaleYAbs,
        )
    }
    if (deviceRadii.any { !it.isFinite() || it < 0f }) return null
    return GPUCorePrimitiveRRectRawFacts(
        leftBits = deviceLeft.toRawBits(),
        topBits = deviceTop.toRawBits(),
        rightBits = deviceRight.toRawBits(),
        bottomBits = deviceBottom.toRawBits(),
        topLeftXBits = deviceRadii[0].toRawBits(),
        topLeftYBits = deviceRadii[1].toRawBits(),
        topRightXBits = deviceRadii[2].toRawBits(),
        topRightYBits = deviceRadii[3].toRawBits(),
        bottomRightXBits = deviceRadii[4].toRawBits(),
        bottomRightYBits = deviceRadii[5].toRawBits(),
        bottomLeftXBits = deviceRadii[6].toRawBits(),
        bottomLeftYBits = deviceRadii[7].toRawBits(),
    )
}

private fun GPURRect.rawRRectFacts(): GPUCorePrimitiveRRectRawFacts =
    GPUCorePrimitiveRRectRawFacts(
        leftBits = rect.left.toRawBits(),
        topBits = rect.top.toRawBits(),
        rightBits = rect.right.toRawBits(),
        bottomBits = rect.bottom.toRawBits(),
        topLeftXBits = topLeft.x.toRawBits(),
        topLeftYBits = topLeft.y.toRawBits(),
        topRightXBits = topRight.x.toRawBits(),
        topRightYBits = topRight.y.toRawBits(),
        bottomRightXBits = bottomRight.x.toRawBits(),
        bottomRightYBits = bottomRight.y.toRawBits(),
        bottomLeftXBits = bottomLeft.x.toRawBits(),
        bottomLeftYBits = bottomLeft.y.toRawBits(),
    )

private fun GPUTransformFacts.rawRRectTransformFacts(): GPUCorePrimitiveRRectTransformRawFacts =
    GPUCorePrimitiveRRectTransformRawFacts(
        type = when (type) {
            GPUTransformType.Identity -> GPUCorePrimitiveRectTransformType.Identity
            GPUTransformType.Translate -> GPUCorePrimitiveRectTransformType.Translate
            GPUTransformType.Scale -> GPUCorePrimitiveRectTransformType.Scale
            GPUTransformType.Affine -> GPUCorePrimitiveRectTransformType.Affine
            GPUTransformType.Perspective -> GPUCorePrimitiveRectTransformType.Perspective
            GPUTransformType.Singular -> GPUCorePrimitiveRectTransformType.Singular
        },
        translateXBits = translateX.toRawBits(),
        translateYBits = translateY.toRawBits(),
        scaleXBits = scaleX.toRawBits(),
        scaleYBits = scaleY.toRawBits(),
        skewXBits = skewX.toRawBits(),
        skewYBits = skewY.toRawBits(),
    )
