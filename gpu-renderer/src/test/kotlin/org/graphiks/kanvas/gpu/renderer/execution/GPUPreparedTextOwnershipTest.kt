package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUPipelineLayout
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUSampler
import io.ygdrasil.webgpu.GPUShaderModule
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureView
import java.io.File
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.IdentityHashMap
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedTextAuthenticatedComposite
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedTextCompositeAdmissionToken
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedTextCompositionObserver
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedTextCompositeProgram
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedTextCompositeProgramResult
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedTextShaderComposer
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextNativeProgramHandoff
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class GPUPreparedTextOwnershipTest {
    @Test
    fun `composite admission and native handoff cannot be forged through the public model`() {
        val program = GPUPreparedTextCompositeProgram::class.java
        assertTrue(program.declaredConstructors.all { constructor ->
            Modifier.isPrivate(constructor.modifiers) || constructor.isSynthetic
        })
        assertTrue(program.declaredMethods.none { method ->
            method.name == "copy" || method.name.startsWith("copy$")
        })

        val tokenAuthority = GPUPreparedTextCompositeAdmissionToken::class.java
        assertTrue(tokenAuthority.isSealed)
        assertEquals(
            listOf("IssuedGPUPreparedTextCompositeAdmissionToken"),
            tokenAuthority.permittedSubclasses.map { issued -> issued.simpleName },
        )
        assertTrue(tokenAuthority.permittedSubclasses.all { issued ->
            !Modifier.isPublic(issued.modifiers)
        })

        val snapshotAuthority = GPUPreparedTextAuthenticatedComposite::class.java
        assertTrue(snapshotAuthority.isSealed)
        assertEquals(
            listOf("IssuedGPUPreparedTextAuthenticatedComposite"),
            snapshotAuthority.permittedSubclasses.map { issued -> issued.simpleName },
        )
        assertTrue(snapshotAuthority.permittedSubclasses.all { issued ->
            !Modifier.isPublic(issued.modifiers)
        })

        val nativeFactory = GPUPreparedTextNativeProgramHandoff.Companion::class.java
            .declaredMethods
            .single { method -> method.name.startsWith("fromAuthenticated") }
        assertEquals(
            listOf(GPUPreparedTextAuthenticatedComposite::class.java),
            nativeFactory.parameterTypes.toList(),
        )

        val admissionClass = Class.forName(
            "org.graphiks.kanvas.gpu.renderer.materials." +
                "GPUPreparedTextCompositeAdmission",
        )
        val fileFacadeClass = Class.forName(
            "org.graphiks.kanvas.gpu.renderer.materials." +
                "GPUPreparedTextShaderComposerKt",
        )
        val visibleEmitterClasses = listOf(
            program,
            GPUPreparedTextCompositeProgram.Companion::class.java,
            GPUPreparedTextShaderComposer::class.java,
            admissionClass,
            fileFacadeClass,
        )
        val rawIssuers = visibleEmitterClasses
            .flatMap { emitter -> emitter.declaredMethods.asList() }
            .filter { method ->
                (
                    method.returnType == GPUPreparedTextCompositeProgram::class.java ||
                        method.returnType == GPUPreparedTextCompositeProgramResult::class.java
                    ) &&
                    method.parameterTypes.count { type -> type == String::class.java } >= 4 &&
                    (Modifier.isPublic(method.modifiers) || method.isSynthetic)
            }
        val visibleProgramConstructors = program.declaredConstructors
            .filter { constructor ->
                Modifier.isPublic(constructor.modifiers) || constructor.isSynthetic
            }
        val visibleAdmissionConstructors = admissionClass.declaredConstructors
            .filter { constructor ->
                Modifier.isPublic(constructor.modifiers) || constructor.isSynthetic
            }
        val visibleAdmissionApis = GPUPreparedTextCompositeProgram.Companion::class.java
            .declaredMethods
            .filter { method ->
                (
                    method.returnType == GPUPreparedTextCompositeProgram::class.java ||
                        method.returnType == GPUPreparedTextCompositeProgramResult::class.java
                    ) &&
                    (Modifier.isPublic(method.modifiers) || method.isSynthetic)
            }

        assertEquals(
            emptyList(),
            rawIssuers.map { method ->
                "${method.declaringClass.simpleName}.${method.name}:${method.modifiers}"
            },
        )
        assertEquals(1, visibleProgramConstructors.size)
        assertEquals(
            listOf(
                admissionClass.name,
                "kotlin.jvm.internal.DefaultConstructorMarker",
            ),
            visibleProgramConstructors.single().parameterTypes.map(Class<*>::getName),
        )
        assertEquals(1, visibleAdmissionConstructors.size)
        assertEquals(
            listOf(
                GPUPreparedMaterialProgram::class.java,
                String::class.java,
                String::class.java,
                GPUFixedFunctionBlendState::class.java,
                GPUPreparedTextCompositionObserver::class.java,
            ),
            visibleAdmissionConstructors.single().parameterTypes.toList(),
        )
        assertEquals(1, visibleAdmissionApis.size)
        assertEquals(
            listOf(
                GPUPreparedMaterialProgram::class.java,
                String::class.java,
                String::class.java,
                GPUFixedFunctionBlendState::class.java,
                GPUPreparedTextCompositionObserver::class.java,
            ),
            visibleAdmissionApis.single().parameterTypes.toList(),
        )
    }

    @Test
    fun `pipeline acquisition authority is sealed and only privately issued`() {
        val authority = GPUWgpu4kPreparedTextPipelineAcquisition::class.java

        assertTrue(authority.isSealed)
        assertEquals(
            listOf("IssuedGPUWgpu4kPreparedTextPipelineAcquisition"),
            authority.permittedSubclasses.map { it.simpleName },
        )
        assertContains(
            File(
                "src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/" +
                    "GPUWgpu4kPreparedTextSessionCache.kt",
            ).readText(),
            "private class IssuedGPUWgpu4kPreparedTextPipelineAcquisition(",
        )
    }

    @TestFactory
    fun `setup failure closes every created native object exactly once`(): List<DynamicTest> =
        listOf(
            FailurePoint("shader module", "createShaderModule", 1, sampled = false),
            FailurePoint("draw bind-group layout", "createBindGroupLayout", 1, sampled = false),
            FailurePoint("material bind-group layout", "createBindGroupLayout", 2, sampled = false),
            FailurePoint("atlas bind-group layout", "createBindGroupLayout", 3, sampled = false),
            FailurePoint("pipeline layout", "createPipelineLayout", 1, sampled = false),
            FailurePoint("pipeline", "createRenderPipeline", 1, sampled = false),
            FailurePoint("R8 texture", "createTexture", 1, sampled = false),
            FailurePoint("R8 view", "createView", 1, sampled = false),
            FailurePoint("material texture", "createTexture", 2, sampled = true),
            FailurePoint("material view", "createView", 2, sampled = true),
            FailurePoint("instance buffer", "createBuffer", 1, sampled = false),
            FailurePoint("draw-uniform buffer", "createBuffer", 2, sampled = false),
            FailurePoint("material-uniform buffer", "createBuffer", 3, sampled = false),
            FailurePoint("bind group", "createBindGroup", 1, sampled = false),
        ).map { point ->
            DynamicTest.dynamicTest(point.label) {
                val fixture = preparedTextNativePreflightFixture(
                    materialProgram = if (point.sampled) {
                        sampledPreparedTextMaterialProgram("ownership:${point.label}")
                    } else {
                        GPUPreparedTextPreflightFixture.baselineMaterialProgram()
                    },
                )
                val native = OwnershipPreparedTextNative(point.operation, point.ordinal)
                val cache = GPUWgpu4kPreparedTextSessionCache(
                    native.device,
                    fixture.context.deviceGeneration,
                )

                val refused = assertIs<GPUPreparedRenderRunMaterialization.Refused>(
                    GPUWgpu4kPreparedTextRenderRunMaterializer(
                        native.device,
                        cache,
                    ).materializeAcceptedRun(
                        preparedTextTestRunPlan(fixture),
                        fixture.context.deviceGeneration,
                    ),
                )
                assertTrue(refused.code.startsWith("failed.prepared_text"))
                refused.retainedCloseOwner?.close()
                cache.close()

                assertTrue(native.created.all { handle ->
                    native.closeCounts[handle] == 1
                }, "${point.label}: ${native.closeCounts.values}")
            }
        }

    @Test
    fun `frame resources transfer once and a later frame never reuses page or buffers`() {
        val fixture = preparedTextNativePreflightFixture()
        val native = OwnershipPreparedTextNative()
        val cache = GPUWgpu4kPreparedTextSessionCache(
            native.device,
            fixture.context.deviceGeneration,
        )
        val materializer = GPUWgpu4kPreparedTextRenderRunMaterializer(native.device, cache)

        val first = assertIs<GPUPreparedRenderRunMaterialization.Ready>(
            materializer.materializeAcceptedRun(
                preparedTextTestRunPlan(fixture),
                fixture.context.deviceGeneration,
            ),
        )
        val firstTexture = first.scopeOperands
            .filterIsInstance<GPUPreparedNativeScopeOperand.TextureUpload>()
            .single().destination.texture
        val firstBuffers = first.uniformUploads.map { it.destination.buffer }
        assertTrue((listOf(firstTexture) + firstBuffers).all {
            native.closeCounts[it] == null
        })
        first.ownedResources.single().close()
        first.ownedResources.single().close()
        assertTrue((listOf(firstTexture) + firstBuffers).all {
            native.closeCounts[it] == 1
        })

        val second = assertIs<GPUPreparedRenderRunMaterialization.Ready>(
            materializer.materializeAcceptedRun(
                preparedTextTestRunPlan(fixture),
                fixture.context.deviceGeneration,
            ),
        )
        val secondTexture = second.scopeOperands
            .filterIsInstance<GPUPreparedNativeScopeOperand.TextureUpload>()
            .single().destination.texture
        val secondBuffers = second.uniformUploads.map { it.destination.buffer }
        assertNotSame(firstTexture, secondTexture)
        firstBuffers.zip(secondBuffers).forEach { (old, fresh) -> assertNotSame(old, fresh) }
        assertTrue((listOf(secondTexture) + secondBuffers).all {
            native.closeCounts[it] == null
        })

        second.ownedResources.single().close()
        cache.close()
        assertTrue(native.created.all { native.closeCounts[it] == 1 })
    }

    @Test
    fun `cache rejects stale generations and recreated generation owns a fresh pipeline`() {
        val fixture = preparedTextNativePreflightFixture()
        val native = OwnershipPreparedTextNative()
        val generation = fixture.context.deviceGeneration
        val program = preparedTextTestRunPlan(fixture).bindings.first().nativeProgram
        val firstCache = GPUWgpu4kPreparedTextSessionCache(native.device, generation)
        val first = assertIs<GPUPreparedTextCacheBatchAcquire.Ready>(
            firstCache.acquireBatch(listOf(program), generation),
        )
        val firstPipeline = first.pipelinesByKey.getValue(program.pipelineKey).pipeline

        val stale = assertIs<GPUPreparedTextCacheBatchAcquire.Refused>(
            firstCache.acquireBatch(
                listOf(program),
                GPUDeviceGenerationID(generation.value + 1L),
            ),
        )
        assertEquals("stale.prepared_text.device_generation", stale.code)
        firstCache.invalidateForDeviceLoss()
        firstCache.close()
        assertTrue(native.created.all { native.closeCounts[it] == 1 })

        val secondGeneration = GPUDeviceGenerationID(generation.value + 1L)
        val secondCache = GPUWgpu4kPreparedTextSessionCache(
            native.device,
            secondGeneration,
        )
        val second = assertIs<GPUPreparedTextCacheBatchAcquire.Ready>(
            secondCache.acquireBatch(listOf(program), secondGeneration),
        )
        assertNotSame(
            firstPipeline,
            second.pipelinesByKey.getValue(program.pipelineKey).pipeline,
        )
        secondCache.close()
        assertTrue(native.created.all { native.closeCounts[it] == 1 })
    }

    @Test
    fun `shared material sampler is closed once and a failed creation can retry`() {
        val fixtures = listOf("first", "second").map { suffix ->
            preparedTextNativePreflightFixture(
                materialProgram = sampledPreparedTextMaterialProgram("sampler-owner:$suffix"),
            )
        }
        val plans = fixtures.map(::preparedTextTestRunPlan)
        val generation = fixtures.first().context.deviceGeneration
        val program = plans.first().bindings.first().nativeProgram
        val resources = plans.flatMap { plan ->
            plan.bindings.flatMap { binding -> binding.materialSampledResourcePlans }
        }.distinctBy { resource -> resource.resourceKey }
        val native = OwnershipPreparedTextNative(
            failingOperation = "createSampler",
            failingOrdinal = 2,
        )
        val cache = GPUWgpu4kPreparedTextSessionCache(native.device, generation)

        assertIs<GPUPreparedTextCacheBatchAcquire.Refused>(
            cache.acquireBatch(
                programs = listOf(program),
                generation = generation,
                materialResourcesByPipelineKey = mapOf(program.pipelineKey to resources),
            ),
        )
        assertIs<GPUPreparedTextCacheBatchAcquire.Ready>(
            cache.acquireBatch(
                programs = listOf(program),
                generation = generation,
                materialResourcesByPipelineKey = mapOf(program.pipelineKey to resources),
            ),
        )
        cache.close()

        assertEquals(3, native.created.count { handle -> handle is GPUSampler })
        assertTrue(native.created.all { handle -> native.closeCounts[handle] == 1 })
    }
}

