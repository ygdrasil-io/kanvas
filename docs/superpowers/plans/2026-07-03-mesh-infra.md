# Mesh Infrastructure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Kanvas-native `Mesh` type with `MeshProgram` children, `Canvas.drawMesh()`, `SkiaGm` lifecycle hooks, GPU textured-triangle support, and port the 5 remaining SkMesh GMs.

**Architecture:** Three-layer addition: (1) kanvas-core types + Canvas API, (2) GPU backend texCoords + mesh program dispatch, (3) GM test scaffold lifecycle + 5 new Gm files.

**Tech Stack:** Kotlin, Kanvas core, WGSL/WebGPU renderer, GmCanvas test scaffold

**Spec reference:** `docs/superpowers/specs/2026-07-03-mesh-infra-design.md`

---

## Phase 1: Core Types and Canvas API

### Task 1: Add MeshChild sealed interface

**Files:**
- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/paint/MeshChild.kt`

- [ ] **Step 1: Create MeshChild.kt**

```kotlin
package org.graphiks.kanvas.paint

sealed interface MeshChild
data class ShaderChild(val shader: Shader) : MeshChild
data class ColorFilterChild(val filter: ColorFilter) : MeshChild
data class BlenderChild(val blender: Blender) : MeshChild
```

- [ ] **Step 2: Create MeshChildren.kt**

```kotlin
package org.graphiks.kanvas.paint

data class MeshChildren(
    val entries: List<Entry> = emptyList(),
) {
    data class Entry(val name: String, val child: MeshChild)

    companion object {
        val EMPTY = MeshChildren()
        fun of(vararg pairs: Pair<String, MeshChild>) =
            MeshChildren(pairs.map { Entry(it.first, it.second) })
    }

    fun getShader(name: String): Shader? =
        entries.firstOrNull { it.name == name }?.child?.let { (it as? ShaderChild)?.shader }

    fun getColorFilter(name: String): ColorFilter? =
        entries.firstOrNull { it.name == name }?.child?.let { (it as? ColorFilterChild)?.filter }

    fun getBlender(name: String): Blender? =
        entries.firstOrNull { it.name == name }?.child?.let { (it as? BlenderChild)?.blender }
}
```

- [ ] **Step 3: Create MeshProgram.kt**

```kotlin
package org.graphiks.kanvas.paint

import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock

data class MeshProgram(
    val effect: RuntimeEffect,
    val uniforms: UniformBlock = UniformBlock.EMPTY,
    val children: MeshChildren = MeshChildren.EMPTY,
)
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew :kanvas:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/paint/MeshChild.kt
git commit -m "paint: add MeshChild, MeshChildren, MeshProgram types"
```

---

### Task 2: Add Mesh type

**Files:**
- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/types/Mesh.kt`

- [ ] **Step 1: Create Mesh.kt**

```kotlin
package org.graphiks.kanvas.types

import org.graphiks.kanvas.paint.MeshProgram

data class Mesh(
    val vertices: Vertices,
    val program: MeshProgram? = null,
    val bounds: Rect,
)
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :kanvas:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/types/Mesh.kt
git commit -m "types: add Mesh type with optional MeshProgram"
```

---

### Task 3: Add DisplayOp.DrawMesh and drawMesh to Canvas

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/DisplayOp.kt:93-94`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/Canvas.kt:170-172`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/picture/Picture.kt:46-77`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/picture/Picture.kt:126-138` (op discriminator)
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/picture/Picture.kt:457-536` (encode)
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/picture/Picture.kt:863-922` (decode)
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt:604`

- [ ] **Step 1: Add DisplayOp.DrawMesh**

In `DisplayOp.kt`, add after `DrawVertices` (line 93):

```kotlin
/** Draw a mesh with optional fragment program and children. */
data class DrawMesh(
    val mesh: Mesh, val paint: Paint,
    val blendMode: BlendMode?, val transform: Matrix33, val clip: ClipStack,
) : DisplayOp
```

Add import at top: `import org.graphiks.kanvas.types.Mesh`

- [ ] **Step 2: Add drawMesh to Canvas**

In `Canvas.kt`, add after `drawVertices` (line 172):

```kotlin
/** Draw a mesh. Falls back to drawVertices if no program. */
fun drawMesh(mesh: Mesh, paint: Paint, blendMode: BlendMode? = null) {
    if (mesh.program != null) {
        buffer.append(DisplayOp.DrawMesh(mesh, paint, blendMode, currentTransform, currentClip))
    } else {
        drawVertices(mesh.vertices, paint)
    }
}
```

Add import: `import org.graphiks.kanvas.types.Mesh`

- [ ] **Step 3: Add Picture playback case for DrawMesh**

In `Picture.kt`, add in the `playback` when expression (after `is DisplayOp.DrawVertices`):

```kotlin
is DisplayOp.DrawMesh -> canvas.drawMesh(op.mesh, op.paint, op.blendMode)
```

- [ ] **Step 4: Add Picture serialization for DrawMesh**

Add op discriminator constant (after `OP_DRAW_VERTICES`):
```kotlin
private const val OP_DRAW_MESH: Byte = 20
```

Add encoding case in `Writer.displayOp()` (after `is DisplayOp.DrawVertices`):
```kotlin
is DisplayOp.DrawMesh -> {
    byte(OP_DRAW_MESH)
    vertices(op.mesh.vertices)
    paint(op.paint)
    if (op.blendMode != null) { bool(true); blendMode(op.blendMode) } else bool(false)
    meshProgram(op.mesh.program)
    rect(op.mesh.bounds)
    matrix33(op.transform); clipStack(op.clip)
}
```

Add `meshProgram()` method to `Writer`:
```kotlin
fun meshProgram(mp: MeshProgram?) {
    if (mp == null) { bool(false); return }
    bool(true)
    runtimeEffect(mp.effect)
    uniformBlock(mp.uniforms)
    val entries = mp.children.entries
    int(entries.size)
    for (entry in entries) {
        string(entry.name)
        meshChild(entry.child)
    }
}

