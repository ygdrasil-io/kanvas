# FP-05 Prepared Text Route Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrer `DisplayOp.DrawText` vers la frame préparée WebGPU commune avec atlas A8/COLRv0, paint complet, stroke, blur, refus terminaux, ownership exact et aucune continuation legacy.

**Architecture:** Le chantier construit d’abord les autorités font/glyph et paint communes, puis un `GPUPreparedTextLowerer` pur et un `PreparedTextFrameInventory` immuable par frame. Les sous-runs A8 et COLRv0 deviennent des payloads fermés de la frame Surface existante ; le preflight valide tout avant allocation et le materializer WebGPU partage les uploads R8, l’instance buffer, l’encoder, le submit et l’ownership de completion. La gate produit reste fermée jusqu’aux tests pixels/natifs et la suppression atomique de la famille legacy `Text`.

**Tech Stack:** Kotlin/JVM, Gradle 9.2, Java toolchain 25, wgpu4k/WebGPU, WGSL parser-validé via wgsl4k, `kotlin.test`, JUnit Jupiter pour les matrices paramétrées du module `:kanvas`.

## Global Constraints

- Ne pas porter Ganesh ou Graphite ; Graphite+Dawn sert uniquement de référence technique bornée au commit `defc3a5a92966c32cb2a6a901e2fa3036a13bb8a`.
- Garder WebGPU comme unique backend GPU et WGSL comme cible shader.
- Ne pas reconstruire le compilateur SkSL, son IR ou sa VM.
- Un runtime effect admis doit utiliser un descriptor Kanvas enregistré, un comportement Kotlin/CPU et un WGSL parser-validé.
- Ne pas créer de `Recorder`, `Device`, `DrawContext`, `TextureProxy`, `ResourceProvider` polymorphe ou hiérarchie multi-backends inspirés de Graphite.
- Aucun handle wgpu4k ne doit entrer dans le lowerer, l’inventaire, les payloads ou le preflight pur.
- Toute ambiguïté ou anomalie wgpu4k doit produire une reproduction minimale et un signalement wgpu4k ; aucun workaround Kanvas caché.
- L’animation et `unsupported.image.animation` restent inchangés.
- Les image filters appliqués au texte restent des refus terminaux jusqu’à FP-07.
- La résidence atlas persistante, l’éviction et la réutilisation inter-frame restent dans FP-09.
- Les rendus GM, scores et affirmations de proximité de performance avec Graphite restent dans FP-11.
- Le développement garde la gate produit texte fermée ; la bascule est atomique dans la Task 14.
- Un refus après admission ne doit jamais revenir au renderer immediate, CPU ou legacy.
- `.superpowers/sdd/` ne doit jamais être ajouté au commit ou à la PR.
- Toutes les commandes shell de ce plan passent par `rtk`; Gradle s’exécute avec `rtk proxy ./gradlew`.

---

## File and Interface Map

### Font, glyph and handoff authority

- `font/core/src/main/kotlin/org/graphiks/kanvas/font/FontIdentityAuthority.kt`
  - dérive `FontSourceID` depuis bytes immuables + provenance ;
  - ne parse pas le font et ne dépend pas du renderer.
- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/GlyphMaskKey.kt`
  - porte la clé de masque exacte, le face index et le blur normalisé.
- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/GlyphSurface.kt`
  - reste l’autorité de rasterisation A8 et produit une couverture 4×4 antialiasée.
- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/GlyphMaskBlur.kt`
  - applique le blur de masque avant coloration et calcule le padding.
- `font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextArtifacts.kt`
  - porte la génération numérique non négative et les artefacts immuables.
- `font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUPreparedTextAtlas.kt`
  - décrit pages R8, placements, instances et sous-runs sans handle.
- `font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextRouteRefusals.kt`
  - devient l’unique source des codes `unsupported.text.*`.

### Surface lowering and inventory

- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextContracts.kt`
  - snapshots de fonte, draws abaissés, représentations et résultats typés.
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextFontResolver.kt`
  - résout `FontTypeface`/`KanvasTypeface` vers des bytes et identités stables.
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextLowerer.kt`
  - lowerer pur, transactionnel, sans allocation native.
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/PreparedTextFrameInventory.kt`
  - déduplique, packe, crée les pages et sous-runs dans l’ordre exact.
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextSemanticBuilder.kt`
  - transforme les faits Surface en payloads renderer fermés.
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilder.kt`
  - orchestre lowering → inventaire → mapper → recorder → builder commun.
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt`
  - consomme l’inventaire et attribue un `commandId` unique par sous-run.

### Shared paint and upload authorities

- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/GPUPreparedMaterialProgram.kt`
  - compile un `GPUMaterialDescriptor` déjà admis vers un programme WGSL/payload partagé.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/artifacts/GPUPreparedR8UploadArtifact.kt`
  - snapshot R8 générique utilisé par A8 et COLRv0.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUR8FrameResourcePlan.kt`
  - plan de texture R8, staging, upload et ownership.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUTextureFrameResourcePlan.kt`
  - interface commune aux plans image et R8.

### Recording, preflight and native execution

- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/PayloadContracts.kt`
  - ajoute `GPUDrawSemanticPayload.TextA8` et aligne `ColorGlyph` sur l’artefact R8.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedSurfaceFrameTaskListBuilder.kt`
  - construit uploads/draws hétérogènes et dédupliqués.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedSurfaceNativePreflight.kt`
  - valide génération, ABI, budgets, bindings et ordre avant le premier handle.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedTextSessionCache.kt`
  - ne cache que les objets invariants de pipeline/binding, jamais les pages FP-05.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedTextRenderRunMaterializer.kt`
  - matérialise pages, instance buffer, uniforms et bind groups.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedSurfaceFramePayloadMaterializer.kt`
  - assemble Core/Image/Text/ColorGlyph selon le plan scellé.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/PreparedTextA8Shader.kt`
  - shader A8 prémultiplié et parser-validé.

### Stable interfaces carried across tasks

```kotlin
@JvmInline
value class GPUTextArtifactGeneration(val value: Int) {
    init { require(value >= 0) }
}

sealed interface GPUPreparedTextLowering {
    data class Ready(val draw: GPUPreparedTextDraw) : GPUPreparedTextLowering
    data class Refused(
        val code: String,
        val operationIndex: Int,
        val facts: Map<String, String>,
    ) : GPUPreparedTextLowering
}

sealed interface PreparedTextFrameInventoryResult {
    data class Ready(val inventory: PreparedTextFrameInventory) :
        PreparedTextFrameInventoryResult
    data class Refused(
        val code: String,
        val operationIndex: Int?,
        val facts: Map<String, String>,
    ) : PreparedTextFrameInventoryResult
}

sealed interface GPUPreparedMaterialProgramResult {
    data class Ready(val program: GPUPreparedMaterialProgram) :
        GPUPreparedMaterialProgramResult
    data class Refused(val code: String, val facts: Map<String, String>) :
        GPUPreparedMaterialProgramResult
}
```

---

### Task 1: Canonical font identity and numeric generation

**Files:**
- Create: `font/core/src/main/kotlin/org/graphiks/kanvas/font/FontIdentityAuthority.kt`
- Create: `font/core/src/test/kotlin/org/graphiks/kanvas/font/FontIdentityAuthorityTest.kt`
- Modify: `font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextArtifacts.kt`
- Modify: `font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextTelemetry.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/text/TextContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/commands/NormalizedDrawCommand.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/GPUTextA8RoutePlanner.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/RecordingContracts.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/text/FontTypeface.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/text/FontTypefaceIdentityTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/text/GPUTextCommandHandoffTest.kt`

**Interfaces:**
- Consumes: `FontSourceIdentityPreimage`, `FontSourceProvenance`, `GPUTextArtifactReference`.
- Produces:

```kotlin
object FontIdentityAuthority {
    fun memorySource(
        bytes: ByteArray,
        declaredName: String,
        parserGeneration: Int = 1,
    ): FontSourceIdentityPreimage
}

