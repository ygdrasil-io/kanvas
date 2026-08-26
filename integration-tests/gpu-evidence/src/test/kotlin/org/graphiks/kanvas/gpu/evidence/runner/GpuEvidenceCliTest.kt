package org.graphiks.kanvas.gpu.evidence.runner

import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry
import org.graphiks.kanvas.gpu.evidence.catalog.ComparisonPolicy
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceCase
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceExpectation
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceSceneDescriptor
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceSceneId
import org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalog
import org.graphiks.kanvas.gpu.evidence.catalog.OraclePolicy
import org.graphiks.kanvas.gpu.evidence.oracle.CpuOracle
import org.graphiks.kanvas.gpu.evidence.programs.KanvasSurfaceProgram
import org.graphiks.kanvas.gpu.evidence.artifacts.EvidenceSelection
import org.graphiks.kanvas.test.ComparisonUtils
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.RenderStats

@OptIn(ExperimentalUnsignedTypes::class)
class GpuEvidenceCliTest {
    @Test fun `request parser accepts repeated scenes and preserves compatibility accessor semantics`() {
        val root = Files.createTempDirectory("gpu-evidence-cli")

        val single = GpuEvidenceCliRequest.parse(arrayOf("--repository-root", root.toString(), "--source-commit", "a".repeat(40), "--scene", "custom-runtime-effect-unregistered-refusal"))
        assertEquals(EvidenceSelection.Explicit(listOf("custom-runtime-effect-unregistered-refusal")), single.selection)
        assertEquals("custom-runtime-effect-unregistered-refusal", single.sceneId)

        val multiple = GpuEvidenceCliRequest.parse(arrayOf("--repository-root", root.toString(), "--source-commit", "a".repeat(40), "--scene", "aggregate-memory-budget-refusal", "--scene", "custom-runtime-effect-unregistered-refusal"))
        assertEquals(EvidenceSelection.Explicit(listOf("aggregate-memory-budget-refusal", "custom-runtime-effect-unregistered-refusal")), multiple.selection)
        assertNull(multiple.sceneId)
    }

    @Test fun `request parser reads scenes file and supports all selection`() {
        val root = Files.createTempDirectory("gpu-evidence-cli")
        val scenesFile = Files.createTempFile("gpu-evidence-scenes", ".txt")
        Files.writeString(scenesFile, "custom-runtime-effect-unregistered-refusal\naggregate-memory-budget-refusal\n")

        val fromFile = GpuEvidenceCliRequest.parse(arrayOf("--repository-root", root.toString(), "--source-commit", "a".repeat(40), "--scenes-file", scenesFile.toString()))
        assertEquals(EvidenceSelection.Explicit(listOf("aggregate-memory-budget-refusal", "custom-runtime-effect-unregistered-refusal")), fromFile.selection)
        assertNull(fromFile.sceneId)

        val all = GpuEvidenceCliRequest.parse(arrayOf("--repository-root", root.toString(), "--source-commit", "a".repeat(40), "--all"))
        assertSame(EvidenceSelection.All, all.selection)
        assertNull(all.sceneId)
    }

    @Test fun `request parser requires an explicit selection`() {
        val root = Files.createTempDirectory("gpu-evidence-cli")
        assertFailsWith<IllegalArgumentException> {
            GpuEvidenceCliRequest.parse(arrayOf("--repository-root", root.toString(), "--source-commit", "a".repeat(40)))
        }
    }

