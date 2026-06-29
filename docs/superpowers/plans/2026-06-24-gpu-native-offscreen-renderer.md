# GpuNativeOffscreenRenderer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace solid-color diagnostic PNGs with real gradient WGSL renders for gradient scenes (linear-gradient-lanes, radial-swatch, sweep-disk).

**Architecture:** Compose inline WGSL per gradient type (vertex fullscreen triangle + fragment with gradient math reading from uniforms). Reuse existing `GPUBackendRenderRecorder.drawFullscreenUniformPayloadPass` for execution. GpuNativeOffscreenRenderer extends the same session/target lifecycle as RectOnlyOffscreenRenderer.

**Tech Stack:** Kotlin/JVM, WebGPU (wgpu4k), WGSL

---

### Task 1: WgslComposer — WGSL composition helpers

**Files:**
- Create: `gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/WgslComposer.kt`

- [ ] **Step 1: Create WgslComposer.kt**

```kotlin
package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

object WgslComposer {

    private val vsMain: String = """
@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}
"""

    fun linearGradientWgsl(): String = """
struct Uniforms { start: vec4f, end: vec4f, startColor: vec4f, endColor: vec4f };

@group(0) @binding(0) var<uniform> uniforms: Uniforms;

$vsMain

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let dir = uniforms.end.xy - uniforms.start.xy;
    let lenSq = dot(dir, dir);
    var t = -1.0e30;
    if (lenSq >= 1.0e-12) {
        t = dot(pos.xy - uniforms.start.xy, dir) / lenSq;
    }
    t = clamp(t, 0.0, 1.0);
    return mix(uniforms.startColor, uniforms.endColor, t);
}
"""

    fun radialGradientWgsl(): String = """
struct Uniforms { center: vec4f, startColor: vec4f, endColor: vec4f };

@group(0) @binding(0) var<uniform> uniforms: Uniforms;

$vsMain

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let radius = uniforms.center.z;
    var t = -1.0e30;
    if (radius > 0.0) {
        t = length(pos.xy - uniforms.center.xy) / radius;
    }
    t = clamp(t, 0.0, 1.0);
    return mix(uniforms.startColor, uniforms.endColor, t);
}
"""

    fun sweepGradientWgsl(): String = """
const TWO_PI: f32 = 6.2831853071795864;

struct Uniforms { center: vec4f, angles: vec4f, startColor: vec4f, endColor: vec4f };

@group(0) @binding(0) var<uniform> uniforms: Uniforms;

$vsMain

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let d = pos.xy - uniforms.center.xy;
    var t = 0.0;
    if (d.x != 0.0 || d.y != 0.0) {
        let a = atan2(d.y, d.x);
        var u = a / TWO_PI;
        if (u < 0.0) { u = u + 1.0; }
        let sweep = uniforms.angles.y - uniforms.angles.x;
        if (sweep > 0.0) {
            t = (u - uniforms.angles.x / 360.0) * (360.0 / sweep);
        }
    }
    t = clamp(t, 0.0, 1.0);
    return mix(uniforms.startColor, uniforms.endColor, t);
}
"""

    fun solidColorWgsl(): String = """
struct Uniforms { color: vec4f };

@group(0) @binding(0) var<uniform> uniforms: Uniforms;

$vsMain

@fragment
fn fs_main() -> @location(0) vec4f {
    return uniforms.color;
}
"""
}
```

- [ ] **Step 2: Commit**

```
git add gpu-renderer-scenes/.../offscreen/WgslComposer.kt
git commit -m "KGPU-M14-005: add WgslComposer with gradient WGSL helpers"
```

### Task 2: UniformPacker — uniform byte packing

**Files:**
- Create: `gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/UniformPacker.kt`

- [ ] **Step 1: Create UniformPacker.kt**

```kotlin
package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import java.nio.ByteBuffer
import java.nio.ByteOrder

object UniformPacker {

    fun linearGradientBytes(
        startX: Float, startY: Float, endX: Float, endY: Float,
        startColor: SceneColor, endColor: SceneColor,
    ): ByteArray {
        val buf = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        buf.putFloat(startX); buf.putFloat(startY); buf.putFloat(0f); buf.putFloat(0f) // start vec4
        buf.putFloat(endX); buf.putFloat(endY); buf.putFloat(0f); buf.putFloat(0f)     // end vec4
        buf.putFloat(startColor.r); buf.putFloat(startColor.g); buf.putFloat(startColor.b); buf.putFloat(startColor.a) // startColor vec4
        buf.putFloat(endColor.r); buf.putFloat(endColor.g); buf.putFloat(endColor.b); buf.putFloat(endColor.a)         // endColor vec4
        return buf.array()
    }

    fun radialGradientBytes(
        centerX: Float, centerY: Float, radius: Float,
        startColor: SceneColor, endColor: SceneColor,
    ): ByteArray {
        val buf = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN)
        buf.putFloat(centerX); buf.putFloat(centerY); buf.putFloat(radius); buf.putFloat(0f) // center vec4
        buf.putFloat(startColor.r); buf.putFloat(startColor.g); buf.putFloat(startColor.b); buf.putFloat(startColor.a)
        buf.putFloat(endColor.r); buf.putFloat(endColor.g); buf.putFloat(endColor.b); buf.putFloat(endColor.a)
        return buf.array()
    }

    fun sweepGradientBytes(
        centerX: Float, centerY: Float, startAngle: Float, endAngle: Float,
        startColor: SceneColor, endColor: SceneColor,
    ): ByteArray {
        val buf = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        buf.putFloat(centerX); buf.putFloat(centerY); buf.putFloat(0f); buf.putFloat(0f)    // center vec4
        buf.putFloat(startAngle); buf.putFloat(endAngle); buf.putFloat(0f); buf.putFloat(0f) // angles vec4
        buf.putFloat(startColor.r); buf.putFloat(startColor.g); buf.putFloat(startColor.b); buf.putFloat(startColor.a)
        buf.putFloat(endColor.r); buf.putFloat(endColor.g); buf.putFloat(endColor.b); buf.putFloat(endColor.a)
        return buf.array()
    }

    fun solidColorBytes(color: SceneColor): ByteArray {
        val buf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        buf.putFloat(color.r); buf.putFloat(color.g); buf.putFloat(color.b); buf.putFloat(color.a)
        return buf.array()
    }
}
```