data class GPUTextArtifactRef(
    val artifactType: String,
    val artifactId: String,
    val artifactKeyHash: String,
    val generation: GPUTextArtifactGeneration,
    val routeHint: String? = null,
)
```

- `NormalizedDrawCommand.DrawTextRun.atlasGenerations` remplace `atlasGenerationTokens`.
- `FontTypeface.fontBytes` retourne un copy ; `faceIndex`, `sourceId` et `typefaceId` sont dérivés du snapshot.

- [ ] **Step 1: Write failing font identity and immutability tests**

```kotlin
@Test
fun `memory font identity changes with content and preserves caller bytes`() {
    val first = byteArrayOf(0, 1, 2, 3)
    val same = first.copyOf()
    val changed = byteArrayOf(0, 1, 2, 4)
    val firstId = FontIdentityAuthority.memorySource(first, "fixture").sourceId()
    assertEquals(firstId, FontIdentityAuthority.memorySource(same, "fixture").sourceId())
    assertNotEquals(firstId, FontIdentityAuthority.memorySource(changed, "fixture").sourceId())
    first[0] = 99
    assertEquals(firstId, FontIdentityAuthority.memorySource(same, "fixture").sourceId())
}

@Test
fun `font typeface snapshots constructor bytes`() {
    val source = liberationFontBytes()
    val typeface = FontTypeface(source, "LiberationSans", faceIndex = 0)
    val sourceId = typeface.sourceId
    source.fill(0)
    assertEquals(sourceId, typeface.sourceId)
    assertFalse(typeface.fontBytes.all { it == 0.toByte() })
}
```

- [ ] **Step 2: Run the identity tests and observe the missing authority/current constant-ID failure**

Run:

```bash
rtk proxy ./gradlew :font:core:test :kanvas:test \
  --tests "org.graphiks.kanvas.font.FontIdentityAuthorityTest" \
  --tests "org.graphiks.kanvas.text.FontTypefaceIdentityTest"
```

Expected: compilation fails because `FontIdentityAuthority`, `sourceId` and `typefaceId` do not exist.

- [ ] **Step 3: Implement content/provenance identity and snapshot `FontTypeface`**

Use SHA-256 of the byte snapshot, `FontSourceKind.MEMORY`, the collection face count when known, `tableTags = emptyList()` and `parserGeneration = 1`. Add `faceIndex: Int = 0` to `FontTypeface`, validate it, pass it to `DefaultOpenTypeFaceParser`, replace the constant UUID with `sourceId`, and expose `typefaceId` only when that exact face parsed successfully. Add a TTC fixture assertion proving that face indices `0` and `1` share a source ID but have different typeface IDs.

- [ ] **Step 4: Write failing numeric-generation tests**

```kotlin
@Test
fun `renderer handoff retains one typed numeric generation`() {
    val reference = artifactReference(generation = GPUTextArtifactGeneration(3))
        .toRendererTextArtifactRef()
    assertEquals(GPUTextArtifactGeneration(3), reference.generation)
    assertFailsWith<IllegalArgumentException> { GPUTextArtifactGeneration(-1) }
}
```

- [ ] **Step 5: Replace free-form generation strings**

Change all `generationToken`/`atlasGenerationTokens` fields and tests to `GPUTextArtifactGeneration`/`atlasGenerations`. Serialize telemetry as a JSON number:

```kotlin
append("\"generation\":")
append(generation.value)
```

The A8 planner compares typed values directly and no longer parses or checks an `"atlas-generation-"` prefix.

- [ ] **Step 6: Run focused and aggregate contract tests**

Run:

```bash
rtk proxy ./gradlew :font:core:test :font:gpu-api:test :gpu-renderer:test :kanvas:test \
  --tests "org.graphiks.kanvas.font.FontIdentityAuthorityTest" \
  --tests "org.graphiks.kanvas.glyph.gpu.GPUTextTelemetrySurfaceTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.text.GPUTextCommandHandoffTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.analysis.GPUTextA8RoutePlannerTest" \
  --tests "org.graphiks.kanvas.text.FontTypefaceIdentityTest"
```

Expected: all selected tests pass; no occurrence of `atlasGenerationTokens` or `generationToken` remains in production Kotlin.

- [ ] **Step 7: Commit**

```bash
rtk git add font/core font/gpu-api gpu-renderer/src kanvas/src
rtk git commit -m "fix(text): canonicalize font identity and generations"
```

---

### Task 2: Exact glyph mask key and real antialiased A8 coverage

**Files:**
- Create: `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/GlyphMaskKey.kt`
- Create: `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/GlyphMaskKeyTest.kt`
- Modify: `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/GlyphSurface.kt`
- Modify: `font/src/main/kotlin/org/graphiks/kanvas/font/glyph/A8Rasterizer.kt`
- Modify: `font/src/main/kotlin/org/graphiks/kanvas/font/atlas/GlyphAtlasUploadPlan.kt`
- Test: `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/GlyphSurfaceTest.kt`
- Test: `font/src/test/kotlin/org/graphiks/kanvas/font/glyph/A8RasterizerTest.kt`

**Interfaces:**
- Consumes: `GlyphStrikeKey`, `OutlineGlyphRepresentation`, `A8GlyphMask`.
- Produces:

```kotlin
enum class GlyphMaskBlurStyle { NORMAL, SOLID, OUTER, INNER }

data class GlyphMaskBlurKey(
    val style: GlyphMaskBlurStyle,
    val sigma: Float,
    val rasterScaleX: Float,
    val rasterScaleY: Float,
)

data class GlyphMaskKey(
    val strikeKey: GlyphStrikeKey,
    val faceIndex: Int,
    val sourceOutlineSha256: String,
    val rasterizerVersion: String = "a8-nonzero-4x4-v1",
    val blur: GlyphMaskBlurKey? = null,
) {
    fun canonicalPreimage(): String
    fun sha256(): String
}
```

- `A8Rasterizer` dans `:font` devient un adapter vers `GlyphMaskGenerator` ; il ne garde plus un second algorithme.

- [ ] **Step 1: Write failing mask-key separation tests**

```kotlin
@Test
fun `mask key separates face subpixel variation palette and blur`() {
    val base = maskKey(faceIndex = 0, subpixelX = 0f, palette = null, blur = null)
    assertNotEquals(base.sha256(), base.copy(faceIndex = 1).sha256())
    assertNotEquals(base.sha256(), maskKey(subpixelX = 0.25f).sha256())
    assertNotEquals(base.sha256(), maskKey(variation = mapOf("wght" to 700f)).sha256())
    assertNotEquals(base.sha256(), maskKey(palette = "palette-1").sha256())
    assertNotEquals(base.sha256(), maskKey(blur = blurKey(2f)).sha256())
}
```

- [ ] **Step 2: Write failing antialiasing tests**

Use a diagonal triangle whose edge crosses pixel interiors:

```kotlin
@Test
fun `A8 rasterizer emits deterministic intermediate coverage`() {
    val outline = OutlineGlyphRepresentation(
        glyphId = 7,
        pathCommands = listOf("M 0 0", "L 4 0", "L 0 4", "Z"),
    )
    val generator = object : GlyphMaskGenerator {}
    val first = generator.generate(outline, strikeKey(glyphId = 7))
    val second = generator.generate(outline, strikeKey(glyphId = 7))
    assertEquals(first, second)
    assertTrue(first.pixels.any { it in 1..254 })
    assertTrue(0 in first.pixels)
    assertTrue(255 in first.pixels)
}
```

- [ ] **Step 3: Run tests and verify the current binary rasterizer fails**

Run:

```bash
rtk proxy ./gradlew :font:glyph:test :font:test \
  --tests "org.graphiks.kanvas.glyph.GlyphMaskKeyTest" \
  --tests "org.graphiks.kanvas.glyph.GlyphSurfaceTest" \
  --tests "org.graphiks.kanvas.font.glyph.A8RasterizerTest"
```

Expected: key tests do not compile and the diagonal coverage assertion finds only `0`/`255`.

- [ ] **Step 4: Implement canonical key serialization**

Serialize float values with `toBits()`, sort variation axes, include all fields shown in the interface, require `faceIndex >= 0`, finite positive scale, finite non-negative sigma and lowercase 64-character outline hash.

- [ ] **Step 5: Implement deterministic 4×4 coverage**

For each output pixel, evaluate the existing non-zero winding predicate at:

```kotlin
private val A8_SAMPLE_OFFSETS = doubleArrayOf(0.125, 0.375, 0.625, 0.875)
coverage = ((insideSamples * 255) + 8) / 16
```

Keep the existing command-count and pixel-count limits. Empty outlines remain valid zero-sized masks; malformed outlines remain stable refusals.

- [ ] **Step 6: Replace the duplicate root rasterizer with an adapter**

Convert `ScaledGlyph.commands` to the canonical `OutlineGlyphRepresentation`, call the canonical generator, then copy its immutable `List<Int>` to `ByteArray`. Preserve `A8Bitmap` for source compatibility until its callers migrate in Task 5.

- [ ] **Step 7: Run tests and verify immutability**

Run:

```bash
rtk proxy ./gradlew :font:glyph:test :font:test
```

Expected: all module tests pass; selected fixtures include intermediate coverage; caller mutation cannot change a stored mask or hash.

- [ ] **Step 8: Commit**

```bash
rtk git add font/glyph font/src
rtk git commit -m "feat(text): add canonical antialiased glyph masks"
```

---

### Task 3: Shared prepared material program authority

**Files:**
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/GPUPreparedMaterialProgram.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/materials/GPUPreparedMaterialProgramTest.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaterialMapper.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaterialMapperTest.kt`