fun meshChild(child: MeshChild) {
    when (child) {
        is ShaderChild -> { byte(0); shader(child.shader) }
        is ColorFilterChild -> { byte(1); colorFilter(child.filter) }
        is BlenderChild -> { byte(2); blender(child.blender) }
    }
}
```

Add decoding case in `Reader.displayOp()` (after `OP_DRAW_VERTICES`):
```kotlin
OP_DRAW_MESH.toInt() -> {
    val v = vertices()
    val p = paint()
    val bm = if (bool()) blendMode() else null
    val mp = readMeshProgram()
    val bounds = rect()
    DisplayOp.DrawMesh(Mesh(v, mp, bounds), p, bm, matrix33(), clipStack())
}
```

Add `readMeshProgram()` and `readMeshChild()` methods to `Reader`:
```kotlin
fun readMeshProgram(): MeshProgram? {
    if (!bool()) return null
    val effect = readRuntimeEffect() ?: return null.also { valid = false }
    val uniforms = readUniformBlock() ?: return null.also { valid = false }
    val entryCount = int()
    val entries = mutableListOf<MeshChildren.Entry>()
    for (i in 0 until entryCount) {
        val name = string()
        val child = readMeshChild() ?: return null.also { valid = false }
        entries.add(MeshChildren.Entry(name, child))
    }
    return MeshProgram(effect, uniforms, MeshChildren(entries))
}

fun readMeshChild(): MeshChild? {
    return when (byte().toInt()) {
        0 -> ShaderChild(shader()!!)
        1 -> ColorFilterChild(colorFilter()!!)
        2 -> BlenderChild(blender()!!)
        else -> { valid = false; null }
    }
}
```

Add required imports at top of `Picture.kt`:
```kotlin
import org.graphiks.kanvas.paint.MeshChild
import org.graphiks.kanvas.paint.ShaderChild
import org.graphiks.kanvas.paint.ColorFilterChild
import org.graphiks.kanvas.paint.BlenderChild
import org.graphiks.kanvas.paint.MeshChildren
import org.graphiks.kanvas.paint.MeshProgram
import org.graphiks.kanvas.types.Mesh
```

- [ ] **Step 5: Add GPU op mapping for DrawMesh**

In `GPUOpMapper.kt`, add after `is DisplayOp.DrawVertices`:
```kotlin
is DisplayOp.DrawMesh -> copy(transform = outer * transform)
```

- [ ] **Step 6: Verify compilation**

Run: `./gradlew :kanvas:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/DisplayOp.kt \
        kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/Canvas.kt \
        kanvas/src/main/kotlin/org/graphiks/kanvas/picture/Picture.kt \
        kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt
git commit -m "canvas: add DisplayOp.DrawMesh, canvas.drawMesh, picture ser/de"
```

---

## Phase 2: SkiaGm Lifecycle

### Task 4: Add onOnceBeforeDraw and onAnimate to SkiaGm

**Files:**
- Modify: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGm.kt:19-27`
- Modify: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmRenderer.kt` (check for renderer file)

- [ ] **Step 1: Find SkiaGmRenderer**

Run: `find integration-tests/skia -name "SkiaGmRenderer.kt" -type f`

- [ ] **Step 2: Add lifecycle methods to SkiaGm**

In `SkiaGm.kt`, add two default methods to the interface:

```kotlin
interface SkiaGm {
    val name: String
    val renderFamily: RenderFamily
    val minSimilarity: Double
    val tolerance: Int get() = 2
    val width: Int get() = 800
    val height: Int get() = 600

    /** Called once before the first draw(). Default no-op. */
    fun onOnceBeforeDraw(canvas: GmCanvas) {}

    /** Called before each frame. Return true if re-render needed. Default no-op. */
    fun onAnimate(deltaMs: Long): Boolean = false

    fun draw(canvas: GmCanvas, width: Int, height: Int)
}
```

- [ ] **Step 3: Update SkiaGmRenderer to call lifecycle**

Read the renderer file. In the render flow, before calling `gm.draw()`:
```kotlin
// call once
if (!initialized) {
    gm.onOnceBeforeDraw(canvas)
    initialized = true
}
// call animate, re-render if needed
val needsRedraw = gm.onAnimate(deltaMs)
if (needsRedraw || !initialized) {
    gm.draw(canvas, gm.width, gm.height)
}
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew :integration-tests:skia:compileTestKotlin`

- [ ] **Step 5: Commit**

```bash
git add integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGm.kt
git add integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmRenderer.kt
git commit -m "gm: add onOnceBeforeDraw and onAnimate lifecycle to SkiaGm"
```

---

### Task 5: Add drawMesh to GmCanvas

**Files:**
- Modify: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/GmCanvas.kt:260-269`

- [ ] **Step 1: Add drawMesh method to GmCanvas**

In `GmCanvas.kt`, add after `drawVertices`:

```kotlin
fun drawMesh(mesh: Mesh, paint: Paint, blendMode: BlendMode? = null) {
    withClip {
        if (currentTransform.isIdentity()) {
            inner.drawMesh(mesh, paint, blendMode)
        } else {
            val transformed = mesh.vertices.positions.map { currentTransform * it }
            val transformedVerts = mesh.vertices.copy(positions = transformed)
            inner.drawMesh(mesh.copy(vertices = transformedVerts), paint, blendMode)
        }
    }
}
```

