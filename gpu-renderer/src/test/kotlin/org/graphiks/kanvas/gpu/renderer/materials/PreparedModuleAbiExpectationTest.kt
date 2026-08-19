package org.graphiks.kanvas.gpu.renderer.materials

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.graphiks.kanvas.gpu.renderer.wgsl.WgslBindingReflection
import org.graphiks.kanvas.gpu.renderer.wgsl.WgslEntryPointReflection
import org.graphiks.kanvas.gpu.renderer.wgsl.WgslLayoutMemberReflection
import org.graphiks.kanvas.gpu.renderer.wgsl.WgslLayoutReflection
import org.graphiks.kanvas.gpu.renderer.wgsl.WgslReflectionReport

class PreparedModuleAbiExpectationTest {
    private val report = WgslReflectionReport(
        sourceId = "test",
        entryPoints = listOf(
            WgslEntryPointReflection("vs_main", "vertex"),
            WgslEntryPointReflection("fs_main", "fragment"),
        ),
        bindings = listOf(
            WgslBindingReflection(
                group = 1,
                binding = 0,
                name = "solidMaterial",
                resourceKind = "uniformBuffer",
                access = "read",
                minBindingSize = 16,
            ),
        ),
        layouts = listOf(
            WgslLayoutReflection(
                structName = "SolidMaterialBlock",
                addressSpace = "uniform",
                size = 16,
                alignment = 16,
                members = listOf(
                    WgslLayoutMemberReflection(
                        name = "color",
                        type = "vec4<f32>",
                        offset = 0,
                        size = 16,
                        alignment = 16,
                    ),
                ),
            ),
        ),
    )
    private val canonical = PreparedModuleAbiExpectation(
        bindings = listOf(
            PreparedAbiBinding(
                group = 1,
                binding = 0,
                resourceKind = "uniformBuffer",
                access = "read",
                minBindingSize = 16,
            ),
        ),
        uniformLayout = PreparedAbiLayout(
            size = 16,
            alignment = 16,
            members = listOf(
                PreparedAbiMember(
                    name = "color",
                    type = "vec4<f32>",
                    offset = 0,
                    size = 16,
                    alignment = 16,
                ),
            ),
        ),
    )

    @Test
    fun `gate rejects deliberate payload layout and resource mismatches`() {
        assertNull(canonical.mismatch(report, uniformPayloadSize = 16))
        assertNotNull(canonical.mismatch(report, uniformPayloadSize = 32))
        assertNotNull(
            canonical.copy(
                uniformLayout = canonical.uniformLayout.copy(
                    members = listOf(
                        canonical.uniformLayout.members.single().copy(offset = 4),
                    ),
                ),
            ).mismatch(report, uniformPayloadSize = 16),
        )
        assertNotNull(
            canonical.copy(
                bindings = listOf(
                    canonical.bindings.single().copy(binding = 1),
                ),
            ).mismatch(report, uniformPayloadSize = 16),
        )
    }

    @Test
    fun `composable gate rejects a constant-size uniform member layout mismatch`() {
        val constantSizeMismatch = report.layouts.single().copy(
            members = listOf(
                report.layouts.single().members.single().copy(
                    name = "otherColor",
                    type = "array<f32, 4>",
                    alignment = 4,
                    stride = 4,
                ),
            ),
        )

        assertNotNull(
            composableUniformLayoutMismatch(
                expected = canonical.uniformLayout,
                uniformBindingSize = 16,
                reflectedLayouts = listOf(constantSizeMismatch),
            ),
        )
    }
}
