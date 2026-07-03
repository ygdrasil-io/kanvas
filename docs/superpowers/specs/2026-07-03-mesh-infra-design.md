# Mesh Infrastructure Design

Date: 2026-07-03
Status: Draft

## Scope

Design the full Kanvas-native mesh infrastructure needed to port all 11 SkMesh GMs
from `skia-integration-tests` to `integration-tests/skia/gm/mesh/`, including the 5
currently STUB GMs that depend on mesh-level runtime-effect children.

## Architecture decisions

- **Mesh extends Vertices**: `Mesh` wraps `Vertices` + optional `MeshProgram`.
  No program → fallback to existing `drawVertices` path. Single new drawOp.
- **RuntimeEffect for fragment programs**: reuse existing WGSL-backed
  `RuntimeEffect` infra. Children are bound to named WGSL uniform slots.
- **onAnimate + onOnceBeforeDraw**: non-breaking additions to `SkiaGm`.
  Default no-op implementations keep existing GMs working.
- **GPU textured triangles**: new `dispatchTexturedTriangles` path when
  `Vertices.texCoords != null`, replacing current degrade.
- **GPU mesh program**: `displayMesh` routes through `WGSLModuleBuilder` with
  children bound via `BindGroupLayout`.

## Type design

### MeshProgram

```kotlin
data class MeshProgram(
    val effect: RuntimeEffect,
    val uniforms: UniformBlock = UniformBlock.EMPTY,
    val children: MeshChildren = MeshChildren.EMPTY,
)
```

### MeshChildren

```kotlin
data class MeshChildren(
    val entries: List<Entry> = emptyList(),
) {
    data class Entry(val name: String, val child: MeshChild)
    companion object {
        val EMPTY = MeshChildren()
        fun of(vararg pairs: Pair<String, MeshChild>) =
            MeshChildren(pairs.map { Entry(it.first, it.second) })
    }
}

sealed interface MeshChild
data class ShaderChild(val shader: Shader) : MeshChild
data class ColorFilterChild(val filter: ColorFilter) : MeshChild
data class BlenderChild(val blender: Blender) : MeshChild
```

### Mesh

```kotlin
data class Mesh(
    val vertices: Vertices,
    val program: MeshProgram? = null,
    val bounds: Rect,
)
```

### Canvas API

```kotlin
fun drawMesh(mesh: Mesh, paint: Paint, blendMode: BlendMode? = null)
```

Emits `DisplayOp.DrawMesh` if `program != null`, otherwise `DisplayOp.DrawVertices`.

### SkiaGm lifecycle (non-breaking)

```kotlin
interface SkiaGm {
    // existing
    val name: String
    val renderFamily: RenderFamily
    val minSimilarity: Double
    val width: Int
    val height: Int

    // new — defaults are no-op
    fun onOnceBeforeDraw(canvas: GmCanvas) {}
    fun onAnimate(deltaMs: Long): Boolean = false

    fun draw(canvas: GmCanvas, width: Int, height: Int)
}
```

Execution: `onOnceBeforeDraw` once, then per-frame `onAnimate` → `draw`.

## GPU path

### Textured triangles

When `Vertices.texCoords != null`, route to `dispatchTexturedTriangles`:

```
DrawVertices(with texCoords) → extract triangles + UVs → dispatchTexturedTriangles
```

Binds `paint.shader` (image sampler) with per-vertex UVs. Extends existing
`draw_flat` WGSL with a textured variant.

### Mesh program dispatch

```
DrawMesh(program) → WGSL module builder → pipeline bind group
                  ├─ bind children (shader1, shader2, colorFilter, blender)
                  ├─ bind uniforms
                  └─ dispatch with vertices + indices
```

Uses `WGSLModuleBuilder` with the mesh's `RuntimeEffect`. Children mapped
to named WGSL `@group`/`@binding` slots.

### Fallback behavior

| Scenario | GPU | CPU |
|---|---|---|
| Mesh sans program | drawVertices (tessellation) | drawVertices (tessellation) |
| Mesh avec program | WGSL pipeline | `paint.colorFilter` + `paint.blendMode` |
| Vertices + texCoords | dispatchTexturedTriangles | degrade (paint.shader only) |

## GM migration mapping

| GM | Uses | Mapping |
|---|---|---|
| CustomMeshGm | positions + colors + indices | Mesh sans program → drawVertices ✓ done |
| CustomMeshCsGm | positions + float4 colors | Mesh sans program, premul inline ✓ done |
| CustomMeshUniformsGm | positions + colors + uniform | Mesh sans program, uniform × color inline ✓ done |
| PictureMeshGm | positions + indices + picture | Mesh sans program + PictureRecorder ✓ done |
| MeshUpdateGm | positions + indices + update | Mesh sans program, recréation Vertices ✓ done |
| CustomMeshCsUniformsGm | positions + color space + uniform | Mesh sans program, conversion inline ✓ done |
| MeshZeroInitGm | GPU buffer zero-init | STUB — sémantique GPU pure |
| MeshWithImageGm | positions + uvs + indices + shader1 + animate | `Mesh(program=MeshProgram(effect, children=[shader1]))` + `onAnimate` |
| MeshWithPaintColorGm | positions + uvs + indices + shader2 + blender + animate | `Mesh(program, children=[shader2, blender])` + `onAnimate` |
| MeshWithPaintImageGm | positions + uvs + indices + shader1 + paint shader + animate | `Mesh(program, children=[shader1])` + paint shader |
| MeshWithEffectsGm | positions + uvs + indices + shader1/2 + colorFilter + blender + animate | `Mesh(program, children=[shader1, shader2, colorFilter, blender])` + `onAnimate` |

## Shared mesh-with-shaders program

The 4 `MeshWithShaders*` GMs share a single registered `RuntimeEffect`:

```kotlin
val MESH_WITH_SHADERS_EFFECT = RuntimeEffect.descriptor(
    name = "mesh_with_shaders",
    fragmentWgsl = """
        @group(0) @binding(0) var myShader1: texture_2d<f32>;
        @group(0) @binding(1) var mySampler1: sampler;
        @group(0) @binding(2) var myShader2: texture_2d<f32>;
        @group(0) @binding(3) var mySampler2: sampler;
        // myColorFilter, myBlend via color filter / blend bindings
    """,
)
```

Each variant injects different children via `MeshChildren`.

## Image resources

Copy to `integration-tests/skia/src/test/resources/images/`:
- `mandrill_128.png`
- `color_wheel.png`

Loaded via `Image.decode(classLoader.getResourceAsStream(path)!!.readBytes())`.

## Implementation order

1. **Types** — `Mesh`, `MeshProgram`, `MeshChildren`, `MeshChild` in kanvas-core
2. **Canvas** — `drawMesh()` + `DisplayOp.DrawMesh` in kanvas-core
3. **SkiaGm** — `onOnceBeforeDraw`, `onAnimate` defaults in integration-tests/skia
4. **GPU texCoords** — `dispatchTexturedTriangles` in gpu-renderer
5. **GPU mesh program** — `displayMesh` + WGSL binding in gpu-renderer
6. **GMs** — port 4 `MeshWithShaders*` + `MeshZeroInit` (stub) + image resources

## Non-migrated

- `MeshZeroInitGm` — GPU buffer zero-initialization semantics; no Kanvas equivalent.
  Remains `STUB.MESH` with `NotImplementedError`.
- Custom per-mesh fragment programs not covered by registered descriptors remain
  out of scope; the 4 `MeshWithShaders*` GMs use the single shared descriptor above.
