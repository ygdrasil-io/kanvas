package org.graphiks.math

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State

@State(Scope.Benchmark)
public open class SkMathJvmBackendBenchmark {
    private val a = floatArrayOf(
        1.25f, -0.10f, 0.33f, 0.0f,
        0.20f, 0.75f, -0.25f, 0.0f,
        0.50f, -0.40f, 1.10f, 0.0f,
        13f, -7f, 3f, 1f,
    )
    private val b = floatArrayOf(
        0.90f, 0.25f, 0.10f, 0.001f,
        -0.20f, 1.10f, 0.15f, -0.002f,
        0.05f, -0.35f, 0.80f, 0.003f,
        5f, 7f, -2f, 1f,
    )
    private val out = FloatArray(16)

    @Benchmark
    public open fun scalarM44Concat(): Float {
        SkMathScalar.m44Concat(a, b, out)
        return out[0] + out[5] + out[10] + out[15]
    }

    @Benchmark
    public open fun simdM44Concat(): Float {
        SkMathSimdJvm.m44Concat(a, b, out)
        return out[0] + out[5] + out[10] + out[15]
    }

    @Benchmark
    public open fun scalarDot4(): Float =
        SkMathScalar.dot4(1f, 2f, 3f, 4f, -4f, 3f, -2f, 1f)

    @Benchmark
    public open fun simdDot4(): Float =
        SkMathSimdJvm.dot4(1f, 2f, 3f, 4f, -4f, 3f, -2f, 1f)
}
