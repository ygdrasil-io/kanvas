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

        val method = vectorMethod
        if (method == null) {
            val reason = vectorLoadFailure ?: "Vector API implementation is unavailable"
            return scalar("Vector API unavailable: $reason")
        }

        return try {
            val lanes = method.invoke(null, width, height, packedSrcOverClear, dst) as Int
            CpuVectorKernelAttempt(
                usedVector = true,
                kernelId = VECTOR_KERNEL_ID,
                diagnostics = listOf("Vector API selected: $VECTOR_KERNEL_ID lanes=$lanes"),
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
