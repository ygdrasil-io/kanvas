package org.graphiks.math.matrix

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

class Matrix3x3F64Test {
    @Test
    fun inverseIdentityCanonicalizesSignedZero() {
        val inverseF32 = Matrix3x3F64(kxF64 = -0.0).invertToMatrix3x3F32OrNull()!!
        assertEquals(Matrix3x3F32.Identity, inverseF32)
        assertEquals(0, inverseF32.kx.toBits())
    }

    @Test
    fun inverseAffinePreservesExactPublicValues() {
        assertEquals(Matrix3x3F32(sx = .5f, kx = -.25f, tx = -3f, sy = .25f, ty = 1f),
            Matrix3x3F64(sxF64 = 2.0, kxF64 = 2.0, txF64 = 4.0, syF64 = 4.0, tyF64 = -4.0)
                .invertToMatrix3x3F32OrNull())
    }

    @Test
    fun inversePerspectivePreservesExactPublicValues() {
        assertEquals(Matrix3x3F32(persp0 = -.25f, persp1 = .5f),
            Matrix3x3F64(persp0F64 = .25, persp1F64 = -.5).invertToMatrix3x3F32OrNull())
    }

    @Test
    fun inverseRefusesSingularNonFiniteAndNonRepresentableProjection() {
        assertNull(Matrix3x3F64(sxF64 = 0.0).invertToMatrix3x3F32OrNull())
        assertNull(Matrix3x3F64(kxF64 = Double.NaN).invertToMatrix3x3F32OrNull())
        assertNull(Matrix3x3F64(txF64 = Double.POSITIVE_INFINITY).invertToMatrix3x3F32OrNull())
        assertNull(Matrix3x3F64(sxF64 = 1e-100).invertToMatrix3x3F32OrNull())
    }

    @Test
    fun `classification distinguishes identity axis aligned general affine and perspective exactly`() {
        assertEquals(PathTransformClass.Identity, Matrix3x3F64().classifyPathTransform())
        assertEquals(
            PathTransformClass.AxisAlignedAffine,
            Matrix3x3F64(sxF64 = -2.0, syF64 = 3.0, txF64 = 5.0, tyF64 = -7.0).classifyPathTransform(),
        )
        assertEquals(
            PathTransformClass.GeneralAffine,
            Matrix3x3F64(kxF64 = 1e-12).classifyPathTransform(),
        )
        assertEquals(
            PathTransformClass.GeneralAffine,
            Matrix3x3F64(kyF64 = -1e-12).classifyPathTransform(),
        )
        assertEquals(
            PathTransformClass.Perspective,
            Matrix3x3F64(persp0F64 = 1e-15).classifyPathTransform(),
        )
        assertEquals(
            PathTransformClass.Perspective,
            Matrix3x3F64(persp2F64 = 1.0 + 1e-15).classifyPathTransform(),
        )
    }

    @Test
    fun `signed zero coefficients retain their identity classification`() {
        assertEquals(
            PathTransformClass.Identity,
            Matrix3x3F64(
                kxF64 = -0.0,
                txF64 = -0.0,
                kyF64 = -0.0,
                tyF64 = -0.0,
                persp0F64 = -0.0,
                persp1F64 = -0.0,
            ).classifyPathTransform(),
        )
    }

    @Test
    fun `conversion preserves every F32 coefficient exactly while canonicalizing signed zero`() {
        val matrixF32 = Matrix3x3F32(
            sx = -0.0f,
            kx = Float.fromBits(0x3eaaaaab),
            tx = Float.MIN_VALUE,
            ky = -Float.MIN_VALUE,
            sy = Float.fromBits(0x7f7fffff),
            ty = -Float.fromBits(0x7f7fffff),
            persp0 = Float.fromBits(0x00800000),
            persp1 = Float.fromBits(0x3f000001),
            persp2 = 1.0f,
        )

        val mapped = matrixF32.toMatrix3x3F64()

        assertEquals(0.0.toBits(), mapped.sxF64.toBits())
        assertEquals(exactF64(matrixF32.kx), mapped.kxF64)
        assertEquals(exactF64(matrixF32.tx), mapped.txF64)
        assertEquals(exactF64(matrixF32.ky), mapped.kyF64)
        assertEquals(exactF64(matrixF32.sy), mapped.syF64)
        assertEquals(exactF64(matrixF32.ty), mapped.tyF64)
        assertEquals(exactF64(matrixF32.persp0), mapped.persp0F64)
        assertEquals(exactF64(matrixF32.persp1), mapped.persp1F64)
        assertEquals(exactF64(matrixF32.persp2), mapped.persp2F64)
    }

    @Test
    fun `finite validation leaves non finite coefficients visible to the caller`() {
        assertTrue(Matrix3x3F64().isFinite())
        assertFalse(Matrix3x3F64(sxF64 = Double.NaN).isFinite())
        assertFalse(Matrix3x3F64(persp2F64 = Double.POSITIVE_INFINITY).isFinite())
    }

    private fun exactF64(valueF32: Float): Double = Float.fromBits(valueF32.toRawBits()).toDouble()
}
