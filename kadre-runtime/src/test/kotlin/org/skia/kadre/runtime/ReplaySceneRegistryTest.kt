package org.skia.kadre.runtime

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReplaySceneRegistryTest {
    @Test
    fun m73ReplayPackKeepsPmJsonContract() {
        val packJson = replayPackJson("demo", "  ")

        assertContains(packJson, "\"id\": \"m73-kadre-replay-pack-v1\"")
        assertContains(packJson, "\"sceneCount\": 5")
        assertContains(packJson, "\"renderableSceneCount\": 4")
        assertContains(packJson, "\"unsupportedSceneCount\": 1")
        assertContains(packJson, "\"sceneIds\"")
        assertContains(packJson, "\"unsupportedSceneIds\"")
        assertContains(packJson, "m73-nested-rrect-clip-refusal-v1")

        val defaultScene = requireNotNull(M73_REPLAY_SCENES_BY_ID[M73_DEFAULT_SCENE_CONTRACT_ID])
        val sceneJson = defaultScene.toJson("  ")
        assertContains(sceneJson, "\"source\": \"kanvas-replay-data\"")
        assertContains(sceneJson, "\"sourceSceneId\": \"linear-gradient-rect\"")
        assertContains(sceneJson, "\"commandCounters\"")
        assertContains(sceneJson, "\"total\": 2")
        assertContains(sceneJson, "\"supported\": 2")
        assertContains(sceneJson, "\"unsupported\": 0")
        assertContains(sceneJson, "\"backgroundClear\": 1")
        assertContains(sceneJson, "\"fillRect\": 1")
        assertContains(sceneJson, "\"srcOver\": 1")
        assertContains(sceneJson, "\"partialAlpha\": 0")
        assertContains(sceneJson, "\"blendMode\": \"SrcOver\"")
        assertContains(sceneJson, "\"alpha\": 1.0000")
        assertContains(sceneJson, "\"unsupportedCommands\": []")
    }

    @Test
    fun unsupportedReplaySceneKeepsStableReason() {
        val scene = requireNotNull(M73_REPLAY_SCENES_BY_ID["m73-nested-rrect-clip-refusal-v1"])

        assertEquals("expected-unsupported", scene.status)
        assertEquals(false, scene.renderedByKadre)
        assertEquals(2, scene.totalCommandCount)
        assertEquals(1, scene.supportedCommandCount)
        assertEquals(1, scene.unsupportedCommandCount)
        assertEquals(listOf("nested-rrect-difference-clip"), scene.unsupportedCommands)
        assertContains(scene.toJson("  "), "\"unsupportedCommands\": [\"nested-rrect-difference-clip\"]")
    }

    @Test
    fun smokeModeDoesNotExposeReplayPack() {
        assertEquals("null", replayPackJson("smoke", "  "))
    }

    @Test
    fun replaySceneRequiresExactlyOneClearCommand() {
        val error = assertFailsWith<IllegalArgumentException> {
            ReplaySceneEvidence(
                id = "invalid-no-clear",
                title = "invalid",
                source = "test",
                sourceSceneId = "test",
                version = 1,
                commandSource = "test",
                commands = listOf(
                    ReplayCommand.FillRect("rect", 0.0, 0.0, 1.0, 1.0, ReplayColor(1.0, 0.0, 0.0)),
                ),
                dashboardRow = "test",
                cpuRoute = "test",
                gpuRoute = "test",
                pipelineKey = "test",
            )
        }

        assertContains(error.message.orEmpty(), "must contain exactly one ReplayCommand.Clear")
    }

    @Test
    fun m75ReplayPackEvidenceKeepsPerSceneSchema() {
        val evidence = buildReplayPackEvidence()
        val json = evidence.toJson()

        assertEquals(5, evidence.sceneCount)
        assertEquals(4, evidence.renderableSceneCount)
        assertEquals(1, evidence.expectedUnsupportedSceneCount)
        assertEquals(0, evidence.failedSceneCount)
        assertContains(json, "\"packId\": \"m75-kadre-replay-pack-evidence-v1\"")
        assertContains(json, "\"linearIssues\": [\"FOR-91\", \"FOR-110\", \"FOR-111\", \"FOR-112\", \"FOR-113\"]")
        assertContains(json, "\"cpuReference\"")
        assertContains(json, "\"nativeEvidence\"")
        assertContains(json, "\"readbackImage\": \"reports/wgsl-pipeline/m70-kadre-native/native-demo-readback.png\"")
        assertContains(json, "\"reason\": \"m73.kadre-replay-scene-expected-unsupported\"")
        assertContains(json, "\"status\": \"not-generated\"")
    }

    @Test
    fun m76GeneratedMetadataReplayMapsSelectedScenesAndRefusesUnsupportedMetadata() {
        val evidence = buildGeneratedMetadataReplayEvidence(m76ManifestFixture())
        val json = evidence.toJson()

        assertEquals(6, evidence.sceneCount)
        assertEquals(4, evidence.mappedSceneCount)
        assertEquals(2, evidence.refusedMetadataCount)
        assertEquals(0, evidence.failedSceneCount)
        assertContains(json, "\"packId\": \"m76-generated-metadata-replay-v1\"")
        assertContains(json, "\"sourceManifest\": \"reports/wgsl-pipeline/scenes/generated/results.json\"")
        assertContains(json, "\"replaySceneId\": \"m76-solid-rect-metadata-replay-v1\"")
        assertContains(json, "\"replaySceneId\": \"m76-linear-gradient-rect-metadata-replay-v1\"")
        assertContains(json, "\"reason\": \"m76.metadata.source-status-not-pass\"")
        assertContains(json, "\"reason\": \"m76.metadata.unsupported-route-family\"")
        assertContains(json, "\"sourceSceneId\": \"path-aa-convexpaths-edge-budget\"")
        assertContains(json, "\"sourceSceneId\": \"runtime-effect-simple\"")
    }

    @Test
    fun m77BlendAlphaReplayCoversSrcOverAlphaAndUnsupportedBlend() {
        val evidence = buildBlendAlphaReplayEvidence()
        val json = evidence.toJson()

        assertEquals(3, evidence.sceneCount)
        assertEquals(2, evidence.renderableSceneCount)
        assertEquals(1, evidence.expectedUnsupportedSceneCount)
        assertEquals(0, evidence.failedSceneCount)
        assertEquals(2, evidence.partialAlphaSceneCount)
        assertEquals(4, evidence.srcOverCommandCount)
        assertEquals(3, evidence.partialAlphaCommandCount)
        assertContains(json, "\"packId\": \"m77-blend-alpha-replay-v1\"")
        assertContains(json, "\"linearIssues\": [\"FOR-93\", \"FOR-119\", \"FOR-120\", \"FOR-121\", \"FOR-122\", \"FOR-123\"]")
        assertContains(json, "\"id\": \"m77-alpha-srcover-stack-replay-v1\"")
        assertContains(json, "\"id\": \"m77-gradient-alpha-srcover-replay-v1\"")
        assertContains(json, "\"id\": \"m77-multiply-blend-refusal-v1\"")
        assertContains(json, "\"unsupportedBlendReason\": \"m77.unsupported-blend-mode.kMultiply\"")
        assertContains(json, "\"blendMode\": \"SrcOver\"")
        assertContains(json, "\"alpha\": 0.3500")
        assertContains(json, "\"endAlpha\": 0.7000")
        assertContains(json, "\"oracle\": \"src-over-partial-alpha-sampled-reference\"")
    }

    @Test
    fun m77SceneCommandsExposeBlendAlphaSemanticsAndCpuOracleFacts() {
        val alphaStack = M77_BLEND_ALPHA_REPLAY_SCENES.single { it.id == "m77-alpha-srcover-stack-replay-v1" }
        val unsupported = M77_BLEND_ALPHA_REPLAY_SCENES.single { it.id == "m77-multiply-blend-refusal-v1" }

        assertEquals("renderable", alphaStack.status)
        assertEquals(3, alphaStack.totalCommandCount)
        assertEquals(2, alphaStack.fillRectCount)
        assertEquals(2, alphaStack.srcOverCommandCount)
        assertEquals(1, alphaStack.partialAlphaCommandCount)
        assertContains(alphaStack.toJson("  "), "\"blendMode\": \"SrcOver\"")
        assertContains(alphaStack.toJson("  "), "\"alpha\": 0.4500")

        val cpuReference = renderReplayCpuOracle(640, 420, alphaStack)
        assertEquals(REPLAY_CPU_ORACLE_API, cpuReference.api)
        assertEquals(8888851062735355593L, cpuReference.sampledChecksum)
        assertEquals(5520, cpuReference.nonTransparentPixels)
        assertEquals(0, cpuReference.bitmapSampledPixels)

        assertEquals("expected-unsupported", unsupported.status)
        assertEquals(1, unsupported.unsupportedCommandCount)
        assertEquals(listOf("m77.unsupported-blend-mode.kMultiply"), unsupported.unsupportedCommands)
        assertContains(unsupported.toJson("  "), "\"unsupportedCommands\": [\"m77.unsupported-blend-mode.kMultiply\"]")
    }

    @Test
    fun m78ClipReplayCoversClipRectCountsAndComplexClipRefusal() {
        val evidence = buildClipReplayEvidence()
        val json = evidence.toJson()

        assertEquals(3, evidence.sceneCount)
        assertEquals(2, evidence.renderableSceneCount)
        assertEquals(1, evidence.expectedUnsupportedSceneCount)
        assertEquals(0, evidence.failedSceneCount)
        assertEquals(3, evidence.clipRectCommandCount)
        assertEquals(3, evidence.clipIntersectCommandCount)
        assertEquals(3, evidence.srcOverCommandCount)
        assertEquals(2, evidence.partialAlphaCommandCount)
        assertContains(json, "\"packId\": \"m78-clip-replay-v1\"")
        assertContains(json, "\"linearIssues\": [\"FOR-94\", \"FOR-124\", \"FOR-125\", \"FOR-126\", \"FOR-127\", \"FOR-128\"]")
        assertContains(json, "\"id\": \"m78-clipped-solid-rect-replay-v1\"")
        assertContains(json, "\"id\": \"m78-clipped-alpha-gradient-replay-v1\"")
        assertContains(json, "\"id\": \"m78-complex-clip-refusal-v1\"")
        assertContains(json, "\"unsupportedClipReason\": \"m78.clip.unsupported-complex-clip\"")
        assertContains(json, "\"oracle\": \"clip-rect-intersect-sampled-reference\"")
    }

    @Test
    fun m78SceneCommandsExposeClipBoundsOperationAndCpuOracleFacts() {
        val solidClip = M78_CLIP_REPLAY_SCENES.single { it.id == "m78-clipped-solid-rect-replay-v1" }
        val alphaClip = M78_CLIP_REPLAY_SCENES.single { it.id == "m78-clipped-alpha-gradient-replay-v1" }
        val unsupported = M78_CLIP_REPLAY_SCENES.single { it.id == "m78-complex-clip-refusal-v1" }

        assertEquals("renderable", solidClip.status)
        assertEquals(3, solidClip.totalCommandCount)
        assertEquals(1, solidClip.clipRectCommandCount)
        assertEquals(1, solidClip.clipIntersectCommandCount)
        assertEquals(1, solidClip.fillRectCount)
        assertEquals(1, solidClip.srcOverCommandCount)
        assertContains(solidClip.toJson("  "), "\"family\": \"clipRect\"")
        assertContains(solidClip.toJson("  "), "\"operation\": \"intersect\"")
        assertContains(solidClip.toJson("  "), "\"left\": 0.2400")
        assertContains(solidClip.toJson("  "), "\"right\": 0.7200")

        assertEquals(2, alphaClip.clipRectCommandCount)
        assertEquals(2, alphaClip.fillRectCount)
        assertEquals(2, alphaClip.partialAlphaCommandCount)
        assertContains(alphaClip.toWgsl(0.0), "in.uv.x >= 0.180000f")
        assertContains(alphaClip.toWgsl(0.0), "in.uv.y < 0.680000f")
        assertContains(alphaClip.toWgsl(0.0), "in.uv.x < (0.080000f + 0.840000f)")

        val cpuReference = renderReplayCpuOracle(640, 420, solidClip)
        assertEquals(REPLAY_CPU_ORACLE_API, cpuReference.api)
        assertEquals(7657149549507766923L, cpuReference.sampledChecksum)
        assertEquals(5520, cpuReference.nonTransparentPixels)
        assertEquals(0, cpuReference.bitmapSampledPixels)

        assertEquals("expected-unsupported", unsupported.status)
        assertEquals(1, unsupported.unsupportedCommandCount)
        assertEquals(listOf("m78.clip.unsupported-complex-clip"), unsupported.unsupportedCommands)
        assertContains(unsupported.toJson("  "), "\"unsupportedCommands\": [\"m78.clip.unsupported-complex-clip\"]")
    }

    @Test
    fun m78ReplayScenesAreSelectableByLiveReplayRegistry() {
        val replayScenes = replayScenesById()

        assertEquals(M73_REPLAY_SCENES.size + M77_BLEND_ALPHA_REPLAY_SCENES.size + M78_CLIP_REPLAY_SCENES.size + M79_BITMAP_REPLAY_SCENES.size, replayScenes.size)
        assertEquals("renderable", requireNotNull(replayScenes["m78-clipped-solid-rect-replay-v1"]).status)
        assertEquals("renderable", requireNotNull(replayScenes["m78-clipped-alpha-gradient-replay-v1"]).status)
        assertEquals("expected-unsupported", requireNotNull(replayScenes["m78-complex-clip-refusal-v1"]).status)
    }

    @Test
    fun m79BitmapReplayCoversFixtureBackedScenesAndUnsupportedSampler() {
        val evidence = buildBitmapReplayEvidence()
        val json = evidence.toJson()

        assertEquals(4, evidence.sceneCount)
        assertEquals(3, evidence.renderableSceneCount)
        assertEquals(1, evidence.expectedUnsupportedSceneCount)
        assertEquals(0, evidence.failedSceneCount)
        assertEquals(3, evidence.bitmapCommandCount)
        assertEquals(3, evidence.fixtureBackedBitmapCommandCount)
        assertEquals(2, evidence.nearestSamplerCommandCount)
        assertEquals(1, evidence.linearSamplerCommandCount)
        assertEquals(1, evidence.unsupportedBitmapCommandCount)
        assertEquals(1, evidence.clipRectCommandCount)
        assertEquals(1, evidence.clipIntersectCommandCount)
        assertEquals(4, evidence.srcOverCommandCount)
        assertEquals(2, evidence.partialAlphaCommandCount)
        assertContains(json, "\"packId\": \"m79-bitmap-replay-v1\"")
        assertContains(json, "\"linearIssues\": [\"FOR-95\", \"FOR-129\", \"FOR-130\", \"FOR-131\", \"FOR-132\", \"FOR-133\"]")
        assertContains(json, "\"id\": \"m79-bitmap-fixture-nearest-replay-v1\"")
        assertContains(json, "\"id\": \"m79-bitmap-fixture-linear-alpha-replay-v1\"")
        assertContains(json, "\"id\": \"m79-bitmap-fixture-clipped-nearest-replay-v1\"")
        assertContains(json, "\"id\": \"m79-bitmap-mipmap-sampler-refusal-v1\"")
        assertContains(json, "\"unsupportedBitmapReason\": \"m79.bitmap.unsupported-sampler.mipmap\"")
        assertContains(json, "\"oracle\": \"fixture-backed-bitmap-sampling-reference\"")
    }

    @Test
    fun m79BitmapCommandsExposeContractFieldsCountersAndCpuOracleFacts() {
        val nearest = M79_BITMAP_REPLAY_SCENES.single { it.id == "m79-bitmap-fixture-nearest-replay-v1" }
        val linearAlpha = M79_BITMAP_REPLAY_SCENES.single { it.id == "m79-bitmap-fixture-linear-alpha-replay-v1" }
        val clipped = M79_BITMAP_REPLAY_SCENES.single { it.id == "m79-bitmap-fixture-clipped-nearest-replay-v1" }
        val unsupported = M79_BITMAP_REPLAY_SCENES.single { it.id == "m79-bitmap-mipmap-sampler-refusal-v1" }

        assertEquals("renderable", nearest.status)
        assertEquals(1, nearest.bitmapCommandCount)
        assertEquals(1, nearest.fixtureBackedBitmapCommandCount)
        assertEquals(1, nearest.nearestBitmapSamplerCommandCount)
        assertEquals(0, nearest.linearBitmapSamplerCommandCount)
        assertContains(nearest.toJson("  "), "\"family\": \"bitmapRect\"")
        assertContains(nearest.toJson("  "), "\"fixtureId\": \"m79-fixture-checker-rgba8-4x4\"")
        assertContains(nearest.toJson("  "), "\"sourceBounds\"")
        assertContains(nearest.toJson("  "), "\"destinationBounds\"")
        assertContains(nearest.toJson("  "), "\"sampler\": \"nearest\"")
        assertContains(nearest.toJson("  "), "\"blendMode\": \"SrcOver\"")
        assertContains(nearest.toJson("  "), "\"alpha\": 1.0000")
        assertContains(nearest.toJson("  "), "\"owner\": \"kanvas\"")
        assertContains(nearest.toJson("  "), "\"storage\": \"in-repo-kotlin-fixture\"")

        assertEquals(1, linearAlpha.bitmapCommandCount)
        assertEquals(2, linearAlpha.srcOverCommandCount)
        assertEquals(1, linearAlpha.partialAlphaCommandCount)
        assertEquals(1, linearAlpha.linearBitmapSamplerCommandCount)
        assertContains(linearAlpha.toJson("  "), "\"fixtureId\": \"m79-fixture-alpha-swatch-rgba8-4x4\"")
        assertContains(linearAlpha.toJson("  "), "\"sampler\": \"linear\"")
        assertContains(linearAlpha.toJson("  "), "\"alpha\": 0.8200")
        assertContains(linearAlpha.toWgsl(0.0), "bitmapPixels")

        assertEquals(1, clipped.clipRectCommandCount)
        assertEquals(1, clipped.clipIntersectCommandCount)
        assertEquals(1, clipped.bitmapCommandCount)
        assertEquals(1, clipped.nearestBitmapSamplerCommandCount)
        assertEquals(1, clipped.partialAlphaCommandCount)
        assertContains(clipped.toJson("  "), "\"family\": \"clipRect\"")
        assertContains(clipped.toJson("  "), "\"operation\": \"intersect\"")
        assertContains(clipped.toJson("  "), "\"fixtureId\": \"m79-fixture-checker-rgba8-4x4\"")
        assertContains(clipped.toWgsl(0.0), "in.uv.x >= 0.240000f")
        assertContains(clipped.toWgsl(0.0), "in.uv.y < 0.520000f")

        val cpuReference = renderReplayCpuOracle(640, 420, nearest)
        val linearAlphaCpuReference = renderReplayCpuOracle(640, 420, linearAlpha)
        val clippedCpuReference = renderReplayCpuOracle(640, 420, clipped)
        assertEquals(REPLAY_CPU_ORACLE_API, cpuReference.api)
        assertEquals(2208507294891662972L, cpuReference.sampledChecksum)
        assertEquals(5520, cpuReference.nonTransparentPixels)
        assertEquals(75428, cpuReference.bitmapSampledPixels)
        assertEquals(-8675548398312149882L, linearAlphaCpuReference.sampledChecksum)
        assertEquals(5520, linearAlphaCpuReference.nonTransparentPixels)
        assertEquals(71603, linearAlphaCpuReference.bitmapSampledPixels)
        assertEquals(-2224348050358112279L, clippedCpuReference.sampledChecksum)
        assertEquals(27468, clippedCpuReference.bitmapSampledPixels)
        assertEquals(true, clippedCpuReference.bitmapSampledPixels < cpuReference.bitmapSampledPixels)

        assertEquals("expected-unsupported", unsupported.status)
        assertEquals(1, unsupported.unsupportedCommandCount)
        assertEquals(1, unsupported.unsupportedBitmapCommandCount)
        assertEquals(listOf("m79.bitmap.unsupported-sampler.mipmap"), unsupported.unsupportedCommands)
        assertContains(unsupported.toJson("  "), "\"unsupportedCommands\": [\"m79.bitmap.unsupported-sampler.mipmap\"]")
    }

    @Test
    fun m79ReplayScenesAreSelectableByLiveReplayRegistry() {
        val replayScenes = replayScenesById()

        assertEquals("renderable", requireNotNull(replayScenes["m79-bitmap-fixture-nearest-replay-v1"]).status)
        assertEquals("renderable", requireNotNull(replayScenes["m79-bitmap-fixture-linear-alpha-replay-v1"]).status)
        assertEquals("renderable", requireNotNull(replayScenes["m79-bitmap-fixture-clipped-nearest-replay-v1"]).status)
        assertEquals("expected-unsupported", requireNotNull(replayScenes["m79-bitmap-mipmap-sampler-refusal-v1"]).status)
    }

    @Test
    fun sharedReplayCpuOracleExposesTypedResultAndUnsupportedFacts() {
        val unsupported = M79_BITMAP_REPLAY_SCENES.single { it.id == "m79-bitmap-mipmap-sampler-refusal-v1" }
        val oracle = renderReplayCpuOracle(640, 420, unsupported)

        assertEquals(REPLAY_CPU_ORACLE_API, oracle.api)
        assertEquals(640, oracle.deviceWidth)
        assertEquals(420, oracle.deviceHeight)
        assertEquals(unsupported.id, oracle.sceneId)
        assertEquals("expected-unsupported", oracle.sceneStatus)
        assertEquals(false, oracle.rendered)
        assertEquals(listOf("m79.bitmap.unsupported-sampler.mipmap"), oracle.unsupportedReasons)
        assertEquals(-5166315637198820477L, oracle.sampledChecksum)
        assertEquals(5520, oracle.nonTransparentPixels)
    }

    @Test
    fun sharedReplayCpuOracleRejectsMalformedInputsWithStableMessages() {
        val invalidDevice = assertFailsWith<IllegalArgumentException> {
            renderReplayCpuOracle(0, 420)
        }
        assertContains(invalidDevice.message.orEmpty(), "ReplayCpuOracle device dimensions must be positive: 0x420")

        val invalidFixture = assertFailsWith<IllegalArgumentException> {
            ReplayCommand.BitmapRect(
                label = "missing-fixture",
                fixtureId = "missing-fixture",
                srcX = 0.0,
                srcY = 0.0,
                srcWidth = 1.0,
                srcHeight = 1.0,
                dstX = 0.0,
                dstY = 0.0,
                dstWidth = 1.0,
                dstHeight = 1.0,
                sampler = ReplayBitmapSampler.Nearest,
                provenance = "test",
            )
        }
        assertContains(invalidFixture.message.orEmpty(), "Unknown replay bitmap fixture id: missing-fixture")

        val invalidFill = assertFailsWith<IllegalArgumentException> {
            ReplayCommand.FillRect("bad-fill", 0.0, 0.0, 0.0, 1.0, ReplayColor(1.0, 0.0, 0.0))
        }
        assertContains(invalidFill.message.orEmpty(), "FillRect bad-fill bounds must be positive")

        val invalidClip = assertFailsWith<IllegalArgumentException> {
            ReplayCommand.ClipRect("bad-clip", 0.0, 0.0, 1.0, -1.0)
        }
        assertContains(invalidClip.message.orEmpty(), "ClipRect bad-clip bounds must be positive")

        val invalidBitmapBounds = assertFailsWith<IllegalArgumentException> {
            ReplayCommand.BitmapRect(
                label = "bad-bitmap",
                fixtureId = ReplayBitmapFixtures.checker4x4.id,
                srcX = 0.0,
                srcY = 0.0,
                srcWidth = 0.0,
                srcHeight = 1.0,
                dstX = 0.0,
                dstY = 0.0,
                dstWidth = 1.0,
                dstHeight = 1.0,
                sampler = ReplayBitmapSampler.Nearest,
                provenance = "test",
            )
        }
        assertContains(invalidBitmapBounds.message.orEmpty(), "BitmapRect bad-bitmap source bounds must be positive")

        val invalidBitmapDestinationBounds = assertFailsWith<IllegalArgumentException> {
            ReplayCommand.BitmapRect(
                label = "bad-bitmap-destination",
                fixtureId = ReplayBitmapFixtures.checker4x4.id,
                srcX = 0.0,
                srcY = 0.0,
                srcWidth = 1.0,
                srcHeight = 1.0,
                dstX = 0.0,
                dstY = 0.0,
                dstWidth = 0.0,
                dstHeight = 1.0,
                sampler = ReplayBitmapSampler.Nearest,
                provenance = "test",
            )
        }
        assertContains(invalidBitmapDestinationBounds.message.orEmpty(), "BitmapRect bad-bitmap-destination destination bounds must be positive")
    }

    @Test
    fun m80SharedReplayOracleEvidenceSummarizesSharedApiAndFailureCoverage() {
        val evidence = buildSharedReplayOracleEvidence()
        val json = evidence.toJson()

        assertEquals("org.skia.kadre.runtime.ReplayCpuOracle", REPLAY_CPU_ORACLE_API)
        assertEquals(replayScenesById().size, evidence.sceneCount)
        assertEquals(0, evidence.failedSceneCount)
        assertEquals(0, evidence.failedValidationRowCount)
        assertEquals(true, evidence.supportedCommandFamilies.contains("bitmapRect"))
        assertEquals(true, evidence.supportedCommandFamilies.contains("clipRect"))
        assertContains(json, "\"packId\": \"m80-shared-replay-oracle-v1\"")
        assertContains(json, "\"oracleApi\": \"org.skia.kadre.runtime.ReplayCpuOracle\"")
        assertContains(json, "\"sampledChecksum\"")
        assertContains(json, "\"bitmapSampledPixels\"")
        assertContains(json, "\"failedValidationRowCount\": 0")
        assertContains(json, "\"id\": \"invalid-fixture-and-bounds\"")
    }

    @Test
    fun m81NativeFrameCaptureKeepsTruthfulOffscreenReadbackSchema() {
        val evidence = buildM81NativeFrameCaptureEvidence(Path("..").toAbsolutePath().normalize())
        val json = evidence.toJsonElement().toString()

        assertEquals("offscreen-texture-readback-produced", evidence.captureStatus)
        assertEquals(180, evidence.frameCount)
        assertEquals(true, evidence.nativePresented)
        assertEquals(true, evidence.realOffscreenReadback)
        assertEquals(false, evidence.windowSurfaceReadback)
        assertEquals(1, evidence.unsupportedCaptureCount)
        assertContains(json, "\"packId\":\"m81-native-frame-capture-v1\"")
        assertContains(json, "\"linearIssues\":[\"FOR-97\",\"FOR-139\",\"FOR-140\",\"FOR-141\",\"FOR-142\",\"FOR-143\"]")
        assertContains(json, "\"captureStatus\":\"offscreen-texture-readback-produced\"")
        assertContains(json, "\"realNativeWindowSurfaceReadback\":false")
        assertContains(json, "\"realNativeOffscreenTextureReadback\":true")
        assertContains(json, "\"m81.window-surface-readback-not-implemented\"")
        assertContains(json, "\"imagePath\":\"reports/wgsl-pipeline/m70-kadre-native/native-demo-readback.png\"")
        assertContains(json, "\"format\":\"RGBA8Unorm\"")
        assertContains(json, "\"backend\":\"wgpu4k-native\"")
    }

    @Test
    fun m81NativeFrameCaptureSerializesUnsupportedHostRefusalSchema() {
        val evidence = M81NativeFrameCaptureEvidence(
            nativeDemo = buildJsonObject {
                put("nativePresented", false)
                put("presentedFrames", 0)
                put("requestedFrames", 3)
                put("adapterInfo", "")
                put("surface", JsonObject(emptyMap()))
                put("capture", JsonObject(emptyMap()))
            },
            routeStatus = buildJsonObject {
                put("status", "blocked")
                put("reason", "fixture.host-unavailable")
                put("capture", JsonObject(emptyMap()))
            },
            nativeSmoke = buildJsonObject {
                put("status", "blocked")
                put("presentedFrames", 0)
            },
        )
        val json = evidence.toJsonElement().toString()

        assertEquals("refused-host-unsupported", evidence.captureStatus)
        assertEquals(false, evidence.realOffscreenReadback)
        assertEquals(false, evidence.windowSurfaceReadback)
        assertEquals(
            listOf(
                "m81.host-unsupported",
                "m81.adapter-unavailable",
                "m81.capture-unsupported",
                "m81.window-surface-readback-not-implemented",
            ),
            evidence.unsupportedCaptureReasons,
        )
        assertContains(json, "\"captureStatus\":\"refused-host-unsupported\"")
        assertContains(json, "\"unsupportedCaptureCount\":4")
        assertContains(json, "\"m81.host-unsupported\"")
        assertContains(json, "\"m81.adapter-unavailable\"")
        assertContains(json, "\"m81.capture-unsupported\"")
        assertContains(json, "\"m81.window-surface-readback-not-implemented\"")
        assertContains(json, "\"id\":\"m81.refusal-taxonomy\"")
    }

    @Test
    fun m81NativeFrameCaptureSerializesMissingSourceAndBlankArtifactRefusals() {
        val missingSource = M81NativeFrameCaptureEvidence(
            nativeDemo = JsonObject(emptyMap()),
            routeStatus = JsonObject(emptyMap()),
            nativeSmoke = JsonObject(emptyMap()),
        )
        val blankArtifact = M81NativeFrameCaptureEvidence(
            nativeDemo = buildJsonObject {
                put("nativePresented", true)
                put("presentedFrames", 1)
                put("requestedFrames", 1)
                put("adapterInfo", "AdapterInfo(device=fixture)")
                put("surface", buildJsonObject {
                    put("width", 64)
                    put("height", 64)
                    put("format", "BGRA8Unorm")
                })
                put("capture", buildJsonObject {
                    put("status", "produced")
                    put("reason", "m70.native-offscreen-texture-readback")
                    put("imagePath", "reports/wgsl-pipeline/fixture/blank.png")
                    put("realNativeReadback", true)
                    put("windowSurfaceReadback", false)
                    put("nonTransparentPixels", 0)
                })
            },
            routeStatus = buildJsonObject {
                put("status", "native-runnable")
                put("reason", "fixture")
                put("capture", JsonObject(emptyMap()))
            },
            nativeSmoke = buildJsonObject {
                put("status", "native-runnable")
                put("presentedFrames", 1)
            },
        )

        assertEquals("refused-infrastructure-source-missing", missingSource.captureStatus)
        assertEquals(true, "m81.infrastructure-source-evidence-missing" in missingSource.unsupportedCaptureReasons)
        assertEquals("refused-blank-artifact", blankArtifact.captureStatus)
        assertEquals(true, "m81.blank-artifact" in blankArtifact.unsupportedCaptureReasons)
        assertEquals(true, "m81.window-surface-readback-not-implemented" in blankArtifact.unsupportedCaptureReasons)
    }

    private fun m76ManifestFixture(): String = """
        {
          "scenes": [
            ${m76SceneFixture("solid-rect", "Solid filled rect", "pass", "reports/wgsl-pipeline/2026-05-30-m46-solid-rect-generated-evidence.md", "artifacts/solid-rect", listOf("source.generated", "feature.shape.solid", "feature.coverage.analytic-rect", "route.cpu.descriptor", "route.gpu.webgpu"))},
            ${m76SceneFixture("linear-gradient-rect", "Linear gradient rect generated WGSL route", "pass", "reports/wgsl-pipeline/2026-05-28-m39-gradient-srcover-dashboard-scenes.md", "artifacts/linear-gradient-rect", listOf("source.generated", "feature.gradient.linear", "feature.coverage.analytic-rect", "route.cpu.shader", "route.gpu.webgpu"))},
            ${m76SceneFixture("bitmap-rect-nearest", "Bitmap rect nearest sampling", "pass", "reports/wgsl-pipeline/2026-05-27-m32-drawbitmaprect-skbug4734-resolution.md", "artifacts/bitmap-rect-nearest", listOf("source.generated", "feature.image.bitmap", "feature.sampling.nearest", "route.cpu.image-rect", "route.gpu.webgpu"))},
            ${m76SceneFixture("gradient-color-filter-linear-kplus", "Linear gradient color-filter kPlus route", "pass", "reports/wgsl-pipeline/2026-05-31-m48-paint-blend-transform-generated-evidence.md", "artifacts/gradient-color-filter-linear-kplus", listOf("source.generated", "feature.gradient.linear", "feature.color-filter", "feature.blend.plus", "route.gpu.webgpu"))},
            ${m76SceneFixture("path-aa-convexpaths-edge-budget", "Path AA ConvexPaths edge-budget refusal", "expected-unsupported", "reports/wgsl-pipeline/2026-05-31-m48-expected-unsupported-breadth-evidence.md", "artifacts/path-aa-convexpaths-edge-budget", listOf("source.generated", "feature.path-aa", "feature.coverage.aa", "route.cpu.oracle", "route.gpu.expected-unsupported"))},
            ${m76SceneFixture("runtime-effect-simple", "Registered SimpleRT runtime-effect route", "pass", "reports/wgsl-pipeline/2026-05-31-m47-runtime-effect-simple-generated-evidence.md", "artifacts/runtime-effect-simple", listOf("source.generated", "feature.runtime-effect", "feature.coverage.analytic-rect", "route.cpu.descriptor", "route.gpu.webgpu"))}
          ]
        }
    """.trimIndent()

    private fun m76SceneFixture(
        id: String,
        title: String,
        status: String,
        sourceReport: String,
        artifactRoot: String,
        tags: List<String>,
    ): String = """
        {
          "id": "$id",
          "title": "$title",
          "status": "$status",
          "tags": [${tags.joinToString(", ") { "\"$it\"" }}],
          "generation": {
            "sourceReport": "$sourceReport",
            "artifactRoot": "$artifactRoot"
          }
        }
    """.trimIndent()
}
