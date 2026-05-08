# Migration plan — Raster pipeline completion (post-Group A)

> **Status** : 🔄 **en cours** — plan vivant. Premiers chantiers livrés
> (C5, I1, I2, I3, I4 ✅ ; D1.1 ✅ ; D1.2 / I5 🔄). Voir status par
> chantier ci-dessous.
>
> Ce document liste les chantiers restants pour atteindre la parité-iso
> avec Skia raster (`include/core/*.h` + `include/effects/*.h`), hors
> GM ports et hors backends GPU (Ganesh / Graphite).
>
> Source de vérité : Skia 4.x dans
> `/Users/chaos/workspace/kanvas-forge/skia-main/`.
>
> **But** : que chaque API publique côté Kotlin se comporte de manière
> observable identique à son équivalent C++ Skia, pour permettre à terme
> l'exécution iso-fidèle de DM (Skia DM = test runner upstream).
>
> ## Avancement (snapshot)
>
> | Chantier | Status | Notes |
> |---|---|---|
> | **C5** ARGB_4444 + color-management completion | ✅ shipped (Phase 6s + ARGB_4444 commit) | Display P3 / HDR PQ tests restent (~150 LOC) |
> | **D1.0** SkPathOps skeleton + TightBounds | ✅ shipped | Entry points `Op` / `Simplify` / `AsWinding` retournent `null` jusqu'à D1.3 |
> | **D1.1** Foundation (curves, line ops, intersections, TSect) | ✅ shipped (15 sous-slices) | a, b, c, d.1-3, e.1, e.2.a, e.2.b, e.2.c.1-4, e.3 |
> | **D1.2** Op contour assembly | 🔄 en cours | a, b, b.2.0, c, e, f, i, i.2 livrés ; reste winding propagation + assembleur |
> | **D1.3** Top-level entry points | 📋 pending | Bloqué sur D1.2 close |
> | **D2** SkRuntimeEffect shim | 📋 doc-only | Plan ajouté ; pas d'implem |
> | **D3** Image codecs | 📋 pending | |
> | **D4** DM sink architecture | 📋 pending | |
> | **I1** SkTextBlob + drawTextBlob + Picture wiring | ✅ shipped (I1.1-1.5) | 4 GM ports |
> | **I2** Glyph cache + variable-fonts (light) + subpixel | ✅ shipped (I2.1-2.3) | Variable fonts AWT-wired déféré |
> | **I3** SkRegion + SkAAClip + SkRasterClip | ✅ shipped (I3.1-3.3) | clipMask Phase 7q remplacé par SkAAClip |
> | **I4** SkShaper (Primitive + JavaTextLayout + wrap) | ✅ shipped (I4.1-4.3) | HarfBuzz parity hors scope |
> | **I5** drawPoints / drawAtlas / drawVertices / drawPatch | 🔄 en cours | I5.1, I5.2, I5.3.a/b livrés ; I5.4 (Patch) reste |
> | **C1** Image filters extras | 📋 pending | Group A core déjà shipped (Offset/Blur/MatrixTransform/DropShadow/ColorFilter/Compose) |
> | **C2** Path effects extras (kMorph, StrokeAndFill recipe) | 📋 pending | |
> | **C3** SkEmbossMaskFilter | 📋 pending | |
> | **C4** drawAnnotation / drawDrawable / drawShadow | 📋 pending | |
> | **B1** SkPDF (PDFBox adapter) | 📋 pending | |
> | **B2** SkSVGCanvas | 📋 pending | |
> | **Q1** SkAutoCanvasRestore Kotlin idiom | 📋 pending | |
> | **Q2** Canvas wrappers | 📋 pending | |
> | **Q3** SkBBHFactory + Picture cull | 📋 pending | |
> | **Q4** SkDeferredDisplayList | 📋 low-priority | |
> | **Q5** Linear sRGB diagnostic | 📋 pending | Phase 7e' réinvestigué |

## Table des matières