**Interfaces:**
- Consumes: `GPUMaterialDescriptor`, existing solid/gradient/image/blend/runtime-effect lowerers, `GPUBlendPlanner`.
- Produces:

```kotlin
data class GPUPreparedMaterialProgram(
    val materialKey: String,
    val wgslSource: String,
    val entryPoint: String,
    val uniformBytes: List<Int>,
    val sampledResources: List<GPUPreparedMaterialSampledResource>,
    val paintAlpha: Float,
    val sourceKind: GPUMaterialSourceKind,
    val abiHash: String,
)

object GPUPreparedMaterialProgramCompiler {
    fun compile(
        descriptor: GPUMaterialDescriptor,
        paintAlpha: Float,
        context: GPUMaterialLoweringContext,
    ): GPUPreparedMaterialProgramResult
}

data class GPUPreparedMaterialMapping(
    val descriptor: GPUMaterialDescriptor,
    val paintAlpha: Float,
)

internal fun Paint.toPreparedMaterialMapping(): GPUPreparedMaterialMapping
```

- Cette autorité est commune : aucun `when` spécifique au texte ne choisit les familles de matière.

- [ ] **Step 1: Write the accepted/refused material matrix**

```kotlin
@Test
fun `prepared material compiler accepts exactly common proven sources`() {
    val accepted = listOf(
        solidDescriptor(),
        linearGradientDescriptor(),
        radialGradientDescriptor(),
        sweepGradientDescriptor(),
        conicalGradientDescriptor(),
        supportedBlendShaderDescriptor(),
        registeredRuntimeEffectDescriptor(),
        supportedImageShaderDescriptor(),
    )
    accepted.forEach { descriptor ->
        assertIs<GPUPreparedMaterialProgramResult.Ready>(
            compiler.compile(descriptor, 0.5f, context),
            descriptor.toString(),
        )
    }
    val refused = assertIs<GPUPreparedMaterialProgramResult.Refused>(
        compiler.compile(unregisteredRuntimeEffectDescriptor(), 1f, context),
    )
    assertEquals("unsupported.material.runtime_effect.descriptor", refused.code)
}
```

- [ ] **Step 2: Write the silent-fallback regression test**

```kotlin
@Test
fun `unsupported blend shader is not replaced by its source child`() {
    val mapped = paintWithUnsupportedBlendShader().toMaterial()
    val refused = assertIs<GPUPreparedMaterialProgramResult.Refused>(
        compiler.compile(mapped, 1f, context),
    )
    assertEquals("unsupported.material.blend_shader", refused.code)
}
```

- [ ] **Step 3: Run tests and observe current missing compiler/silent child fallback**

Run:

```bash
rtk proxy ./gradlew :gpu-renderer:test :kanvas:test \
  --tests "org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUMaterialMapperTest"
```

Expected: compiler is absent and the unsupported blend shader currently maps to `srcDesc`.

- [ ] **Step 4: Implement the shared compiler**

Reuse existing lowerers and shader providers. The compiler must:

- return the existing canonical diagnostic for an unsupported source ;
- validate `paintAlpha in 0f..1f` and retain it separately ;
- snapshot uniform bytes and image bytes ;
- parser-validate the final WGSL entry point ;
- derive `materialKey` and `abiHash` from the exact source, uniform layout and resources ;
- never accept arbitrary SkSL or unregistered runtime-effect source.

`toPreparedMaterialMapping()` must avoid applying `Paint.color.a` twice: a solid source uses straight RGB/A semantics with the final paint alpha represented once, while shader/image/gradient sources retain their own source alpha and use `paintAlpha` only as the caller modulation. Existing non-text callers of `toMaterial()` keep their behavior until they opt into this common prepared mapping.

- [ ] **Step 5: Remove material substitution from `GPUMaterialMapper`**

Keep the original `GPUMaterialDescriptor.BlendShader` when common lowering refuses. The later compiler emits the canonical refusal; mapping must not silently alter semantics.

- [ ] **Step 6: Run material and WGSL tests**

Run:

```bash
rtk proxy ./gradlew :gpu-renderer:test :kanvas:test \
  --tests "org.graphiks.kanvas.gpu.renderer.materials.*" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUMaterialMapperTest"
```

Expected: accepted matrix passes, refusal codes match exactly, every accepted WGSL module parses.

- [ ] **Step 7: Commit**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/materials \
  kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaterialMapper.kt \
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaterialMapperTest.kt
rtk git commit -m "feat(renderer): define shared prepared material programs"
```

---

### Task 4: Canonical text refusals and pure lowerer

**Files:**
- Modify: `font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextRouteRefusals.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/text/TextContracts.kt`
- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextContracts.kt`
- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextFontResolver.kt`
- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextLowerer.kt`
- Modify: `kanvas/build.gradle.kts`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextLowererTest.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextRefusalMatrixTest.kt`

**Interfaces:**
- Consumes: `DisplayOp.DrawText`, `GPUPreparedMaterialProgramCompiler`, exact `FontTypeface` identity, `GlyphStrikeKey`, COLRv0 font contracts.
- Produces:

```kotlin
data class GPUPreparedFontFaceSnapshot(
    val sourceId: FontSourceID,
    val typefaceId: TypefaceID,
    val faceIndex: Int,
    val bytes: List<Int>,
    val provenance: String,
)

data class GPUPreparedGlyphInput(
    val glyphId: Int,
    val positionX: Float,
    val positionY: Float,
    val fontSize: Float,
    val strikeKey: GlyphStrikeKey,
)

data class GPUPreparedTextDraw(
    val operationIndex: Int,
    val face: GPUPreparedFontFaceSnapshot,
    val glyphs: List<GPUPreparedGlyphInput>,
    val originX: Float,
    val originY: Float,
    val transform: Matrix33,
    val clip: ClipStack,
    val paint: Paint,
    val material: GPUPreparedMaterialProgram,
    val representationPolicy: GPUPreparedTextRepresentationPolicy,
)

interface GPUPreparedTextFontResolver {
    fun resolve(typeface: Typeface?): GPUPreparedTextFontResolution
}

object GPUPreparedTextLowerer {
    fun lower(
        operation: DisplayOp.DrawText,
        operationIndex: Int,
        target: GPUTargetFacts,
        capabilities: GPUCapabilities,
        fontResolver: GPUPreparedTextFontResolver,
    ): GPUPreparedTextLowering
}
```

- [ ] **Step 1: Centralize exact refusal codes**

Add `GPUTextRefusalCodes` in `GPUTextRouteRefusals.kt` with stable constants for identity, glyph, metrics, representation, artifact, generation, mask, transform, clip, material, blend, filter, budget, ABI, binding, upload, ownership, rasterization and packing. Make renderer aliases import these constants; delete duplicate string literals.

- [ ] **Step 2: Write the lowerer acceptance test**

```kotlin
@Test
fun `lowerer snapshots exact positioned glyphs and paint state`() {
    val operation = drawText(
        glyphs = listOf(5, 9),
        positions = listOf(Point(1.25f, 2f), Point(7.5f, 2f)),
        transform = Matrix33.skew(0.2f, 0f),
        paint = gradientPaint(alpha = 0.5f),
    )
    val ready = assertIs<GPUPreparedTextLowering.Ready>(
        GPUPreparedTextLowerer.lower(operation, 4, target, capabilities, resolver),
    )
    assertEquals(listOf(5, 9), ready.draw.glyphs.map { it.glyphId })
    assertEquals(listOf(1.25f, 7.5f), ready.draw.glyphs.map { it.positionX })
    assertEquals(operation.transform, ready.draw.transform)
    assertEquals(0.5f, ready.draw.material.paintAlpha)
    assertEquals(4, ready.draw.operationIndex)
}
```

- [ ] **Step 3: Write the refusal matrix**

Cover exact rows for: null/unsupported typeface, unstable identity, malformed bytes, negative/out-of-range glyph, missing `.notdef`, mismatched positions, non-finite position/size, singular/perspective transform, unsupported clip, unsupported paint/material/blend, image filter, unsupported mask filter, CBDT/CBLC, sbix, SVG, unproved COLRv1 and missing internal representation.

```kotlin
data class RefusalCase(
    val name: String,
    val operation: DisplayOp.DrawText,
    val expectedCode: String,
)

