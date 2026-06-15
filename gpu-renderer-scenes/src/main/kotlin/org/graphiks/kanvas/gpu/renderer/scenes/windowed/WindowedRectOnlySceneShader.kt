package org.graphiks.kanvas.gpu.renderer.scenes.windowed

import java.util.Locale
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand

object WindowedRectOnlySceneShader {
    fun clearColor(scene: GPURendererScene<*>): SceneColor =
        scene.commands.filterIsInstance<SceneCommand.Clear>().firstOrNull()?.color
            ?: SceneColor(0f, 0f, 0f, 0f)

    fun wgsl(scene: GPURendererScene<*>): String {
        val clear = clearColor(scene)
        val fillBranches = scene.fills().joinToString(separator = "\n") { fill ->
            """
                let ${fill.coverageName()} = rounded_rect_coverage(
                    pixel,
                    vec4<f32>(
                        ${fill.rect.left.wgslFloat()},
                        ${fill.rect.top.wgslFloat()},
                        ${fill.rect.right.wgslFloat()},
                        ${fill.rect.bottom.wgslFloat()}
                    ),
                    ${fill.radius.wgslFloat()}
                );
                if (${fill.coverageName()} > 0.0) {
                    color = src_over_coverage(color, vec4<f32>(
                        ${fill.color.r.wgslFloat()},
                        ${fill.color.g.wgslFloat()},
                        ${fill.color.b.wgslFloat()},
                        ${fill.color.a.wgslFloat()}
                    ), ${fill.coverageName()});
                }
            """.trimIndent()
        }

        return """
            @vertex
            fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4<f32> {
                let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
                let y = f32(idx & 2u) * 2.0 - 1.0;
                return vec4<f32>(x, y, 0.0, 1.0);
            }

            fn rounded_rect_coverage(pixel: vec2<f32>, rect: vec4<f32>, radius: f32) -> f32 {
                if (pixel.x < rect.x || pixel.x >= rect.z || pixel.y < rect.y || pixel.y >= rect.w) {
                    return 0.0;
                }
                if (radius <= 0.0) {
                    return 1.0;
                }
                let clamped_radius = min(radius, min((rect.z - rect.x) * 0.5, (rect.w - rect.y) * 0.5));
                let center = clamp(pixel, rect.xy + vec2<f32>(clamped_radius), rect.zw - vec2<f32>(clamped_radius));
                let edge_distance = length(pixel - center) - clamped_radius;
                return clamp(0.5 - edge_distance, 0.0, 1.0);
            }

            fn src_over_coverage(dst: vec4<f32>, src: vec4<f32>, coverage: f32) -> vec4<f32> {
                let src_alpha = src.a * coverage;
                let out_alpha = src_alpha + dst.a * (1.0 - src_alpha);
                let out_rgb = (src.rgb * src_alpha + dst.rgb * dst.a * (1.0 - src_alpha)) / max(out_alpha, 0.0001);
                return vec4<f32>(out_rgb, out_alpha);
            }

            @fragment
            fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
                let pixel = position.xy;
                var color = vec4<f32>(
                    ${clear.r.wgslFloat()},
                    ${clear.g.wgslFloat()},
                    ${clear.b.wgslFloat()},
                    ${clear.a.wgslFloat()}
                );
            ${fillBranches.prependIndent("    ")}
                return color;
            }
        """.trimIndent()
    }
}

private data class WindowedFill(
    val label: String,
    val rect: org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect,
    val radius: Float,
    val color: SceneColor,
)

private fun GPURendererScene<*>.fills(): List<WindowedFill> =
    commands.withIndex()
        .filter { (_, command) -> command is SceneCommand.FillRect || command is SceneCommand.FillRRect }
        .sortedWith(
            compareBy<IndexedValue<Any?>> { (_, command) ->
                when (command) {
                    is SceneCommand.FillRect -> command.paintOrder
                    is SceneCommand.FillRRect -> command.paintOrder
                    else -> 0
                }
            }.thenBy { it.index },
        )
        .map { (_, command) ->
            when (command) {
                is SceneCommand.FillRect -> WindowedFill(
                    label = command.label,
                    rect = command.rect,
                    radius = 0f,
                    color = command.color,
                )
                is SceneCommand.FillRRect -> WindowedFill(
                    label = command.label,
                    rect = command.rect,
                    radius = command.radius,
                    color = command.color,
                )
                else -> error("Unsupported windowed fill command")
            }
        }

private fun WindowedFill.coverageName(): String =
    "coverage_" + label.replace(Regex("[^A-Za-z0-9_]"), "_")

private fun Float.wgslFloat(): String = String.format(Locale.US, "%.6f", this)
