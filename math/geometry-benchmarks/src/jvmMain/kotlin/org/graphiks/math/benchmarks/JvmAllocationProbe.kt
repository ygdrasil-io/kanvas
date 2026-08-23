package org.graphiks.math.benchmarks

import com.sun.management.ThreadMXBean
import java.io.File
import java.lang.management.ManagementFactory
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.matrix.Matrix3x3F32

public object JvmAllocationProbe {
    @Volatile
    private var sink: Float = 0f

    @JvmStatic
    public fun main(args: Array<String>) {
        require(args.size == 1) { "Expected the allocation report output path" }
        val output = File(args.single())
        output.parentFile.mkdirs()

        val bean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
        if (bean == null || !bean.isThreadAllocatedMemorySupported) {
            writeUnsupported(output)
            return
        }
        if (!enableAllocationTracking(bean)) {
            writeUnsupported(output)
            return
        }

        repeat(WARMUPS) {
            runTransforms(MEASURED_ITERATIONS)
        }

        val threadId = Thread.currentThread().threadId()
        val allocatedBefore = bean.getThreadAllocatedBytes(threadId)
        val measuredSink = runTransforms(MEASURED_ITERATIONS)
        val allocatedAfter = bean.getThreadAllocatedBytes(threadId)
        sink = measuredSink

        if (allocatedBefore < 0L || allocatedAfter < allocatedBefore) {
            writeUnsupported(output)
            return
        }

        val allocatedBytes = allocatedAfter - allocatedBefore
        output.writeText(
            """
            {
              "representation": "$REPRESENTATION",
              "operation": "$OPERATION",
              "iterations": $MEASURED_ITERATIONS,
              "allocatedBytes": $allocatedBytes,
              "allocatedBytesPerOperation": ${allocatedBytes.toDouble() / MEASURED_ITERATIONS}
            }
            """.trimIndent() + "\n",
        )
    }

    private fun enableAllocationTracking(bean: ThreadMXBean): Boolean = try {
        if (!bean.isThreadAllocatedMemoryEnabled) {
            bean.isThreadAllocatedMemoryEnabled = true
        }
        bean.isThreadAllocatedMemoryEnabled
    } catch (_: SecurityException) {
        false
    } catch (_: UnsupportedOperationException) {
        false
    }

    private fun runTransforms(iterations: Int): Float {
        var sum = 0f
        repeat(iterations) { index ->
            val transformed = MATRIX.transform(INPUTS[index and 1])
            sum += transformed.x + transformed.y
        }
        return sum
    }

    private fun writeUnsupported(output: File) {
        output.writeText(
            """
            {
              "representation": "$REPRESENTATION",
              "operation": "$OPERATION",
              "iterations": $MEASURED_ITERATIONS,
              "status": "unsupported"
            }
            """.trimIndent() + "\n",
        )
    }

    private const val REPRESENTATION: String = "FINAL_CLASS"
    private const val OPERATION: String = "Matrix3x3F32.transform(Point2F32)"
    private const val WARMUPS: Int = 5
    private const val MEASURED_ITERATIONS: Int = 1_000_000
    private val MATRIX: Matrix3x3F32 = Matrix3x3F32(
        sx = 1.25f,
        kx = -0.375f,
        tx = 7.5f,
        ky = 0.625f,
        sy = 0.875f,
        ty = -3.25f,
    )
    private val INPUTS: Array<Point2F32> = arrayOf(
        Point2F32(12.5f, -4.75f),
        Point2F32(-8.25f, 6.5f),
    )
}