@ParameterizedTest(name = "{0}")
@MethodSource("refusalCases")
fun `text refusals are stable and terminal`(case: RefusalCase) {
    val result = assertIs<GPUPreparedTextLowering.Refused>(lower(case.operation))
    assertEquals(case.expectedCode, result.code)
}
```

- [ ] **Step 4: Run tests and observe the lowerer is absent**

Run:

```bash
rtk proxy ./gradlew :kanvas:test \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedTextLowererTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedTextRefusalMatrixTest"
```

Expected: compilation fails on the new interfaces.

- [ ] **Step 5: Implement resolver and lowerer transactionally**

The resolver snapshots bytes before parsing. The lowerer validates every glyph and the whole material before returning `Ready`; a single error returns one `Refused` and no partial list. Already-shaped emoji glyph IDs are treated as glyph IDs, never rejected from their source Unicode label. Missing glyph uses same-font glyph `0` only when its outline or COLRv0 representation exists.

Wide-open, scissor and every complex clip already accepted by the common clip authority are preserved; the lowerer must not create a text-only clip allowlist. CFF and variable glyphs that `Canvas.drawText()` already expands to `DrawPath` stay on that prepared geometry route with text provenance and are not counted as atlas success.

- [ ] **Step 6: Prove no native dependency**

Add an architectural test that inspects imports/classes of the three new files and rejects `io.ygdrasil.webgpu`, `GPUDevice`, `GPUQueue`, `GPUTexture`, `GPUBuffer` and `GPUCommandEncoder`.

- [ ] **Step 7: Run the lowerer and existing mapper tests**

Run:

```bash
rtk proxy ./gradlew :font:gpu-api:test :kanvas:test \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedTextLowererTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedTextRefusalMatrixTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryTest"
```

Expected: accepted/refused matrix passes; no legacy gate changes yet.

- [ ] **Step 8: Commit**

```bash
rtk git add font/gpu-api gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/text \
  kanvas/build.gradle.kts kanvas/src
rtk git commit -m "feat(surface): add pure prepared text lowerer"
```

---

### Task 5: Blur-aware immutable frame atlas inventory

**Files:**
- Create: `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/GlyphMaskBlur.kt`
- Create: `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/GlyphMaskBlurTest.kt`
- Create: `font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUPreparedTextAtlas.kt`
- Create: `font/gpu-api/src/test/kotlin/org/graphiks/kanvas/glyph/gpu/GPUPreparedTextAtlasTest.kt`
- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/PreparedTextFrameInventory.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/PreparedTextFrameInventoryTest.kt`
- Modify: `font/src/main/kotlin/org/graphiks/kanvas/font/atlas/GlyphAtlasUploadPlan.kt`

**Interfaces:**
- Consumes: ready lowerer draws, `GlyphMaskKey`, `A8GlyphMask`, COLRv0 layer plans.
- Produces:

```kotlin
data class GPUTextA8AtlasPageArtifact(
    val artifactKey: GPUTextArtifactKey,
    val pageIndex: Int,
    val width: Int,
    val height: Int,
    val rowBytes: Int,
    val bytes: List<Int>,
    val contentSha256: String,
)

data class GPUTextA8Instance(
    val glyphId: Int,
    val deviceQuad: List<Float>,
    val uvRect: GPUTextFloatRect,
    val pageIndex: Int,
)

data class GPUPreparedTextSubRun(
    val operationIndex: Int,
    val subRunIndex: Int,
    val representation: GPUPreparedTextRepresentation,
    val pageIndex: Int?,
    val instances: List<GPUTextA8Instance>,
    val materialKey: String,
    val blendPlanIdentity: String,
)

data class PreparedTextFrameInventory(
    val generation: GPUTextArtifactGeneration,
    val pages: List<GPUTextA8AtlasPageArtifact>,
    val subRunsByOperationIndex: Map<Int, List<GPUPreparedTextSubRun>>,
    val metrics: GPUPreparedTextFrameMetrics,
)
```

- [ ] **Step 1: Write blur formula tests**

```kotlin
@Test
fun `blur styles apply before color with exact padding`() {
    val source = mask3x3(center = 255)
    val normal = blur(source, GlyphMaskBlurKey(NORMAL, 1f, 1f, 1f))
    val solid = blur(source, GlyphMaskBlurKey(SOLID, 1f, 1f, 1f))
    val outer = blur(source, GlyphMaskBlurKey(OUTER, 1f, 1f, 1f))
    val inner = blur(source, GlyphMaskBlurKey(INNER, 1f, 1f, 1f))
    assertEquals(3, normal.paddingPx)
    assertTrue(solid.pixels.center() >= normal.pixels.center())
    assertEquals(0, outer.pixels.center())
    assertTrue(inner.pixels.center() > 0)
}
```

Implement `radius = ceil(3 * sigma * max(abs(scaleX), abs(scaleY)))` and padding equal to radius. Use a normalized separable Gaussian kernel and integer rounding after the vertical pass.

- [ ] **Step 2: Write inventory tests for dedup, multi-page and order**

```kotlin
@Test
fun `inventory deduplicates exact masks and preserves ordered subruns`() {
    val result = inventory(
        listOf(a8Draw(glyphs = listOf(7, 7)), imageBoundary(), a8Draw(glyphs = listOf(8))),
        limits = limits(pageWidth = 8, pageHeight = 8, maxPages = 2),
    )
    val ready = assertIs<PreparedTextFrameInventoryResult.Ready>(result).inventory
    assertEquals(2, ready.pages.sumOf { page -> page.uniqueMaskCount() })
    assertEquals(listOf(0, 2), ready.subRunsByOperationIndex.keys.toList())
    assertTrue(ready.pages.size in 1..2)
}
```

Also cover non-overlap, exact UV, padding, row stride, empty glyph/space, repeated font with different face/variation/subpixel/palette, mutation isolation and deterministic content hash.

- [ ] **Step 3: Write budget refusal tests**

Test `maxPages`, `maxPageBytes`, `maxTotalPageBytes`, `maxGlyphs`, `maxInstances`, `maxSubRuns`, `maxInstanceBytes` and WebGPU texture dimensions. Every row must return its exact `GPUTextRefusalCodes` value and an empty externally visible inventory.

- [ ] **Step 4: Run tests and observe missing inventory/blur**

Run:

```bash
rtk proxy ./gradlew :font:glyph:test :font:gpu-api:test :kanvas:test \
  --tests "org.graphiks.kanvas.glyph.GlyphMaskBlurTest" \
  --tests "org.graphiks.kanvas.glyph.gpu.GPUPreparedTextAtlasTest" \
  --tests "org.graphiks.kanvas.surface.gpu.PreparedTextFrameInventoryTest"
```

Expected: compilation fails because the inventory and blur authority do not exist.

- [ ] **Step 5: Implement immutable blur output and atlas pages**

Pack in stable first-use order with one-pixel unfiltered guard plus blur padding. Split to a new page when the current page cannot place the next exact mask. Never resize or mutate a page after publication. Compute SHA-256 after all bytes are finalized.

- [ ] **Step 6: Implement ordered subrun grouping**

Start a new subrun when representation, page, material key, blend identity, clip identity or transform class changes. Never reorder across an image/core boundary and never merge two non-contiguous text draws.

- [ ] **Step 7: Run all focused tests**

Run:

```bash
rtk proxy ./gradlew :font:glyph:test :font:gpu-api:test :font:test :kanvas:test \
  --tests "org.graphiks.kanvas.surface.gpu.PreparedTextFrameInventoryTest"
```

Expected: deterministic inventory, blur and compatibility adapter tests pass.

- [ ] **Step 8: Commit**

```bash
rtk git add font/glyph font/gpu-api font/src kanvas/src
rtk git commit -m "feat(text): build immutable per-frame glyph inventories"
```

---

### Task 6: Generic R8 upload primitives shared by A8 and COLRv0

**Files:**
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/artifacts/GPUPreparedR8UploadArtifact.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUTextureFrameResourcePlan.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUR8FrameResourcePlan.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUPreparedImageFrameResourcePlan.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlan.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUR8FrameResourcePlanTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlanTest.kt`

**Interfaces:**
- Consumes: immutable page bytes, `GPUUploadLayout`, existing image frame plan.
- Produces:

```kotlin
interface GPUTextureFrameResourcePlan {
    val stagingRef: GPUFrameBufferRef
    val frameTextureRef: GPUFrameTextureRef
    val uploadTaskLayout: GPUUploadLayout
    val preparationRequests: List<GPUResourcePreparationRequest>
    val memoryAllocations: List<GPUFrameMemoryAllocation>
    fun bytesForUpload(): ByteArray
}

