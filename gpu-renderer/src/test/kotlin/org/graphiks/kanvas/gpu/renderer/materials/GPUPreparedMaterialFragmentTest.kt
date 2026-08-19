package org.graphiks.kanvas.gpu.renderer.materials

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURuntimeEffectUniformValue
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialColorContract
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialCoordinateContract
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialFragment
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialFragmentAdmission
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialProgramAdmission
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialSampledBinding
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialUniformBinding
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.KanvasPreparedRuntimeEffectResolver
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.SimpleRTDescriptor
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.preparedRuntimeEffectBindingContractHash
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.preparedRuntimeEffectModuleContractHash
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.preparedRuntimeEffectRouteContractHash
import org.graphiks.kanvas.gpu.renderer.wgsl.WgslBindingReflection
import org.graphiks.kanvas.gpu.renderer.wgsl.validation.KanvasWGSLReflectionProvider
import org.graphiks.kanvas.gpu.renderer.wgsl.validation.KanvasWGSLValidator

class GPUPreparedMaterialFragmentTest {
    private val context = GPUMaterialLoweringContext(
        capabilityClass = "webgpu-test",
        targetFormatClass = "rgba8unorm",
        dictionaryVersion = "material-dictionary:prepared-material:v1",
        runtimeEffectResolver = KanvasPreparedRuntimeEffectResolver(),
    )

    @Test
    fun `every accepted prepared material exposes one premultiplied composable fragment`() {
        acceptedDescriptors().forEach { descriptor ->
            val ready = compile(descriptor, paintAlpha = 0.5f)

            assertEquals(
                GPUPreparedMaterialColorContract.LinearPremultipliedRgba,
                ready.composableFragment.colorContract,
                descriptor.kind.toString(),
            )
            assertEquals(
                GPUPreparedMaterialCoordinateContract.LocalPosition2D,
                ready.composableFragment.coordinateContract,
                descriptor.kind.toString(),
            )
            assertEquals(
                "kanvas_evaluate_material",
                ready.composableFragment.evaluationFunction,
                descriptor.kind.toString(),
            )
            val fragmentSource = ready.composableFragment.declarationsWgsl +
                "\n\n" +
                ready.composableFragment.evaluationFunctionWgsl
            val parsed = KanvasWGSLValidator().parse(fragmentSource)
            assertTrue(
                parsed.syntaxErrors.isEmpty(),
                "${descriptor.kind}: ${parsed.syntaxErrors.joinToString()}",
            )
        }
    }

    @Test
    fun `uniform values and paint alpha do not alter fragment ABI`() {
        val first = compile(solid(r = 0.25f), paintAlpha = 0.5f)
        val second = compile(solid(r = 0.75f), paintAlpha = 0.25f)

        assertNotEquals(first.materialKey, second.materialKey)
        assertEquals(
            first.composableFragment.fragmentHash,
            second.composableFragment.fragmentHash,
        )
        assertEquals(
            first.composableFragment.abiHash,
            second.composableFragment.abiHash,
        )
    }

    @Test
    fun `texture content changes material identity but not fragment identity or ABI`() {
        val first = compile(image(byteArrayOf(1, 2, 3, 4)), paintAlpha = 1f)
        val second = compile(image(byteArrayOf(1, 2, 3, 5)), paintAlpha = 1f)

        assertNotEquals(first.materialKey, second.materialKey)
        assertEquals(
            first.composableFragment.fragmentHash,
            second.composableFragment.fragmentHash,
        )
        assertEquals(
            first.composableFragment.abiHash,
            second.composableFragment.abiHash,
        )
    }

    @Test
    fun `sampled resource topology changes the fragment ABI and uses canonical bindings`() {
        val unsampled = compile(solid(), paintAlpha = 1f).composableFragment
        val sampled = compile(image(byteArrayOf(1, 2, 3, 4)), paintAlpha = 1f)
            .composableFragment

        assertNotEquals(unsampled.abiHash, sampled.abiHash)
        assertEquals(emptyList(), unsampled.sampledBindings)
        assertEquals(
            listOf(
                GPUPreparedMaterialSampledBinding(
                    resourceIndex = 0,
                    textureBinding = 1,
                    samplerBinding = 2,
                ),
            ),
            sampled.sampledBindings,
        )
        assertEquals(1, sampled.uniformBinding?.group)
        assertEquals(0, sampled.uniformBinding?.binding)

        val parsed = KanvasWGSLValidator().parse(
            sampled.declarationsWgsl + "\n\n" + sampled.evaluationFunctionWgsl,
        )
        val report = requireNotNull(KanvasWGSLReflectionProvider().reflect(parsed).report)
        assertEquals(
            listOf(
                Triple(1, 0, "uniformBuffer"),
                Triple(1, 1, "sampledTexture"),
                Triple(1, 2, "sampler"),
            ),
            report.bindings.map { Triple(it.group, it.binding, it.resourceKind) },
        )
    }

