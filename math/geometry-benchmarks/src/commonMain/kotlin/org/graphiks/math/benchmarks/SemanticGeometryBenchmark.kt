package org.graphiks.math.benchmarks

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State
import org.graphiks.math.geometry.MutablePoint2F32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.vector.MutableVector2F32
import org.graphiks.math.vector.Vector2F32

@State(Scope.Benchmark)
public class SemanticGeometryBenchmark {
    @Param("FINAL_CLASS")
    public var representation: String = "FINAL_CLASS"

    private val matrix: Matrix3x3F32 = Matrix3x3F32(
        sx = 1.25f,
        kx = -0.375f,
        tx = 7.5f,
        ky = 0.625f,
        sy = 0.875f,
        ty = -3.25f,
    )
    private val point: Point2F32 = Point2F32(12.5f, -4.75f)
    private val vector: Vector2F32 = Vector2F32(-3.5f, 9.25f)
    private val pointSources: Array<Point2F32> = Array(BATCH_SIZE) { index ->
        Point2F32(index * 0.25f - 64f, index * -0.125f + 32f)
    }
    private val pointDestinations: Array<MutablePoint2F32> = Array(BATCH_SIZE) {
        MutablePoint2F32(0f, 0f)
    }
    private val mutableVector: MutableVector2F32 = MutableVector2F32(0f, 0f)

    @Benchmark
    public fun transformSinglePoint(): Float {
        val transformed = matrix.transform(point)
        return transformed.x + transformed.y
    }

    @Benchmark
    public fun transformSingleVector(): Float {
        val transformed = matrix.transform(vector)
        return transformed.x + transformed.y
    }

    @Benchmark
    public fun transformPointBatch(): Float {
        matrix.transformPoints(pointSources, pointDestinations)
        var sum = 0f
        for (destination in pointDestinations) {
            sum += destination.x
        }
        return sum
    }

    @Benchmark
    public fun mutableVectorAccumulation(): Float {
        mutableVector.x = 0.5f
        mutableVector.y = -0.25f
        repeat(BATCH_SIZE) {
            mutableVector.add(vector)
        }
        return mutableVector.x + mutableVector.y
    }

    private companion object {
        private const val BATCH_SIZE: Int = 1_024
    }
}