    @Test fun `request parser rejects duplicate selection inputs`() {
        val root = Files.createTempDirectory("gpu-evidence-cli")
        val duplicateScenesFile = Files.createTempFile("gpu-evidence-scenes-duplicate", ".txt")
        Files.writeString(duplicateScenesFile, "custom-runtime-effect-unregistered-refusal\ncustom-runtime-effect-unregistered-refusal\n")

        assertFailsWith<IllegalArgumentException> {
            GpuEvidenceCliRequest.parse(
                arrayOf(
                    "--repository-root", root.toString(),
                    "--source-commit", "a".repeat(40),
                    "--scene", "custom-runtime-effect-unregistered-refusal",
                    "--scene", "custom-runtime-effect-unregistered-refusal",
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GpuEvidenceCliRequest.parse(
                arrayOf(
                    "--repository-root", root.toString(),
                    "--source-commit", "a".repeat(40),
                    "--scene", "custom-runtime-effect-unregistered-refusal",
                    "--all",
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GpuEvidenceCliRequest.parse(
                arrayOf(
                    "--repository-root", root.toString(),
                    "--source-commit", "a".repeat(40),
                    "--scenes-file", duplicateScenesFile.toString(),
                ),
            )
        }
    }

    @Test fun `cli rejects an unknown selected scene before opening the runtime`() {
        val events = mutableListOf<String>()

        val code = runner(FakeRuntime(events)).run(
            arrayOf(
                "--repository-root", Files.createTempDirectory("gpu-evidence-cli").toString(),
                "--source-commit", "a".repeat(40),
                "--scene", "unknown-scene",
            ),
        )

        assertEquals(2, code)
        assertEquals(emptyList(), events)
    }

    @Test fun `cli disposes a created backend before returning a failing exit code`() {
        val events = mutableListOf<String>()
        val code = runner(FakeRuntime(events)).run(validArgs())
        assertEquals(1, code)
        assertEquals(listOf("open-session", "close-session", "dispose"), events)
    }
    @Test fun `cli opens executes then closes and disposes a returned backend`() {
        val events = mutableListOf<String>()
        val code = runner(FakeRuntime(events, returned = true)).run(validArgs())
        assertEquals(1, code)
        assertEquals(listOf("open-session", "execute", "close-session", "dispose"), events)
    }

    @Test fun `cli disposes after close throws`() {
        val closeEvents = mutableListOf<String>()
        assertEquals(1, runner(FakeRuntime(closeEvents, closeFails = true)).run(validArgs()))
        assertEquals(listOf("open-session", "close-session", "dispose"), closeEvents)
    }

    @Test fun `cli closes and disposes after open throws following runtime session creation`() {
        val openEvents = mutableListOf<String>()
        assertEquals(1, runner(FakeRuntime(openEvents, openFails = true)).run(validArgs()))
        assertEquals(listOf("open-session", "runtime-session-created", "close-session", "dispose"), openEvents)
    }

    @Test fun `cli keeps primary execution failure when both cleanup hooks fail`() {
        val events = mutableListOf<String>()
        val closeFailure = IllegalStateException("close")
        val disposeFailure = IllegalStateException("dispose")
        val result = runner(FakeRuntime(events, returned = true, executionFails = true, closeFailure = closeFailure, disposeFailure = disposeFailure)).runResult(validArgs())
        assertEquals(1, result.exitCode)
        assertEquals("primary execution", assertNotNull(result.failure).message)
        assertEquals(listOf(closeFailure, disposeFailure), result.failure.suppressed.toList())
        assertEquals(listOf("open-session", "execute", "close-session", "dispose"), events)
    }

    @Test fun `cli rethrows dispose Error after close Exception and retains close failure`() {
        val events = mutableListOf<String>()
        val closeFailure = IllegalStateException("close")
        val disposeFailure = LinkageError("fatal dispose")
        val failure = assertFailsWith<LinkageError> {
            runner(FakeRuntime(events, closeFailure = closeFailure, disposeFailure = disposeFailure)).run(validArgs())
        }
        assertSame(disposeFailure, failure)
        assertEquals(listOf(closeFailure), failure.suppressed.toList())
        assertEquals(listOf("open-session", "close-session", "dispose"), events)
    }

    @Test fun `cli rethrows close Error after dispose Exception and retains dispose failure`() {
        val events = mutableListOf<String>()
        val closeFailure = LinkageError("fatal close")
        val disposeFailure = IllegalStateException("dispose")
        val failure = assertFailsWith<LinkageError> {
            runner(FakeRuntime(events, closeFailure = closeFailure, disposeFailure = disposeFailure)).run(validArgs())
        }
        assertSame(closeFailure, failure)
        assertEquals(listOf(disposeFailure), failure.suppressed.toList())
        assertEquals(listOf("open-session", "close-session", "dispose"), events)
    }

    @Test fun `cli de-duplicates the same cleanup failure instance`() {
        val events = mutableListOf<String>()
        val cleanupFailure = IllegalStateException("shared cleanup")
        var disposeThrew = false
        val result = runner(FakeRuntime(events, closeFailure = cleanupFailure, disposeFailure = cleanupFailure, onDisposeFailure = { disposeThrew = true })).runResult(validArgs())
        assertEquals(1, result.exitCode)
        assertSame(cleanupFailure, result.failure)
        assertEquals(true, disposeThrew)
        assertEquals(emptyList(), cleanupFailure.suppressed.toList())
        assertEquals(listOf("open-session", "close-session", "dispose"), events)
    }

    @Test fun `cli does not duplicate a cleanup failure already reachable through the root cause`() {
        val events = mutableListOf<String>()
        val disposeFailure = IllegalStateException("dispose")
        val closeFailure = IllegalStateException("close", disposeFailure)
        val result = runner(FakeRuntime(events, closeFailure = closeFailure, disposeFailure = disposeFailure)).runResult(validArgs())
        assertEquals(1, result.exitCode)
        assertSame(closeFailure, result.failure)
        assertSame(disposeFailure, closeFailure.cause)
        assertEquals(emptyList(), closeFailure.suppressed.toList())
        assertEquals(listOf("open-session", "close-session", "dispose"), events)
    }

    @Test fun `cli snapshots dispose failure that has fatal close root as cause`() {
        val events = mutableListOf<String>()
        val closeFailure = LinkageError("fatal close")
        val disposeFailure = IllegalStateException("dispose", closeFailure)
        val failure = assertFailsWith<LinkageError> {
            runner(FakeRuntime(events, closeFailure = closeFailure, disposeFailure = disposeFailure)).run(validArgs())
        }
        assertSame(closeFailure, failure)
        assertEquals(1, failure.suppressed.size)
        assertCycleAvoidanceSnapshot(failure.suppressed.single(), disposeFailure)
        assertSame(closeFailure, disposeFailure.cause)
        assertEquals(listOf("open-session", "close-session", "dispose"), events)
    }

    @Test fun `cli snapshots dispose failure that already suppresses fatal close root`() {
        val events = mutableListOf<String>()
        val closeFailure = LinkageError("fatal close")
        val disposeFailure = IllegalStateException("dispose")
        disposeFailure.addSuppressed(closeFailure)
        val failure = assertFailsWith<LinkageError> {
            runner(FakeRuntime(events, closeFailure = closeFailure, disposeFailure = disposeFailure)).run(validArgs())
        }
        assertSame(closeFailure, failure)
        assertEquals(1, closeFailure.suppressed.size)
        assertCycleAvoidanceSnapshot(closeFailure.suppressed.single(), disposeFailure)
        assertEquals(listOf(closeFailure), disposeFailure.suppressed.toList())
        assertEquals(listOf("open-session", "close-session", "dispose"), events)
    }

    @Test fun `cli keeps execution fatal as root and retains both cleanup failures in order`() {
        val events = mutableListOf<String>()
        val closeFailure = IllegalStateException("close")
        val disposeFailure = IllegalArgumentException("dispose")
        val failure = assertFailsWith<LinkageError> {
            runner(FakeRuntime(events, returned = true, executionFatal = true, closeFailure = closeFailure, disposeFailure = disposeFailure)).run(validArgs())
        }
        assertEquals("fatal execution", failure.message)
        assertEquals(listOf(closeFailure, disposeFailure), failure.suppressed.toList())
        assertEquals(listOf("open-session", "execute", "close-session", "dispose"), events)
    }

    @Test fun `cli terminates failure graph traversal on an unrelated cause and suppressed cycle`() {
        val events = mutableListOf<String>()
        val closeFailure = LinkageError("fatal close")
        val cycleHead = IllegalStateException("cycle head")
        val cycleTail = IllegalStateException("cycle tail", cycleHead)
        cycleHead.addSuppressed(cycleTail)
        val disposeFailure = IllegalStateException("dispose", cycleHead)
        val failure = assertFailsWith<LinkageError> {
            runner(FakeRuntime(events, closeFailure = closeFailure, disposeFailure = disposeFailure)).run(validArgs())
        }
        assertSame(closeFailure, failure)
        assertSame(disposeFailure, failure.suppressed.single())
        assertEquals(listOf("open-session", "close-session", "dispose"), events)
    }

    @Test fun `cli propagates execution Error only after close and dispose`() {
        val events = mutableListOf<String>()
        val failure = assertFailsWith<LinkageError> {
            runner(FakeRuntime(events, returned = true, executionFatal = true)).run(validArgs())
        }
        assertEquals("fatal execution", failure.message)
        assertEquals(listOf("open-session", "execute", "close-session", "dispose"), events)
    }

    @Test fun `cli propagates open Error only after close and dispose`() {
        val events = mutableListOf<String>()
        val failure = assertFailsWith<LinkageError> {
            runner(FakeRuntime(events, openFatal = true)).run(validArgs())
        }
        assertEquals("fatal open", failure.message)
        assertEquals(listOf("open-session", "runtime-session-created", "close-session", "dispose"), events)
    }

    @Test fun `cli propagates cleanup Error after still attempting dispose`() {
        val events = mutableListOf<String>()
        val failure = assertFailsWith<LinkageError> {
            runner(FakeRuntime(events, closeFatal = true)).run(validArgs())
        }
        assertEquals("fatal close", failure.message)
        assertEquals(listOf("open-session", "close-session", "dispose"), events)
    }

    @Test fun `cli does not absorb parser Error`() {
        val events = mutableListOf<String>()
        val failure = assertFailsWith<LinkageError> {
            runner(FakeRuntime(events), requestParser = { throw LinkageError("fatal parse") }).run(validArgs())
        }
        assertEquals("fatal parse", failure.message)
        assertEquals(emptyList(), events)
    }

    @Test fun `cli does not write generated evidence for an unexpected render refusal`() {
        val root = Files.createTempDirectory("gpu-evidence-cli-refusal")

        assertEquals(1, runner(OutcomeRuntime(Outcome.UnexpectedRefusal)).run(validArgs(root)))
        assertFalse(Files.exists(root.resolve("reports/gpu-renderer/evidence/correctness/generated")))
    }

    @Test fun `cli does not write generated evidence when rendered pixels fail comparison`() {
        val root = Files.createTempDirectory("gpu-evidence-cli-comparison")
        val runtime = SurfaceComparisonRuntime()
        val evidenceCase = surfaceComparisonCase(runtime)

        assertEquals(1, GpuEvidenceCliRunner(runtime, cases = listOf(evidenceCase)).run(validArgs(root, "surface-comparison")))
        assertFalse(Files.exists(root.resolve("reports/gpu-renderer/evidence/correctness/generated")))
    }

    @Test fun `cli dispatches a Surface program without preparing it through the backend`() {
        val root = Files.createTempDirectory("gpu-evidence-cli-surface")
        val runtime = SurfaceRuntime()
        val surfaceCase = EvidenceCase(
            EvidenceSceneDescriptor(EvidenceSceneId("surface-cli"), "Surface CLI", "Surface dispatch contract.", 1, 1, 1L, emptySet(), EvidenceExpectation.ShouldRender, OraclePolicy.GeneratedCpu("literal-rgba", 1), ComparisonPolicy(0, 100.0, 1, "Exact literal RGBA8 oracle."), emptySet()),
            KanvasSurfaceProgram("kanvas.surface.render", {}, sessionFactory = { _, _, _ ->
                object : KanvasSurfaceRenderSession {
                    override fun render(): RenderResult {
                        runtime.observeSurfaceRender()
                        return RenderResult(ubyteArrayOf(0u, 0u, 0u, 0u), 1, 1, diagnostics = Diagnostics(), stats = RenderStats(1, 0, 1, 1, 1f))
                    }
                }
            }),
            CpuOracle { _, _ -> byteArrayOf(0, 0, 0, 0) },
        )

        assertEquals(0, GpuEvidenceCliRunner(runtime, cases = listOf(surfaceCase)).run(validArgs(root, "surface-cli")))
        assertEquals(1, runtime.surfaceRenders)
        assertEquals(0, runtime.prepareCalls)
        val generatedRoot = root.resolve("reports/gpu-renderer/evidence/correctness/generated/${"a".repeat(40)}")
        assertTrue(Files.isRegularFile(generatedRoot.resolve("catalog.json")))
        assertTrue(Files.isRegularFile(generatedRoot.resolve("environment.json")))
    }

    @Test fun `cli preserves original checked in png bytes for rendered scenes`() {
        val root = Files.createTempDirectory("gpu-evidence-cli-checked-in")
        val runtime = SurfaceRuntime()
        val original = pngBytes(byteArrayOf(1, 2, 3, 4), 1, 1)
        val checkedInCase = EvidenceCase(
            EvidenceSceneDescriptor(
                EvidenceSceneId("checked-in-cli"),
                "Checked in CLI",
                "Checked-in PNG route contract.",
                1,
                1,
                1L,
                emptySet(),
                EvidenceExpectation.ShouldRender,
                OraclePolicy.CheckedInPng("oracle.png", sha256(original), "checked-in-release"),
                ComparisonPolicy(0, 100.0, 1, "Exact literal RGBA8 oracle."),
                emptySet(),
            ),
            KanvasSurfaceProgram("kanvas.surface.render", {}, sessionFactory = { _, _, _ ->
                object : KanvasSurfaceRenderSession {
                    override fun render(): RenderResult {
                        runtime.observeSurfaceRender()
                        return RenderResult(ubyteArrayOf(1u, 2u, 3u, 4u), 1, 1, diagnostics = Diagnostics(), stats = RenderStats(1, 0, 1, 1, 1f))
                    }
                }
            }),
            CpuOracle { _, _ -> byteArrayOf(1, 2, 3, 4) },
        )

        assertEquals(
            0,
            GpuEvidenceCliRunner(
                runtime,
                cases = listOf(checkedInCase),
                checkedInPngLoader = { path -> assertEquals("oracle.png", path); original },
            ).run(validArgs(root, "checked-in-cli")),
        )
        val generatedRoot = root.resolve("reports/gpu-renderer/evidence/correctness/generated/${"a".repeat(40)}")
        assertContentEquals(original, Files.readAllBytes(generatedRoot.resolve("checked-in-cli/skia.png")))
        assertFalse(Files.exists(generatedRoot.resolve("checked-in-cli/cpu.png")))
    }

    @Test fun `cli executes only the selected scenes and writes generated root metadata`() {
        val root = Files.createTempDirectory("gpu-evidence-cli-selection")
        val runtime = SelectionRuntime()
        val cases = listOf(refusalCase("selected-scene"), refusalCase("unselected-scene"))

        val code = GpuEvidenceCliRunner(runtime, cases = cases).run(
            arrayOf("--repository-root", root.toString(), "--source-commit", "a".repeat(40), "--scene", "selected-scene"),
        )

        assertEquals(0, code)
        assertEquals(listOf("selected-scene"), runtime.executedSceneIds)
        val generatedRoot = root.resolve("reports/gpu-renderer/evidence/correctness/generated/${"a".repeat(40)}")
        assertTrue(Files.isRegularFile(generatedRoot.resolve("catalog.json")))
        assertTrue(Files.isRegularFile(generatedRoot.resolve("environment.json")))
        assertTrue(Files.isDirectory(generatedRoot.resolve("selected-scene")))
        assertFalse(Files.exists(generatedRoot.resolve("unselected-scene")))
    }

    @Test fun `late scene failure does not publish a partial generated commit root`() {
        val root = Files.createTempDirectory("gpu-evidence-cli-partial")
        val runtime = LateFailureRuntime("second-scene")
        val cases = listOf(refusalCase("first-scene"), refusalCase("second-scene"))

        assertEquals(
            1,
            GpuEvidenceCliRunner(runtime, cases = cases).run(
                arrayOf("--repository-root", root.toString(), "--source-commit", "a".repeat(40), "--scene", "first-scene", "--scene", "second-scene"),
            ),
        )
        val generatedRoot = root.resolve("reports/gpu-renderer/evidence/correctness/generated/${"a".repeat(40)}")
        assertFalse(Files.exists(generatedRoot))
    }

    @Test fun `catalog staging failure leaves an existing generated commit root unchanged`() {
        val root = Files.createTempDirectory("gpu-evidence-cli-catalog-failure")
        val generatedRoot = root.resolve("reports/gpu-renderer/evidence/correctness/generated/${"a".repeat(40)}")
        Files.createDirectories(generatedRoot)
        Files.writeString(generatedRoot.resolve("sentinel.txt"), "keep")

        val result = GpuEvidenceCliRunner(
            SelectionRuntime(),
            cases = listOf(refusalCase("selected-scene")),
            writeGeneratedCatalog = { _, _, _, _, _ -> error("catalog failure") },
        ).runResult(arrayOf("--repository-root", root.toString(), "--source-commit", "a".repeat(40), "--scene", "selected-scene"))

        assertEquals(1, result.exitCode)
        assertEquals("catalog failure", assertNotNull(result.failure).message)
        assertEquals("keep", Files.readString(generatedRoot.resolve("sentinel.txt")))
        assertFalse(Files.exists(generatedRoot.resolve("selected-scene")))
    }

    @Test fun `publisher restores the old generated root after a partial non-atomic install`() {
        val root = Files.createTempDirectory("gpu-evidence-publisher-partial")
        var moves = 0
        val publisher = GeneratedEvidenceRootPublisher(
            root,
            "a".repeat(40),
            moveStrategy = { source, destination, _ ->
                moves++
                if (moves == 2) {
                    copyTree(source, destination)
                    throw IOException("injected partial non-atomic generated install")
                }
                Files.move(source, destination)
            },
        )
        val staging = publisher.createStagingRepositoryRoot()
        val destination = publisher.generatedRoot(root)
        Files.createDirectories(destination)
        Files.writeString(destination.resolve("sentinel.txt"), "old")
        val staged = publisher.generatedRoot(staging)
        Files.createDirectories(staged)
        Files.writeString(staged.resolve("sentinel.txt"), "new")
        val before = snapshotRegularFiles(destination)

        assertFailsWith<IOException> { publisher.publish(staging) }

        assertEquals(before, snapshotRegularFiles(destination))
    }

    @Test fun `publisher restores the old generated root after a partial first backup move`() {
        val root = Files.createTempDirectory("gpu-evidence-publisher-first-move")
        val publisher = GeneratedEvidenceRootPublisher(
            root,
            "a".repeat(40),
            moveStrategy = { source, destination, _ ->
                if (source == publisherDestination(root)) {
                    Files.createDirectories(destination)
                    Files.copy(source.resolve("sentinel.txt"), destination.resolve("sentinel.txt"))
                    throw IOException("injected partial generated backup move")
                }
                Files.move(source, destination)
            },
        )
        val staging = publisher.createStagingRepositoryRoot()
        val destination = publisher.generatedRoot(root)
        Files.createDirectories(destination)
        Files.writeString(destination.resolve("sentinel.txt"), "old")
        val staged = publisher.generatedRoot(staging)
        Files.createDirectories(staged)
        Files.writeString(staged.resolve("sentinel.txt"), "new")
        val before = snapshotRegularFiles(destination)

        assertFailsWith<IOException> { publisher.publish(staging) }

        assertEquals(before, snapshotRegularFiles(destination))
    }

    @Test fun `publisher removes an incomplete destination after a partial initial install`() {
        val root = Files.createTempDirectory("gpu-evidence-publisher-initial")
        val publisher = GeneratedEvidenceRootPublisher(
            root,
            "a".repeat(40),
            moveStrategy = { source, destination, _ ->
                copyTree(source, destination)
                throw IOException("injected partial initial generated install")
            },
        )
        val staging = publisher.createStagingRepositoryRoot()
        val staged = publisher.generatedRoot(staging)
        Files.createDirectories(staged)
        Files.writeString(staged.resolve("sentinel.txt"), "new")

        assertFailsWith<IOException> { publisher.publish(staging) }

        assertFalse(Files.exists(publisher.generatedRoot(root)))
    }

    @Test fun `publisher does not fail after a successful install when backup cleanup fails`() {
        val root = Files.createTempDirectory("gpu-evidence-publisher-cleanup")
        var cleanupAttempts = 0
        val publisher = GeneratedEvidenceRootPublisher(
            root,
            "a".repeat(40),
            cleanupStrategy = {
                cleanupAttempts++
                throw IOException("injected backup cleanup failure")
            },
        )
        val staging = publisher.createStagingRepositoryRoot()
        val destination = publisher.generatedRoot(root)
        Files.createDirectories(destination)
        Files.writeString(destination.resolve("sentinel.txt"), "old")
        val staged = publisher.generatedRoot(staging)
        Files.createDirectories(staged)
        Files.writeString(staged.resolve("sentinel.txt"), "new")

        val published = publisher.publish(staging)

        assertEquals(destination, published)
        assertEquals("new", Files.readString(destination.resolve("sentinel.txt")))
        assertEquals(1, cleanupAttempts)
    }

    private fun snapshotRegularFiles(root: java.nio.file.Path): Map<String, List<Byte>> =
        Files.walk(root).use { stream ->
            stream.iterator().asSequence().filter(Files::isRegularFile).associate { path ->
                root.relativize(path).toString() to Files.readAllBytes(path).toList()
            }
        }

    private fun copyTree(source: java.nio.file.Path, destination: java.nio.file.Path) {
        Files.walk(source).use { stream ->
            stream.forEach { current ->
                val relative = source.relativize(current)
                val target = destination.resolve(relative.toString())
                if (Files.isDirectory(current)) {
                    Files.createDirectories(target)
                } else {
                    Files.createDirectories(target.parent)
                    Files.copy(current, target)
                }
            }
        }
    }

    private fun publisherDestination(root: java.nio.file.Path): java.nio.file.Path =
        root.resolve("reports/gpu-renderer/evidence/correctness/generated/${"a".repeat(40)}")

    private fun assertCycleAvoidanceSnapshot(snapshot: Throwable, original: Throwable) {
        val message = assertNotNull(snapshot.message)
        assertTrue(message.contains("failure snapshotted to avoid a cycle"))
        assertTrue(message.contains(original.javaClass.name))
        assertTrue(message.contains(original.message ?: "null"))
        assertNull(snapshot.cause)
        assertEquals(emptyList(), snapshot.suppressed.toList())
    }

    private fun runner(
        runtime: EvidenceRuntimePort,
        requestParser: (Array<String>) -> GpuEvidenceCliRequest = GpuEvidenceCliRequest::parse,
    ) = GpuEvidenceCliRunner(runtime, requestParser = requestParser, cases = GpuEvidenceCatalog.refusalCases)

    private fun surfaceComparisonCase(runtime: SurfaceComparisonRuntime) = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId("surface-comparison"),
            "Surface comparison",
            "Deterministic fake Surface pixels intentionally differ from the CPU oracle.",
            1,
            1,
            1L,
            emptySet(),
            EvidenceExpectation.ShouldRender,
            OraclePolicy.GeneratedCpu("literal-zero-rgba", 1),
            ComparisonPolicy(0, 100.0, 1, "Exact fake comparison oracle."),
            emptySet(),
        ),
        KanvasSurfaceProgram("kanvas.surface.render", {}, sessionFactory = { _, _, _ ->
            object : KanvasSurfaceRenderSession {
                override fun render(): RenderResult {
                    runtime.observeSurfaceRender()
                    return RenderResult(
                        ubyteArrayOf(255u, 0u, 0u, 255u),
                        1,
                        1,
                        diagnostics = Diagnostics(),
                        stats = RenderStats(1, 0, 1, 1, 1f),
                    )
                }
            }
        }),
        CpuOracle { _, _ -> byteArrayOf(0, 0, 0, 0) },
    )

    private fun refusalCase(sceneId: String) = EvidenceCase(
        EvidenceSceneDescriptor(
            EvidenceSceneId(sceneId),
            "Refusal $sceneId",
            "Selection contract test case.",
            1,
            1,
            1L,
            emptySet(),
            EvidenceExpectation.ShouldRefuse("unsupported.fake"),
            OraclePolicy.StableRefusal,
            null,
            emptySet(),
        ),
        object : RoutedSceneProgram {
            override val routeId: String = "product.fake"
            override fun prepare(context: SceneRecordingContext): ScenePreparation = error("unused in selection test")
        },
        oracle = null,
    )

    private fun validArgs(root: java.nio.file.Path = Files.createTempDirectory("gpu-evidence-cli"), scene: String = "custom-runtime-effect-unregistered-refusal") = arrayOf("--repository-root", root.toString(), "--source-commit", "a".repeat(40), "--scene", scene)
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun pngBytes(rgba: ByteArray, width: Int, height: Int): ByteArray {
        val file = Files.createTempFile("gpu-evidence-cli", ".png").toFile()
        return try { ComparisonUtils.saveRgbaAsPng(rgba, width, height, file); file.readBytes() } finally { file.delete() }
    }
    private class FakeRuntime(private val events: MutableList<String>, private val returned: Boolean = false, private val closeFails: Boolean = false, private val openFails: Boolean = false, private val executionFails: Boolean = false, private val executionFatal: Boolean = false, private val openFatal: Boolean = false, private val closeFatal: Boolean = false, private val closeFailure: Throwable? = null, private val disposeFailure: Throwable? = null, private val onDisposeFailure: (() -> Unit)? = null) : EvidenceRuntimePort {
        override fun open(): EvidenceBackendPort? { events += "open-session"; if (openFails || openFatal) { events += "runtime-session-created"; if (openFatal) throw LinkageError("fatal open") else error("primary open") }; return if (returned) object : EvidenceBackendPort { override val capabilities: EvidenceCapabilities? = EvidenceCapabilities("fake"); override val deviceGeneration = 1L; override fun telemetry() = org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry(); override fun prepare(program: SceneProgram, context: EvidenceRecordingRequest): EvidenceProgramPreparation { events += "execute"; if (executionFatal) throw LinkageError("fatal execution"); if (executionFails) error("primary execution"); return EvidenceProgramPreparation.Refused("product.fake", "unsupported.fake", "fake", emptyList()) }; override fun prepareSceneFrame(width: Int, height: Int): EvidencePreparedFramePort = error("unreachable") } else null }
        override fun close() { events += "close-session"; closeFailure?.let { throw it }; if (closeFatal) throw LinkageError("fatal close"); if (closeFails) error("close") }
        override fun dispose() { events += "dispose"; disposeFailure?.let { onDisposeFailure?.invoke(); throw it } }
    }

    private enum class Outcome { UnexpectedRefusal, ComparisonFailure }

    private class OutcomeRuntime(private val outcome: Outcome) : EvidenceRuntimePort {
        override fun open(): EvidenceBackendPort = object : EvidenceBackendPort {
            override val capabilities: EvidenceCapabilities? = EvidenceCapabilities("fake")
            override val deviceGeneration: Long = 1L
            private var submissions = 0L
            override fun telemetry() = GPUBackendRuntimeTelemetry(submissions = submissions)
            override fun prepare(program: SceneProgram, context: EvidenceRecordingRequest): EvidenceProgramPreparation = when (outcome) {
                Outcome.UnexpectedRefusal -> EvidenceProgramPreparation.Refused("product.fake", "unsupported.fake", "unexpected refusal", emptyList())
                Outcome.ComparisonFailure -> EvidenceProgramPreparation.Recorded("product.fake", PreparedEvidenceProgram(null, context.readbackRequestId), emptyList())
            }
            override fun prepareSceneFrame(width: Int, height: Int): EvidencePreparedFramePort = object : EvidencePreparedFramePort {
                override fun render(program: PreparedEvidenceProgram): EvidenceCompletedFrame {
                    submissions++
                    return EvidenceCompletedFrame.succeeded(program.readbackRequestId, ByteArray(width * height * 4))
                }
                override fun close() = Unit
            }
        }
        override fun close() = Unit
        override fun dispose() = Unit
    }

    private class SurfaceRuntime : EvidenceRuntimePort {
        var prepareCalls = 0
        var surfaceRenders = 0
        private var submissions = 0L
        fun observeSurfaceRender() { surfaceRenders++; submissions++ }
        override fun open(): EvidenceBackendPort = object : EvidenceBackendPort {
            override val capabilities = EvidenceCapabilities("fake")
            override val deviceGeneration = 1L
            override fun telemetry() = GPUBackendRuntimeTelemetry(submissions = submissions)
            override fun prepare(program: SceneProgram, context: EvidenceRecordingRequest): EvidenceProgramPreparation { prepareCalls++; error("Surface program reached backend preparation") }
            override fun prepareSceneFrame(width: Int, height: Int): EvidencePreparedFramePort = error("Surface program reached prepared frame")
        }
        override fun close() = Unit
        override fun dispose() = Unit
    }

    private class SurfaceComparisonRuntime : EvidenceRuntimePort {
        private var submissions = 0L
        fun observeSurfaceRender() { submissions++ }
        override fun open(): EvidenceBackendPort = object : EvidenceBackendPort {
            override val capabilities = EvidenceCapabilities("fake")
            override val deviceGeneration = 1L
            override fun telemetry() = GPUBackendRuntimeTelemetry(submissions = submissions)
            override fun prepare(program: SceneProgram, context: EvidenceRecordingRequest): EvidenceProgramPreparation = error("Surface program reached backend preparation")
            override fun prepareSceneFrame(width: Int, height: Int): EvidencePreparedFramePort = error("Surface program reached prepared frame")
        }
        override fun close() = Unit
        override fun dispose() = Unit
    }

    private class SelectionRuntime : EvidenceRuntimePort {
        val executedSceneIds = mutableListOf<String>()
        override fun open(): EvidenceBackendPort = object : EvidenceBackendPort {
            override val capabilities = EvidenceCapabilities("fake")
            override val deviceGeneration = 1L
            override fun telemetry() = GPUBackendRuntimeTelemetry()
            override fun prepare(program: SceneProgram, context: EvidenceRecordingRequest): EvidenceProgramPreparation {
                executedSceneIds += context.descriptor.id.value
                return EvidenceProgramPreparation.Refused("product.fake", "unsupported.fake", "selected refusal", emptyList())
            }
            override fun prepareSceneFrame(width: Int, height: Int): EvidencePreparedFramePort = error("unreachable")
        }
        override fun close() = Unit
        override fun dispose() = Unit
    }

    private class LateFailureRuntime(private val failingSceneId: String) : EvidenceRuntimePort {
        override fun open(): EvidenceBackendPort = object : EvidenceBackendPort {
            override val capabilities = EvidenceCapabilities("fake")
            override val deviceGeneration = 1L
            override fun telemetry() = GPUBackendRuntimeTelemetry()
            override fun prepare(program: SceneProgram, context: EvidenceRecordingRequest): EvidenceProgramPreparation {
                val stableReason = if (context.descriptor.id.value == failingSceneId) "unexpected.fake" else "unsupported.fake"
                return EvidenceProgramPreparation.Refused("product.fake", stableReason, "selected refusal", emptyList())
            }
            override fun prepareSceneFrame(width: Int, height: Int): EvidencePreparedFramePort = error("unreachable")
        }
        override fun close() = Unit
        override fun dispose() = Unit
    }
}