class GPUPreparedR8UploadArtifact internal constructor(
    val key: String,
    val width: Int,
    val height: Int,
    val rowBytes: Int,
    val generation: Long,
    val contentHash: String,
    bytes: ByteArray,
) {
    fun tightBytesForUpload(): ByteArray
}
```

- `GPUImageFrameResourcePlan` implémente `GPUTextureFrameResourcePlan`.
- `GPUFrameStep.UploadResourceStep.textureResourcePlan` remplace la spécialisation image ; les accessors typés sont calculés par cast, pas stockés en double.

- [ ] **Step 1: Write immutable R8 artifact tests**

```kotlin
@Test
fun `R8 artifact snapshots bytes and validates content hash`() {
    val bytes = byteArrayOf(0, 1, 128.toByte(), 255.toByte())
    val artifact = prepareR8("page-0", 2, 2, 2, 3, bytes)
    bytes.fill(0)
    assertContentEquals(byteArrayOf(0, 1, 128.toByte(), 255.toByte()), artifact.tightBytesForUpload())
    assertEquals(sha256(artifact.tightBytesForUpload()), artifact.contentHash)
}
```

- [ ] **Step 2: Write generic texture-plan tests**

Prove both `GPUImageFrameResourcePlan` and `GPUR8FrameResourcePlan` satisfy the same upload-step constructor, while RGBA and R8 row padding remain format-specific.

- [ ] **Step 3: Run tests and observe missing generic plan**

Run:

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "org.graphiks.kanvas.gpu.renderer.resources.GPUR8FrameResourcePlanTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanTest"
```

Expected: compilation fails on the new artifact/plan types.

- [ ] **Step 4: Implement R8 row upload layout**

Use 256-byte WebGPU copy-row alignment for staging, keep tight row bytes in the artifact, zero padding bytes, validate `bytes.size == rowBytes * height`, `rowBytes >= width`, non-negative generation and limits before allocating any native resource.

- [ ] **Step 5: Generalize existing image upload steps mechanically**

Replace stored `imageResourcePlan` with `textureResourcePlan` and retain:

```kotlin
val imageResourcePlan: GPUImageFrameResourcePlan?
    get() = textureResourcePlan as? GPUImageFrameResourcePlan
val r8ResourcePlan: GPUR8FrameResourcePlan?
    get() = textureResourcePlan as? GPUR8FrameResourcePlan
```

Update recording, preflight and materializer call sites without changing FP-04 behavior.

- [ ] **Step 6: Run FP-04 regression suites**

Run:

```bash
rtk proxy ./gradlew :gpu-renderer:test :kanvas:test \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedSurfaceNativePreflightTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedImageRenderRunMaterializerTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceFrameBuilderTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceProductNativeSmokeTest"
```

Expected: FP-04 image tests remain green and new R8 tests pass.

- [ ] **Step 7: Commit**

```bash
rtk git add gpu-renderer/src kanvas/src/test
rtk git commit -m "refactor(renderer): share prepared texture upload plans"
```

---

### Task 7: Text semantic payloads and Surface command expansion

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/PayloadContracts.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/GPUPreparedTextPayloadTest.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUFramePathApiInventory.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilder.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceSemanticBuilder.kt`
- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextSemanticBuilder.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextSemanticBuilderTest.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilderTextTest.kt`

**Interfaces:**
- Consumes: `PreparedTextFrameInventory`, common material program, generic R8 artifact.
- Produces:

```kotlin
class GPUDrawSemanticPayload.TextA8 internal constructor(
    payloadRef: GPUDrawPayloadRef,
    val atlas: GPUPreparedR8UploadArtifact,
    val atlasGeneration: GPUTextArtifactGeneration,
    val instances: List<GPUTextA8Instance>,
    val material: GPUPreparedMaterialProgram,
    val targetBounds: GPUPixelBounds,
    val scissorBounds: GPUPixelBounds,
    val blendPlanIdentity: String,
    val canonicalHash: String,
) : GPUDrawSemanticPayload
```

- `GPUDrawSemanticPayload.ColorGlyph` contient le même type d’artefact R8, tout en gardant ses layers/couleurs et son shader distinct.
- `GPUFramePathVisualCommand.preparedText` porte le sous-run préparé.

- [ ] **Step 1: Write payload immutability/hash tests**

```kotlin
@Test
fun `TextA8 snapshots atlas instances material and generation`() {
    val input = textPayloadInput(coverage = listOf(0, 1, 128, 255), generation = 3)
    val semantic = GPUPreparedTextPayloadGatherer().gather(input)
    input.atlasBytes.fill(0)
    input.instances.clear()
    assertEquals(listOf(0, 1, 128, 255), semantic.atlas.tightBytesForUpload().map { it.toInt() and 255 })
    assertEquals(GPUTextArtifactGeneration(3), semantic.atlasGeneration)
    assertTrue(semantic.hasCanonicalHashIntegrity())
}
```

- [ ] **Step 2: Write command expansion and ordering tests**

Input: `Core -> Text(two A8 subruns) -> Image -> Text(COLRv0)`. Assert unique command IDs `0..4`, original operation provenance, stable subrun index and exact paint order. No approximate `fontSize * 10` bounds or `hashCode()` identity may remain.

- [ ] **Step 3: Run tests and observe missing payload/expansion**

Run:

```bash
rtk proxy ./gradlew :gpu-renderer:test :kanvas:test \
  --tests "org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedTextPayloadTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedTextSemanticBuilderTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceFrameBuilderTextTest"
```

Expected: compilation fails because `TextA8` and prepared text command facts are absent.

- [ ] **Step 4: Implement two-pass text mapping**

In `GPUPreparedSurfaceFrameBuilder`:

1. lower every `DrawText` ;
2. build one inventory for the whole frame ;
3. call `GPUOpMapper.mapOperations(..., preparedTextInventory = inventory)` ;
4. emit one visual command per subrun with sequential IDs ;
5. record only after all lowering/inventory work is accepted.

The diagnostic inventory path calls the same production functions.

- [ ] **Step 5: Implement semantic gathering**

Gather `TextA8` from exact page, instances and material. Gather COLRv0 through the existing `GPUColorGlyphPayloadGatherer`, replacing its inline atlas bytes by the shared R8 artifact. Stroke subruns are not emitted as `TextA8`; Task 11 maps them to prepared path geometry.

- [ ] **Step 6: Run focused and existing Surface builder tests**

Run:

```bash
rtk proxy ./gradlew :gpu-renderer:test :kanvas:test \
  --tests "org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedTextPayloadTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedTextSemanticBuilderTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceFrameBuilderTextTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceFrameBuilderTest"
```

Expected: text semantics are pure and exact; FP-04 regressions pass; product gate remains closed.

- [ ] **Step 7: Commit**

```bash
rtk git add gpu-renderer/src kanvas/src
rtk git commit -m "feat(surface): gather prepared text semantics"
```

---

### Task 8: Heterogeneous task graph and upload-before-sample ordering

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedSurfaceFrameTaskListBuilder.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUColorGlyphPreparedTaskListBuilder.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedSurfaceFrameTaskListBuilderTextTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUColorGlyphPreparedTaskListBuilderTest.kt`

**Interfaces:**
- Consumes: ordered packets with `CorePrimitive`, `SampledImage`, `TextA8`, `ColorGlyph`.
- Produces: one `GPUTaskList` with deduplicated image/R8 uploads, frame-global instance/uniform buffers, ordered render packets and optional one readback.

- [ ] **Step 1: Write exact graph-order test**

```kotlin
@Test
fun `mixed task graph uploads shared text pages before first use`() {
    val recorded = buildMixed("Core -> Text -> Image -> Text -> ColorGlyph")
    val plan = GPUFramePlanner.plan(recorded.taskList)
    assertEquals(
        listOf("CorePrimitive", "TextA8", "SampledImage", "TextA8", "ColorGlyph"),
        renderSemantics(plan),
    )
    assertTrue(textUploadIndex(plan, page = 0) < firstTextConsumerIndex(plan, page = 0))
    assertEquals(1, textUploads(plan, page = 0).size)
    assertEquals(1, plan.steps.count { it is GPUFrameStep.ReadbackCopyStep })
}
```

- [ ] **Step 2: Write batching and budget tests**

Assert:

- one identical mask stored once ;
- one page uploaded once ;
- draw count equals ordered subrun count ;
- instance count equals glyph instance count ;
- no `UniformData`/bind-group resource is created per glyph ;
- multi-page resources all use `FrameLocal` and survive until completion ;
- aggregate buffer/texture limits refuse before task publication.

- [ ] **Step 3: Run tests and observe builder refusal for text payloads**

Run:

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameTaskListBuilderTextTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUColorGlyphPreparedTaskListBuilderTest"
```