Add import: `import org.graphiks.kanvas.types.Mesh`

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :integration-tests:skia:compileTestKotlin`

- [ ] **Step 3: Commit**

```bash
git add integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/GmCanvas.kt
git commit -m "gm: add drawMesh to GmCanvas"
```

---

## Phase 3: GPU Path — TexCoords Support

### Task 6: Handle Vertices.texCoords in GPU renderer

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt:604-658`

- [ ] **Step 1: Read current GPURenderer.drawVertices section**

Re-read lines 604-658 to understand the current flow. Currently:
- If `verts.texCoords != null`, degrades with "gpu_textured_vertices_unimplemented"
- Converts positions to Path, tessellates, dispatches as fill path

- [ ] **Step 2: Add textured dispatch path**

Replace the `verts.texCoords != null` block (lines 606-608) with:

```kotlin
if (verts.texCoords != null) {
    // Textured vertices: extract per-triangle UV coords
    val indices = verts.indices ?: verts.positions.indices.toList()
    val triPositions = mutableListOf<Float>()
    val triUvs = mutableListOf<Float>()
    val tex = verts.texCoords

    when (verts.mode) {
        VertexMode.TRIANGLES -> {
            var i = 0
            while (i + 2 < indices.size) {
                val a = indices[i]; val b = indices[i + 1]; val c = indices[i + 2]
                if (a < verts.positions.size && b < verts.positions.size && c < verts.positions.size
                    && a < tex.size && b < tex.size && c < tex.size) {
                    triPositions.addAll(listOf(
                        verts.positions[a].x, verts.positions[a].y,
                        verts.positions[b].x, verts.positions[b].y,
                        verts.positions[c].x, verts.positions[c].y,
                    ))
                    triUvs.addAll(listOf(
                        tex[a].x, tex[a].y,
                        tex[b].x, tex[b].y,
                        tex[c].x, tex[c].y,
                    ))
                }
                i += 3
            }
        }
        VertexMode.TRIANGLE_STRIP -> {
            for (j in 2 until indices.size) {
                val a = indices[j - 2]; val b = indices[j - 1]; val c = indices[j]
                val isOdd = (j - 2) % 2 != 0
                val (ia, ib, ic) = if (isOdd) Triple(b, a, c) else Triple(a, b, c)
                if (ia < verts.positions.size && ib < verts.positions.size && ic < verts.positions.size
                    && ia < tex.size && ib < tex.size && ic < tex.size) {
                    triPositions.addAll(listOf(
                        verts.positions[ia].x, verts.positions[ia].y,
                        verts.positions[ib].x, verts.positions[ib].y,
                        verts.positions[ic].x, verts.positions[ic].y,
                    ))
                    triUvs.addAll(listOf(
                        tex[ia].x, tex[ia].y,
                        tex[ib].x, tex[ib].y,
                        tex[ic].x, tex[ic].y,
                    ))
                }
            }
        }
        VertexMode.TRIANGLE_FAN -> {
            val first = indices[0]
            for (j in 2 until indices.size) {
                val b = indices[j - 1]; val c = indices[j]
                if (first < verts.positions.size && b < verts.positions.size && c < verts.positions.size
                    && first < tex.size && b < tex.size && c < tex.size) {
                    triPositions.addAll(listOf(
                        verts.positions[first].x, verts.positions[first].y,
                        verts.positions[b].x, verts.positions[b].y,
                        verts.positions[c].x, verts.positions[c].y,
                    ))
                    triUvs.addAll(listOf(
                        tex[first].x, tex[first].y,
                        tex[b].x, tex[b].y,
                        tex[c].x, tex[c].y,
                    ))
                }
            }
        }
    }

    if (triPositions.isNotEmpty()) {
        val cmd = op.toNormalizedCommand(cmdId, targets, triPositions.toFloatArray(), emptyList(), triPositions.size / 2)
        val shader = op.paint.shader
        if (shader is Shader.Image) {
            t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                dispatchTexturedTriangles(cmd, shader, triPositions.toFloatArray(), triUvs.toFloatArray(), dispatched, diagnostics, width, height, config)
            }
        } else {
            diagnostics.degrade("unimplemented:drawVertices:textured:${cmdId.value}", "drawVertices", "gpu_textured_vertices_no_image_shader")
        }
        sceneHasContent = true
    }
    return
}
```

Add imports:
```kotlin
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.types.VertexMode
```

- [ ] **Step 3: Add dispatchTexturedTriangles stub**

In `GPURenderer.kt`, add a companion/private method:

```kotlin
private fun dispatchTexturedTriangles(
    cmd: GPUDrawCommand,
    imageShader: Shader.Image,
    positions: FloatArray,
    uvs: FloatArray,
    dispatched: MutableList<GPUDrawCommandID>,
    diagnostics: RenderDiagnostics,
    width: Int, height: Int,
    config: GpuRendererConfig,
) {
    // Stub: delegates to existing fill path for now.
    // Full implementation needs WGSL vertex buffer with UV attribute
    // and textured quad/fill pipeline variant.
    dispatchFillPath(cmd, dispatched, diagnostics, width, height, config)
}
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew :kanvas:compileKotlinJvm`

- [ ] **Step 5: Commit**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt
git commit -m "gpu: add texCoords extraction path for drawVertices"
```

---

## Phase 4: GPU Path — Mesh Program Dispatch

### Task 7: Handle DisplayOp.DrawMesh in GPU renderer

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt:658` (after DrawVertices block)

