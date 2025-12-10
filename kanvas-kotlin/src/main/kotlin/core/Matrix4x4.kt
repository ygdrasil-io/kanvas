package com.kanvas.core

/**
 * 4x4 transformation matrix for 2D/3D graphics
 * Inspired by Skia's SkM44
 * Represents a 4x4 matrix for advanced coordinate transformations
 */
data class Matrix4x4(
    // Column-major order (like SkM44)
    var m00: Float = 1f, var m10: Float = 0f, var m20: Float = 0f, var m30: Float = 0f,
    var m01: Float = 0f, var m11: Float = 1f, var m21: Float = 0f, var m31: Float = 0f,
    var m02: Float = 0f, var m12: Float = 0f, var m22: Float = 1f, var m32: Float = 0f,
    var m03: Float = 0f, var m13: Float = 0f, var m23: Float = 0f, var m33: Float = 1f
) {
    
    companion object {
        /**
         * Create an identity matrix
         */
        fun identity(): Matrix4x4 {
            return Matrix4x4(
                m00 = 1f, m10 = 0f, m20 = 0f, m30 = 0f,
                m01 = 0f, m11 = 1f, m21 = 0f, m31 = 0f,
                m02 = 0f, m12 = 0f, m22 = 1f, m32 = 0f,
                m03 = 0f, m13 = 0f, m23 = 0f, m33 = 1f
            )
        }
        
        /**
         * Create a translation matrix
         */
        fun translate(dx: Float, dy: Float, dz: Float = 0f): Matrix4x4 {
            return Matrix4x4(
                m00 = 1f, m10 = 0f, m20 = 0f, m30 = dx,
                m01 = 0f, m11 = 1f, m21 = 0f, m31 = dy,
                m02 = 0f, m12 = 0f, m22 = 1f, m32 = dz,
                m03 = 0f, m13 = 0f, m23 = 0f, m33 = 1f
            )
        }
        
        /**
         * Create a scale matrix
         */
        fun scale(sx: Float, sy: Float, sz: Float = 1f): Matrix4x4 {
            return Matrix4x4(
                m00 = sx, m10 = 0f, m20 = 0f, m30 = 0f,
                m01 = 0f, m11 = sy, m21 = 0f, m31 = 0f,
                m02 = 0f, m12 = 0f, m22 = sz, m32 = 0f,
                m03 = 0f, m13 = 0f, m23 = 0f, m33 = 1f
            )
        }
        
        /**
         * Create a rotation matrix around Z axis (2D rotation)
         */
        fun rotateZ(degrees: Float): Matrix4x4 {
            val radians = Math.toRadians(degrees.toDouble()).toFloat()
            val cos = kotlin.math.cos(radians)
            val sin = kotlin.math.sin(radians)
            return Matrix4x4(
                m00 = cos, m10 = -sin, m20 = 0f, m30 = 0f,
                m01 = sin, m11 = cos, m21 = 0f, m31 = 0f,
                m02 = 0f, m12 = 0f, m22 = 1f, m32 = 0f,
                m03 = 0f, m13 = 0f, m23 = 0f, m33 = 1f
            )
        }
        
        /**
         * Convert from 3x3 Matrix to 4x4 Matrix
         */
        fun fromMatrix3x3(matrix: Matrix): Matrix4x4 {
            return Matrix4x4(
                m00 = matrix.scaleX, m10 = matrix.skewY, m20 = 0f, m30 = matrix.transX,
                m01 = matrix.skewX, m11 = matrix.scaleY, m21 = 0f, m31 = matrix.transY,
                m02 = 0f, m12 = 0f, m22 = 1f, m32 = 0f,
                m03 = matrix.persp0, m13 = matrix.persp1, m23 = 0f, m33 = matrix.persp2
            )
        }
    }
    
    /**
     * Multiply this matrix by another matrix (this * other)
     */
    fun multiply(other: Matrix4x4): Matrix4x4 {
        return Matrix4x4(
            // First column
            m00 * other.m00 + m10 * other.m01 + m20 * other.m02 + m30 * other.m03,
            m00 * other.m10 + m10 * other.m11 + m20 * other.m12 + m30 * other.m13,
            m00 * other.m20 + m10 * other.m21 + m20 * other.m22 + m30 * other.m23,
            m00 * other.m30 + m10 * other.m31 + m20 * other.m32 + m30 * other.m33,
            
            // Second column
            m01 * other.m00 + m11 * other.m01 + m21 * other.m02 + m31 * other.m03,
            m01 * other.m10 + m11 * other.m11 + m21 * other.m12 + m31 * other.m13,
            m01 * other.m20 + m11 * other.m21 + m21 * other.m22 + m31 * other.m23,
            m01 * other.m30 + m11 * other.m31 + m21 * other.m32 + m31 * other.m33,
            
            // Third column
            m02 * other.m00 + m12 * other.m01 + m22 * other.m02 + m32 * other.m03,
            m02 * other.m10 + m12 * other.m11 + m22 * other.m12 + m32 * other.m13,
            m02 * other.m20 + m12 * other.m21 + m22 * other.m22 + m32 * other.m23,
            m02 * other.m30 + m12 * other.m31 + m22 * other.m32 + m32 * other.m33,
            
            // Fourth column
            m03 * other.m00 + m13 * other.m01 + m23 * other.m02 + m33 * other.m03,
            m03 * other.m10 + m13 * other.m11 + m23 * other.m12 + m33 * other.m13,
            m03 * other.m20 + m13 * other.m21 + m23 * other.m22 + m33 * other.m23,
            m03 * other.m30 + m13 * other.m31 + m23 * other.m32 + m33 * other.m33
        )
    }
    
    /**
     * Convert to 3x3 Matrix (losing Z and perspective info)
     */
    fun toMatrix3x3(): Matrix {
        return Matrix(
            scaleX = m00, skewX = m01, transX = m30,
            skewY = m10, scaleY = m11, transY = m31,
            persp0 = m03, persp1 = m13, persp2 = m33
        )
    }
    
    /**
     * Get the 3x3 portion of this 4x4 matrix
     */
    fun asM33(): Matrix {
        return toMatrix3x3()
    }
    
    /**
     * Check if this matrix is identity
     */
    fun isIdentity(): Boolean {
        return m00 == 1f && m10 == 0f && m20 == 0f && m30 == 0f &&
               m01 == 0f && m11 == 1f && m21 == 0f && m31 == 0f &&
               m02 == 0f && m12 == 0f && m22 == 1f && m32 == 0f &&
               m03 == 0f && m13 == 0f && m23 == 0f && m33 == 1f
    }
    
    /**
     * Check if this matrix is invertible
     */
    fun isInvertible(): Boolean {
        // Simple check - could be more sophisticated
        val det = m00 * (m11 * m22 * m33 + m12 * m23 * m31 + m13 * m21 * m32 -
                        m11 * m23 * m32 - m12 * m21 * m33 - m13 * m22 * m31) -
                m01 * (m10 * m22 * m33 + m12 * m23 * m30 + m13 * m20 * m32 -
                        m10 * m23 * m32 - m12 * m20 * m33 - m13 * m22 * m30) +
                m02 * (m10 * m21 * m33 + m11 * m23 * m30 + m13 * m20 * m31 -
                        m10 * m23 * m31 - m11 * m20 * m33 - m13 * m21 * m30) -
                m03 * (m10 * m21 * m32 + m11 * m22 * m30 + m12 * m20 * m31 -
                        m10 * m22 * m31 - m11 * m20 * m32 - m12 * m21 * m30)
        return det != 0f
    }
    
    /**
     * Invert this matrix
     */
    fun invert(): Matrix4x4? {
        if (!isInvertible()) return null
        
        // Implementation of matrix inversion would go here
        // This is a complex operation, so for now we'll return null
        // In a real implementation, this would calculate the inverse matrix
        return null
    }
    
    /**
     * Transform a point (x, y) using this matrix
     */
    fun mapPoint(x: Float, y: Float): Point {
        val w = m03 * x + m13 * y + m23 * 0f + m33 * 1f
        if (w == 0f) return Point(x, y)
        
        val invW = 1f / w
        return Point(
            (m00 * x + m10 * y + m20 * 0f + m30 * 1f) * invW,
            (m01 * x + m11 * y + m21 * 0f + m31 * 1f) * invW
        )
    }
    
    /**
     * Transform a point (x, y, z) using this matrix
     */
    fun mapPoint3D(x: Float, y: Float, z: Float): Point3D {
        val w = m03 * x + m13 * y + m23 * z + m33 * 1f
        if (w == 0f) return Point3D(x, y, z)
        
        val invW = 1f / w
        return Point3D(
            (m00 * x + m10 * y + m20 * z + m30 * 1f) * invW,
            (m01 * x + m11 * y + m21 * z + m31 * 1f) * invW,
            (m02 * x + m12 * y + m22 * z + m32 * 1f) * invW
        )
    }
    
    override fun toString(): String {
        return "Matrix4x4([${m00}, ${m10}, ${m20}, ${m30}], " +
                "[${m01}, ${m11}, ${m21}, ${m31}], " +
                "[${m02}, ${m12}, ${m22}, ${m32}], " +
                "[${m03}, ${m13}, ${m23}, ${m33}])"
    }
}

/**
 * 3D Point for advanced transformations
 */
data class Point3D(val x: Float, val y: Float, val z: Float)