Expected: the common builder rejects non-Core/non-Image semantics.

- [ ] **Step 4: Extend common task construction**

Create one R8 upload task per exact page artifact key and generation. Add dependencies from upload to every consuming render. Concatenate A8 instances into one aligned frame buffer and expose per-subrun `firstInstance`/`instanceCount`. Preserve packet order; never globally sort by material/page.

- [ ] **Step 5: Fold COLRv0 task topology into the common builder**

Keep `GPUColorGlyphPreparedTaskListBuilder` as a thin test adapter that calls the common builder with one `ColorGlyph` semantic. Delete its independent topology logic after parity tests prove identical resource roles and codes.

- [ ] **Step 6: Run recording regressions**

Run:

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameTaskListBuilder*" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUColorGlyphPreparedTaskListBuilderTest"
```

Expected: exact ordering and budget tests pass; old ColorGlyph adapter remains behavior-compatible.

- [ ] **Step 7: Commit**

```bash
rtk git add gpu-renderer/src
rtk git commit -m "feat(renderer): record mixed prepared text tasks"
```

---

### Task 9: Pure text preflight and zero-allocation refusal

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedSurfaceNativePreflight.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedTextNativePreflightTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedSurfaceNativePreflightTest.kt`

**Interfaces:**
- Consumes: complete `GPUFramePlan`, encoder plan, resources, shader/material contract and generation seal.
- Produces: accepted ordered runs `Core | Image | TextA8 | ColorGlyph`, or one stable refusal before native materialization.

- [ ] **Step 1: Write stale generation and malformed ABI tests**

```kotlin
@Test
fun `text preflight refuses stale generation before native allocation`() {
    val input = textFrame(generation = 3)
    val stale = input.copy(generationSeal = input.generationSeal.withTextPageGeneration(2))
    val result = preflight.validate(stale)
    assertEquals("stale.preflight.text.atlas_generation", assertIs<Refused>(result).code)
    assertEquals(0, nativeProbe.totalCreations)
}
```

Add rows for altered bytes/hash, page dimensions, row stride, UV, instance stride/range, material ABI, shader entry point, binding layout, upload dependency, scissor, blend identity, resource lifetime and ownership.

- [ ] **Step 2: Write exact mixed-run partition test**

Assert preflight emits run types and source-scope indices matching:

```text
Core -> TextA8 -> SampledImage -> TextA8 -> ColorGlyph -> Readback
```

and exact operand keys for R8 upload, render, readback and optional surface blit.

- [ ] **Step 3: Run tests and observe text semantics are refused**

Run:

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedTextNativePreflightTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedSurfaceNativePreflightTest"
```

Expected: new accepted text frames fail because only Core/Image runs are recognized.

- [ ] **Step 4: Implement text authority validation**

Validate:

- one upload per unique R8 artifact and upload-before-first-consumer ;
- exact `GPUTextArtifactGeneration` across artifact, payload, resource and encoder scope ;
- R8 bytes/hash/row layout ;
- instance ranges non-overlapping and within the frame buffer ;
- material WGSL entry point, ABI hash, uniforms and sampled resources ;
- fixed-function or shader blend plan from the common authority ;
- target/scissor/clip authority ;
- `FrameLocal` page ownership in FP-05 ;
- exact resource/operand partition.

- [ ] **Step 5: Run preflight and full recording tests**

Run:

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedTextNativePreflightTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedSurfaceNativePreflightTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameTaskListBuilderTextTest"
```

Expected: all accepted/refused cases pass with `nativeProbe.totalCreations == 0` for every refusal.

- [ ] **Step 6: Commit**

```bash
rtk git add gpu-renderer/src
rtk git commit -m "feat(renderer): preflight prepared text frames"
```

---

### Task 10: Native A8 shader, batching and ownership

**Files:**
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/PreparedTextA8Shader.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedTextSessionCache.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedTextRenderRunMaterializer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedSurfaceFramePayloadMaterializer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kFramePayloadMaterializerDispatcher.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/PreparedTextA8ShaderTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedTextRenderRunMaterializerTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedTextOwnershipTest.kt`

**Interfaces:**
- Consumes: accepted TextA8 runs, generic R8 plan, shared material program.
- Produces: target-bound render operands with one instanced draw per subrun and completion-owned frame resources.

- [ ] **Step 1: Write shader formula and parser tests**

The fragment contract must contain this exact semantic sequence:

```wgsl
let paintStraightLinear = evaluate_prepared_material(input.localPosition);
let paintAlpha = clamp(text.paintAlpha, 0.0, 1.0);
let coverage = textureSample(textAtlas, textSampler, input.uv).r;
let sourceAlpha = paintStraightLinear.a * paintAlpha * coverage;
return vec4<f32>(
    paintStraightLinear.rgb * sourceAlpha,
    sourceAlpha,
);
```

The test parses/reflection-validates the module, asserts one R8 texture/sampler, one instance buffer, one material uniform block, and rejects the legacy `vec4(color.rgb, a8 * color.a)` formula.

- [ ] **Step 2: Write materializer batching tests**

With 100 glyph instances in two compatible subruns, assert:

```kotlin
assertEquals(2, native.drawCalls.size)
assertEquals(listOf(64, 36), native.drawCalls.map { it.instanceCount })
assertEquals(2, native.createdBindGroups.size)
assertEquals(1, native.createdInstanceBuffers.size)
assertEquals(1, native.createdTextures.size)
```

- [ ] **Step 3: Write ownership and rollback tests**

Cover:

- failure during texture/view/sampler/buffer/bind-group creation closes every created object once ;
- failure after submit retains pages/buffers/bind groups until completion ;
- readback failure does not release completion resources early ;
- materializer close/recreate does not reuse a dead page ;
- invariant pipeline cache is `Borrowed`, page and buffers are `PayloadOwnedCompletion`.

- [ ] **Step 4: Run tests and observe missing native text materializer**

Run:

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "org.graphiks.kanvas.gpu.renderer.wgsl.PreparedTextA8ShaderTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedTextRenderRunMaterializerTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedTextOwnershipTest"
```

Expected: compilation fails because the shader/materializer do not exist.

- [ ] **Step 5: Implement pipeline-only session cache**

Cache bind-group layouts, pipeline layouts, samplers and render pipelines by exact material/target/ABI key. Do not cache atlas textures, views, instance buffers, uniforms or bind groups across frames.

- [ ] **Step 6: Implement A8 materialization**

Create/upload R8 pages and one concatenated instance buffer, encode material uniforms/resources, create one bind group per compatible subrun, issue instanced draws, then return transferable completion owners. Follow the already-proven FP-04 setup ledger and retained-close-owner pattern.

- [ ] **Step 7: Integrate into mixed Surface materializer**

Add Text runs to `operandsByStep`; keep the final order solely from `accepted.exactScopeKeys`. Do not introduce a second encoder, submit or readback.

- [ ] **Step 8: Run native proxy tests**

Run:

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "org.graphiks.kanvas.gpu.renderer.wgsl.PreparedTextA8ShaderTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedTextRenderRunMaterializerTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedTextOwnershipTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedSurfaceFramePayloadMaterializerTest"
```

Expected: batching, formula, ordering and close-once assertions pass.

- [ ] **Step 9: Commit**

```bash
rtk git add gpu-renderer/src
rtk git commit -m "feat(renderer): materialize prepared A8 text"
```

---

### Task 11: COLRv0/emoji integration in the mixed frame

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/PayloadContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kColorGlyphFramePayloadMaterializer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kColorGlyphSessionCache.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kColorGlyphRenderRunMaterializer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedSurfaceFramePayloadMaterializer.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedColorGlyphMixedFrameTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUColorGlyphTrueColrFixtureTest.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedEmojiTextTest.kt`

**Interfaces:**
- Consumes: existing `GPUColorGlyphLayerPlan`, COLRv0 parser/handoff, generic R8 artifact, mixed frame plan.
- Produces: ordered `ColorGlyph` run operands using the distinct primitive-color shader and shared R8 upload/ownership path.