    @Test
    fun `admitted compiler inputs determine fragment code ABI and topology identities`() {
        val baseline = compile(solid(r = 0.25f), paintAlpha = 1f).composableFragment
        val sameShapeDifferentUniform =
            compile(solid(r = 0.75f), paintAlpha = 1f).composableFragment
        val differentCodeAndAbi =
            compile(linearGradient(), paintAlpha = 1f).composableFragment
        val sampled =
            compile(image(byteArrayOf(1, 2, 3, 4)), paintAlpha = 1f).composableFragment

        assertEquals(baseline.fragmentHash, sameShapeDifferentUniform.fragmentHash)
        assertEquals(baseline.abiHash, sameShapeDifferentUniform.abiHash)
        assertNotEquals(baseline.fragmentHash, differentCodeAndAbi.fragmentHash)
        assertNotEquals(baseline.abiHash, differentCodeAndAbi.abiHash)
        assertEquals(baseline.sampledBindings.size, differentCodeAndAbi.sampledBindings.size)
        assertNotEquals(baseline.abiHash, sampled.abiHash)
        assertNotEquals(baseline.sampledBindings, sampled.sampledBindings)
    }

    @Test
    fun `admitted fragment and program hashes are exact and deterministic`() {
        val first = compile(solid(), paintAlpha = 0.5f)
        val second = compile(solid(), paintAlpha = 0.5f)
        val fragment = first.composableFragment

        assertEquals(
            sha256Hex(
                (
                    fragment.declarationsWgsl +
                        "\n\n" +
                        fragment.evaluationFunctionWgsl
                    ).encodeToByteArray(),
            ),
            fragment.fragmentHash,
        )
        assertEquals(first.materialKey, second.materialKey)
        assertEquals(fragment.fragmentHash, second.composableFragment.fragmentHash)
        assertEquals(fragment.abiHash, second.composableFragment.abiHash)
        assertEquals(first.abiHash, second.abiHash)
    }

    @Test
    fun `composable binding gate rejects missing extra and duplicate bindings`() {
        val uniform = GPUPreparedMaterialUniformBinding(minBindingSizeBytes = 16)
        val sampled = listOf(
            GPUPreparedMaterialSampledBinding(
                resourceIndex = 0,
                textureBinding = 1,
                samplerBinding = 2,
            ),
        )
        val reflected = listOf(
            reflectedBinding(0, "uniformBuffer", minBindingSize = 16),
            reflectedBinding(
                binding = 1,
                resourceKind = "sampledTexture",
                sampleType = "float",
                viewDimension = "2d",
            ),
            reflectedBinding(2, "sampler"),
        )

        assertEquals(null, composableBindingMismatch(uniform, sampled, reflected))
        assertTrue(composableBindingMismatch(uniform, sampled, reflected.dropLast(1)) != null)
        assertTrue(
            composableBindingMismatch(
                uniform,
                sampled,
                reflected + reflectedBinding(3, "sampler"),
            ) != null,
        )
        assertTrue(
            composableBindingMismatch(
                uniform,
                sampled,
                reflected + reflected.last(),
            ) != null,
        )
    }

    @Test
    fun `straight and premultiplied sources select their declared normalization`() {
        val straight = compile(registeredRuntimeEffect(), paintAlpha = 1f).composableFragment
        val premultiplied = compile(linearGradient(), paintAlpha = 1f).composableFragment

        assertTrue(
            "source.rgb * source.a" in straight.evaluationFunctionWgsl,
            straight.evaluationFunctionWgsl,
        )
        assertTrue(
            "return source;" in premultiplied.evaluationFunctionWgsl,
            premultiplied.evaluationFunctionWgsl,
        )
    }

