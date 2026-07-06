package org.graphiks.kanvas.gpu.renderer.compute

import org.graphiks.kanvas.gpu.renderer.pipelines.GPUComputePipelineKey
import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.KanvasWGSLValidator
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLBindingLayout
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLComputeModule
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLFragment
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLModuleHash
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLParserState
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLReflectionResult
import org.graphiks.wgsl.parser.Lowerer
import org.graphiks.wgsl.parser.parseWgslResult
import org.graphiks.kanvas.gpu.renderer.wgsl.WgslReflectionReport
import org.graphiks.kanvas.gpu.renderer.wgsl.reflectWgslModule
import java.security.MessageDigest
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

data class GPUComputeTessellationPlan(
    val wgslModule: WGSLComputeModule,
    val dispatchGrid: DispatchGrid,
    val pipelineKey: GPUComputePipelineKey,
    val vertexCount: Int,
    val expectedWorkgroupSize: Int,
) {
    companion object {
        const val MAX_VERTEX_BUDGET = 1_000_000
        private const val WGSL_RESOURCE_PATH = "wgsl/compute_tessellation.wgsl"

        fun forPathFill(vertexCount: Int, workgroupSize: Int): GPUComputeTessellationPlan =
            create(vertexCount, workgroupSize, "fill")

        fun forPathStroke(vertexCount: Int, workgroupSize: Int): GPUComputeTessellationPlan =
            create(vertexCount, workgroupSize, "stroke")

        private fun create(vertexCount: Int, workgroupSize: Int, mode: String): GPUComputeTessellationPlan {
            require(vertexCount > 0) { "vertexCount must be positive, got $vertexCount" }
            require(workgroupSize > 0) { "workgroupSize must be positive, got $workgroupSize" }
            val workgroups = ceil(vertexCount.toDouble() / workgroupSize.toDouble()).toInt()
            val wgslModule = loadAndBuildModule(workgroupSize)
            val pipelineKey = GPUComputePipelineKey(
                "compute-tessellation-${mode}-${vertexCount}-${workgroupSize}-${wgslModule.moduleHash.value}",
            )
            return GPUComputeTessellationPlan(
                wgslModule = wgslModule,
                dispatchGrid = DispatchGrid(x = workgroups),
                pipelineKey = pipelineKey,
                vertexCount = vertexCount,
                expectedWorkgroupSize = workgroupSize,
            )
        }

        private fun loadAndBuildModule(workgroupSize: Int): WGSLComputeModule {
            val source = GPUComputeTessellationPlan::class.java.classLoader.getResource(WGSL_RESOURCE_PATH)!!.readText()
            val moduleHash = WGSLModuleHash("compute-tessellation:${sha256(source)}")
            val validator = KanvasWGSLValidator()
            val parsed = validator.parse(source)

            val bindings = listOf(
                WGSLBindingLayout(
                    group = 0,
                    binding = 0,
                    visibility = setOf("compute"),
                    resourceKind = "storage-buffer",
                    access = "read",
                    layoutRole = "vertices",
                    diagnosticLabel = "compute_vertices",
                ),
                WGSLBindingLayout(
                    group = 0,
                    binding = 1,
                    visibility = setOf("compute"),
                    resourceKind = "storage-buffer",
                    access = "read_write",
                    layoutRole = "outputs",
                    diagnosticLabel = "compute_outputs",
                ),
            )

            val parserState = if (parsed.syntaxErrors.isEmpty()) {
                WGSLParserState(
                    status = "parser-backed",
                    toolName = "external-wgsl-parser",
                    message = "validated by parser-backed WGSL validation",
                )
            } else {
                WGSLParserState(
                    status = "parse-failed",
                    toolName = "external-wgsl-parser",
                    message = parsed.syntaxErrors.joinToString("; "),
                )
            }

            val reflection = WGSLReflectionResult.Accepted(
                moduleHash = moduleHash,
                bindings = bindings,
                uniforms = emptyList(),
                storage = emptyList(),
                diagnostics = emptyList(),
                parserState = parserState,
                reflectionSource = if (parserState.parserBacked) "parser-backed" else "fixture-declared",
            )

            val fragment = WGSLFragment(
                fragmentId = "compute-tessellation",
                stage = "compute",
                sourceHash = sha256(source),
                entryPoints = listOf("compute_main"),
                bindingLayouts = bindings,
                uniformLayouts = emptyList(),
                storageLayouts = emptyList(),
                requiredFeatures = emptyList(),
                diagnosticLabel = "compute_tessellation",
            )

            return WGSLComputeModule(
                moduleHash = moduleHash,
                entryPoint = "compute_main",
                fragments = listOf(fragment),
                bindings = bindings,
                uniformLayouts = emptyList(),
                storageLayouts = emptyList(),
                reflection = reflection,
                workgroupPolicy = "fixed-size:${workgroupSize}",
                resourceAccessPolicy = "read-vertices,read-write-outputs",
            )
        }

        private fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
            return digest.take(16)
        }

        fun validateWgsl(source: String): List<String> {
            return try {
                val validator = KanvasWGSLValidator()
                val parsed = validator.parse(source)
                parsed.syntaxErrors
            } catch (_: NoClassDefFoundError) {
                emptyList()
            } catch (_: ClassNotFoundException) {
                emptyList()
            } catch (_: LinkageError) {
                listOf("WGSL parser linkage error: validation unavailable")
            } catch (e: Throwable) {
                listOf("WGSL parser validation error: ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        fun reflectComputeModule(source: String): WgslReflectionReport? {
            return try {
                val parsed = parseWgslResult(source)
                if (parsed.errors.isNotEmpty()) {
                    return null
                }
                val lowered = Lowerer().lower(parsed.translationUnit)
                lowered.reflectWgslModule(sourceId = "compute-tessellation")
            } catch (_: NoClassDefFoundError) {
                null
            } catch (_: ClassNotFoundException) {
                null
            } catch (_: LinkageError) {
                null
            }
        }
    }

    fun analyze(capabilities: GPUCapabilities): GPUComputeTessellationRoute {
        if (!capabilities.computeSupported) {
            return GPUComputeTessellationRoute.CapabilityUnavailable(
                "unsupported.tessellation.compute_capability_absent",
            )
        }
        if (vertexCount > MAX_VERTEX_BUDGET) {
            return GPUComputeTessellationRoute.Refused(
                RefuseDiagnostic(
                    code = "unsupported.tessellation.vertex_budget_exceeded",
                    message = "vertex count $vertexCount exceeds max budget $MAX_VERTEX_BUDGET",
                    stage = "tessellation.analysis",
                    terminal = true,
                ),
            )
        }
        val syntaxErrors = validateWgsl(wgslSource())
        if (syntaxErrors.isNotEmpty()) {
            return GPUComputeTessellationRoute.Refused(
                RefuseDiagnostic(
                    code = "unsupported.tessellation.wgsl_validation",
                    message = "WGSL validation failed: ${syntaxErrors.joinToString("; ")}",
                    stage = "tessellation.wgsl",
                    terminal = true,
                ),
            )
        }
        val reflection = reflectComputeModule(wgslSource())
        if (reflection != null) {
            val reflectionRefusal = validateReflection(reflection)
            if (reflectionRefusal != null) {
                return GPUComputeTessellationRoute.Refused(reflectionRefusal)
            }
        }
        return GPUComputeTessellationRoute.Accepted(
            GPUComputeTessellationArtifact(
                planKey = pipelineKey.value,
                vertexCount = vertexCount,
            ),
        )
    }

    private fun validateReflection(reflection: WgslReflectionReport): RefuseDiagnostic? {
        val entryPoint = reflection.entryPoints.firstOrNull()
        if (entryPoint == null || entryPoint.stage != "compute") {
            return RefuseDiagnostic(
                code = "unsupported.tessellation.entry_point_not_compute",
                message = "expected a compute entry point, found stage=${entryPoint?.stage ?: "none"}",
                stage = "tessellation.reflection",
                terminal = true,
            )
        }
        val storageBindings = reflection.bindings.filter { it.resourceKind == "storageBuffer" }
        if (storageBindings.isEmpty()) {
            return RefuseDiagnostic(
                code = "unsupported.tessellation.missing_storage_bindings",
                message = "compute tessellation requires storage buffer bindings, found none",
                stage = "tessellation.reflection",
                terminal = true,
            )
        }
        val reflectedWorkgroupSize = entryPoint.workgroupSize
        if (reflectedWorkgroupSize != null) {
            val firstDimension = reflectedWorkgroupSize.firstOrNull() ?: 1
            val unresolvedOverrideWorkgroupSize = reflectedWorkgroupSize.all { it == 1 }
            if (!unresolvedOverrideWorkgroupSize && firstDimension != expectedWorkgroupSize) {
                return RefuseDiagnostic(
                    code = "unsupported.tessellation.workgroup_size_mismatch",
                    message = "WGSL workgroup size $firstDimension does not match plan workgroup size $expectedWorkgroupSize",
                    stage = "tessellation.reflection",
                    terminal = true,
                )
            }
        }
        return null
    }

    fun wgslSource(): String =
        GPUComputeTessellationPlan::class.java.classLoader.getResource(WGSL_RESOURCE_PATH)!!.readText()

    object CpuOracle {
        data class VertexOutput(val position: Pair<Float, Float>, val coverage: Float)

        fun computeTessellation(vertices: List<Pair<Float, Float>>): List<VertexOutput> =
            vertices.map { pos -> VertexOutput(pos, 1.0f) }

        fun circleVertices(n: Int, radius: Float = 1.0f, cx: Float = 0f, cy: Float = 0f): List<Pair<Float, Float>> =
            (0 until n).map { i ->
                val angle = 2.0 * Math.PI * i / n
                Pair(cx + radius * cos(angle).toFloat(), cy + radius * sin(angle).toFloat())
            }

        fun squareVertices(): List<Pair<Float, Float>> = listOf(
            Pair(-1f, -1f), Pair(1f, -1f), Pair(1f, 1f), Pair(-1f, 1f),
        )
    }
}