- [ ] **Step 1: Write color semantics tests**

```kotlin
@Test
fun `COLRv0 keeps layer color and applies paint alpha once`() {
    val rgba = renderColorGlyph(
        paletteColor = rgba(0.8f, 0.4f, 0.2f, 0.75f),
        paintColor = rgba(0f, 1f, 0f, 0.5f),
    )
    assertEquals(rgba(0.3f, 0.15f, 0.075f, 0.375f), rgba, absoluteTolerance = 1f / 255f)
}
```

Add foreground/currentColor, palette selection, layer order and A8+COLRv0 same-frame rows.

- [ ] **Step 2: Write emoji representation tests**

Accept:

- already-shaped monochrome emoji glyph with A8 representation ;
- already-shaped COLRv0 glyph ;
- a ZWJ sequence already reduced to one glyph ID.

Refuse exact codes for implicit shaping, font fallback, unproved COLRv1, CBDT/CBLC, sbix and SVG. The tests never infer support/refusal from the word “emoji”.

- [ ] **Step 3: Run tests and observe the isolated one-packet limitation**

Run:

```bash
rtk proxy ./gradlew :gpu-renderer:test :kanvas:test \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedColorGlyphMixedFrameTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedEmojiTextTest"
```

Expected: mixed ColorGlyph fails because the current materializer requires one render scope and one packet.

- [ ] **Step 4: Split invariant ColorGlyph cache from page residency**

Retain pipeline/layout/sampler cache entries; remove atlas texture residency from the session cache for FP-05. Pages remain `PayloadOwnedCompletion` and use the generic R8 upload artifact.

- [ ] **Step 5: Implement multi-run ColorGlyph materialization**

Materialize every accepted ColorGlyph run into the canonical target, preserve layer order, bind foreground/palette data, and return operands keyed by source scope. Do not reuse the A8 fragment shader.

- [ ] **Step 6: Run true-font, mixed and ownership tests**

Run:

```bash
rtk proxy ./gradlew :gpu-renderer:test :kanvas:test \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUColorGlyphTrueColrFixtureTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedColorGlyphMixedFrameTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedEmojiTextTest"
```

Expected: COLRv0/foreground/alpha/emoji rows pass, unsupported formats retain exact refusals.

- [ ] **Step 7: Commit**

```bash
rtk git add gpu-renderer/src kanvas/src/test
rtk git commit -m "feat(renderer): mix prepared color glyph runs"
```

---

### Task 12: Text stroke, blur and filter boundaries

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextLowerer.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/PreparedTextFrameInventory.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUStroke.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextStrokeTest.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextBlurTest.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextFilterBoundaryTest.kt`

**Interfaces:**
- Consumes: exact glyph outlines, common prepared path/stroke authority, `NormalizedMaskFilter.Blur`.
- Produces: stroke text as prepared path visuals with text provenance; blur text as padded A8 masks; terminal image-filter refusal.

- [ ] **Step 1: Write stroke routing tests**

For width `0`, positive width, caps `BUTT/ROUND/SQUARE`, joins `MITER/ROUND/BEVEL`, miter limits and supported dash arrays:

```kotlin
val visuals = prepareText(strokedText(cap = ROUND, join = BEVEL, width = 3f))
assertTrue(visuals.all { it.normalized is NormalizedDrawCommand.FillPath })
assertTrue(visuals.all { it.provenance.operation == "drawText.stroke-path" })
assertTrue(visuals.none { it.preparedText is AtlasA8 })
```

Assert unsupported stroke/path effects return the existing common stroke code.

- [ ] **Step 2: Write blur identity/pixel tests**

Assert sigma/style/transform/padding alter `GlyphMaskKey`; repeated identical blur deduplicates; rendered bounds include padding; CPU oracle matches native output with `maxChannelDelta <= 1`.

- [ ] **Step 3: Write filter-boundary tests**

```kotlin
@Test
fun `text image filter is terminal until FP-07`() {
    val refused = assertIs<GPUPreparedTextLowering.Refused>(
        lower(drawText(paint = Paint(imageFilter = blurImageFilter()))),
    )
    assertEquals(GPUTextRefusalCodes.IMAGE_FILTER_REQUIRES_COMPOSITE, refused.code)
}
```

Other unsupported mask filters use the common mask-filter refusal. No legacy renderer probe may be invoked.

- [ ] **Step 4: Run tests and observe missing stroke/blur routes**

Run:

```bash
rtk proxy ./gradlew :kanvas:test \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedTextStrokeTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedTextBlurTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedTextFilterBoundaryTest"
```

Expected: stroke/blur tests fail on the initially refused routes.

- [ ] **Step 5: Lower stroke outlines to common prepared paths**

Resolve all glyph paths first. If any path/metrics/style fails, refuse the logical text draw before emitting the first visual. Otherwise translate each path by text origin + positioned glyph offset, retain the original transform/clip/paint, and pass it through the existing path/stroke lowerer.

- [ ] **Step 6: Connect blur descriptor to mask generation**

Map `NormalizedMaskFilter.Blur` to `GlyphMaskBlurKey`, include it in `GlyphMaskKey`, rasterize then blur before packing. Apply material/color only in the fragment shader.

- [ ] **Step 7: Run stroke, blur and geometry regressions**

Run:

```bash
rtk proxy ./gradlew :font:glyph:test :gpu-renderer:test :kanvas:test \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedTextStrokeTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedTextBlurTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedTextFilterBoundaryTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPathStrokeInputTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.geometry.*Stroke*"
```

Expected: common stroke semantics remain unchanged; blur and terminal filter boundaries pass.

- [ ] **Step 8: Commit**

```bash
rtk git add kanvas/src font/glyph/src
rtk git commit -m "feat(surface): prepare stroked and blurred text"
```

---

### Task 13: Pixel oracles, native mixed smokes and cold-frame telemetry

**Files:**
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextTestFixtures.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextPixelOracle.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextPixelTest.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceTextNativeSmokeTest.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameExecution.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedNativeFramePayload.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedTextFrameCountersTest.kt`

**Interfaces:**
- Consumes: product-seam frame build/execution with gate still closed.
- Produces: independent sRGB oracle, native output evidence and `GPUPreparedTextFrameMetrics`.

- [ ] **Step 1: Create deterministic fixtures**

Fixtures include:

- A8 coverage `0, 1, 128, 255` ;
- diagonal antialiased glyph ;
- repeated glyph/page sharing ;
- two fonts and face indices ;
- affine transform and scissor ;
- one complex clip accepted by the common clip authority ;
- solid + linear/radial/sweep/conical gradient ;
- registered runtime effect and supported image shader ;
- stroke cap/join/dash ;
- blur normal/solid/outer/inner ;
- COLRv0 palette + foreground ;
- monochrome and COLRv0 emoji ;
- missing glyph with/without `.notdef`.

Every fixture accessor returns a fresh byte/list snapshot.

- [ ] **Step 2: Implement the independent pixel oracle**

For A8:

```text
sRGB material -> linear
-> material alpha × paint alpha
-> premultiply
-> multiply RGBA by coverage
-> common blend
-> encode target to sRGB
```

For COLRv0, start from the resolved primitive layer color, not the paint shader color, then apply paint alpha once. Expose `maxChannelDelta(actual, expected)` and require `<= 1`.

- [ ] **Step 3: Write native smoke matrix**

Run through the prepared execution seam, not `GPUBackendRuntimeNative` legacy helpers. Cover completion-only, readback, runtime close/recreate and mixed:

```text
Core -> TextA8 -> Image -> TextA8 -> ColorGlyph
```

Assert one encoder, one submit, at most one readback, nonblank output and exact counters.

- [ ] **Step 4: Run tests and observe missing native/counter integration**

Run:

```bash
rtk proxy ./gradlew :gpu-renderer:test :kanvas:test \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedTextFrameCountersTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedTextPixelTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceTextNativeSmokeTest"
```

Expected: oracle tests compile after fixtures are added; native route/counter assertions fail until integration is complete.

- [ ] **Step 5: Add cold-frame telemetry**

Record lowering/raster/packing nanoseconds, page bytes/count, instances, subruns, draws, bind groups and submits. Keep A8/COLRv0/path-stroke counters separate. Do not add cache hit/miss or warm-frame claims.

Measure at least 30 independent cold frames by rebuilding the frame inventory and frame-local pages for every sample. Publish sorted raw nanoseconds plus `p50 = sample[(n - 1) * 50 / 100]` and `p95 = sample[(n - 1) * 95 / 100]`; do not discard slow samples as warmup because this is explicitly the cold-frame lane.

