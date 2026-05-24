package org.graphiks.math

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

@State(Scope.Benchmark)
public open class SkMatrixBenchmark {
    private lateinit var src: Array<SkPoint>
    private lateinit var dst: Array<SkPoint>
    private lateinit var affine: SkMatrix
    private lateinit var perspective: SkMatrix

    @Setup
    public fun setup() {
        src = Array(256) { i ->
            SkPoint((i % 31).toFloat() - 15f, (i * 17 % 43).toFloat() - 21f)
        }
        dst = Array(src.size) { SkPoint(0f, 0f) }
        affine = SkMatrix.MakeScale(1.25f, -0.75f).preTranslate(13f, -7f)
        perspective = SkMatrix.MakeAll(
            1.25f, 0.20f, 13f,
            -0.10f, 0.75f, -7f,
            0.001f, -0.002f, 1f,
        )
    }

    @Benchmark
    public open fun mapAffinePoints(): Float {
        affine.mapPoints(dst, src, src.size)
        return checksum(dst)
    }

    @Benchmark
    public open fun mapPerspectivePoints(): Float {
        perspective.mapPoints(dst, src, src.size)
        return checksum(dst)
    }

    private fun checksum(points: Array<SkPoint>): Float {
        var sum = 0f
        for (point in points) {
            sum += point.fX * 0.25f + point.fY * 0.75f
        }
        return sum
    }
}

@State(Scope.Benchmark)
public open class SkM44Benchmark {
    private lateinit var a: SkM44
    private lateinit var b: SkM44
    private lateinit var v: SkV4

    @Setup
    public fun setup() {
        a = SkM44.rotate(SkV3(0.2f, 0.7f, 0.1f), 0.35f)
            .preTranslate(4f, -3f, 2f)
            .preScale(1.2f, 0.8f, -1.1f)
        b = SkM44.perspective(0.05f, 100f, 0.7f)
            .postTranslate(3f, 5f, -2f)
        v = SkV4(3f, -5f, 7f, 1f)
    }

    @Benchmark
    public open fun concat(): Float {
        val out = SkM44(a, b)
        return out.rc(0, 0) + out.rc(1, 1) + out.rc(2, 2) + out.rc(3, 3)
    }

    @Benchmark
    public open fun mapVector(): Float {
        val out = a * v
        return out.x + out.y * 0.5f + out.z * 0.25f + out.w * 0.125f
    }
}

@State(Scope.Benchmark)
public open class SkVectorBenchmark {
    private lateinit var v3s: Array<SkV3>
    private lateinit var v4s: Array<SkV4>

    @Setup
    public fun setup() {
        v3s = Array(256) { i ->
            SkV3(i * 0.5f + 1f, i * -0.25f + 2f, i * 0.125f - 3f)
        }
        v4s = Array(256) { i ->
            SkV4(i * 0.5f + 1f, i * -0.25f + 2f, i * 0.125f - 3f, 1f)
        }
    }

    @Benchmark
    public open fun dotNormalizeV3(): Float {
        var sum = 0f
        for (i in 0 until v3s.lastIndex) {
            sum += v3s[i].dot(v3s[i + 1])
            sum += v3s[i].normalize().length()
        }
        return sum
    }

    @Benchmark
    public open fun dotNormalizeV4(): Float {
        var sum = 0f
        for (i in 0 until v4s.lastIndex) {
            sum += v4s[i].dot(v4s[i + 1])
            sum += v4s[i].normalize().length()
        }
        return sum
    }
}

@State(Scope.Benchmark)
public open class SkColorMatrixBenchmark {
    private lateinit var a: SkColorMatrix
    private lateinit var b: SkColorMatrix

    @Setup
    public fun setup() {
        a = SkColorMatrix().also {
            it.setSaturation(0.45f)
            it.postTranslate(0.1f, -0.2f, 0.3f, 0f)
        }
        b = SkColorMatrix().also {
            it.setRGB2YUV()
        }
    }

    @Benchmark
    public open fun concat(): Float {
        val out = a * b
        val values = out.toFloatArray()
        var sum = 0f
        for (value in values) {
            sum += value
        }
        return sum
    }
}