- [ ] **Step 2: Commit**

```
git add gpu-renderer-scenes/.../offscreen/UniformPacker.kt
git commit -m "KGPU-M14-005: add UniformPacker for gradient uniform bytes"
```

### Task 3: GpuNativeOffscreenRenderer

**Files:**
- Create: `gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/GpuNativeOffscreenRenderer.kt`
- Modify: `gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/RenderGpuRendererSceneOffscreenMain.kt`

- [ ] **Step 1: Create GpuNativeOffscreenRenderer.kt**

```kotlin
package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import java.nio.file.Path
import kotlin.io.path.createDirectories
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRectDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendUniformPayloadDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUClearColor
import org.graphiks.kanvas.gpu.renderer.execution.GPUOffscreenTargetRequest
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand

class GpuNativeOffscreenRenderer {
    fun render(scene: GPURendererScene<SceneCommand>, outputDir: Path): OffscreenRunReport {
        val sceneId = scene.sceneId.value
        outputDir.createDirectories()

        val runtime = GPUBackendRuntimeFactory.createOrNull()
            ?: return OffscreenRunReport.failed(sceneId, "webgpu-context-unavailable")

        runtime.use { session ->
            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(
                    width = scene.dimensions.width,
                    height = scene.dimensions.height,
                    colorFormat = OFFSCREEN_COLOR_FORMAT,
                ),
            ).use { target ->
                val drawPlan = buildNativeDrawPlan(sceneId, scene.commands)
                val pixels = renderToPixels(target, drawPlan)
                val imagePath = outputDir.resolve(RENDER_FILE_NAME)
                val width = target.target.descriptor.width
                val height = target.target.descriptor.height
                RectOnlyOffscreenRenderer().writePng(pixels, width, height, imagePath)
                return OffscreenRunReport.rendered(
                    sceneId = sceneId,
                    imagePath = RENDER_FILE_NAME,
                    width = width,
                    height = height,
                    byteCount = (width * height * 4).toLong(),
                    nonTransparentPixels = pixels.countNonTransparentPixels(),
                    diagnostics = nativeRenderedDiagnostics(sceneId, scene.commands),
                )
            }
        }
    }

    internal fun renderToPixels(
        target: org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTarget,
        drawPlan: NativeDrawPlan,
    ): ByteArray {
        target.encode(
            clearColor = GPUClearColor(
                red = drawPlan.clearColor.r.toDouble(),
                green = drawPlan.clearColor.g.toDouble(),
                blue = drawPlan.clearColor.b.toDouble(),
                alpha = drawPlan.clearColor.a.toDouble(),
            ),
        ) {
            drawPlan.groups.forEach { group ->
                val wgsl = group.wgsl
                val draws = group.draws
                it.drawFullscreenUniformPayloadPass(
                    wgsl = wgsl,
                    colorFormat = OFFSCREEN_COLOR_FORMAT,
                    draws = draws,
                )
            }
        }
        return target.readRgba()
    }
}
```

Plus the supporting types (NativeDrawPlan, NativeDrawGroup, etc.) and diagnostic/validation helpers.

- [ ] **Step 2: Modify RenderGpuRendererSceneOffscreenMain.kt** to detect gradient scenes and use GpuNativeOffscreenRenderer

Add a `supportsNativeOffscreen()` check and dispatch to `GpuNativeOffscreenRenderer().render()` for gradient scenes.

- [ ] **Step 3: Commit**

```
git add gpu-renderer-scenes/.../offscreen/GpuNativeOffscreenRenderer.kt
git add gpu-renderer-scenes/.../offscreen/RenderGpuRendererSceneOffscreenMain.kt
git commit -m "KGPU-M14-005: add GpuNativeOffscreenRenderer"
```

### Task 4: Re-render scene evidence PNGs

**Files:**
- Modify: `reports/gpu-renderer-scenes/offscreen/linear-gradient-lanes/render.png`
- Modify: `reports/gpu-renderer-scenes/offscreen/radial-swatch/render.png`
- Modify: `reports/gpu-renderer-scenes/offscreen/sweep-disk/render.png`

- [ ] **Step 1: Re-render linear-gradient-lanes**

`rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=linear-gradient-lanes`

- [ ] **Step 2: Re-render radial-swatch**

`rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=radial-swatch`

- [ ] **Step 3: Re-render sweep-disk**

`rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=sweep-disk`

- [ ] **Step 4: Commit updated PNGs**

```
git add reports/gpu-renderer-scenes/offscreen/linear-gradient-lanes/
git add reports/gpu-renderer-scenes/offscreen/radial-swatch/
git add reports/gpu-renderer-scenes/offscreen/sweep-disk/
git commit -m "KGPU-M14-005: update offscreen scene evidence with real gradient renders"
```

### Task 5: Update ticket status + push

- [ ] **Step 1: Update KGPU-M14-005.md** status to `done` with status notes

- [ ] **Step 2: Update README.md** and STATUS.md

- [ ] **Step 3: Push**

```
git push
```