    @Test
    fun `runtime source contract propagates through material fragment and final identities`() {
        val descriptor = SimpleRTDescriptor.createDescriptor()
        val straightProgram =
            assertIs<GPUPreparedRuntimeEffectResolution.Ready>(
                KanvasPreparedRuntimeEffectResolver().resolve(
                    descriptor.id.value,
                    descriptor.version.value,
                ),
            ).program
        val premultipliedContract =
            GPUPreparedRuntimeEffectSourceColorContract.LinearPremultipliedRgba
        val premultipliedDescriptor = descriptor.copy(
            sourceColorContract = premultipliedContract,
        )
        val premultipliedProgram = straightProgram.copy(
            sourceColorContract = premultipliedContract,
            moduleHash = preparedRuntimeEffectModuleContractHash(
                wgslModuleHash = descriptor.wgslPlan.moduleHash,
                sourceColorContract = premultipliedContract,
            ),
            bindingPlanHash = preparedRuntimeEffectBindingContractHash(
                descriptorBindingPlanHash = descriptor.resources.bindingPlanHash,
                sourceColorContract = premultipliedContract,
            ),
            routeContractHash = preparedRuntimeEffectRouteContractHash(
                descriptor = premultipliedDescriptor,
                sourceColorContract = premultipliedContract,
            ),
        )
        fun compileWith(program: GPUPreparedRuntimeEffectProgram): GPUPreparedMaterialProgram {
            val resolver = GPUPreparedRuntimeEffectResolver { _, _ ->
                GPUPreparedRuntimeEffectResolution.Ready(program)
            }
            return assertIs<GPUPreparedMaterialProgramResult.Ready>(
                GPUPreparedMaterialProgramCompiler.compile(
                    registeredRuntimeEffect(),
                    1f,
                    context.copy(runtimeEffectResolver = resolver),
                ),
            ).program
        }

        val straight = compileWith(straightProgram)
        val premultiplied = compileWith(premultipliedProgram)

        assertNotEquals(straight.materialKey, premultiplied.materialKey)
        assertNotEquals(
            straight.composableFragment.fragmentHash,
            premultiplied.composableFragment.fragmentHash,
        )
        assertNotEquals(straight.abiHash, premultiplied.abiHash)
        assertEquals(
            straight.composableFragment.abiHash,
            premultiplied.composableFragment.abiHash,
        )
    }

    @Test
    fun `admitted fragment exposes an immutable sampled binding snapshot`() {
        val fragment =
            compile(image(byteArrayOf(1, 2, 3, 4)), paintAlpha = 1f).composableFragment

        assertEquals(1, fragment.sampledBindings.size)
        assertTrue(
            runCatching {
                @Suppress("UNCHECKED_CAST")
                (fragment.sampledBindings as MutableList<GPUPreparedMaterialSampledBinding>).clear()
            }.isFailure,
        )
    }

    @Test
    fun `program exposes no JVM boundary that can reattach a caller fragment and identity`() {
        val baseline = compile(solid(), paintAlpha = 1f)
        val replacement = compile(
            linearGradient(),
            paintAlpha = 1f,
        ).composableFragment
        assertEquals(
            baseline.composableFragment.sampledBindings.size,
            replacement.sampledBindings.size,
        )
        assertNotEquals(baseline.composableFragment.fragmentHash, replacement.fragmentHash)
        assertNotEquals(baseline.composableFragment.abiHash, replacement.abiHash)

        val callableConstructors = GPUPreparedMaterialProgram::class.java.declaredConstructors
            .filterNot { constructor -> constructor.isSynthetic }
            .filter { constructor ->
                Modifier.isPublic(constructor.modifiers) &&
                    GPUPreparedMaterialFragment::class.java in constructor.parameterTypes
            }
        val callableIssuers = GPUPreparedMaterialProgram::class.java.declaredClasses
            .flatMap { nested -> nested.declaredMethods.asList() }
            .filterNot { method -> method.isSynthetic }
            .filter { method ->
                Modifier.isPublic(method.modifiers) &&
                    method.returnType == GPUPreparedMaterialProgram::class.java &&
                    GPUPreparedMaterialFragment::class.java in method.parameterTypes
            }

        assertTrue(callableConstructors.isEmpty(), callableConstructors.joinToString())
        assertTrue(callableIssuers.isEmpty(), callableIssuers.joinToString())
    }

    @Test
    fun `prepared material constructors are private and every JVM issuer is synthetic`() {
        val authorityTypes = setOf(
            GPUPreparedMaterialProgram::class.java,
            GPUPreparedMaterialFragment::class.java,
            GPUPreparedMaterialProgramAdmission::class.java,
            GPUPreparedMaterialFragmentAdmission::class.java,
        )
        val callableConstructors = authorityTypes
            .flatMap { type -> type.declaredConstructors.asList() }
            .filterNot { constructor -> constructor.isSynthetic }
        assertTrue(callableConstructors.isNotEmpty())
        assertTrue(
            callableConstructors.all { constructor ->
                Modifier.isPrivate(constructor.modifiers)
            },
            callableConstructors.joinToString(),
        )

        val callableIssuers = authorityTypes
            .flatMap { type -> type.declaredClasses.asList() }
            .flatMap { nested -> nested.declaredMethods.asList() }
            .filter { method ->
                Modifier.isPublic(method.modifiers) &&
                    method.returnType in authorityTypes
            }
        assertTrue(callableIssuers.isNotEmpty())
        assertTrue(
            callableIssuers.all { method -> method.isSynthetic },
            callableIssuers.joinToString(),
        )
        assertTrue(
            runCatching {
                Class.forName(
                    "org.graphiks.kanvas.gpu.renderer.materials.contracts." +
                        "GPUPreparedMaterialFragmentIdentity",
                )
            }.isFailure,
        )
    }

