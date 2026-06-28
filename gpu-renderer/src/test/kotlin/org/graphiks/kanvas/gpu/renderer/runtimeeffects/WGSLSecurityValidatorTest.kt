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
            "atomic" to WGSLParsedModule(sourceHash = "sha256:1", usesAtomics = true),
            "unbounded storage buffer" to WGSLParsedModule(sourceHash = "sha256:2", usesUnboundedStorageBuffers = true),
            "read-write buffer" to WGSLParsedModule(sourceHash = "sha256:3", usesReadWriteBuffers = true),
            "ptr operations" to WGSLParsedModule(sourceHash = "sha256:4", usesPtrOperations = true),
            "recursive functions" to WGSLParsedModule(sourceHash = "sha256:5", hasRecursiveFunctions = true),
            "unbounded loops" to WGSLParsedModule(sourceHash = "sha256:6", hasUnboundedLoops = true),
            "dynamic sampling" to WGSLParsedModule(sourceHash = "sha256:7", usesDynamicSampling = true),
            "texture store" to WGSLParsedModule(sourceHash = "sha256:8", usesTextureStore = true),
            "dynamic binding" to WGSLParsedModule(sourceHash = "sha256:9", usesDynamicBinding = true),
            "compute shader" to WGSLParsedModule(sourceHash = "sha256:10", usesComputeShader = true),
            "workgroup builtins" to WGSLParsedModule(sourceHash = "sha256:11", usesWorkgroupBuiltins = true),
        )
        for ((label, module) in cases) {
            val report = WGSLSecurityValidator().validateSecurity(module)
            assertFalse(report.isSecure, "$label should be blocked")
            assertTrue(report.errors.isNotEmpty(), "$label should produce at least one error")
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
