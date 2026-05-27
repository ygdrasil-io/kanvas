package org.skia.pipeline

import java.lang.management.ManagementFactory
import java.util.Locale
import kotlin.system.measureNanoTime

fun main() {
    val width = System.getProperty("kanvas.cpu.vector.benchmark.width")?.toIntOrNull() ?: 2048
    val height = System.getProperty("kanvas.cpu.vector.benchmark.height")?.toIntOrNull() ?: 2048
    val warmups = System.getProperty("kanvas.cpu.vector.benchmark.warmups")?.toIntOrNull() ?: 5
    val iterations = System.getProperty("kanvas.cpu.vector.benchmark.iterations")?.toIntOrNull() ?: 20
    val allocationIterations = System.getProperty("kanvas.cpu.vector.benchmark.allocationIterations")?.toIntOrNull()
        ?: iterations
    val ir = KanvasPipelineIR.demoSolidRectIr(Rgba(0.25f, 0.5f, 0.9f, 1f))

    repeat(warmups) {
        CpuScalarPipelineExecutor.execute(ir, width, height, CpuPipelineExecutionOptions(CpuVectorMode.Disabled))
        CpuScalarPipelineExecutor.execute(ir, width, height, CpuPipelineExecutionOptions(CpuVectorMode.Force))
    }

    val scalar = measure(iterations) {
        CpuScalarPipelineExecutor.execute(ir, width, height, CpuPipelineExecutionOptions(CpuVectorMode.Disabled))
    }
    val vectorResult = CpuScalarPipelineExecutor.execute(ir, width, height, CpuPipelineExecutionOptions(CpuVectorMode.Force))
    val vector = measure(iterations) {
        CpuScalarPipelineExecutor.execute(ir, width, height, CpuPipelineExecutionOptions(CpuVectorMode.Force))
    }
    val scalarAllocation = measureAllocation(allocationIterations) {
        CpuScalarPipelineExecutor.execute(ir, width, height, CpuPipelineExecutionOptions(CpuVectorMode.Disabled))
    }
    val vectorAllocation = measureAllocation(allocationIterations) {
        CpuScalarPipelineExecutor.execute(ir, width, height, CpuPipelineExecutionOptions(CpuVectorMode.Force))
    }
    val scalarResult = CpuScalarPipelineExecutor.execute(ir, width, height, CpuPipelineExecutionOptions(CpuVectorMode.Disabled))
    val speedup = scalar.medianNanos.toDouble() / vector.medianNanos.toDouble()
    val accepted = vectorResult is CpuExecutionResult.Success &&
        vectorResult.kernelId == "java25.vector.solid_src_over_clear" &&
        speedup >= 1.5

    check(scalarResult is CpuExecutionResult.Success)
    check(vectorResult is CpuExecutionResult.Success)
    check(scalarResult.pixels.argb8888.contentEquals(vectorResult.pixels.argb8888)) {
        "Vector pilot output diverged from scalar output"
    }

    println("GRA-28 Java 25 Vector API pilot benchmark")
    println("machine=${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}")
    println("jdk=${System.getProperty("java.runtime.version")} vendor=${System.getProperty("java.vendor")}")
    println("image=${width}x$height warmups=$warmups iterations=$iterations")
    println("scalarKernel=${scalarResult.kernelId} medianMs=${scalar.medianMillis()} minMs=${scalar.minMillis()}")
    println("vectorKernel=${vectorResult.kernelId} medianMs=${vector.medianMillis()} minMs=${vector.minMillis()}")
    println("vectorDiagnostics=${vectorResult.diagnostics.joinToString("; ")}")
    println("speedup=${format(speedup)}")
    println("scalarGcCollections=${scalar.gcCollections} scalarGcTimeMillis=${scalar.gcTimeMillis}")
    println("vectorGcCollections=${vector.gcCollections} vectorGcTimeMillis=${vector.gcTimeMillis}")
    println("allocationMetric=threadAllocatedBytesPerExecute")
    println("allocationTargetBPerOp=0.0")
    println("allocationIterations=$allocationIterations")
    println("scalarAllocBytesPerOp=${scalarAllocation.bytesPerOpString()} units=B/op supported=${scalarAllocation.supported}")
    println("vectorAllocBytesPerOp=${vectorAllocation.bytesPerOpString()} units=B/op supported=${vectorAllocation.supported}")
    println("allocationException=CpuScalarPipelineExecutor.execute allocates the destination IntArray/PixelBuffer per benchmark operation; promoted hot-loop target remains 0.0 B/op inside the fill kernel.")
    println("decision=${if (accepted) "accepted" else "rejected"}")
}

private data class Measurement(
    val medianNanos: Long,
    val minNanos: Long,
    val gcCollections: Long,
    val gcTimeMillis: Long,
) {
    fun medianMillis(): String = millis(medianNanos)
    fun minMillis(): String = millis(minNanos)

    private fun millis(nanos: Long): String = format(nanos / 1_000_000.0)
}

private fun format(value: Double): String = String.format(Locale.US, "%.3f", value)

private fun measure(iterations: Int, block: () -> Unit): Measurement {
    val beforeGc = gcSnapshot()
    val samples = LongArray(iterations)
    repeat(iterations) { index ->
        samples[index] = measureNanoTime(block)
    }
    val afterGc = gcSnapshot()
    samples.sort()
    return Measurement(
        medianNanos = samples[samples.size / 2],
        minNanos = samples.first(),
        gcCollections = afterGc.collections - beforeGc.collections,
        gcTimeMillis = afterGc.timeMillis - beforeGc.timeMillis,
    )
}

private data class AllocationMeasurement(
    val bytesPerOp: Double?,
    val supported: Boolean,
) {
    fun bytesPerOpString(): String = bytesPerOp?.let(::format) ?: "unavailable"
}

private fun measureAllocation(iterations: Int, block: () -> Unit): AllocationMeasurement {
    val bean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
        ?: return AllocationMeasurement(bytesPerOp = null, supported = false)
    if (!bean.isThreadAllocatedMemorySupported) {
        return AllocationMeasurement(bytesPerOp = null, supported = false)
    }
    val wasEnabled = bean.isThreadAllocatedMemoryEnabled
    if (!wasEnabled) bean.isThreadAllocatedMemoryEnabled = true
    return try {
        val threadId = Thread.currentThread().threadId()
        val before = bean.getThreadAllocatedBytes(threadId)
        repeat(iterations) { block() }
        val after = bean.getThreadAllocatedBytes(threadId)
        if (before < 0L || after < before) {
            AllocationMeasurement(bytesPerOp = null, supported = false)
        } else {
            AllocationMeasurement(bytesPerOp = (after - before).toDouble() / iterations.toDouble(), supported = true)
        }
    } finally {
        if (!wasEnabled) bean.isThreadAllocatedMemoryEnabled = false
    }
}

private data class GcSnapshot(val collections: Long, val timeMillis: Long)

private fun gcSnapshot(): GcSnapshot {
    val beans = ManagementFactory.getGarbageCollectorMXBeans()
    return GcSnapshot(
        collections = beans.sumOf { it.collectionCount.coerceAtLeast(0L) },
        timeMillis = beans.sumOf { it.collectionTime.coerceAtLeast(0L) },
    )
}