    private fun compile(
        descriptor: GPUMaterialDescriptor,
        paintAlpha: Float,
    ): GPUPreparedMaterialProgram =
        assertIs<GPUPreparedMaterialProgramResult.Ready>(
            GPUPreparedMaterialProgramCompiler.compile(descriptor, paintAlpha, context),
            descriptor.toString(),
        ).program

    private fun acceptedDescriptors(): List<GPUMaterialDescriptor> = listOf(
        solid(),
        linearGradient(),
        radialGradient(),
        sweepGradient(),
        conicalGradient(),
        image(byteArrayOf(1, 2, 3, 4)),
        supportedBlend(),
        registeredRuntimeEffect(),
    )

    private fun solid(r: Float = 0.25f) =
        GPUMaterialDescriptor.SolidColor(r = r, g = 0.5f, b = 0.75f, a = 0.8f)

    private fun linearGradient() =
        GPUMaterialDescriptor.LinearGradient(
            startX = 0f,
            startY = 0f,
            endX = 32f,
            endY = 8f,
            startR = 1f,
            startG = 0f,
            startB = 0f,
            startA = 0.25f,
            endR = 0f,
            endG = 0f,
            endB = 1f,
            endA = 0.75f,
            allStopPositions = floatArrayOf(0f, 1f),
            allStopColors = floatArrayOf(1f, 0f, 0f, 0.25f, 0f, 0f, 1f, 0.75f),
        )

    private fun radialGradient() =
        GPUMaterialDescriptor.RadialGradient(
            centerX = 8f,
            centerY = 9f,
            radius = 10f,
            startR = 1f,
            startG = 1f,
            startB = 0f,
            startA = 1f,
            endR = 0f,
            endG = 1f,
            endB = 1f,
            endA = 0.5f,
            allStopPositions = floatArrayOf(0f, 1f),
            allStopColors = floatArrayOf(1f, 1f, 0f, 1f, 0f, 1f, 1f, 0.5f),
        )

    private fun sweepGradient() =
        GPUMaterialDescriptor.SweepGradient(
            centerX = 4f,
            centerY = 5f,
            startAngle = 0f,
            endAngle = 270f,
            startR = 1f,
            startG = 0f,
            startB = 1f,
            startA = 1f,
            endR = 0f,
            endG = 1f,
            endB = 0f,
            endA = 0.4f,
            allStopPositions = floatArrayOf(0f, 1f),
            allStopColors = floatArrayOf(1f, 0f, 1f, 1f, 0f, 1f, 0f, 0.4f),
        )

    private fun conicalGradient() =
        GPUMaterialDescriptor.ConicalGradient(
            startX = 1f,
            startY = 2f,
            endX = 8f,
            endY = 9f,
            startRadius = 1f,
            endRadius = 12f,
            startR = 0.2f,
            startG = 0.4f,
            startB = 0.6f,
            startA = 0.8f,
            endR = 0.8f,
            endG = 0.6f,
            endB = 0.4f,
            endA = 0.2f,
            allStopPositions = floatArrayOf(0f, 1f),
            allStopColors = floatArrayOf(0.2f, 0.4f, 0.6f, 0.8f, 0.8f, 0.6f, 0.4f, 0.2f),
        )

    private fun image(pixels: ByteArray) =
        GPUMaterialDescriptor.ImageDraw(
            imageSourceId = "prepared-material-image",
            imageWidth = 1,
            imageHeight = 1,
            rgbaPixels = pixels,
            samplingFilterMode = "nearest",
            alphaOnly = false,
            tintR = 0.25f,
            tintG = 0.5f,
            tintB = 0.75f,
            tintA = 0.6f,
        )

    private fun supportedBlend() =
        GPUMaterialDescriptor.BlendShader(
            mode = "SRC_OVER",
            dst = solid(),
            src = solid(r = 0.8f),
        )

    private fun registeredRuntimeEffect() =
        GPUMaterialDescriptor.RuntimeEffect(
            effectId = "runtime.simple_rt",
            descriptorVersion = 1,
            uniforms = mapOf(
                "gColor" to GPURuntimeEffectUniformValue.Float4(0.25f, 0.5f, 0.75f, 0.8f),
            ),
        )

    private fun reflectedBinding(
        binding: Int,
        resourceKind: String,
        minBindingSize: Int? = null,
        sampleType: String? = null,
        viewDimension: String? = null,
    ) = WgslBindingReflection(
        group = 1,
        binding = binding,
        name = "binding$binding",
        resourceKind = resourceKind,
        access = "read",
        minBindingSize = minBindingSize,
        sampleType = sampleType,
        viewDimension = viewDimension,
    )

    private fun sha256Hex(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