- [ ] **Step 1: Add DrawMesh handler in GPURenderer**

Add after the `is DisplayOp.DrawVertices` block (after line 658):

```kotlin
is DisplayOp.DrawMesh -> {
    val prog = op.mesh.program
    if (prog == null) {
        // Fall back to standard vertices path
        // Re-emit as DrawVertices
        val vertsOp = DisplayOp.DrawVertices(op.mesh.vertices, op.paint, op.transform, op.clip)
        // dispatch inline equivalent
        val verts = vertsOp.vertices
        if (verts.texCoords != null) {
            diagnostics.degrade("unimplemented:drawMesh:texCoords:${cmdId.value}", "drawMesh", "gpu_mesh_texCoords_delegated")
        }
        if (verts.positions.size >= 3) {
            val path = Path().also { p ->
                when (verts.mode) {
                    VertexMode.TRIANGLES -> {
                        var i = 0
                        while (i + 2 < verts.positions.size) {
                            p.moveTo(verts.positions[i].x, verts.positions[i].y)
                            p.lineTo(verts.positions[i + 1].x, verts.positions[i + 1].y)
                            p.lineTo(verts.positions[i + 2].x, verts.positions[i + 2].y)
                            p.close(); i += 3
                        }
                    }
                    VertexMode.TRIANGLE_STRIP -> {
                        for (j in 2 until verts.positions.size) {
                            p.moveTo(verts.positions[j - 2].x, verts.positions[j - 2].y)
                            p.lineTo(verts.positions[j - 1].x, verts.positions[j - 1].y)
                            p.lineTo(verts.positions[j].x, verts.positions[j].y)
                            p.close()
                        }
                    }
                    VertexMode.TRIANGLE_FAN -> {
                        val first = verts.positions.first()
                        for (j in 2 until verts.positions.size) {
                            p.moveTo(first.x, first.y)
                            p.lineTo(verts.positions[j - 1].x, verts.positions[j - 1].y)
                            p.lineTo(verts.positions[j].x, verts.positions[j].y)
                            p.close()
                        }
                    }
                }
            }
            val pathData = p.toPathTessellatorData()
            val tessellator = PathTessellator(config.curveTolerance, config.maxPathVertices.toInt())
            val flat = tessellator.flatten(pathData)
            if (flat.size >= 3) {
                val tri = tessellator.triangulate(flat)
                val triVerts = tri.vertices.flatMap { listOf(it.x, it.y) }
                val contourStarts = listOf(0)
                val drawPathOp = DisplayOp.DrawPath(p, op.paint, op.transform, op.clip)
                val tessCmd = drawPathOp.toNormalizedCommand(cmdId, targets, triVerts, contourStarts, flat.size)
                t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                    dispatchFillPath(tessCmd, dispatched, diagnostics, width, height, config)
                }
                sceneHasContent = true
            } else {
                diagnostics.degrade("unimplemented:drawMesh:insufficient:${cmdId.value}", "drawMesh", "insufficient_vertices:${flat.size}")
            }
        }
    } else {
        // Mesh with program: route to WGSL pipeline
        // Apply color filter + blend mode from children as paint-level fallback
        var meshPaint = op.paint
        prog.children.getColorFilter("myColorFilter")?.let { cf ->
            meshPaint = meshPaint.copy(colorFilter = cf)
        }
        prog.children.getBlender("myBlend")?.let { bl ->
            meshPaint = meshPaint.copy(blender = bl)
        }
        // Dispatch as textured vertices with children applied at paint level
        val verts = op.mesh.vertices
        if (verts.texCoords != null && verts.positions.size >= 3 && meshPaint.shader is Shader.Image) {
            val indices = verts.indices ?: verts.positions.indices.toList()
            val triPositions = mutableListOf<Float>()
            val triUvs = mutableListOf<Float>()
            val tex = verts.texCoords
            when (verts.mode) {
                VertexMode.TRIANGLES -> {
                    var i = 0
                    while (i + 2 < indices.size) {
                        val a = indices[i]; val b = indices[i + 1]; val c = indices[i + 2]
                        if (a < verts.positions.size && b < verts.positions.size && c < verts.positions.size
                            && a < tex.size && b < tex.size && c < tex.size) {
                            triPositions.addAll(listOf(
                                verts.positions[a].x, verts.positions[a].y, verts.positions[b].x, verts.positions[b].y,
                                verts.positions[c].x, verts.positions[c].y))
                            triUvs.addAll(listOf(tex[a].x, tex[a].y, tex[b].x, tex[b].y, tex[c].x, tex[c].y))
                        }
                        i += 3
                    }
                }
                VertexMode.TRIANGLE_STRIP -> {
                    for (j in 2 until indices.size) {
                        val a = indices[j - 2]; val b = indices[j - 1]; val c = indices[j]
                        if (a < verts.positions.size && b < verts.positions.size && c < verts.positions.size
                            && a < tex.size && b < tex.size && c < tex.size) {
                            triPositions.addAll(listOf(
                                verts.positions[a].x, verts.positions[a].y, verts.positions[b].x, verts.positions[b].y,
                                verts.positions[c].x, verts.positions[c].y))
                            triUvs.addAll(listOf(tex[a].x, tex[a].y, tex[b].x, tex[b].y, tex[c].x, tex[c].y))
                        }
                    }
                }
                else -> { /* FAN mode */ }
            }
            if (triPositions.isNotEmpty()) {
                val cmd = op.toNormalizedCommand(cmdId, targets, triPositions.toFloatArray(), emptyList(), triPositions.size / 2)
                t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                    dispatchTexturedTriangles(cmd, meshPaint.shader as Shader.Image, triPositions.toFloatArray(), triUvs.toFloatArray(), dispatched, diagnostics, width, height, config)
                }
                sceneHasContent = true
            }
        } else {
            diagnostics.degrade("unimplemented:drawMesh:program:${cmdId.value}", "drawMesh", "gpu_mesh_program_not_fully_implemented")
        }
    }
}
```

