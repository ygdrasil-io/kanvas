package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WGSLSecurityValidatorTest {

    @Test
    fun `clean WGSL passes all security checks`() {
        val module = WGSLParsedModule(
            sourceHash = "sha256:abc",
            uniforms = listOf("u_color"),
            textures = emptyList(),
            bindGroups = listOf("group0"),
            loopIterationCount = 2,
            functionDepth = 3,
        )
        val report = WGSLSecurityValidator().validateSecurity(module)
        assertTrue(report.isSecure, "clean WGSL should pass security")
        assertTrue(report.errors.isEmpty())
    }

    @Test
    fun `each blocked feature produces an error`() {
        val cases = listOf(
            Triple("atomic", WGSLParsedModule(sourceHash = "sha256:1", usesAtomics = true), "custom-wgsl.unsafe-atomic"),
            Triple("unbounded storage buffer", WGSLParsedModule(sourceHash = "sha256:2", usesUnboundedStorageBuffers = true), "custom-wgsl.unsafe-storage-buffer"),
            Triple("read-write buffer", WGSLParsedModule(sourceHash = "sha256:3", usesReadWriteBuffers = true), "custom-wgsl.unsafe-read-write-buffer"),
            Triple("ptr operations", WGSLParsedModule(sourceHash = "sha256:4", usesPtrOperations = true), "custom-wgsl.unsafe-ptr"),
            Triple("recursive functions", WGSLParsedModule(sourceHash = "sha256:5", hasRecursiveFunctions = true), "custom-wgsl.unsafe-recursion"),
            Triple("unbounded loops", WGSLParsedModule(sourceHash = "sha256:6", hasUnboundedLoops = true), "custom-wgsl.unsafe-loop"),
            Triple("dynamic sampling", WGSLParsedModule(sourceHash = "sha256:7", usesDynamicSampling = true), "custom-wgsl.unsafe-dynamic-sampling"),
            Triple("texture store", WGSLParsedModule(sourceHash = "sha256:8", usesTextureStore = true), "custom-wgsl.unsafe-texture-store"),
            Triple("dynamic binding", WGSLParsedModule(sourceHash = "sha256:9", usesDynamicBinding = true), "custom-wgsl.unsafe-dynamic-binding"),
            Triple("compute shader", WGSLParsedModule(sourceHash = "sha256:10", usesComputeShader = true), "custom-wgsl.unsafe-compute"),
            Triple("workgroup builtins", WGSLParsedModule(sourceHash = "sha256:11", usesWorkgroupBuiltins = true), "custom-wgsl.unsafe-workgroup"),
        )
        for ((label, module, expectedCode) in cases) {
            val report = WGSLSecurityValidator().validateSecurity(module)
            assertFalse(report.isSecure, "$label should be blocked")
            assertTrue(report.errors.isNotEmpty(), "$label should produce at least one error")
            assertEquals(expectedCode, report.errors.first().code, "$label should have error code $expectedCode")
            assertTrue(report.errors.all { it.severity == WGSLSecurityErrorSeverity.ERROR })
        }
    }

    @Test
    fun `ray query is blocked when device does not support it`() {
        val module = WGSLParsedModule(sourceHash = "sha256:ray", usesRayQuery = true)
        val capabilities = WGSLDeviceCapabilities(supportsRayQuery = false)
        val report = WGSLSecurityValidator(capabilities).validateSecurity(module)
        assertFalse(report.isSecure)
        assertEquals("custom-wgsl.unsafe-ray-query", report.errors.first().code)
    }

    @Test
    fun `ray query is allowed when device supports it`() {
        val module = WGSLParsedModule(sourceHash = "sha256:ray", usesRayQuery = true)
        val capabilities = WGSLDeviceCapabilities(supportsRayQuery = true)
        val report = WGSLSecurityValidator(capabilities).validateSecurity(module)
        assertTrue(report.isSecure)
    }

    @Test
    fun `too many uniforms is blocked`() {
        val module = WGSLParsedModule(
            sourceHash = "sha256:many",
            uniforms = (1..20).map { "u$it" },
        )
        val report = WGSLSecurityValidator().validateSecurity(module)
        assertFalse(report.isSecure)
        assertEquals("custom-wgsl.uniform-count-exceeded", report.errors.first().code)
    }

    @Test
    fun `too many textures is blocked`() {
        val module = WGSLParsedModule(
            sourceHash = "sha256:many_tex",
            textures = (1..10).map { "t$it" },
        )
        val report = WGSLSecurityValidator().validateSecurity(module)
        assertFalse(report.isSecure)
        assertEquals("custom-wgsl.texture-count-exceeded", report.errors.first().code)
    }

    @Test
    fun `too many bind groups is blocked`() {
        val module = WGSLParsedModule(
            sourceHash = "sha256:many_groups",
            bindGroups = (1..5).map { "g$it" },
        )
        val report = WGSLSecurityValidator().validateSecurity(module)
        assertFalse(report.isSecure)
        assertEquals("custom-wgsl.bind-group-count-exceeded", report.errors.first().code)
    }

    @Test
    fun `excessive loop iterations is blocked`() {
        val module = WGSLParsedModule(
            sourceHash = "sha256:loops",
            loopIterationCount = 2000,
        )
        val report = WGSLSecurityValidator().validateSecurity(module)
        assertFalse(report.isSecure)
        assertEquals("custom-wgsl.loop-iteration-exceeded", report.errors.first().code)
    }

    @Test
    fun `excessive function depth is blocked`() {
        val module = WGSLParsedModule(
            sourceHash = "sha256:deep",
            functionDepth = 10,
        )
        val report = WGSLSecurityValidator().validateSecurity(module)
        assertFalse(report.isSecure)
        assertEquals("custom-wgsl.function-depth-exceeded", report.errors.first().code)
    }

    @Test
    fun `at-limit resource counts pass security`() {
        val module = WGSLParsedModule(
            sourceHash = "sha256:at_limit",
            uniforms = (1..16).map { "u$it" },
            textures = (1..8).map { "t$it" },
            bindGroups = (1..4).map { "g$it" },
            loopIterationCount = 1024,
            functionDepth = 8,
        )
        val report = WGSLSecurityValidator().validateSecurity(module)
        assertTrue(report.isSecure, "values at max limits should pass")
        assertEquals(emptyList(), report.errors)
    }

    @Test
    fun `texture dimensions exceeding device capacity is blocked`() {
        val module = WGSLParsedModule(
            sourceHash = "sha256:big_tex",
            textures = listOf("t0"),
            maxTextureDimensions = 8192,
        )
        val capabilities = WGSLDeviceCapabilities(maxTextureDimensions = 4096)
        val report = WGSLSecurityValidator(capabilities).validateSecurity(module)
        assertFalse(report.isSecure)
        assertEquals("custom-wgsl.texture-dimension-exceeded", report.errors.first().code)
    }

    @Test
    fun `unbounded storage buffer with storage buffers present is doubly caught`() {
        val module = WGSLParsedModule(
            sourceHash = "sha256:bad_storage",
            storageBuffers = listOf("sb0"),
            usesUnboundedStorageBuffers = true,
        )
        val report = WGSLSecurityValidator().validateSecurity(module)
        assertFalse(report.isSecure)
        val codes = report.errors.map { it.code }
        assertTrue("custom-wgsl.unsafe-storage-buffer" in codes)
        assertTrue(report.errors.size >= 2, "should catch in both blocked features and bounds check")
    }

    @Test
    fun `texture store with dynamic sampling is doubly caught`() {
        val module = WGSLParsedModule(
            sourceHash = "sha256:bad_tex",
            textures = listOf("t0"),
            usesDynamicSampling = true,
            usesTextureStore = true,
        )
        val report = WGSLSecurityValidator().validateSecurity(module)
        assertFalse(report.isSecure)
        val codes = report.errors.map { it.code }
        assertTrue("custom-wgsl.unsafe-dynamic-sampling" in codes)
        assertTrue("custom-wgsl.unsafe-texture-store" in codes)
    }

    @Test
    fun `atomics are blocked when device does not support them`() {
        val module = WGSLParsedModule(sourceHash = "sha256:at", usesAtomics = true)
        val capabilities = WGSLDeviceCapabilities(supportsAtomics = false)
        val report = WGSLSecurityValidator(capabilities).validateSecurity(module)
        assertFalse(report.isSecure)
        val codes = report.errors.map { it.code }
        assertTrue("custom-wgsl.unsafe-atomic" in codes)
        assertTrue("custom-wgsl.device-unsupported" in codes)
    }

    @Test
    fun `security report errors carry code message and severity`() {
        val module = WGSLParsedModule(sourceHash = "sha256:err", usesAtomics = true)
        val report = WGSLSecurityValidator().validateSecurity(module)
        val error = report.errors.first()
        assertEquals("custom-wgsl.unsafe-atomic", error.code)
        assertTrue(error.message.isNotBlank())
        assertEquals(WGSLSecurityErrorSeverity.ERROR, error.severity)
    }
}
