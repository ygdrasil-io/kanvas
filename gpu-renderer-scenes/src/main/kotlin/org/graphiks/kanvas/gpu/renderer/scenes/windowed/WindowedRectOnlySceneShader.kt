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
                if (pixel.x >= ${fill.rect.left.wgslFloat()} && pixel.x < ${fill.rect.right.wgslFloat()} &&
                    pixel.y >= ${fill.rect.top.wgslFloat()} && pixel.y < ${fill.rect.bottom.wgslFloat()}) {
                    color = src_over(color, vec4<f32>(
                        ${fill.color.r.wgslFloat()},
                        ${fill.color.g.wgslFloat()},
                        ${fill.color.b.wgslFloat()},
                        ${fill.color.a.wgslFloat()}
                    ));
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

            fn src_over(dst: vec4<f32>, src: vec4<f32>) -> vec4<f32> {
                let out_alpha = src.a + dst.a * (1.0 - src.a);
                let out_rgb = (src.rgb * src.a + dst.rgb * dst.a * (1.0 - src.a)) / max(out_alpha, 0.0001);
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

private fun GPURendererScene<*>.fills(): List<SceneCommand.FillRect> =
    commands.withIndex()
        .filter { (_, command) -> command is SceneCommand.FillRect }
        .sortedWith(
            compareBy<IndexedValue<Any?>> { (_, command) ->
                (command as SceneCommand.FillRect).paintOrder
            }.thenBy { it.index },
        )
        .map { (_, command) -> command as SceneCommand.FillRect }

private fun Float.wgslFloat(): String = String.format(Locale.US, "%.6f", this)