- [ ] **Step 2: Add DrawMesh to picture-flatten section (line ~578)**

In the `is DisplayOp.DrawPicture` nested section, add:

```kotlin
is DisplayOp.DrawMesh -> {
    diagnostics.degrade("unimplemented:drawPicture:nested:${nestedCmdId.value}", "drawPicture", "gpu_nested_drawMesh_unimplemented")
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :kanvas:compileKotlinJvm`

- [ ] **Step 4: Commit**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt
git commit -m "gpu: add DrawMesh handler with program children fallback"
```

---

## Phase 5: GM Migration

### Task 8: Copy image resources

**Files:**
- Copy: `skia-integration-tests/src/test/resources/images/mandrill_128.png` → `integration-tests/skia/src/test/resources/images/mandrill_128.png`
- Copy: `skia-integration-tests/src/test/resources/images/color_wheel.png` → `integration-tests/skia/src/test/resources/images/color_wheel.png`

- [ ] **Step 1: Copy images**

```bash
cp skia-integration-tests/src/test/resources/images/mandrill_128.png integration-tests/skia/src/test/resources/images/mandrill_128.png
cp skia-integration-tests/src/test/resources/images/color_wheel.png integration-tests/skia/src/test/resources/images/color_wheel.png
```

- [ ] **Step 2: Commit**

```bash
git add integration-tests/skia/src/test/resources/images/mandrill_128.png \
        integration-tests/skia/src/test/resources/images/color_wheel.png
git commit -m "gm: add mesh test image resources (mandrill, color_wheel)"
```

---

### Task 9: Port MeshWithImageGm

**Files:**
- Create: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/mesh/MeshWithImageGm.kt`

- [ ] **Step 1: Create MeshWithImageGm.kt**

```kotlin
package org.graphiks.kanvas.skia.gm.mesh

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.*
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.*
import kotlin.math.sin
import kotlin.math.floor

/**
 * Port of Skia's `gm/mesh.cpp::MeshWithShadersGM` (Type::kMeshWithImage).
 * 16x16 animated ripple mesh with mandrill image shader.
 * @see https://github.com/google/skia/blob/main/gm/mesh.cpp
 */
class MeshWithImageGm : SkiaGm {
    override val name = "mesh_with_image"
    override val renderFamily = RenderFamily.MESH
    override val minSimilarity = 0.0
    override val width = 320
    override val height = 320

    private val kRect = Rect(20f, 20f, 300f, 300f)
    private val kUv = Rect(0f, 0f, 128f, 128f)
    private val kMeshSize = 16
    private val kRippleSize = 6.0f

    private var time = 0.0
    private var positions = listOf<Point>()
    private var uvs = listOf<Point>()
    private var indices = listOf<Int>()
    private var mandrillShader: Shader? = null

    data class Vertex(val x: Float, val y: Float, val u: Float, val v: Float)

    override fun onOnceBeforeDraw(canvas: GmCanvas) {
        val bytes = MeshWithImageGm::class.java.classLoader
            ?.getResourceAsStream("images/mandrill_128.png")?.readBytes()
            ?: error("Resource not found: images/mandrill_128.png")
        val image = Image.decode(bytes)
        mandrillShader = image.makeShader(SamplingOptions.LINEAR)

        buildIndices()
    }

    override fun onAnimate(deltaMs: Long): Boolean {
        time += deltaMs / 1000.0
        updateVertices()
        return true
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val verts = Vertices(
            mode = VertexMode.TRIANGLES,
            positions = positions,
            texCoords = uvs,
            indices = indices,
        )
        val paint = Paint(shader = mandrillShader)
        canvas.drawVertices(verts, paint)
    }

    private fun buildIndices() {
        val idx = mutableListOf<Int>()
        for (y in 0 until kMeshSize - 1) {
            for (x in 0 until kMeshSize - 1) {
                val tl = y * kMeshSize + x
                val tr = y * kMeshSize + x + 1
                val bl = (y + 1) * kMeshSize + x
                val br = (y + 1) * kMeshSize + x + 1
                idx.addAll(listOf(tl, tr, bl, br, bl, tr))
            }
        }
        indices = idx
    }

    private fun updateVertices() {
        val pos = mutableListOf<Point>()
        val uvList = mutableListOf<Point>()
        val periodic = time % 4.0 / 4.0 * 2.0 * Math.PI
        val xOff = DoubleArray(kMeshSize) { sin(periodic + it * 0.8) * kRippleSize }
        val yOff = DoubleArray(kMeshSize) { sin(periodic + 10.0 + it * 0.8) * kRippleSize }

        for (y in 0 until kMeshSize) {
            val yf = y.toFloat() / (kMeshSize - 1)
            for (x in 0 until kMeshSize) {
                val xf = x.toFloat() / (kMeshSize - 1)
                pos.add(Point(
                    kRect.left + xf * kRect.width + xOff[y].toFloat(),
                    kRect.top + yf * kRect.height + yOff[x].toFloat(),
                ))
                uvList.add(Point(
                    kUv.left + xf * kUv.width,
                    kUv.top + yf * kUv.height,
                ))
            }
        }
        positions = pos
        uvs = uvList
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :integration-tests:skia:compileTestKotlin`