- [ ] **Step 6: Run native smokes serially**

Run:

```bash
rtk proxy ./gradlew :kanvas:test --no-parallel \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedTextPixelTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceTextNativeSmokeTest"
```

Expected: every accepted row has `maxChannelDelta <= 1`; mixed frame uses one submit; close/recreate succeeds.

- [ ] **Step 7: Run FP-04 + FP-05 native regression set**

Run:

```bash
rtk proxy ./gradlew :gpu-renderer:test :kanvas:test --no-parallel \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedSurfaceFramePayloadMaterializerTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceProductNativeSmokeTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceTextNativeSmokeTest"
```

Expected: images and text pass together; no wgpu4k crash or ownership leak.

- [ ] **Step 8: Commit**

```bash
rtk git add gpu-renderer/src kanvas/src
rtk git commit -m "test(surface): prove prepared text pixels and lifetimes"
```

---

### Task 14: Atomic product cutover and legacy Text removal

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameGate.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPULegacyImmediatePathAdapter.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductRouter.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductEntry.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameGateTest.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductRouterTest.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductEntryTest.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextNoFallbackTest.kt`

**Interfaces:**
- Consumes: fully proven prepared text build/preflight/materializer.
- Produces: `DrawText` is prepared-or-terminal; legacy adapter contains only `Vertices` and `Composites`.

- [ ] **Step 1: Write gate and no-fallback tests before changing production**

```kotlin
@Test
fun `DrawText enters prepared candidate`() {
    assertIs<GPUPreparedSurfaceEligibility.Candidate>(
        GPUPreparedSurfaceFrameGate.classify(listOf(validDrawText()), config),
    )
}

@Test
fun `post-admission text refusal is terminal and never calls legacy`() {
    val legacy = LegacyProbe()
    val route = router.route(listOf(refusedText()), executionPort = refusingPort(legacy))
    assertIs<GPUPreparedSurfaceProductRoute.Terminal>(route)
    assertEquals(0, legacy.invocations)
}
```

- [ ] **Step 2: Write production absence tests**

Assert repository production source has no:

- `legacy.surface.prepared.family.text` ;
- `LegacyDisplayOpFamily.Text` ;
- `GPULegacyImmediatePathAdapter` branch for `DrawText` ;
- product call from accepted text to `TextBridge.rasterize`, `GpuTextBlob`, `renderShaderText` or `renderColorText`.

- [ ] **Step 3: Run tests and verify RED on the still-closed gate**

Run:

```bash
rtk proxy ./gradlew :kanvas:test \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceFrameGateTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceProductRouterTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedTextNoFallbackTest"
```

Expected: DrawText still classifies as legacy and absence assertions fail.

- [ ] **Step 4: Perform the atomic cutover**

- classify `DrawText` as a visual prepared operation ;
- include text in the router’s prepared-family terminal policy ;
- remove `Text` from `LegacyDisplayOpFamily` and `allowedFamilies` ;
- delete `preparedSurfaceCode()` text branch and its string ;
- make every prepared text refusal terminal after candidate admission ;
- keep `Vertices` and `Composites` unchanged.

- [ ] **Step 5: Run product tests serially**

Run:

```bash
rtk proxy ./gradlew :kanvas:test --no-parallel \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceFrameGateTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceProductRouterTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceProductEntryTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedTextNoFallbackTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceTextNativeSmokeTest"
```

Expected: prepared success/refusal behavior passes; legacy probe remains zero.

- [ ] **Step 6: Search production sources**

Run:

```bash
rtk rg -n \
  "legacy\\.surface\\.prepared\\.family\\.text|LegacyDisplayOpFamily\\.Text" \
  kanvas/src/main gpu-renderer/src/main font
```

Expected: no matches.

- [ ] **Step 7: Commit**

```bash
rtk git add kanvas/src
rtk git commit -m "feat(surface): activate prepared text routing"
```

---

### Task 15: FP-05 evidence, aggregate validation and independent review

**Files:**
- Create: `reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-05-prepared-text-route.md`
- Modify: `reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md`
- Modify only if the implementation changed a public contract:
  - `.upstream/specs/font/04-glyph-rendering-and-coverage.md`
  - `.upstream/specs/font/05-color-fonts-emoji-and-fixtures.md`

**Interfaces:**
- Consumes: all Task 1–14 test results, telemetry, review findings and non-claims.
- Produces: bounded closure evidence for FP-05; FP-06 remains next active frame-plan task.

- [ ] **Step 1: Write the evidence report from measured outputs**

Record:

- exact commits and commands ;
- test counts and native host/device facts ;
- accepted A8/COLRv0/material/stroke/blur rows ;
- refusal matrix and exact codes ;
- pixel deltas ;
- upload-before-sample graph ;
- encoder/submit/readback and ownership counters ;
- cold-frame p50/p95 samples ;
- explicit FP-07/FP-09/FP-10/FP-11 non-claims ;
- any wgpu4k issue URL and minimized reproduction if one was required.

- [ ] **Step 2: Run focused module suites**

Run:

```bash
rtk proxy ./gradlew :font:core:test :font:glyph:test :font:gpu-api:test \
  :font:test :gpu-renderer:test :kanvas:test --no-parallel
```

Expected: all relevant suites pass. A pre-existing unrelated failure must be reproduced separately and documented with exact command/output; it must not be hidden or relabeled as FP-05 success.

- [ ] **Step 3: Run source and diff hygiene checks**

Run:

```bash
rtk git diff --check
rtk rg -n \
  "legacy\\.surface\\.prepared\\.family\\.text|LegacyDisplayOpFamily\\.Text|atlasGenerationTokens|generationToken" \
  kanvas/src/main gpu-renderer/src/main font
rtk git status --short
```

Expected: `git diff --check` is clean; production search has no obsolete text route/generation matches; only intended report/spec changes are uncommitted.

- [ ] **Step 4: Request independent two-stage review**

Use `superpowers:requesting-code-review` with:

1. a spec-compliance reviewer checking every FP-05 criterion and refusal boundary ;
2. a technical reviewer checking Graphite+Dawn pragmatism, transform order, premultiplication, generation identity, batching, rollback and completion ownership.

Classify every finding as Critical, Important, Minor or invalid. Fix every legitimate Critical/Important finding, rerun its smallest reproducer, then rerun the focused aggregate.

- [ ] **Step 5: Update active roadmap only after review is clean**

Set FP-05 status to `completed` and link the evidence report only when no legitimate Critical/Important finding remains. Keep FP-06 `pending`; do not mark FP-07, FP-09, FP-10 or FP-11 complete.

- [ ] **Step 6: Commit evidence and roadmap**

```bash
rtk git add reports/upstream-rebaseline .upstream/specs/font
rtk git commit -m "docs(surface): close prepared text route"
```

- [ ] **Step 7: Final verification of the branch**

Run:

```bash
rtk git status --short --branch
rtk git log --oneline --decorate -20
rtk git diff --check origin/codex/graphite-dawn-frame-plan-design...HEAD
```

Expected: worktree clean, commits ordered Task 1–15, diff check clean, `.superpowers/sdd/` absent.

---

## Plan Self-Review Checklist

- [ ] Every design requirement maps to at least one task:
  - identity/generation → Task 1 ;
  - exact mask/AA → Task 2 ;
  - common materials → Task 3 ;
  - pure lowerer/refusals/missing glyph/emoji boundaries → Task 4 ;
  - blur/atlas/dedup/budgets/subruns → Task 5 ;
  - shared R8 upload → Task 6 ;
  - semantic payloads/exact bounds/order → Task 7 ;
  - task graph/batching → Task 8 ;
  - preflight/zero allocation → Task 9 ;
  - A8 shader/native ownership → Task 10 ;
  - COLRv0/emoji → Task 11 ;
  - stroke/filter boundaries → Task 12 ;
  - pixel/native/performance evidence → Task 13 ;
  - atomic cutover/no fallback → Task 14 ;
  - report/review/roadmap → Task 15.
- [ ] No step delegates unspecified error handling, edge cases or tests.
- [ ] All later signatures use `GPUTextArtifactGeneration`, `GPUPreparedMaterialProgram`, `GPUPreparedR8UploadArtifact`, `PreparedTextFrameInventory` and `GPUDrawSemanticPayload.TextA8` consistently.
- [ ] No task introduces persistent atlas residency before FP-09.
- [ ] No task regenerates GM renders/scores before FP-11.
- [ ] No task changes animation.