1. [Principes iso-fidélité](#principes-iso-fidélité)
   - [Iso-fidelity exceptions](#iso-fidelity-exceptions)
2. [Architecture cible](#architecture-cible)
3. [Chantiers critiques DM](#chantiers-critiques-dm)
   - [D1 — `SkPathOps`](#d1--skpathops)
   - [D2 — `SkRuntimeEffect` (compatibility shim)](#d2--skruntimeeffect-compatibility-shim--iso-fidelity-exception)
   - [D3 — Image codecs (`SkCodec` + `encodeToData`)](#d3--image-codecs-skcodec--encodetodata)
   - [D4 — DM sink architecture](#d4--dm-sink-architecture)
4. [Chantiers fidélité Skia core](#chantiers-fidélité-skia-core)
   - [I1 — `SkTextBlob`](#i1--sktextblob)
   - [I2 — Variable fonts + glyph mask cache](#i2--variable-fonts--glyph-mask-cache)
   - [I3 — `SkRegion` / `SkAAClip`](#i3--skregion--skaaclip)
   - [I4 — `SkShaper`](#i4--skshaper)
   - [I5 — `drawPoints` / `drawAtlas` / `drawVertices` / `drawPatch`](#i5--drawpoints--drawatlas--drawvertices--drawpatch)
5. [Chantiers compléments core](#chantiers-compléments-core)
   - [C1 — Image filters extras](#c1--image-filters-extras)
   - [C2 — Path effects extras](#c2--path-effects-extras)
   - [C3 — `SkEmbossMaskFilter`](#c3--skembossmaskfilter)
   - [C4 — Canvas opérations manquantes](#c4--canvas-opérations-manquantes)
   - [C5 — Color management completion](#c5--color-management-completion)
6. [Backends alternatifs raster](#backends-alternatifs-raster)
   - [B1 — `SkPDF`](#b1--skpdf)
   - [B2 — `SkSVGCanvas`](#b2--sksvgcanvas)
7. [Infrastructure & qualité](#infrastructure--qualité)
   - [Q1 — `SkAutoCanvasRestore` Kotlin idiom](#q1--skautocanvasrestore-kotlin-idiom)
   - [Q2 — Canvas wrappers (`PaintFilter`, `NoDraw`, `Overdraw`)](#q2--canvas-wrappers-paintfilter-nodraw-overdraw)
   - [Q3 — `SkBBHFactory` + Picture cull](#q3--skbbhfactory--picture-cull)
   - [Q4 — `SkDeferredDisplayList`](#q4--skdeferreddisplaylist)
   - [Q5 — Linear sRGB working space (Phase 7e' réinvestigué)](#q5--linear-srgb-working-space-phase-7e-réinvestigué)
8. [Séquençage recommandé](#séquençage-recommandé)
9. [Pratiques iso-fidélité](#pratiques-iso-fidélité)

---

## Principes iso-fidélité

Pour chaque chantier on vise les 4 niveaux de parité dans cet ordre :

1. **API surface** — signatures publiques identiques à upstream
   (`SkXxx::Make(...)`, `SkXxx::method(...)`), ré-exposées en Kotlin
   idiomatique.
   - Verdict : `gh diff include/core/SkXxx.h` doit montrer une
     correspondance 1:1 entre méthodes publiques.

2. **Sémantique observable** — comportement identique pour les inputs
   testés. Vérifié par :
   - Unit tests qui replient les fixtures Skia (gtest fixture data).
   - GM ports : score de similarité ≥ 95% sur la GM canonical.

3. **Algorithme** — le code Kotlin suit la même structure que le C++
   (mêmes branches, même nommage interne pour faciliter la
   maintenance).
   - Verdict : commentaire `// Mirrors Skia's SkXxx.cpp::method` à
     chaque fonction non triviale, avec lien vers la ligne précise
     d'upstream.

4. **Performance** — non-objective ; on n'optimise pas avant d'avoir
   l'iso-correctness. Cibler ~1.5x plus lent que Skia natif est OK.

**Out of scope** :
- GPU rendering (Ganesh / Graphite)
- GM porting (le plan en cours dans `MIGRATION_PLAN.md` racine s'en
  charge incrément par incrément)
- Multi-threading
- Sérialisation binaire de `SkPicture` (différée jusqu'à un usage clair)

### Iso-fidelity exceptions

La règle iso-fidélité s'applique à l'**API publique** et au
**comportement observable**. Pour les composants où l'upstream Skia
s'appuie sur **SkSL** (Skia Shading Language — interprété via SkVM
côté raster, compilé vers GLSL/MSL/SPIR-V côté GPU), on conserve la
surface API mais on substitue l'implémentation par un **registry
de fonctions Kotlin pré-compilées**.

**Justification projet** : la décision GPU étant de partir
directement en WGSL (pas de SkSL → WGSL transpiler), l'investissement
dans un parser + interpréteur SkSL côté raster (~5 000 LOC) serait
orphelin — il ne sert que la partie CPU sans aucun levier GPU. On
préfère donc :

- Conserver `SkRuntimeEffect.MakeForShader(sksl: String)` côté API
  (pour que tout code client compile et que le porting GM reste
  syntaxiquement iso).
- Hacher la source SkSL → lookup dans un registry → dispatch vers
  une lambda Kotlin qui implémente exactement la même math.
- Pour toute source SkSL inconnue du registry : retourner
  `Result.failure` proprement (pas de crash) ; documenter que
  l'effet doit être enregistré explicitement.

**Liste des composants concernés** :
- `SkRuntimeEffect` (chantier D2) — détail dans la section dédiée.

**Tous les autres composants restent strictement iso** (algorithme +
précision flottante).

---

## Architecture cible

```
┌─ SkCanvas ─────────────────┐    ┌─ SkSurface ───────┐
│ - drawXxx ops              │    │ - MakeRaster(info)│
│ - save / saveLayer         │────│ - canvas (cached) │
│ - clipXxx                  │    │ - makeImageSnap() │
│ - matrix transforms        │    └───────────────────┘
└──────────────┬─────────────┘
               │ delegates to
               ▼
┌─ SkBitmapDevice ───────────────────────────────────┐
│ - drawRect / drawPath / drawImageRect              │
│ - drawPathWithMaskFilter (Phase 7c)                │
│ - inDeviceColorSpace + applyColorFilter (Phase 7e) │
│ - blend / blendF16Premul / blendF16PremulMode      │
│ - compositeFrom (saveLayer restore)                │
└──────────────┬─────────────────────────────────────┘
               │
   ┌───────────┴────────────────┐
   │                            │
   ▼                            ▼
┌─ SkPath ─────────┐    ┌─ SkBitmap ────────────────┐
│ - immutable      │    │ - 8888 / F16Norm          │
│ - verb stream    │    │ - eraseColor (xform-aware)│
│ - SkPathBuilder  │    │ - getPixel / setPixel     │
└──────────────────┘    │ - getPixelF16             │
                       └────────────────────────────┘

Effects pipeline (Group A — completed) :
   path → pathEffect → stroker → maskFilter → colorFilter → blendMode
   image → imageFilter → colorFilter → blendMode

What this plan adds :
- Path operations layer (D1)
- Runtime effects shim (D2 — SkSL surface, Kotlin registry impl)
- Image I/O layer (D3)
- DM sink layer (D4)
- Text layer extensions (I1, I2, I4)
- Region layer (I3)
- Extra canvas ops (I5, C4)
- Filter / effect completions (C1, C2, C3)
- ARGB_4444 + linear sRGB (C5, Q5)
- Alternative backends (B1, B2)
```

---

## Chantiers critiques DM

### D1 — `SkPathOps` 🔄 en cours (D1.0 + D1.1 ✅ ; D1.2 🔄 ; D1.3 📋)

**Skia upstream files** :
- `include/pathops/SkPathOps.h` (public API : 4 functions)
- `src/pathops/` (~80 files, ~12000 LOC C++)

**Kotlin target** :
- `kanvas-skia/src/main/kotlin/org/skia/pathops/SkPathOps.kt` (entry)
- `kanvas-skia/src/main/kotlin/org/skia/pathops/internal/*.kt` (subdivision, segment intersection, contour assembly)

**API surface** (verbatim from `SkPathOps.h`) :
```kotlin
public object SkPathOps {
    public enum class Op { kDifference, kIntersect, kUnion, kXOR, kReverseDifference }

    /** Returns a path that is `Op(one, two)` ; null if the operation fails. */
    public fun Op(one: SkPath, two: SkPath, op: Op): SkPath?

    /** Returns a path equivalent to [path] with no self-intersections. */
    public fun Simplify(path: SkPath): SkPath?

    /** Same as Simplify but always uses `kWinding` fill type. */
    public fun AsWinding(path: SkPath): SkPath?

    /** Returns the tight (curve-aware) bounding box of [path]. */
    public fun TightBounds(path: SkPath): SkRect?
}
```

**Phase decomposition** :

- **D1.1** — Foundation : segment types, intersection primitives. ✅ **shipped**
  - Sous-slices livrés : (a) double-precision primitives `SkDPoint` /
    `SkDLine` / `SkDRect` ; (b) curves `SkDQuad` / `SkDCubic` /
    `SkDConic` + `SkDRect.setBounds` curve-tight ; (c)
    `SkLineParameters` + `SkIntersections` (line ops) + `isLinear` /
    pinned `subDivide` ; (d.1) `SkDQuad ↔ SkDLine` intersection ;
    (d.2) `SkDCubic ↔ SkDLine` + `binarySearch` / `searchRoots` ;
    (d.3) `SkDConic ↔ SkDLine` ; (e.1) cross-curve `hullIntersects` +
    `SkDCubic.convexHull` ; (e.2.a) `SkTCurve` abstraction +
    `SkTQuad` / `SkTConic` / `SkTCubic` wrappers ; (e.2.b)
    `SkTCoincident` + `SkTSpan` (per-span TSect state) ; (e.2.c.1)
    `SkTSect` skeleton + linked-list lifecycle ; (e.2.c.2)
    coincidence machinery (16 methods) ; (e.2.c.3) intersect
    machinery (`intersects` / `linesIntersect` / `trim` / `EndsEqual`
    / `isParallel`) ; (e.2.c.4) `SkTSect.BinarySearch` +
    `SkClosestSect` / `SkClosestRecord` ; (e.3)
    `SkIntersections.intersect` curve-curve wrappers — **D1.1 close.**
  - **LOC** estimés : ~3000.

- **D1.2** — Op contour assembly. 🔄 **en cours**
  - Sous-slices livrés : (a) `SkOpPtT` + `SkOpSpanBase` + `SkOpSpan`
    data model + 4 forward-decl skeletons ; (b) `SkOpAngle` data
    model + linked-list ops + simple accessors ; (b.2.0) `SkDCurve`
    / `SkDCurveSweep` + `SkOpSegment.subDivide` ; (c) `SkOpSegment`
    data model (structural / span-list / static helpers) ; (e)
    `SkOpContour` + `SkOpContourHead` + `SkOpContourBuilder` ; (f)
    `SkOpEdgeBuilder` (reads `SkPath` verbs into `SkOpContour`) ;
    (i) `SkPathWriter` (per-contour writer + simple assembly) ;
    (i.2) `SkPathWriter.assemble` (partials stitching).
  - À faire : `SkPathOpsTSect.kt` self-intersections (D1.1.e core
    déjà disponible), winding-number propagation across spans
    (`SkPathOpsCommon.kt`).
  - **LOC** estimés restants : ~2000.

- **D1.3** — Top-level `Op` / `Simplify` / `AsWinding` /
  `TightBounds` entry points. 📋 **pending** (bloqué sur D1.2 close).
  - `SkOpAssembler.kt` : runs the algorithm and produces `SkPath`.
  - **LOC** : ~2000.
  - **Tests** : 1000+ Skia path-pair fixtures from
    `tests/PathOpsOpTest.cpp`. GM ports : `pathops*` cluster (~15 GMs).

**Total LOC** : ~9000-12000.
**Estimated time** : 2-3 weeks per slice (D1.1 / D1.2 / D1.3) for a
single engineer. Largest chantier in this plan.

**Validation** : ensemble fixtures replays + ports of
`gm/pathopsfuzz.cpp` + `gm/pathopsskpclip.cpp` + `gm/complexclip2.cpp`.

**Risk** : Bézier intersection numerical robustness. Skia uses
extensively-tuned epsilon thresholds developed over years. Any
deviation causes spurious zero-length segments or missed
intersections that crash downstream.

**Alternative** : JNI binding to Skia native (`libskia.a`). Casse
l'autonomie pure-Kotlin, mais ~10x moins de LOC.

---

### D2 — `SkRuntimeEffect` (compatibility shim — *iso-fidelity exception*)

> ⚠️ **Iso-fidelity exception** : ce chantier substitue le moteur SkSL
> upstream par un registry Kotlin. Voir
> [§ Iso-fidelity exceptions](#iso-fidelity-exceptions) pour la
> justification (décision projet : pas de SkSL côté GPU → pas de
> levier pour parser/interpréter SkSL côté raster).

**Skia upstream files** (référence API uniquement, pas portées) :
- `include/effects/SkRuntimeEffect.h` (API publique — surface conservée
  iso).
- `src/sksl/` (~100 fichiers, ~30 000 LOC : parser + IR + interpreter
  SkVM) — **non portés** ; remplacés par le registry.

**Kotlin target** :
- `kanvas-skia/src/main/kotlin/org/skia/effects/runtime/SkRuntimeEffect.kt`
  (façade publique iso-API).
- `kanvas-skia/src/main/kotlin/org/skia/effects/runtime/SkRuntimeEffectRegistry.kt`
  (registry interne : `SkSL hash → KotlinImpl`).
- `kanvas-skia/src/main/kotlin/org/skia/effects/runtime/SkRuntimeShader.kt`
  / `SkRuntimeColorFilter.kt` / `SkRuntimeBlender.kt` (bindings vers
  `SkShader` / `SkColorFilter` / `SkBlender`).
- `kanvas-skia/src/main/kotlin/org/skia/effects/runtime/effects/*.kt`
  (un fichier par effet enregistré, hand-porté depuis le SkSL upstream).

**API surface** (strictement iso, *aucune* divergence visible côté
client) :
```kotlin
public class SkRuntimeEffect private constructor(
    private val source: String,
    private val sourceHash: Long,
    private val signature: SkRuntimeSignature,
    private val impl: SkRuntimeImpl,
) {
    public class Result(
        public val effect: SkRuntimeEffect?,
        public val errorText: String,
    )

    public companion object {
        /** Compile SkSL source for shader use (subject to registry lookup). */
        public fun MakeForShader(sksl: String): Result

        /** Compile for color filter use. */
        public fun MakeForColorFilter(sksl: String): Result

        /** Compile for blender use (2 input colors). */
        public fun MakeForBlender(sksl: String): Result
    }

    /** Build a shader / color filter / blender from this effect. */
    public fun makeShader(uniforms: SkData?, children: Array<SkShader?> = emptyArray()): SkShader?
    public fun makeColorFilter(uniforms: SkData?, children: Array<SkColorFilter?> = emptyArray()): SkColorFilter?
    public fun makeBlender(uniforms: SkData?, children: Array<SkBlender?> = emptyArray()): SkBlender?

    /** Reflection on the registered effect. */
    public fun uniforms(): List<Uniform>
    public fun children(): List<Child>
}
```

**Internal contract** :
```kotlin
internal interface SkRuntimeImpl {
    val uniforms: List<SkRuntimeEffect.Uniform>
    val children: List<SkRuntimeEffect.Child>

    /** Evaluate the effect at one (x, y) point. `coords` is null for color filter / blender. */
    fun shade(
        coords: SkPoint?,
        srcColor: SkColor4f?,    // for color filter / blender
        dstColor: SkColor4f?,    // for blender
        uniforms: ByteBuffer,
        childResolvers: Array<(SkPoint) -> SkColor4f>,
    ): SkColor4f
}

internal object SkRuntimeEffectRegistry {
    private val effects: MutableMap<Long, () -> SkRuntimeImpl> = mutableMapOf()

    /** Register an effect by its canonical SkSL source. Hash is FNV-1a 64-bit on the *normalized* source. */
    fun register(canonicalSource: String, factory: () -> SkRuntimeImpl)

    fun lookup(sksl: String): SkRuntimeImpl?
}
```

**Source normalization** (avant hashing) :
- Strip line comments `// …` et bloc `/* … */`.
- Collapse runs of whitespace to one space.
- Strip leading/trailing whitespace.
- → garantit que des variantes triviales (espaces, indentation) du
  même SkSL hashent identiquement.

**Phase decomposition** :

- **D2.1** — Façade + registry plumbing.
  - `SkRuntimeEffect.kt` (façade, parse triviale de la signature
    `uniform` / `in` / `vec4 main(...)` pour extraire les noms
    d'uniforms et children — pas un vrai parser SkSL, juste un
    regex ciblé sur les déclarations top-level).
  - `SkRuntimeEffectRegistry.kt` (lookup + register + hash FNV-1a).
  - `SkRuntimeShader/ColorFilter/Blender.kt` (bindings vers les
    interfaces `SkShader` / `SkColorFilter` / `SkBlender`).
  - `SkData.kt` si pas déjà présent (wrapper `ByteBuffer` pour
    uniforms).
  - **LOC** : ~700.
  - **Tests** : registry hit/miss, signature parse correcte,
    `Result.failure` pour SkSL inconnu.

- **D2.2** — Hand-port des effets utilisés par les GMs upstream.
  - Pour chaque GM qui appelle `SkRuntimeEffect::MakeForShader(...)`
    (cibles initiales : `runtimeshader.cpp`, `runtimecolorfilter.cpp`,
    `lit_filter.cpp`, `runtimefunc.cpp`, `null_color_filter.cpp` —
    ~10 GMs en tout), extraire la string SkSL et hand-porter la math
    en Kotlin (un fichier par effet, ~30-80 LOC).
  - Chaque port est un commit séparé pour traçabilité.
  - **LOC** : ~30-80 par effet × ~10 = ~300-800.
  - **Tests** : pixel-iso vs PNG upstream pour chaque GM porté.

- **D2.3** — `SkBlender` plumbing dans le pipeline paint.
  - Nouvelle interface `SkBlender` (jusqu'ici toute la blending
    passe par `SkBlendMode`).
  - Wire dans `SkBitmapDevice` : si `paint.blender != null`, use it
    au lieu du `SkBlendMode`.
  - **LOC** : ~250.
  - **Tests** : custom blender via runtime effect → applied correctly.

**Total LOC** : ~1 250-1 750 (vs ~7 000-10 000 pour SkSL VM complet).
**Estimated time** : 1-2 semaines.

**Validation** :
- Tests unitaires : registry, hash stability, signature parse.
- Pixel-iso GMs : tous les `runtime*` GMs portés voient leurs PNG
  upstream reproduits à >95% similarité.

**Comportement quand un SkSL inconnu est passé** :
- `MakeForShader(unknownSksl)` → `Result(effect = null, errorText =
  "SkSL not registered: <hash>. Add an entry to SkRuntimeEffectRegistry.")`
- DM pipeline : un GM qui essaie un SkSL non-enregistré loggue le
  hash et le skip dans son rapport (on peut alors rétro-porter
  l'effet à la demande).

**Évolution future** :
- Si un besoin de runtime effect *dynamique* apparaît (SkSL non
  connu à compile-time), on pourra alors investir dans un vrai
  parser SkSL — sans casser l'API existante (le registry reste un
  fallback fast-path).
- Si le projet GPU change d'avis et adopte SkSL, le moteur deviendra
  partagé raster/GPU et le shim sera retiré (l'API publique reste).

---

### D3 — Image codecs (`SkCodec` + `encodeToData`)

**Skia upstream files** :
- `include/codec/SkCodec.h`, `SkPngDecoder.h`, `SkJpegDecoder.h`,
  `SkWebpDecoder.h`, etc.
- `include/encode/SkPngEncoder.h`, `SkJpegEncoder.h`, etc.
- `src/codec/`, `src/images/`

**Kotlin target** :
- `kanvas-skia/src/main/kotlin/org/skia/codec/SkCodec.kt` (decoder
  facade)
- `kanvas-skia/src/main/kotlin/org/skia/encode/SkEncoder.kt` (encoder
  facade)
- Per-format implementations in `org/skia/codec/png/`, `jpeg/`, etc.

**Strategy** : prefer **JVM `imageio` adapters** for formats it
supports (PNG, JPEG, GIF, BMP) ; pure-Kotlin minimal codec for
WEBP and HEIF only when imageio doesn't ship them. This keeps LOC
manageable while reaching multi-format parity.

**Phase decomposition** :

- **D3.1** ✅ — `SkCodec` decoder facade + PNG. Package
  `org.skia.codec` ([SkCodec.kt](kanvas-skia/src/main/kotlin/org/skia/codec/SkCodec.kt))
  ships the facade (`Result` enum, `MakeFromData` / `MakeFromStream`,
  `getInfo` / `getEncodedFormat` / `getICCProfile` / `getPixels` /
  `getImage`) plus the `SkEncodedImageFormat` enum.
  [SkPngCodec.kt](kanvas-skia/src/main/kotlin/org/skia/codec/png/SkPngCodec.kt)
  registers as the first `SkCodec.Decoder`, sniffs the PNG signature,
  walks the `iCCP` chunk for the embedded profile (the DM Rec.2020
  references rely on this), and dispatches to a 16-bpc → F16 path or
  an 8-bpc → 8888 path. `TestUtils.loadReferenceBitmap` /
  `loadReferenceColorSpace` now go through the codec, so the inline
  iCCP / `BufferedImage` plumbing is gone from the test harness.
  - **LOC** : ~454 main + ~220 test = 674 total (cf. plan estimate
    ~400 — the overage covers the `SkEncodedImageFormat` enum and a
    fuller test surface than the plan called out).
  - **Tests** :
    [SkPngCodecTest.kt](kanvas-skia/src/test/kotlin/org/skia/codec/png/SkPngCodecTest.kt)
    (10 cases : signature dispatch, 8-bit + 16-bpc decode parity,
    determinism on `bigrect.png`, geometry + colour-type validation).
    Full kanvas-skia suite **2126 / 2126 green** through the new path.

- **D3.2** ✅ — JPEG via `imageio`.
  [SkJpegCodec.kt](kanvas-skia/src/main/kotlin/org/skia/codec/jpeg/SkJpegCodec.kt)
  registers as the second `SkCodec.Decoder` (signature `FF D8 FF`),
  delegates the bitstream decode to ImageIO, and reconstructs the
  embedded ICC profile from `APP2 / ICC_PROFILE` chunks (multi-marker
  walker, sorted by chunk index, contiguous-coverage validation, raw
  ICC bytes are not deflated unlike PNG iCCP). JPEG is always 8-bit
  opaque so the codec only emits `kRGBA_8888` bitmaps tagged
  `kUnpremul`.
  - **LOC** : ~221 main + ~209 test = 430 total (cf. plan estimate
    ~150 — overage is the upstream-faithful APP2 walker for
    multi-chunk ICC reconstruction, which the plan had not scoped).
  - **Tests** :
    [SkJpegCodecTest.kt](kanvas-skia/src/test/kotlin/org/skia/codec/jpeg/SkJpegCodecTest.kt)
    (7 cases : signature dispatch incl. PNG-non-match, decode
    plumbing, opaque-alpha invariant, flat-colour round-trip within
    JPEG tolerance, APP2 multi-chunk reassembly with deliberate
    out-of-order emission, geometry validation). Full kanvas-skia
    suite **2133 / 2133 green**.

- **D3.3** ✅ — GIF + BMP + WBMP via `imageio`. Three sibling
  decoders under `org.skia.codec.gif` /
  [`bmp`](kanvas-skia/src/main/kotlin/org/skia/codec/bmp/SkBmpCodec.kt)
  /
  [`wbmp`](kanvas-skia/src/main/kotlin/org/skia/codec/wbmp/SkWbmpCodec.kt),
  each parallel in shape to [SkJpegCodec.kt](kanvas-skia/src/main/kotlin/org/skia/codec/jpeg/SkJpegCodec.kt)
  : sniff the format-specific signature, delegate the bitstream
  decode to ImageIO, and emit `kRGBA_8888 / kUnpremul / sRGB`. None
  of the three formats carry an ICC profile in practice so the
  codecs return `null` from `getICCProfile`.
  GIF animation is **deferred** as the plan calls out — only the
  first frame is decoded. WBMP signature is the loose upstream
  triple `(type==0, fixedHeader & 0x9F == 0, valid VLQ width/height)` ;
  it is registered last in `SkCodec.Decoders` so every other
  format with a stronger magic gets first refusal.
  - **LOC** : ~298 main + ~219 test = 517 total (cf. plan estimate
    ~250 — overage covers the parallel-but-distinct kdoc per format
    and the WBMP VLQ header walker).
  - **Tests** : `SkGifCodecTest` (4), `SkBmpCodecTest` (4 — incl.
    24-bit BMP byte-identical round-trip), `SkWbmpCodecTest` (4 —
    incl. a "loose magic" rejection case for `00 00`-prefixed
    non-WBMP bytes). 12/12 green ; full kanvas-skia suite
    **2189 / 2189 green**.

- **D3.4** — WEBP (no `imageio` support).
  - Option A : pure-Kotlin port of libwebp's lossless decoder
    (~3000 LOC, complex VP8L bitstream).
  - Option B : optional dependency on Google's `webp` Java jar via
    Maven coordinate (~5 LOC integration, external dep).
  - Recommend Option B for v1 ; mark `webp` extension as opt-in.

- **D3.5** ✅ — `SkPngEncoder` + `SkJpegEncoder` re-exposing
  ImageIO encoders through the upstream Skia API. Two `object`s
  under `org.skia.encode` mirroring upstream's
  `SkPngEncoder` / `SkJpegEncoder` namespaces : static
  `Encode(bitmap, options): ByteArray?` + `Encode(dst, bitmap,
  options): Boolean`, plus per-format `Options` data classes
  ([SkPngEncoder.kt](kanvas-skia/src/main/kotlin/org/skia/encode/SkPngEncoder.kt)
  / [SkJpegEncoder.kt](kanvas-skia/src/main/kotlin/org/skia/encode/SkJpegEncoder.kt)).
  Shared `EncoderSupport.bitmapToBufferedImage` helper handles the
  `kRGBA_8888` / `kRGBA_F16Norm` projection.
  **What's honoured today** : JPEG `quality` (wired through to
  `JPEGImageWriter.setCompressionQuality`). **What's advisory** :
  PNG filter / zlib / tEXt fields and JPEG downsample / alphaOption
  — plumbed for source-compat with upstream call sites but ignored
  by ImageIO. JPEG drops alpha automatically (the writer rejects
  ARGB inputs, so the bitmap is composited onto opaque RGB before
  encoding — equivalent to upstream's `kIgnore`).
  **What's deferred** : embedding the bitmap's
  [SkColorSpace] as an `iCCP` / APP2-ICC chunk on encode, so a
  non-sRGB `SkBitmap` loses its working-space tag through an
  encode→decode round-trip. Tracked as a follow-up when a workflow
  needs it.
  - **LOC** : ~226 main + ~217 test = 443 total (cf. plan estimate
    ~200 — overage covers the upstream-faithful `Options` /
    `FilterFlag` / `Downsample` / `AlphaOption` enums).
  - **Tests** : `SkPngEncoderTest` (6 — encode → decode round-trip
    is **byte-identical** for opaque pixels, stream/byte-array
    parity, options validation), `SkJpegEncoderTest` (5 — quality
    plumbing verified by `lower quality → fewer bytes`,
    quantisation tolerance round-trip, alpha drop). 11/11 green ;
    full kanvas-skia suite **2205 / 2205 green**.

- **D3.6** — `SkImage.encodeToData(format, quality)` convenience.
  - **LOC** : ~50.

**Total LOC** : ~1000-1500 (with WEBP via opt-in dep ; +3000 if
pure-Kotlin WEBP).

**Validation** : decode + encode roundtrip = pixel-identical for
PNG (lossless) ; for lossy formats (JPEG / WEBP) within ~1 ulp per
channel after re-decode.

---

### D4 — DM sink architecture

**Skia upstream files** :
- `dm/DM.cpp` (the runner)
- `dm/DMSrcSink.h`, `dm/DMSrcSink.cpp` (sink implementations)
- `dm/DMGpuSupport.h`

**Kotlin target** :
- `kanvas-skia/src/main/kotlin/org/skia/dm/Sink.kt` (interface)
- `kanvas-skia/src/main/kotlin/org/skia/dm/RasterSink8888.kt`
- `kanvas-skia/src/main/kotlin/org/skia/dm/RasterSinkF16.kt`
- `kanvas-skia/src/main/kotlin/org/skia/dm/PictureSink.kt`
- `kanvas-skia/src/main/kotlin/org/skia/dm/PdfSink.kt` (depends on B1)
- `kanvas-skia/src/main/kotlin/org/skia/dm/SvgSink.kt` (depends on B2)
- `kanvas-skia/src/main/kotlin/org/skia/dm/Runner.kt` (entry point)

**API surface** :
```kotlin
public interface Sink {
    public val tag: String  // e.g. "8888", "f16", "pic-8888", "pdf"
    public fun draw(src: GM): Result

    public sealed class Result {
        public data class Ok(val output: ByteArray, val mimeType: String) : Result()
        public data class Error(val message: String) : Result()
    }
}

public class Runner(private val sinks: List<Sink>, private val gms: List<GM>) {
    public fun run(): Report
}

public data class Report(
    val passed: List<RunRecord>,
    val failed: List<RunRecord>,
    val toJson: () -> String,
)
```

**Phase decomposition** :

- **D4.1** — `Sink` interface + `RasterSink8888` + `RasterSinkF16`.
  - **LOC** : ~250.
  - Drives existing `runGmTest` through the new interface.

- **D4.2** — `PictureSink` : record GM into `SkPicture`, replay into
  raster sink, compare. Depends on Q3 (BBH cull) for the canonical
  `gl-pic-*` sinks but works without.
  - **LOC** : ~150.
  - Validates Phase 6r record/replay end-to-end on every GM.

- **D4.3** — `Runner` + `Report` JSON output mirroring upstream
  `dm/dm.json` format.
  - **LOC** : ~300.

- **D4.4** — CLI flags (`--config`, `--match`, `--blacklist`)
  matching upstream's syntax.
  - **LOC** : ~200.

- **D4.5** — `PdfSink` (after B1) and `SvgSink` (after B2).
  - **LOC** : ~150 ensemble.

**Total LOC** : ~700-1100 (excluding B1 / B2).

**Validation** : run all GMs through all sinks ; for raster sinks,
output pixels should be ~identical to direct render (gives 100% iso
on Picture playback). For PDF/SVG sinks, compare with upstream-
generated reference PDFs/SVGs (lower fidelity expected — these are
vector formats).

---

## Chantiers fidélité Skia core

### I1 — `SkTextBlob` ✅ shipped

**Skia upstream files** :
- `include/core/SkTextBlob.h`, `SkTextBlobBuilder.h`
- `src/core/SkTextBlob.cpp`, `SkTextBlobBuilder.cpp`

**Kotlin target** :
- `kanvas-skia/src/main/kotlin/org/skia/foundation/SkTextBlob.kt`
- `kanvas-skia/src/main/kotlin/org/skia/foundation/SkTextBlobBuilder.kt`

**API surface** :
```kotlin
public class SkTextBlob internal constructor(
    internal val runs: List<Run>,
    internal val cullRect: SkRect,
) {
    public fun bounds(): SkRect = cullRect

    internal sealed class Run {
        data class HorizontalSpread(
            val font: SkFont, val glyphIds: IntArray,
            val xs: FloatArray, val constY: Float,
        ) : Run()
        data class FullPositions(
            val font: SkFont, val glyphIds: IntArray,
            val positions: FloatArray,  // x0, y0, x1, y1, ...
        ) : Run()
        // RotateScale, RSXform, … — defer
    }
}

public class SkTextBlobBuilder {
    public fun allocRun(font: SkFont, count: Int, x: Float, y: Float, bounds: SkRect? = null): Allocation
    public fun allocRunPosH(font: SkFont, count: Int, y: Float, bounds: SkRect? = null): AllocationPosH
    public fun allocRunPos(font: SkFont, count: Int, bounds: SkRect? = null): AllocationPos
    public fun make(): SkTextBlob?
}

public fun SkCanvas.drawTextBlob(blob: SkTextBlob, x: Float, y: Float, paint: SkPaint)
```

**Phase decomposition** :

- **I1.1** — `SkTextBlobBuilder` with `allocRun` (uniform x-spacing,
  constant y) + `allocRunPosH` (per-glyph x, constant y). ✅ shipped
  - **LOC** : ~250.
  - **Tests** : 6 unit tests (build + bounds + rebuild).

- **I1.2** — `allocRunPos` (full per-glyph positions). ✅ shipped
  - **LOC** : ~100.

- **I1.3** — `SkCanvas.drawTextBlob` integration : delegate to
  existing `drawString` per-glyph for v1, or directly to glyph mask
  cache when I2 ships. ✅ shipped
  - **LOC** : ~100.

- **I1.4** — `SkPicture` recording integration : add `DrawTextBlob`
  variant to `SkRecord` + `SkRecordingCanvas` + `SkPicture.playback`. ✅ shipped
  - **LOC** : ~80.
  - **Tests** : 1 integration test (record + playback identical to
    direct draw).

- **I1.5** — Port `gm/textblob*.cpp` (5-10 GMs). ✅ 4 ports shipped
  (`TextBlobGM`, `TextBlobColorTransGM`, `TextBlobShaderGM`,
  `TextBlobUseAfterGpuFreeGM` smoke).

**Total LOC** : ~600 + GM ports.

**Validation** : Picture playback iso-identical for any GM that
contains text. Currently text-bearing GMs lose their text content
in Picture playback because `drawString` isn't recordable as a
distinct verb (it routes through `drawPath`).

---

### I2 — Variable fonts + glyph mask cache ✅ shipped (light variant)

**Skia upstream files** :
- `src/sfnt/SkOTUtils.cpp` (variable fvar table parser)
- `src/core/SkGlyph.cpp`, `SkGlyphRunPainter.cpp`
- `src/core/SkStrike*.cpp` (the glyph cache)

**Kotlin target** :
- `kanvas-skia/src/main/kotlin/org/skia/foundation/awt/SkGlyphCache.kt`
  (LRU mask cache, AWT-backed)
- `kanvas-skia/src/main/kotlin/org/skia/foundation/SkFontVariation.kt`
  (variable font axes)

**API surface** :
```kotlin
public data class SkFontVariation(val axis: Tag, val value: Float)

public class SkFont(
    typeface: SkTypeface,
    size: Float,
    scaleX: Float,
    skewX: Float,
    public var variations: List<SkFontVariation> = emptyList(),
) { /* ... */ }

internal object SkGlyphCache {
    public fun rasterize(font: SkFont, glyphId: Int): GlyphMask
    public data class GlyphMask(
        val width: Int, val height: Int,
        val originX: Int, val originY: Int,
        val alpha: ByteArray,
    )
}
```

**Phase decomposition** :

- **I2.1** — Glyph mask cache (LRU bounded by N entries or M MB). ✅ shipped
  - Key : `(typefaceId, size, scaleX, skewX, glyphId, edging)`.
  - Value : 8-bit alpha mask + origin.
  - **LOC** : ~300.
  - **Perf gain** : ~10x on text-heavy GMs (currently every glyph
    re-rasterised per draw).

- **I2.2** — Variable font axes via AWT
  `Font.deriveFont(Map<TextAttribute, ?>)` + `OPTICAL_SIZE`,
  `WEIGHT`, `WIDTH`, `POSTURE` attributes. ✅ light shipped (data
  class `SkFontVariation` + `SkFont.variations` field) ; AWT
  wiring full encore à câbler — pas bloquant pour les GMs actuels.
  - **LOC** : ~200.
  - GMs : `varfont*` (~5 GMs).

- **I2.3** — Subpixel positioning fast path (instead of always
  rasterising at integer x,y). ✅ shipped (pour `drawTextBlob`).
  - **LOC** : ~150.
  - Improves AA quality on horizontal text runs.

**Total LOC** : ~700 + GM ports.

**Validation** : run text-heavy GMs and measure perf delta. For
variable fonts, port `gm/variable_width.cpp`.

---

### I3 — `SkRegion` / `SkAAClip` ✅ shipped

**Skia upstream files** :
- `include/core/SkRegion.h`
- `src/core/SkRegion*.cpp`, `src/core/SkAAClip.cpp`
- `src/core/SkRasterClip.cpp`

**Kotlin target** :
- `kanvas-skia/src/main/kotlin/org/skia/foundation/SkRegion.kt`
- `kanvas-skia/src/main/kotlin/org/skia/foundation/SkAAClip.kt`
- `kanvas-skia/src/main/kotlin/org/skia/core/SkRasterClip.kt`

**API surface** (`SkRegion`) :
```kotlin
public class SkRegion {
    public constructor()
    public constructor(rect: SkIRect)

    public fun setRect(r: SkIRect): Boolean
    public fun setEmpty(): Boolean
    public fun setPath(path: SkPath, clip: SkRegion): Boolean

    public enum class Op { kDifference, kIntersect, kUnion, kXOR, kReverseDifference, kReplace }

    public fun op(rgn: SkRegion, op: Op): Boolean
    public fun op(rect: SkIRect, op: Op): Boolean

    public fun contains(x: Int, y: Int): Boolean
    public fun contains(rect: SkIRect): Boolean
    public fun isEmpty(): Boolean
    public fun isRect(): Boolean
    public fun getBounds(): SkIRect

    public class Iterator(rgn: SkRegion) {
        public fun done(): Boolean
        public fun next()
        public fun rect(): SkIRect
    }
}
```

**Phase decomposition** :

- **I3.1** — `SkRegion` core : run-based 1D representation, set ops
  via scanline merging. ✅ shipped (a, b, c)
  - Sous-slices : (a) data model + queries + iterator, (b) set
    operations via scanline merging, (c) `setPath` via path
    scanline rasterisation.
  - **LOC** : ~1500.
  - **Tests** : ~50 set-op tests vs Skia fixtures.

- **I3.2** — `SkAAClip` : AA region with per-row alpha runs. ✅ shipped (a, b, c)
  - Sous-slices : (a) core (data model + region promotion), (b)
    `setPath` via 4×4 supersampled `SkRegion`, (c) set operations
    on alpha runs.
  - **LOC** : ~1000.
  - **Tests** : ~30 AA-clip tests.

- **I3.3** — `SkRasterClip` integration : replace current Phase 7q
  alpha-mask-bitmap with the run-based representation. Faster + less
  memory for typical clip shapes. ✅ shipped (a, b)
  - Sous-slices : (a) `SkAAClip.coverage(x, y)` query method (hot
    path du rasterizer), (b) `clipMask` Phase 7q remplacé par
    `SkAAClip` dans `SkRasterClip`.
  - **LOC** : ~500.

**Total LOC** : ~3000.

**Validation** : `gm/clipregion.cpp`, `gm/clip_*.cpp` (~15 GMs).
Phase 7q's clipPath should benefit from massive perf improvement
on convex clips.

---

### I4 — `SkShaper` (text shaping) ✅ shipped

**Skia upstream files** :
- `modules/skshaper/include/SkShaper.h`
- `modules/skshaper/src/SkShaper_*.cpp`

**Kotlin target** :
- `kanvas-skia/src/main/kotlin/org/skia/shaper/SkShaper.kt`

**Strategy** : Java `java.awt.font.TextLayout` + ICU bridges already
ship in JDK. Use them for shaping (handles bidi, ligatures, basic
scripts) ; expose Skia API on top.

**API surface** :
```kotlin
public abstract class SkShaper {
    public abstract fun shape(
        utf8: String, font: SkFont, leftToRight: Boolean,
        width: Float, runHandler: RunHandler,
    )

    public interface RunHandler {
        public fun beginLine()
        public fun runInfo(info: RunInfo)
        public fun commitRunInfo()
        public fun runBuffer(info: RunInfo): Buffer
        public fun commitRunBuffer(info: RunInfo)
        public fun commitLine()
    }

    public companion object {
        public fun MakePrimitive(): SkShaper
        public fun MakeJavaTextLayout(): SkShaper
    }
}
```

**Phase decomposition** :

- **I4.1** — `SkShaper.MakePrimitive` : naive char-by-char glyph
  mapping (no shaping). Already exists implicitly in
  `SkFont.makeTextPath` ; re-expose as `SkShaper`. ✅ shipped
  - **LOC** : ~150.

- **I4.2** — `SkShaper.MakeJavaTextLayout` : delegate to JDK's
  `TextLayout` for shaping. ✅ shipped
  - **LOC** : ~400.
  - Handles bidi (Arabic, Hebrew), basic ligatures, kerning.

- **I4.3** — Linebreak / wrapping support via ICU `BreakIterator`. ✅ shipped
  (via `java.text.BreakIterator` JDK).
  - **LOC** : ~200.

**Total LOC** : ~750.

**Out of scope v1** : HarfBuzz parity (advanced OT features, complex
Indic / Khmer / Thai shaping). Defer to JNI-HarfBuzz binding when
needed.

**Validation** : port any non-Latin GM (`gm/i18n_text.cpp` etc.).

---

### I5 — `drawPoints` / `drawAtlas` / `drawVertices` / `drawPatch` 🔄 en cours (I5.1, I5.2, I5.3 ✅ ; I5.4 📋)

**Skia upstream files** :
- `include/core/SkCanvas.h` (the public methods)
- `src/core/SkBitmapDevice.cpp` (raster impls)

**Kotlin target** :
- Add methods to `kanvas-skia/src/main/kotlin/org/skia/core/SkCanvas.kt`
  + `SkBitmapDevice.kt`.

**API surface** (verbatim from upstream) :
```kotlin
// SkCanvas
public open fun drawPoints(mode: PointMode, points: Array<SkPoint>, paint: SkPaint)
public open fun drawAtlas(
    image: SkImage,
    xform: Array<RSXform>, src: Array<SkRect>, colors: IntArray? = null,
    blendMode: SkBlendMode, sampling: SkSamplingOptions, cullRect: SkRect? = null,
    paint: SkPaint? = null,
)
public open fun drawVertices(vertices: SkVertices, blendMode: SkBlendMode, paint: SkPaint)
public open fun drawPatch(
    cubics: FloatArray,    // 12 SkPoints = 24 floats
    colors: IntArray?,     // 4 SkColors
    texCoords: Array<SkPoint>?,
    blendMode: SkBlendMode, paint: SkPaint,
)

public enum class PointMode { kPoints, kLines, kPolygon }
```

**Phase decomposition** :

- **I5.1** — `drawPoints` : trivial (delegates to drawCircle for
  kPoints, drawLine for kLines/kPolygon). ✅ shipped
  - **LOC** : ~80.

- **I5.2** — `drawAtlas` : sprite batching. Delegate to
  `drawImageRect` per sprite with RSXform → SkMatrix conversion. ✅ shipped
  - **LOC** : ~250.
  - GMs : `drawatlas*` (~3 GMs).

- **I5.3** — `drawVertices` : triangle mesh rendering with per-vertex
  color + texture coords + blend. ✅ shipped (a, b)
  - Sous-slices : (a) `SkVertices.MakeCopy(...)` + `SkCanvas.drawVertices`
    solid-color path ; (b) per-vertex colour barycentric interpolation
    (full triangle mesh shading). Texture coords (`drawVertices` UV +
    sample image) restent.
  - Algorithm : 1) build SkPath from triangles, 2) for each pixel
    inside, barycentric-interpolate color/UV, sample texture, blend.
  - **LOC** : ~400.
  - GMs : `vertices*` (~5 GMs).

- **I5.4** — `drawPatch` : Coons patch (cubic interp grid). The most
  complex. 📋 pending.
  - Algorithm : tessellate the 4 boundary cubics into a grid of
    triangles, then defer to `drawVertices`.
  - **LOC** : ~300.
  - GMs : `patch*`, `coonspatchmesh*` (~3 GMs).

**Total LOC** : ~1000 + GM ports.

**Validation** : ports of `gm/drawatlas.cpp`, `gm/vertices.cpp`,
`gm/patch.cpp`.

---

## Chantiers compléments core

### C1 — Image filters extras

**Skia upstream files** :
- `include/effects/SkImageFilters.h` (the 15+ factories)
- `src/effects/imagefilters/Sk*ImageFilter.cpp`

**Kotlin target** : extensions à `SkImageFilters` + nouvelles classes
internes dans `kanvas-skia/src/main/kotlin/org/skia/foundation/`.

**Per-filter scope** :

| Filter | Algorithm | LOC |
|---|---|---|
| `Erode` / `Dilate` | morphology : per-pixel min/max over circular kernel | ~150 chacun |
| `DisplacementMap` | sample from input via offset = displacement.{R,G} - 0.5 | ~200 |
| `Lighting` (point/distant/spot) | normal map from height map + Phong shading | ~600 ensemble |
| `Magnifier` | radial scale around centre | ~150 |
| `Tile` | sample input through tiled bounds | ~150 |
| `Arithmetic` | k1·src + k2·dst + k3·src·dst + k4 per channel | ~150 |
| `Merge` | concat N inputs via per-pixel max-alpha | ~150 |
| `Image` | use static SkImage as input source | ~80 |
| `Picture` | use SkPicture replayed into bitmap as input | ~100 |
| `Shader` | use SkShader as input source | ~80 |

**Phase decomposition** :

- **C1.1** — Trivial filters (Image, Picture, Shader, Tile, Arithmetic,
  Merge, Magnifier).
  - **LOC** : ~700.

- **C1.2** — Morphology (Erode, Dilate).
  - **LOC** : ~300.
  - GMs : `morphology*` (~2 GMs).

- **C1.3** — DisplacementMap.
  - **LOC** : ~200.
  - GMs : `displacement*` (~3 GMs).

- **C1.4** — Lighting (point/distant/spot).
  - **LOC** : ~600.
  - GMs : `lighting*` (~5 GMs).

**Total LOC** : ~1800.

**Validation** : port `gm/imagefilters*` cluster (~15 GMs).

---

### C2 — Path effects extras

**Manquants** :
- `Sk1DPathEffect.kMorph` style : path bend along curve (math
  complexe — Bézier reparam by arc length).
- `StrokeAndFill` recipe : composé `Compose(stroke, fill)`.

**LOC** : ~500.

**GMs débloqués** : peu (kMorph mostly used by tools, not by GMs).

---

### C3 — `SkEmbossMaskFilter`

**Skia upstream files** :
- `include/effects/SkEmbossMaskFilter.h`
- `src/effects/SkEmbossMaskFilter.cpp`

**Algorithm** : compute a 3D normal map from the alpha mask gradient,
apply Lambertian shading from a directional light, output a tinted
+ shadowed version.

**Kotlin target** :
- `kanvas-skia/src/main/kotlin/org/skia/foundation/SkEmbossMaskFilter.kt`

**API** :
```kotlin
public class SkEmbossMaskFilter private constructor(/* ... */) : SkMaskFilter() {
    public companion object {
        public fun Make(blurSigma: Float, light: Light): SkMaskFilter?
    }
    public data class Light(
        val direction: SkPoint3,
        val ambient: Float,
        val specular: Float,
    )
}
```

**LOC** : ~400.

**GMs** : `emboss*` (~5 GMs), `bevel*`.

---

### C4 — Canvas opérations manquantes

**Manquants** :
- `drawAnnotation(rect, key, value)` : PDF annotations (no-op for
  raster sinks). LOC : ~50.
- `drawDrawable(drawable, matrix?)` : custom drawable extension slot.
  LOC : ~150.
- `drawShadow(path, recParams)` : Skia 3D shadow primitive (spot +
  ambient shadow projected from path elevation). Complex. LOC : ~600.

**Total** : ~800.

**Priorité** : `drawShadow` only if porting Material Design GMs.

---

### C5 — Color management completion ✅ partially shipped (ARGB_4444 done ; P3/HDR tests reste)

**Manquants** :
- **`ARGB_4444` colortype** : 4 bits per channel, packed `0xARGB`.
  Used by upstream's `XfermodesGM` for the bg checkerboard. ✅ shipped
  - **LOC** : ~200 (storage path + `getPixel`/`setPixel` + erase).
  - **Impact** : XfermodesGM 78% → ~95% (mesuré : XfermodesGM
    repassé via ARGB_4444 checkerboard, plus de divergence visible
    sur le bg).

- **Display P3 / Adobe RGB color spaces** : already supported via
  `SkColorSpace` infrastructure ; just need named singletons + tests.
  - **LOC** : ~100.

- **HDR PQ / HLG transfer functions** : pipeline supports them via
  `SkcmsTFType.PQ/HLG` ; need testing on actual HDR content.
  - **LOC** : 0 (already shipped) + ~150 tests.

**Total** : ~450.

---

## Backends alternatifs raster

### B1 — `SkPDF`

**Skia upstream files** :
- `include/docs/SkPDFDocument.h`
- `src/pdf/` (~50 files, ~15000 LOC)

**Kotlin target** :
- `kanvas-skia/src/main/kotlin/org/skia/pdf/SkPDFDocument.kt`
- `kanvas-skia/src/main/kotlin/org/skia/pdf/internal/*.kt` (PDF object
  model, content stream serializer, font subsetting, image embedding)

**Strategy** : pure-Kotlin PDF writer mirroring Skia's structure.
Or external lib (e.g. PDFBox via Maven) for v1 with a thin `SkCanvas`
adapter on top.

**Phase decomposition** : 5+ slices, very large chantier.

**Total LOC** : ~10000 (pure-Kotlin) or ~500 (PDFBox adapter).

**Recommandation** : **PDFBox adapter** for v1. Pure-Kotlin only if
PDF iso-fidelity becomes critical.

---

### B2 — `SkSVGCanvas`

**Skia upstream files** :
- `include/svg/SkSVGCanvas.h`
- `src/svg/SkSVGCanvas.cpp`

**Strategy** : SkCanvas implementation that serializes ops to SVG XML.
Simpler than PDF (text-based).

**LOC** : ~3000.

**Phase decomposition** :

- **B2.1** — Path → SVG `<path d="...">` serialization. ~500 LOC.
- **B2.2** — Solid fills + strokes → SVG `<g>` with style attrs. ~300.
- **B2.3** — Shaders (linear, radial) → SVG `<linearGradient>` /
  `<radialGradient>` defs. ~600.
- **B2.4** — Image embedding → `<image>` with base64 data URL. ~200.
- **B2.5** — Clipping → `<clipPath>` defs. ~400.
- **B2.6** — Text → `<text>` (defer SkTextBlob → SVG textPath
  extension). ~500.
- **B2.7** — Filters → SVG filter primitives (`<feGaussianBlur>`,
  `<feOffset>`, ...). ~500.

**Validation** : run all GMs through SvgSink, render the resulting
SVG via Batik or browser, compare with raster reference.

---

## Infrastructure & qualité

### Q1 — `SkAutoCanvasRestore` Kotlin idiom

**LOC** : ~30.

```kotlin
public inline fun <R> SkCanvas.withSave(block: SkCanvas.() -> R): R {
    val count = save()
    try { return block() } finally { restoreToCount(count) }
}

public inline fun <R> SkCanvas.withLayer(
    bounds: SkRect? = null, paint: SkPaint? = null,
    block: SkCanvas.() -> R,
): R {
    saveLayer(bounds, paint)
    try { return block() } finally { restore() }
}
```

Refactor existing GM ports to use these (~50 callsites).

---

### Q2 — Canvas wrappers (`PaintFilter`, `NoDraw`, `Overdraw`)

**Skia upstream files** :
- `include/utils/SkPaintFilterCanvas.h`
- `include/utils/SkNoDrawCanvas.h`
- `include/utils/SkOverdrawCanvas.h`

**Kotlin target** :
- `kanvas-skia/src/main/kotlin/org/skia/utils/SkPaintFilterCanvas.kt`
- `kanvas-skia/src/main/kotlin/org/skia/utils/SkNoDrawCanvas.kt`
- `kanvas-skia/src/main/kotlin/org/skia/utils/SkOverdrawCanvas.kt`

**API** : extend `SkCanvas` (open class) + override every draw method.

**LOC** : ~400.

**Use cases** :
- `PaintFilterCanvas` : paint modifier (e.g. force-disable AA for
  perf testing).
- `NoDrawCanvas` : layout pass (compute bounds without rasterizing).
- `OverdrawCanvas` : count how many times each pixel is touched
  (perf debugging).

---

### Q3 — `SkBBHFactory` + Picture cull

**Skia upstream files** :
- `include/core/SkBBHFactory.h`
- `src/core/SkRTree.cpp`

**Algorithm** : R-tree indexes recorded ops by their device-space
bounds. At playback time, query the R-tree with the current clip
bounds and skip ops whose bounds don't intersect.

**Kotlin target** :
- `kanvas-skia/src/main/kotlin/org/skia/core/SkRTree.kt`
- `kanvas-skia/src/main/kotlin/org/skia/core/SkBBHFactory.kt`

**LOC** : ~600.

**Phase decomposition** :

- **Q3.1** — `SkRTree` : N-ary R-tree with branch factor 2-8.
  - **LOC** : ~400.
- **Q3.2** — `SkPictureRecorder` integration : emit op bounds during
  recording. `SkPicture.playback` queries the R-tree.
  - **LOC** : ~200.

**Validation** : huge picture (1000+ ops) replayed under a tiny clip
should be much faster.

---

### Q4 — `SkDeferredDisplayList`

**Skia upstream files** :
- `include/core/SkDeferredDisplayList.h`
- `include/core/SkDeferredDisplayListRecorder.h`

**Algorithm** : record commands on one thread, replay on another. We
already have most of this via `SkPicture` ; the extra step is the
"thread-safe handoff" of recorded ops to a different SkCanvas.

**Kotlin target** :
- `kanvas-skia/src/main/kotlin/org/skia/core/SkDeferredDisplayList.kt`

**LOC** : ~400.

**Priorité** : low — multi-threading is out of scope for our
single-threaded raster rasterizer. Mostly useful for GPU.

---

### Q5 — Linear sRGB working space (Phase 7e' réinvestigué)

**Context** : Phase 7e' was attempted (decode → matrix → encode
wrapper around `applyColorFilter`) and **made ColorMatrixGM worse**
(69% → 49%). Indicates Skia upstream applies the matrix in
**encoded sRGB**, not linear.

**Action** :

1. **Diagnostic** : sample 5-10 pixels per cell of `ColorMatrixGM`,
   compute the expected output for both encoded-sRGB and linear-sRGB
   matrix application, compare with upstream reference. Determine
   which matches better per cell.
2. **If linear is correct for *some* cells** : add a per-filter
   `evaluateInLinear` flag (matches Skia's `gAlwaysClamp` /
   gamut-aware filter design).
3. **If encoded sRGB is correct everywhere** : the gap is elsewhere
   (image sampling precision, alpha-channel modulation order, ...).
   Ratchet bumps would come from those investigations.

**Estimated LOC** : ~80 if just the diagnostic ; ~300 if a per-filter
flag is added.

---

## Séquençage recommandé

DAG of dependencies :

```
                              ┌─ I1 SkTextBlob
                              │    └─ Used by Picture/Surface for text
                              │
                              ├─ I2 Variable fonts + glyph cache
                              │    └─ Built on I1 for glyph runs
                              │
                              ├─ I4 SkShaper
                              │    └─ Built on I1 + I2 for shaping
                              │
                              ├─ I5 drawPoints/Atlas/Vertices/Patch
                              │    └─ Self-contained
                              │
   D4 Sink architecture ──────┼─ C1 Image filters extras
   (depends on Picture +      │    └─ Built on existing SkImageFilter
    Surface from Phase 6r)    │
                              ├─ C5 ARGB_4444
                              │    └─ Self-contained
                              │
   ─── critical for DM ──     ├─ I3 SkRegion / SkAAClip
                              │    └─ Replaces Phase 7q clipMask
                              │
                              ├─ D1 SkPathOps (huge)
                              │    └─ Self-contained
                              │
                              ├─ D2 SkRuntimeEffect (shim, ~1500 LOC)
                              │    └─ Self-contained ; iso-fidelity exception
                              │
                              ├─ D3 image codecs
                              │    └─ Self-contained
                              │
                              ├─ Q1 SkAutoCanvasRestore
                              │    └─ Self-contained
                              │
                              ├─ Q2 Canvas wrappers
                              │    └─ Self-contained
                              │
                              ├─ Q3 SkBBHFactory
                              │    └─ Built on Picture
                              │
                              ├─ Q5 Linear sRGB diagnostic
                              │    └─ Self-contained
                              │
                              ├─ B1 SkPDF (PDFBox adapter)
                              │    └─ Built on I1 for text
                              │
                              ├─ B2 SkSVGCanvas
                              │    └─ Self-contained (filters → SVG <filter>)
                              │
                              ├─ Q4 DeferredDisplayList
                              │    └─ Low priority (single-threaded)
                              │
                              └─ C2/C3/C4 misc completions
```

**Recommended ordering for "DM-ready iso" goal** :

1. **D4** Sink architecture (~700 LOC)
2. **Q1** AutoCanvasRestore (~30 LOC, immediate quality-of-life)
3. **C5** ARGB_4444 (~200 LOC, +1 GM bump)
4. **I1** SkTextBlob (~600 LOC, unblocks all text in Picture)
5. **D3** image codecs (~1000 LOC, JPEG quick win via imageio)
6. **I3** SkRegion (~3000 LOC)
7. **C1** Image filters extras (~1800 LOC, decompose into 4 sub-PRs)
8. **I5** drawPoints/Atlas/Vertices/Patch (~1000 LOC)
9. **Q3** SkBBHFactory (~600 LOC, perf for Picture)
10. **D1** SkPathOps (~9000 LOC, decompose into 3 sub-PRs)
11. **D2** SkRuntimeEffect shim (~1500 LOC, *iso-fidelity exception*)
12. **B1** SkPDF (PDFBox adapter, ~500 LOC)
13. **B2** SkSVGCanvas (~3000 LOC)
14. **C3** SkEmbossMaskFilter (~400 LOC)
15. **I2** Variable fonts + glyph cache (~700 LOC)
16. **I4** SkShaper (~750 LOC)
17. **Q2** Canvas wrappers (~400 LOC)
18. **Q5** Linear sRGB diagnostic (~80 LOC investigation)
19. **C2/C4** Misc completions (~1300 LOC ensemble)
20. **Q4** DeferredDisplayList (~400 LOC, low priority)

**Total estimated LOC** : ~26 000 of new Kotlin code (revised after
D2 → shim), decomposed into ~45 PRs of 100-1500 LOC each.

**Estimated time** : 5-10 months for a single engineer working
half-time, or 1.5-3 months full-time.

---

## Pratiques iso-fidélité

### A. Naming conventions

- Public types : exact Skia name (`SkPath`, `SkImage`, `SkFont`).
- Public methods : same name, lowerCamelCase'd
  (`SkBitmap::eraseColor` → `SkBitmap.eraseColor`).
- Public enums : exact name + variant (`SkBlendMode.kSrcOver`).
- Companion factories : `Make*` style (`SkColorFilters.Matrix(arr)`,
  `SkPath.Circle(...)`).
- Internal helpers : free to name idiomatically.

### B. Comment cross-references

Every non-trivial function should have a `// Mirrors Skia's <filename>
::<method>(...)` comment with a link to the line. Example :

```kotlin
/**
 * Mirrors Skia's
 * [`SkPath::isOval`](https://github.com/google/skia/blob/main/src/core/SkPath.cpp#L513)
 * — returns true iff the path is exactly one closed oval contour
 * with the canonical `kMove + kConic*4 + kClose` verb stream.
 */
```

This makes upstream-tracking trivial when Skia evolves.

### C. Test fixture replays

For each chantier, mine `tests/Sk*Test.cpp` for input/output fixture
data and replay them in Kotlin unit tests. The fidelity bar :

- **API surface tests** : every public method has at least 1 unit
  test covering nominal + 1-2 edge cases.
- **Algorithm tests** : Skia fixtures replayed verbatim, asserting
  the same output.
- **GM ports** : if the chantier unblocks a GM cluster, port at
  least 1 GM from that cluster as integration validation.

### D. Score floor ratchet

For each new GM port, set the floor at `~80% of the achieved score`
to leave room for downstream improvements while catching regressions.

### E. Commit hygiene

- One PR per slice (target ~500 LOC of code + tests).
- Commit message follows Skia changelog conventions :
  `Phase 7d.2 : <one-line summary> + score impact + 0 regression note`.

### F. When upstream Skia changes

If we pull a newer Skia version (currently pinned to 4.x), each
chantier's iso-fidelity may drift. Mitigation :

- Pin to a specific Skia version per chantier (note in the docstring).
- Run a quarterly diff against upstream for cross-referenced files,
  capture any algorithmic changes in a `MIGRATION_PLAN_<area>_DRIFT.md`
  follow-up.

---

## Appendix — file layout

After all chantiers complete, the kanvas-skia structure :

```
kanvas-skia/src/main/kotlin/org/skia/
├── foundation/             # SkPath, SkPaint, SkBitmap, SkImage, SkFont, ...
├── core/                   # SkCanvas, SkSurface, SkBitmapDevice, SkPicture, ...
├── effects/                # SkColorFilters, SkImageFilters, ...  (currently in foundation/)
├── pathops/                # NEW — D1 chantier
├── effects/runtime/        # NEW — D2 chantier (shim, no SkSL VM)
├── codec/                  # NEW — D3 chantier
├── encode/                 # NEW — D3 chantier
├── dm/                     # NEW — D4 chantier
├── shaper/                 # NEW — I4 chantier
├── pdf/                    # NEW — B1 chantier
├── svg/                    # NEW — B2 chantier
├── utils/                  # SkAutoCanvasRestore, PaintFilterCanvas, ... (Q1, Q2)
├── tools/                  # ToolUtils, SkRandom, ...
├── tests/                  # GM ports
├── math/                   # SkRect, SkMatrix, SkPoint, ...
├── skcms/                  # ICC profile machinery
└── awt/                    # JVM-specific glyph cache, font fallback
```

Some current locations (`SkColorFilters` in `foundation/`) might
benefit from migration to a more Skia-aligned layout (`effects/`),
but that's a refactor PR per chantier rather than blocking work.
