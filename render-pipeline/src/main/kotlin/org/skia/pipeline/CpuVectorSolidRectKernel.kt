package org.skia.pipeline

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

data class CpuVectorKernelAttempt(
    val usedVector: Boolean,
    val kernelId: String,
    val diagnostics: List<String>,
)

object CpuVectorSolidRectKernel {
    const val ENABLED_PROPERTY: String = "kanvas.cpu.vector.enabled"
    const val ACCEPTED_GATE_PROPERTY: String = "kanvas.cpu.vector.solidRect.acceptedGate"
    const val ACCEPTED_GATE_ID: String = "solid_src_over_clear/java25/reference-v1"

    private const val VECTOR_CLASS = "org.skia.pipeline.jvm.CpuVectorSolidRectKernelJvm"
    private const val VECTOR_KERNEL_ID = "java25.vector.solid_src_over_clear"
    private const val SCALAR_KERNEL_ID = "cpu.scalar.solid_src_over_clear"

    private val vectorMethod: Method? by lazy { loadVectorMethod() }
    private val vectorLoadFailure: String? by lazy { loadVectorFailure() }

    fun tryFillSrcOverClear(
        width: Int,
        height: Int,
        packedSrcOverClear: Int,
        dst: IntArray,
        mode: CpuVectorMode,
    ): CpuVectorKernelAttempt {
        if (mode == CpuVectorMode.Disabled) {
            return scalar("Vector API disabled by execution options")
        }
        val property = System.getProperty(ENABLED_PROPERTY)
        if (property.equals("false", ignoreCase = true)) {
            return scalar("Vector API disabled by system property $ENABLED_PROPERTY=false")
        }

        if (mode == CpuVectorMode.Auto && System.getProperty(ACCEPTED_GATE_PROPERTY) != ACCEPTED_GATE_ID) {
            return scalar(
                "Vector API rejected by benchmark gate: " +
                    "decision=rejected speedup=0.863 requiredSpeedup=1.500 gate=$ACCEPTED_GATE_ID",
            )
        }

        val method = vectorMethod
        if (method == null) {
            val reason = vectorLoadFailure ?: "Vector API implementation is unavailable"
            return scalar("Vector API unavailable: $reason")
        }

        return try {
            val lanes = method.invoke(null, width, height, packedSrcOverClear, dst) as Int
            val selection = if (mode == CpuVectorMode.Force) "force-selected" else "selected"
            CpuVectorKernelAttempt(
                usedVector = true,
                kernelId = VECTOR_KERNEL_ID,
                diagnostics = listOf("Vector API $selection: $VECTOR_KERNEL_ID lanes=$lanes gate=$ACCEPTED_GATE_ID"),
            )
        } catch (e: InvocationTargetException) {
            scalar("Vector API invocation failed: ${e.targetException::class.simpleName}: ${e.targetException.message}")
        } catch (e: ReflectiveOperationException) {
            scalar("Vector API invocation failed: ${e::class.simpleName}: ${e.message}")
        } catch (e: LinkageError) {
            scalar("Vector API linkage failed: ${e::class.simpleName}: ${e.message}")
        }
    }

    private fun scalar(reason: String): CpuVectorKernelAttempt =
        CpuVectorKernelAttempt(usedVector = false, kernelId = SCALAR_KERNEL_ID, diagnostics = listOf(reason))

    private fun loadVectorMethod(): Method? {
        return try {
            Class.forName(VECTOR_CLASS).getDeclaredMethod(
                "fillSrcOverClear",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                IntArray::class.java,
            )
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: LinkageError) {
            null
        }
    }

    private fun loadVectorFailure(): String? {
        return try {
            Class.forName(VECTOR_CLASS)
            null
        } catch (e: ReflectiveOperationException) {
            "${e::class.simpleName}: ${e.message}"
        } catch (e: LinkageError) {
            "${e::class.simpleName}: ${e.message}"
        }
    }
}