private data class FailurePoint(
    val label: String,
    val operation: String,
    val ordinal: Int,
    val sampled: Boolean,
)

private class OwnershipPreparedTextNative(
    private val failingOperation: String? = null,
    private val failingOrdinal: Int = 0,
) {
    val created = mutableListOf<AutoCloseable>()
    val closeCounts = IdentityHashMap<Any, Int>()
    private val invocationCounts = mutableMapOf<String, Int>()
    private var handleOrdinal = 0

    val device: GPUDevice = nativeHandle("device", track = false) { method, _ ->
        when (method) {
            "createShaderModule" -> create<GPUShaderModule>("shader", method)
            "createBindGroupLayout" -> create<GPUBindGroupLayout>("layout", method)
            "createPipelineLayout" -> create<GPUPipelineLayout>("pipeline-layout", method)
            "createRenderPipeline" -> create<GPURenderPipeline>("pipeline", method)
            "createSampler" -> create<GPUSampler>("sampler", method)
            "createBuffer" -> create<GPUBuffer>("buffer", method)
            "createBindGroup" -> create<GPUBindGroup>("bind-group", method)
            "createTexture" -> {
                failIfRequested(method)
                nativeHandle<GPUTexture>("texture") { textureMethod, _ ->
                    when (textureMethod) {
                        "createView" -> create<GPUTextureView>("texture-view", "createView")
                        else -> null
                    }
                }
            }
            else -> null
        }
    }

    private inline fun <reified T : AutoCloseable> create(
        label: String,
        operation: String,
    ): T {
        failIfRequested(operation)
        return nativeHandle(label)
    }

    private fun failIfRequested(operation: String) {
        val ordinal = invocationCounts.getOrDefault(operation, 0) + 1
        invocationCounts[operation] = ordinal
        if (operation == failingOperation && ordinal == failingOrdinal) {
            error("injected prepared-text failure at $operation#$ordinal")
        }
    }

    private inline fun <reified T> nativeHandle(
        label: String,
        track: Boolean = true,
        crossinline other: (String, Array<out Any?>?) -> Any? = { _, _ -> null },
    ): T {
        val exactLabel = "$label.${handleOrdinal++}"
        val proxy = Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java),
        ) { instance, method, args ->
            when (method.name) {
                "close" -> {
                    closeCounts[instance] = closeCounts.getOrDefault(instance, 0) + 1
                    Unit
                }
                "getLabel", "toString" -> exactLabel
                "setLabel" -> Unit
                "hashCode" -> System.identityHashCode(instance)
                "equals" -> instance === args?.singleOrNull()
                else -> other(method.name, args)
            }
        } as T
        if (track) created += proxy as AutoCloseable
        return proxy
    }
}