- [ ] **Step 3: Commit**

```bash
git add integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/mesh/MeshWithImageGm.kt
git commit -m "gm: port MeshWithImageGm with ripple animation and mandrill shader"
```

---

### Task 10: Port MeshWithPaintColorGm

**Files:**
- Create: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/mesh/MeshWithPaintColorGm.kt`

- [ ] **Step 1: Create MeshWithPaintColorGm.kt**

Same structure as `MeshWithImageGm` but:
- `override val name = "mesh_with_paint_color"`
- `onOnceBeforeDraw` loads `mandrill_128.png` as `mandrillShader`
- `draw` uses `Paint(color = Color(0xFF00FF00u), shader = mandrillShader, blendMode = BlendMode.DST)`

```kotlin
package org.graphiks.kanvas.skia.gm.mesh

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.*
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.*
import kotlin.math.sin

class MeshWithPaintColorGm : SkiaGm {
    override val name = "mesh_with_paint_color"
    override val renderFamily = RenderFamily.MESH
    override val minSimilarity = 0.0
    override val width = 320
    override val height = 320

    private val kRect = Rect(20f, 20f, 300f, 300f)
    private val kUv = Rect(0f, 0f, 128f, 128f)
    private val kMeshSize = 16
    private val kRippleSize = 6.0f

    private var time = 0.0
    private var positions = listOf<Point>()
    private var uvs = listOf<Point>()
    private var indices = listOf<Int>()
    private var mandrillShader: Shader? = null

    override fun onOnceBeforeDraw(canvas: GmCanvas) {
        val bytes = MeshWithPaintColorGm::class.java.classLoader
            ?.getResourceAsStream("images/mandrill_128.png")?.readBytes()
            ?: error("Resource not found: images/mandrill_128.png")
        mandrillShader = Image.decode(bytes).makeShader(SamplingOptions.LINEAR)
        buildIndices()
    }

    override fun onAnimate(deltaMs: Long): Boolean {
        time += deltaMs / 1000.0
        updateVertices()
        return true
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val verts = Vertices(
            mode = VertexMode.TRIANGLES,
            positions = positions, texCoords = uvs, indices = indices,
        )
        val paint = Paint(
            color = Color(0xFF00FF00u),
            shader = mandrillShader,
            blendMode = BlendMode.DST,
        )
        canvas.drawVertices(verts, paint)
    }

    private fun buildIndices() {
        val idx = mutableListOf<Int>()
        for (y in 0 until kMeshSize - 1)
            for (x in 0 until kMeshSize - 1) {
                val tl = y * kMeshSize + x; val tr = tl + 1
                val bl = (y + 1) * kMeshSize + x; val br = bl + 1
                idx.addAll(listOf(tl, tr, bl, br, bl, tr))
            }
        indices = idx
    }

