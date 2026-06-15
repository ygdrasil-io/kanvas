package org.graphiks.kanvas.gpu.renderer.scenes.windowed

import java.util.Locale
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneBitmapSampling
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect

object WindowedRectOnlySceneShader {
    fun clearColor(scene: GPURendererScene<*>): SceneColor =
        scene.commands.filterIsInstance<SceneCommand.Clear>().firstOrNull()?.color
            ?: SceneColor(0f, 0f, 0f, 0f)

    fun wgsl(scene: GPURendererScene<*>): String {
        val clear = clearColor(scene)
        val drawBranches = scene.draws().joinToString(separator = "\n") { draw ->
            """
                let ${draw.shapeCoverageName()} = rounded_rect_coverage(
                    pixel,
                    vec4<f32>(
                        ${draw.rect.left.wgslFloat()},
                        ${draw.rect.top.wgslFloat()},
                        ${draw.rect.right.wgslFloat()},
                        ${draw.rect.bottom.wgslFloat()}
                    ),
                    ${draw.radius.wgslFloat()}
                );
                let ${draw.coverageName()} = ${draw.shapeCoverageName()} * ${draw.clipCoverageExpression()};
                if (${draw.coverageName()} > 0.0) {
                    let ${draw.colorName()} = draw_color(
                        pixel,
                        vec4<f32>(
                            ${draw.rect.left.wgslFloat()},
                            ${draw.rect.top.wgslFloat()},
                            ${draw.rect.right.wgslFloat()},
                            ${draw.rect.bottom.wgslFloat()}
                        ),
                        vec4<f32>(
                            ${draw.startColor.r.wgslFloat()},
                            ${draw.startColor.g.wgslFloat()},
                            ${draw.startColor.b.wgslFloat()},
                            ${draw.startColor.a.wgslFloat()}
                        ),
                        vec4<f32>(
                            ${draw.endColor.r.wgslFloat()},
                            ${draw.endColor.g.wgslFloat()},
                            ${draw.endColor.b.wgslFloat()},
                            ${draw.endColor.a.wgslFloat()}
                        ),
                        vec4<f32>(
                            ${draw.bottomLeftColor.r.wgslFloat()},
                            ${draw.bottomLeftColor.g.wgslFloat()},
                            ${draw.bottomLeftColor.b.wgslFloat()},
                            ${draw.bottomLeftColor.a.wgslFloat()}
                        ),
                        vec4<f32>(
                            ${draw.bottomRightColor.r.wgslFloat()},
                            ${draw.bottomRightColor.g.wgslFloat()},
                            ${draw.bottomRightColor.b.wgslFloat()},
                            ${draw.bottomRightColor.a.wgslFloat()}
                        ),
                        ${draw.paintKind.wgslFloat()}
                    );
                    color = src_over_coverage(color, ${draw.colorName()}, ${draw.coverageName()});
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

            fn rect_coverage(pixel: vec2<f32>, rect: vec4<f32>) -> f32 {
                if (pixel.x < rect.x || pixel.x >= rect.z || pixel.y < rect.y || pixel.y >= rect.w) {
                    return 0.0;
                }
                return 1.0;
            }

            fn draw_color(
                pixel: vec2<f32>,
                rect: vec4<f32>,
                start_color: vec4<f32>,
                end_color: vec4<f32>,
                bottom_left_color: vec4<f32>,
                bottom_right_color: vec4<f32>,
                paint_kind: f32
            ) -> vec4<f32> {
                var color = start_color;
                if (paint_kind >= 2.5) {
                    let uv = clamp((pixel - rect.xy) / max(rect.zw - rect.xy, vec2<f32>(0.0001)), vec2<f32>(0.0), vec2<f32>(1.0));
                    let top = mix(start_color, end_color, uv.x);
                    let bottom = mix(bottom_left_color, bottom_right_color, uv.x);
                    color = mix(top, bottom, uv.y);
                } else if (paint_kind >= 1.5) {
                    let uv = clamp((pixel - rect.xy) / max(rect.zw - rect.xy, vec2<f32>(0.0001)), vec2<f32>(0.0), vec2<f32>(1.0));
                    if (uv.y >= 0.5) {
                        if (uv.x >= 0.5) {
                            color = bottom_right_color;
                        } else {
                            color = bottom_left_color;
                        }
                    } else if (uv.x >= 0.5) {
                        color = end_color;
                    }
                } else if (paint_kind >= 0.5) {
                    let t = clamp((pixel.y - rect.y) / max(rect.w - rect.y, 0.0001), 0.0, 1.0);
                    color = mix(start_color, end_color, t);
                }
                return color;
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
            ${drawBranches.prependIndent("    ")}
                return color;
            }
        """.trimIndent()
    }
}

private data class WindowedDraw(
    val label: String,
    val rect: org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect,
    val radius: Float,
    val startColor: SceneColor,
    val endColor: SceneColor,
    val bottomLeftColor: SceneColor,
    val bottomRightColor: SceneColor,
    val paintKind: Float,
    val clip: SceneRect?,
)

private data class IndexedWindowedDraw(
    val index: Int,
    val command: SceneCommand,
    val clip: SceneRect?,
)

private fun GPURendererScene<*>.draws(): List<WindowedDraw> {
    var activeClip: SceneRect? = null
    val indexedDraws = buildList {
        commands.withIndex().forEach { (index, command) ->
            when (command) {
                is SceneCommand.Clip -> activeClip = command.rect
                is SceneCommand.FillRect,
                is SceneCommand.FillRRect,
                is SceneCommand.LinearGradientRect -> add(IndexedWindowedDraw(index, command, activeClip))
                is SceneCommand.BitmapRect -> if (command.hasFixturePayload) {
                    add(IndexedWindowedDraw(index, command, activeClip))
                }
                else -> Unit
            }
        }
    }

    return indexedDraws
        .sortedWith(
            compareBy<IndexedWindowedDraw> { (_, command) -> command.paintOrder() }
                .thenBy { it.index },
        )
        .map { (_, command, clip) ->
            when (command) {
                is SceneCommand.FillRect -> WindowedDraw(
                    label = command.label,
                    rect = command.rect,
                    radius = 0f,
                    startColor = command.color,
                    endColor = command.color,
                    bottomLeftColor = command.color,
                    bottomRightColor = command.color,
                    paintKind = 0f,
                    clip = clip,
                )
                is SceneCommand.FillRRect -> WindowedDraw(
                    label = command.label,
                    rect = command.rect,
                    radius = command.radius,
                    startColor = command.color,
                    endColor = command.color,
                    bottomLeftColor = command.color,
                    bottomRightColor = command.color,
                    paintKind = 0f,
                    clip = clip,
                )
                is SceneCommand.LinearGradientRect -> WindowedDraw(
                    label = command.label,
                    rect = command.rect,
                    radius = 0f,
                    startColor = command.startColor,
                    endColor = command.endColor,
                    bottomLeftColor = command.startColor,
                    bottomRightColor = command.endColor,
                    paintKind = 1f,
                    clip = clip,
                )
                is SceneCommand.BitmapRect -> WindowedDraw(
                    label = command.label,
                    rect = command.rect ?: error("BitmapRect requires rect fixture payload: ${command.label}"),
                    radius = 0f,
                    startColor = command.source?.topLeft
                        ?: error("BitmapRect requires source fixture payload: ${command.label}"),
                    endColor = command.source.topRight,
                    bottomLeftColor = command.source.bottomLeft,
                    bottomRightColor = command.source.bottomRight,
                    paintKind = when (command.sampling) {
                        SceneBitmapSampling.Nearest -> 2f
                        SceneBitmapSampling.Linear -> 3f
                    },
                    clip = clip,
                )
                else -> error("Unsupported windowed fill command")
            }
        }
}

private fun SceneCommand.paintOrder(): Int =
    when (this) {
        is SceneCommand.FillRect -> paintOrder
        is SceneCommand.FillRRect -> paintOrder
        is SceneCommand.LinearGradientRect -> paintOrder
        is SceneCommand.BitmapRect -> paintOrder
        else -> 0
    }

private fun WindowedDraw.shapeCoverageName(): String = "shape_coverage_" + safeName()

private fun WindowedDraw.coverageName(): String = "coverage_" + safeName()

private fun WindowedDraw.colorName(): String = "draw_color_" + safeName()

private fun WindowedDraw.safeName(): String = label.replace(Regex("[^A-Za-z0-9_]"), "_")

private fun WindowedDraw.clipCoverageExpression(): String =
    clip?.let { clipRect ->
        """
            rect_coverage(
                pixel,
                vec4<f32>(
                    ${clipRect.left.wgslFloat()},
                    ${clipRect.top.wgslFloat()},
                    ${clipRect.right.wgslFloat()},
                    ${clipRect.bottom.wgslFloat()}
                )
            )
        """.trimIndent()
    } ?: "1.0"

private fun Float.wgslFloat(): String = String.format(Locale.US, "%.6f", this)