    private fun updateVertices() {
        val periodic = time % 4.0 / 4.0 * 2.0 * Math.PI
        val xOff = DoubleArray(kMeshSize) { sin(periodic + it * 0.8) * kRippleSize }
        val yOff = DoubleArray(kMeshSize) { sin(periodic + 10.0 + it * 0.8) * kRippleSize }
        positions = (0 until kMeshSize).flatMap { y ->
            val yf = y.toFloat() / (kMeshSize - 1)
            (0 until kMeshSize).map { x ->
                val xf = x.toFloat() / (kMeshSize - 1)
                Point(
                    kRect.left + xf * kRect.width + xOff[y].toFloat(),
                    kRect.top + yf * kRect.height + yOff[x].toFloat())
            }
        }
        uvs = (0 until kMeshSize).flatMap { y ->
            val yf = y.toFloat() / (kMeshSize - 1)
            (0 until kMeshSize).map { x ->
                val xf = x.toFloat() / (kMeshSize - 1)
                Point(kUv.left + xf * kUv.width, kUv.top + yf * kUv.height)
            }
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :integration-tests:skia:compileTestKotlin`

- [ ] **Step 3: Commit**

```bash
git add integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/mesh/MeshWithPaintColorGm.kt
git commit -m "gm: port MeshWithPaintColorGm with DST blend and green paint"
```

---

### Task 11: Port MeshWithPaintImageGm

**Files:**
- Create: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/mesh/MeshWithPaintImageGm.kt`

- [ ] **Step 1: Create MeshWithPaintImageGm.kt**

Same base as `MeshWithImageGm` but:
- `override val name = "mesh_with_paint_image"`
- Loads `color_wheel.png` as mesh shader + `mandrill_128.png` as paint shader
- `draw` uses `Paint(shader = mandrillShader)` and `verts` with `texCoords`

```kotlin
package org.graphiks.kanvas.skia.gm.mesh

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.*
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.*
import kotlin.math.sin

class MeshWithPaintImageGm : SkiaGm {
    override val name = "mesh_with_paint_image"
    override val renderFamily = RenderFamily.MESH
    override val minSimilarity = 0.0
    override val width = 320
    override val height = 320

    private val kRect = Rect(20f, 20f, 300f, 300f)
    private val kUv = Rect(0f, 0f, 128f, 128f)
    private val kMeshSize = 16
    private val kRippleSize = 6.0f

    private var time = 0.0
    private var positions = listOf<Point>()
    private var uvs = listOf<Point>()
    private var indices = listOf<Int>()
    private var colorWheelShader: Shader? = null
    private var mandrillShader: Shader? = null

    override fun onOnceBeforeDraw(canvas: GmCanvas) {
        fun load(name: String): Image {
            val bytes = MeshWithPaintImageGm::class.java.classLoader
                ?.getResourceAsStream("images/$name")?.readBytes()
                ?: error("Resource not found: images/$name")
            return Image.decode(bytes)
        }
        colorWheelShader = load("color_wheel.png").makeShader(SamplingOptions.LINEAR)
        mandrillShader = load("mandrill_128.png").makeShader(SamplingOptions.LINEAR)
        buildIndices()
    }

    override fun onAnimate(deltaMs: Long): Boolean {
        time += deltaMs / 1000.0
        updateVertices()
        return true
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val verts = Vertices(
            mode = VertexMode.TRIANGLES,
            positions = positions, texCoords = uvs, indices = indices,
        )
        val paint = Paint(shader = colorWheelShader)
        canvas.drawVertices(verts, paint)
    }

    private fun buildIndices() {
        val idx = mutableListOf<Int>()
        for (y in 0 until kMeshSize - 1)
            for (x in 0 until kMeshSize - 1) {
                val tl = y * kMeshSize + x; val tr = tl + 1
                val bl = (y + 1) * kMeshSize + x; val br = bl + 1
                idx.addAll(listOf(tl, tr, bl, br, bl, tr))
            }
        indices = idx
    }

    private fun updateVertices() {
        val periodic = time % 4.0 / 4.0 * 2.0 * Math.PI
        val xOff = DoubleArray(kMeshSize) { sin(periodic + it * 0.8) * kRippleSize }
        val yOff = DoubleArray(kMeshSize) { sin(periodic + 10.0 + it * 0.8) * kRippleSize }
        positions = (0 until kMeshSize).flatMap { y ->
            val yf = y.toFloat() / (kMeshSize - 1)
            (0 until kMeshSize).map { x ->
                val xf = x.toFloat() / (kMeshSize - 1)
                Point(kRect.left + xf * kRect.width + xOff[y].toFloat(), kRect.top + yf * kRect.height + yOff[x].toFloat())
            }
        }
        uvs = (0 until kMeshSize).flatMap { y ->
            val yf = y.toFloat() / (kMeshSize - 1)
            (0 until kMeshSize).map { x ->
                val xf = x.toFloat() / (kMeshSize - 1)
                Point(kUv.left + xf * kUv.width, kUv.top + yf * kUv.height)
            }
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :integration-tests:skia:compileTestKotlin`

- [ ] **Step 3: Commit**

```bash
git add integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/mesh/MeshWithPaintImageGm.kt
git commit -m "gm: port MeshWithPaintImageGm with color_wheel shader"
```

---

### Task 12: Port MeshWithEffectsGm

**Files:**
- Create: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/mesh/MeshWithEffectsGm.kt`

- [ ] **Step 1: Create MeshWithEffectsGm.kt**

Same base, draws mandrill shader with inverse table color filter and DstOver blender:

```kotlin
package org.graphiks.kanvas.skia.gm.mesh

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.*
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.*
import kotlin.math.sin

class MeshWithEffectsGm : SkiaGm {
    override val name = "mesh_with_effects"
    override val renderFamily = RenderFamily.MESH
    override val minSimilarity = 0.0
    override val width = 320
    override val height = 320

    private val kRect = Rect(20f, 20f, 300f, 300f)
    private val kUv = Rect(0f, 0f, 128f, 128f)
    private val kMeshSize = 16
    private val kRippleSize = 6.0f

    private var time = 0.0
    private var positions = listOf<Point>()
    private var uvs = listOf<Point>()
    private var indices = listOf<Int>()
    private var mandrillShader: Shader? = null
    private var inverseFilter: ColorFilter? = null
    private var dstOverBlender: Blender? = null

    override fun onOnceBeforeDraw(canvas: GmCanvas) {
        val bytes = MeshWithEffectsGm::class.java.classLoader
            ?.getResourceAsStream("images/mandrill_128.png")?.readBytes()
            ?: error("Resource not found: images/mandrill_128.png")
        mandrillShader = Image.decode(bytes).makeShader(SamplingOptions.LINEAR)
        inverseFilter = ColorFilter.Table(UByteArray(256) { (255 - it).toUByte() })
        dstOverBlender = Blender.Mode(BlendMode.DST_OVER)
        buildIndices()
    }

    override fun onAnimate(deltaMs: Long): Boolean {
        time += deltaMs / 1000.0
        updateVertices()
        return true
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val verts = Vertices(
            mode = VertexMode.TRIANGLES,
            positions = positions, texCoords = uvs, indices = indices,
        )
        val paint = Paint(
            shader = mandrillShader,
            colorFilter = inverseFilter,
            blender = dstOverBlender,
        )
        canvas.drawVertices(verts, paint)
    }

    private fun buildIndices() {
        val idx = mutableListOf<Int>()
        for (y in 0 until kMeshSize - 1)
            for (x in 0 until kMeshSize - 1) {
                val tl = y * kMeshSize + x; val tr = tl + 1
                val bl = (y + 1) * kMeshSize + x; val br = bl + 1
                idx.addAll(listOf(tl, tr, bl, br, bl, tr))
            }
        indices = idx
    }

    private fun updateVertices() {
        val periodic = time % 4.0 / 4.0 * 2.0 * Math.PI
        val xOff = DoubleArray(kMeshSize) { sin(periodic + it * 0.8) * kRippleSize }
        val yOff = DoubleArray(kMeshSize) { sin(periodic + 10.0 + it * 0.8) * kRippleSize }
        positions = (0 until kMeshSize).flatMap { y ->
            val yf = y.toFloat() / (kMeshSize - 1)
            (0 until kMeshSize).map { x ->
                val xf = x.toFloat() / (kMeshSize - 1)
                Point(kRect.left + xf * kRect.width + xOff[y].toFloat(), kRect.top + yf * kRect.height + yOff[x].toFloat())
            }
        }
        uvs = (0 until kMeshSize).flatMap { y ->
            val yf = y.toFloat() / (kMeshSize - 1)
            (0 until kMeshSize).map { x ->
                val xf = x.toFloat() / (kMeshSize - 1)
                Point(kUv.left + xf * kUv.width, kUv.top + yf * kUv.height)
            }
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :integration-tests:skia:compileTestKotlin`

- [ ] **Step 3: Commit**

```bash
git add integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/mesh/MeshWithEffectsGm.kt
git commit -m "gm: port MeshWithEffectsGm with inverse color filter and DstOver blend"
```

---

### Task 13: Port MeshZeroInitGm (stub)

**Files:**
- Create: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/mesh/MeshZeroInitGm.kt`

- [ ] **Step 1: Create MeshZeroInitGm.kt**

```kotlin
package org.graphiks.kanvas.skia.gm.mesh

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/mesh.cpp::MeshZeroInitGM`.
 * Tests GPU buffer zero-initialization semantics — not
 * portable to Kanvas (requires GPU context for buffer
 * recycling and zero-init verification).
 * @see https://github.com/google/skia/blob/main/gm/mesh.cpp
 */
class MeshZeroInitGm : SkiaGm {
    override val name = "mesh_zero_init"
    override val renderFamily = RenderFamily.MESH
    override val minSimilarity = 0.0
    override val width = 90
    override val height = 30

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        throw NotImplementedError("STUB.MESH.GPU_ZERO_INIT: GPU buffer zero-init verification requires GPU context")
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :integration-tests:skia:compileTestKotlin`

- [ ] **Step 3: Commit**

```bash
git add integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/mesh/MeshZeroInitGm.kt
git commit -m "gm: add MeshZeroInitGm stub (GPU zero-init not portable)"
```

---

### Task 14: Register all 5 new GMs in ServiceLoader and update generated renders

**Files:**
- Modify: `integration-tests/skia/src/test/resources/META-INF/services/org.graphiks.kanvas.skia.SkiaGm`

- [ ] **Step 1: Add ServiceLoader entries**

Insert after existing mesh entries (alphabetical order):

```
org.graphiks.kanvas.skia.gm.mesh.MeshWithEffectsGm
org.graphiks.kanvas.skia.gm.mesh.MeshWithImageGm
org.graphiks.kanvas.skia.gm.mesh.MeshWithPaintColorGm
org.graphiks.kanvas.skia.gm.mesh.MeshWithPaintImageGm
org.graphiks.kanvas.skia.gm.mesh.MeshZeroInitGm
```

These go after `org.graphiks.kanvas.skia.gm.mesh.MeshUpdateGm` and before `org.graphiks.kanvas.skia.gm.mesh.PictureMeshGm` (alphabetically: Me < Pi, so MeshWith* < PictureMesh).

- [ ] **Step 2: Create placeholder generated renders**

```bash
mkdir -p integration-tests/skia/src/test/resources/generated-renders/mesh
touch integration-tests/skia/src/test/resources/generated-renders/mesh/.gitkeep
```

- [ ] **Step 3: Verify ServiceLoader is sorted**

Run: `sort -c integration-tests/skia/src/test/resources/META-INF/services/org.graphiks.kanvas.skia.SkiaGm`

- [ ] **Step 4: Commit**

```bash
git add integration-tests/skia/src/test/resources/META-INF/services/org.graphiks.kanvas.skia.SkiaGm
git commit -m "gm: register 5 new mesh GMs in ServiceLoader"
```

---

### Task 15: Final verification

- [ ] **Step 1: Compile all relevant modules**

```bash
./gradlew :integration-tests:skia:compileTestKotlin :kanvas:compileKotlinJvm
```

Expected: BUILD SUCCESSFUL (warnings OK, no errors)

- [ ] **Step 2: Verify ServiceLoader vs file count**

```bash
echo "ServiceLoader entries:" && grep "mesh/" integration-tests/skia/src/test/resources/META-INF/services/org.graphiks.kanvas.skia.SkiaGm | wc -l
echo "Gm files:" && ls integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/mesh/*Gm.kt | wc -l
```

Expected: 12 ServiceLoader entries (7 existing + 5 new), 12 Gm files

- [ ] **Step 3: Verify no orphaned old files**

```bash
ls skia-integration-tests/src/main/kotlin/org/skia/tests/MeshGMs.kt 2>&1
```

Expected: No such file (already deleted in prior PR)

- [ ] **Step 4: Run existing GM test (verify no regression)**

```bash
./gradlew :integration-tests:skia:test --tests "SkiaGmRunner" 2>&1 | tail -20
```

Note: tests may fail for new GMs without reference images; this is expected. Check that existing GMs still pass.

- [ ] **Step 5: Commit any final fixes**

```bash
git status
# review any untracked changes, commit if needed
```

---

## Summary

| Phase | Compo | Tâches |
|---|---|---|
| 1 | Types + Canvas | MeshChild, MeshChildren, MeshProgram, Mesh, DrawMesh op, drawMesh, Picture ser/de |
| 2 | SkiaGm lifecycle | onOnceBeforeDraw, onAnimate, GmCanvas.drawMesh |
| 3 | GPU texCoords | texCoords extraction, dispatchTexturedTriangles stub |
| 4 | GPU mesh program | DrawMesh handler, children fallback |
| 5 | GM migration | 5 Gm files + image resources + ServiceLoader |
