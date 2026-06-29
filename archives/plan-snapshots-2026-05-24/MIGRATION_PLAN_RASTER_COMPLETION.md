# Migration plan — Raster pipeline completion (post-Group A)

> **Status** : 🔄 **en cours** — plan vivant. **17 chantiers livrés**
> (B2 / C1 / C2 / C3 / C4 / C5 / D3 / D4 / I1 / I2 / I3 / I4 / I5 /
> Q1 / Q2 / Q3 / Q4 / Q5 ✅ ; B1 ❌ descoped). 🔄 D1.0 + D1.1 ✅,
> D1.2 active (g.* + h.0–h.6.4 + h.8 + h.9.0–9.2 + h.10 debug-pass).
> **D1.4 shipped** (pathops regression harvest, 335 fixtures = 74 %
> d'upstream, **99.7 % survival** + **95.8 % pixel parity** vs an
> independent rasteriser-set-op oracle). 📋 D2 + D1.4 polish restent.
> Voir status par chantier ci-dessous.
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
> | **D1.2** Op contour assembly | 🔄 en cours | g.* coincidence + h.0–h.4 (Op fast paths + HandleCoincidence orchestrator) + h.5.* (active edges + ray-tracing winding suite + Op end-to-end wiring) + h.6.0–h.6.4 (Simplify end-to-end + AsWinding fast paths) + h.8 (fillMaskFor inverse fill types) + h.9.0–h.9.2 (`SkOpBuilder.resolve` chained-Op fallback, pathops GM harvest, `SkParsePath::FromSVGString`) + h.10 (stale empty-result guard removed — 11 PathOpsRegression fixtures recovered) livrés ; reste finitions |
> | **D1.3** Top-level entry points | 📋 pending | Bloqué sur D1.2 close |
> | **D1.4** PathOps regression harvest | ✅ shipped (extended + debug pass + pixel parity) | Harness data-driven : extracteur Python `extract_pathops_fixtures.py` (~530 LOC) → JSON dump (335 / 451 fixtures = **74 %** d'upstream) → `PathOpsRegressionRunner.kt` JUnit `@ParameterizedTest` + `PathOpsPixelOracle.kt` (~190 LOC). Survival : **334 / 335 = 99.7 %** (floor 99 %). Pixel parity (rasteriser-set-op oracle, 64×64 binary masks, 2×2 block-diff tolerance) : **320 / 334 = 95.8 %** (floor 90 %). **D1 follow-ups** (cubicOp35d, 14 PIXEL_DIVERGE, extractor coverage, I3.4 `getBoundaryPath`, D1.2 finition, D1.3 closure) : voir [MIGRATION_PLAN_D1_FOLLOWUPS.md](MIGRATION_PLAN_D1_FOLLOWUPS.md). |
> | **D2** SkRuntimeEffect façade + per-effect Kotlin ports | 📋 mini-planned | Mini plan livré : **8 sous-slices**, ~3 700 main + ~2 200 test, ~13 GM clusters / ~80 DEF_GM débloquées. Aligné sur la stratégie [WebGPU](MIGRATION_PLAN_GPU_WEBGPU.md) (port hand-écrit par shader-type). Voir [MIGRATION_PLAN_D2_RUNTIME_EFFECT.md](MIGRATION_PLAN_D2_RUNTIME_EFFECT.md). |
> | **D3** Image codecs | ✅ shipped | D3.1 PNG / D3.2 JPEG / D3.3 GIF+BMP+WBMP / D3.4 WEBP (TwelveMonkeys plugin) / D3.5 PNG+JPEG encoders / D3.6 `SkImage.encodeToData` |
> | **D4** DM sink architecture | ✅ shipped | D4.1 Sink + Raster8888/F16 / D4.2 PictureSink / D4.3 Runner + Report / D4.4 DmCli + DmMain / D4.5 SvgSink (PdfSink ❌ descoped per B1) |
> | **I1** SkTextBlob + drawTextBlob + Picture wiring | ✅ shipped (I1.1-1.5) | 4 GM ports |
> | **I2** Glyph cache + variable-fonts (light) + subpixel | ✅ shipped (I2.1-2.3) | Variable fonts AWT-wired déféré |
> | **I3** SkRegion + SkAAClip + SkRasterClip | ✅ shipped (I3.1-3.3) | clipMask Phase 7q remplacé par SkAAClip |
> | **I4** SkShaper (Primitive + JavaTextLayout + wrap) | ✅ shipped (I4.1-4.3) | HarfBuzz parity hors scope |
> | **I5** drawPoints / drawAtlas / drawVertices / drawPatch | ✅ shipped | I5.1 / I5.2 / I5.3.a-c / I5.4 livrés (commit `2de410e`) |
> | **C1** Image filters extras | ✅ shipped | 22 factories manquantes livrées (1 descope, `RuntimeShader` bloqué sur D2). 7 sous-slices, **1501 main + 1473 test** actuels (vs ~2750 + ~1440 estimés). ~30 GM ports débloqués. Voir [MIGRATION_PLAN_C1_IMAGE_FILTERS.md](MIGRATION_PLAN_C1_IMAGE_FILTERS.md). |
> | **C2** Path effects extras (kMorph, StrokeAndFill recipe) | ✅ shipped | `SkPath1DPathEffect.Style.kMorph` ported faithfully (per-vertex bend along input path's normal, kLine→quad upgrade) via a new `ContourMeasure` chord-polyline parametriser ; `kStrokeAndFill_Style` was already shipped in `SkBitmapDevice` (verified). 8 morph tests + existing StrokeAndFill coverage. |
> | **C3** SkEmbossMaskFilter | ✅ shipped | 3-plane dispatch via new `Sk3DMask` + `SkMaskFilter.Format` ; wired into `SkBitmapDevice.drawPathWithMaskFilter`. 14 tests, suite **2453 / 2453 green**. |
> | **C4** drawAnnotation / drawDrawable / drawShadow | ✅ partially shipped | `SkDrawable` base class + `SkCanvas.drawDrawable(matrix?)` and `(x, y)` overloads ; `SkCanvas.drawAnnotation` no-op extension slot (raster sinks ignore by default ; subclasses can override to capture link metadata). `drawShadow` **descoped** — no Material-Design GM in the corpus needs it. 12 tests. |
> | **B1** SkPDF (PDFBox adapter) | ❌ descoped | No ported GM needs PDF — only `internal_links.cpp` is PDF-specific upstream and isn't ported. See B1 section. |
> | **B2** SkSVGCanvas | ✅ shipped | All 5 slices delivered : 1104 main + 1351 test (mini plan estimate ~980 + ~550). See [MIGRATION_PLAN_SVG.md](MIGRATION_PLAN_SVG.md) for the per-slice breakdown. |
> | **Q1** SkAutoCanvasRestore Kotlin idiom | ✅ shipped | `withSave` / `withLayer` extension functions on `SkCanvas` (`SkAutoCanvasRestore.kt`) |
> | **Q2** Canvas wrappers | ✅ shipped | `SkNoDrawCanvas` + `SkPaintFilterCanvas` (abstract) + `SkOverdrawCanvas` in [`org.skia.utils`](kanvas-skia/src/main/kotlin/org/skia/utils/) ; 13 tests. |
> | **Q3** SkBBHFactory + Picture cull | ✅ shipped | `SkBBoxHierarchy` + `SkRTree` (bottom-up bulk-load, branch-factor 6-11) + `SkRTreeFactory` ; `SkPictureRecorder.beginRecording` accepts an optional factory ; per-op bounds computed by `SkPictureBoundsBuilder` ; `SkPicture.playback` queries the BBH for sub-rect clips. 21 tests. |
> | **Q4** SkDeferredDisplayList | ✅ shipped | `SkSurfaceCharacterization` (value-type wrapping `SkImageInfo`) + `SkDeferredDisplayList` (immutable `(characterization, picture)` holder) + `SkDeferredDisplayListRecorder` (single-shot detach) + `SkSurface.draw(ddl): Boolean` characterization-gated playback. Recording delegates to `SkPictureRecorder` ; the addition vs `SkPicture` is the per-surface compatibility check. 12 tests. |
> | **Q5** Linear sRGB diagnostic | ✅ shipped | Diagnostic confirms upstream applies the matrix in **encoded sRGB** (linear-mode wins 0 cells, encoded wins 5/5 non-tie) ; the residual ColorMatrixGM gap is elsewhere. Test in `ColorMatrixModeDiagnosticTest`. |

## Table des matières

1. [Principes iso-fidélité](#principes-iso-fidélité)
   - [Iso-fidelity exceptions](#iso-fidelity-exceptions)
2. [Architecture cible](#architecture-cible)
3. [Chantiers critiques DM](#chantiers-critiques-dm)
   - [D1 — `SkPathOps`](#d1--skpathops)
   - [D2 — `SkRuntimeEffect` (compatibility shim)](#d2--skruntimeeffect-compatibility-shim--iso-fidelity-exception)
   - [D3 — Image codecs (`Codec` + `encodeToData`)](#d3--image-codecs-skcodec--encodetodata)
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
- Alternative backends (~~B1~~ descoped, B2)
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
    `tests/PathOpsOpTest.cpp` — *covered by D1.4 below*. GM ports :
    `pathops*` cluster (only 2 GMs upstream, both shipped).

- **D1.4** — PathOps regression harvest. ✅ **shipped (extended)**.
  - **Pourquoi** : couverture end-to-end de `SkPathOps.Op` était
    **~2 %** d'upstream avant cette slice — 13 appels
    `SkPathOps.Op(...)` dans nos tests + GMs vs 451 fixtures
    `TEST(name)` dans
    [`tests/PathOpsOpTest.cpp`](https://github.com/google/skia/blob/main/tests/PathOpsOpTest.cpp).
    Les 686 tests internes (segment / coincidence / TSect / angle)
    valident les briques ; ils ne couvrent **pas** l'interaction
    multi-composants sur cas-limites — qui est précisément ce que
    chaque `TEST(bug8380)` / `TEST(crbug_526025)` / `TEST(fuzz_*)`
    upstream a fixé une fois pour toutes.
  - **Pipeline livré** :
    1. [`kanvas-skia/tools/extract_pathops_fixtures.py`](kanvas-skia/tools/extract_pathops_fixtures.py)
       — extracteur Python (~530 LOC) qui scrape les 449 fonctions
       `static void <name>(skiatest::Reporter*, const char*)` de
       `PathOpsOpTest.cpp` et émet un JSON dump avec
       `(name, fillTypeA, fillTypeB, pathA verbs, pathB verbs, op)`.
       Gère le format SkPathBuilder + le shortcut
       `SkPath one = SkPath::Rect({l,t,r,b}, dir)` + le cast int
       `setFillType((SkPathFillType) 0..3)` + `path.reset()` +
       `path.addRect` (4-scalar + brace-init + LTRB + XYWH variants)
       + `path.addCircle` (4-cubic Bézier expansion via κ=0.5523) +
       `SkBits2Float(0x…)` decoder pour les coords hex-encodés +
       `testPathOpCheck` variant. Multi-line `cubicTo` etc. sont
       joints par un pré-passe paren-depth tracker.
       **335 fixtures extraites / 451 = 74 %** d'upstream
       (~92 % du subset Op-only après exclusion des 86 fixtures
       `testSimplify`/`testPathOpFuzz` hors-scope).
    2. [`kanvas-skia/src/test/resources/pathops/op_fixtures.json`](kanvas-skia/src/test/resources/pathops/op_fixtures.json)
       — JSON dump (~22 300 lignes) commité comme test resource.
       Re-générer via `python3 kanvas-skia/tools/extract_pathops_fixtures.py
       /path/to/skia/tests/PathOpsOpTest.cpp >
       kanvas-skia/src/test/resources/pathops/op_fixtures.json`
       quand upstream gagne de nouvelles fixtures.
    3. [`PathOpsRegressionRunner`](kanvas-skia/src/test/kotlin/org/skia/pathops/PathOpsRegressionRunner.kt)
       — JUnit 5 `@ParameterizedTest` (~280 LOC) chargeant le JSON
       via `jackson-databind`, replayant chaque verb-stream sur
       `SkPathBuilder`, exécutant `SkPathOps.Op(A, B, op)`, et
       classifiant l'outcome dans une enum
       `{ SURVIVED, RETURNED_NULL, NON_FINITE, THREW, BUILD_FAILED,
       UNKNOWN_OP }`. Un test smoke `pathops Op survival rate stays
       at-or-above the floor` enforce un **floor de 90 %** sur le
       SURVIVED-rate global ; bumper monotone à mesure que le moteur
       s'améliore.
  - **Mesure courante** (post extractor coverage + h.10 debug-pass
    + pixel-parity oracle, 2026-05-09) :
    - **Survival** : 334 / 335 = **99.7 %** (1 RETURNED_NULL,
      `cubicOp35d` — 2 cubics croisant à sub-pixel). Floor 99 %.
    - **Pixel parity** : 320 / 334 = **95.8 %** (14 PIXEL_DIVERGE
      vs un oracle indépendant qui rasterise A et B au pixel et
      applique l'op set-wise). Floor 90 %.
    - Évolution survival : MVP 96.7 % → extraction-extended 96.4 %
      (+32 fixtures) → debug-pass 99.7 % (–11 RETURNED_NULL).
  - **Pixel oracle** ([PathOpsPixelOracle.kt](kanvas-skia/src/test/kotlin/org/skia/pathops/PathOpsPixelOracle.kt))
    — port allégé d'upstream's `comparePaths` strategy. Au lieu
    de convertir le résultat en `SkRegion` (notre port n'a pas
    `getBoundaryPath`), l'oracle :
    1. Scale `A ∪ B` à un bitmap 64×64 (mirrors upstream's
       `kBitWidth/Height`).
    2. Rasterise `A` et `B` au pixel binaire (AA off).
    3. Applique l'op set-wise (`AND` / `OR` / `AND NOT` / `XOR`).
    4. Rasterise le résultat de `SkPathOps.Op(A, B)` au même
       cadre.
    5. Compte les blocs 2×2 divergent ; tolère jusqu'à 8 blocs
       (mirrors upstream's `MAX_ERRORS`).
    Résultat → enum `{ PIXEL_MATCH, PIXEL_DIVERGE, DEGENERATE }`
    enregistré dans le runner pour le floor smoke parallèle.
  - **LOC livrés** : ~530 main (Python) + ~470 test (Kotlin —
    280 runner + 190 oracle) + ~22 300 JSON. Bouge le compteur
    restant D1.4 de "~300 + ~8 k" estimé à **~1 000 + ~22 300
    livré**.
  - **Restant** :
    1. **`cubicOp35d`** — vrai bug algo dans le boolean handling
       de deux cubiques se croisant à sub-pixel. Investigation
       séparée (style D1.2.h.7/h.8) ; ~1-3 jours.
    2. **14 fixtures pixel-divergent** — bumper le floor 90 % vers
       95 %+ par debug-passes successifs. Probable mix de cubic-
       cubic precision + handling des inverse-fill inputs.
       ~1-3 jours par batch.
    3. **~30 fixtures non-extraites** (`SkPoint pts[]` arrays,
       `CubicPathToQuads` helper calls, `SkScalar xA = …` named
       constants, embedded SVG path strings). Effort modéré
       (~1 semaine), gain de couverture marginal.
  - **Bénéfice mesuré** : la suite stays green à **3057 / 3057**
    avec 336 tests `PathOpsRegressionRunner` + 0 crashes vs 451
    cas-limites upstream. Le moteur D1 sort en très bonne forme —
    le seul guard obsolète repéré était un faux-négatif sur les
    résultats Op-empty légitimes (ex: `Op(A, A, kDifference)`).

**Total LOC** : ~9000-12000 (D1.0 → D1.3) + ~300 main + ~8 k data (D1.4).
**Estimated time** : 2-3 weeks per slice (D1.1 / D1.2 / D1.3) plus
6-15 weeks for D1.4 if attacked. Largest chantier in this plan.

**Validation** : ensemble fixtures replays + ports of
`gm/pathopsfuzz.cpp` + `gm/pathopsskpclip.cpp` + `gm/complexclip2.cpp`.

**Risk** : Bézier intersection numerical robustness. Skia uses
extensively-tuned epsilon thresholds developed over years. Any
deviation causes spurious zero-length segments or missed
intersections that crash downstream.

**Alternative** : JNI binding to Skia native (`libskia.a`). Casse
l'autonomie pure-Kotlin, mais ~10x moins de LOC.

---

### D2 — `SkRuntimeEffect` façade + per-effect Kotlin ports

> ✏️ **Mini plan dédié** :
> [MIGRATION_PLAN_D2_RUNTIME_EFFECT.md](MIGRATION_PLAN_D2_RUNTIME_EFFECT.md)
> couvre la décomposition complète en 8 sous-slices (D2.0
> SkBlender + paint plumbing, D2.1 façade + dispatch table, D2.2
> bindings, D2.3 Builder + SkData, D2.4.a-d hand-port des effets,
> D2.5 image-filter integration, D2.6 DM pipeline). La section
> ci-dessous reste comme **résumé** ; tout détail nouveau va dans
> le mini plan.

> 🔁 **Stratégie alignée sur le plan GPU.** Ce chantier suit la
> même politique que
> [MIGRATION_PLAN_GPU_WEBGPU.md § Phase G4](MIGRATION_PLAN_GPU_WEBGPU.md#phase-g4--shader-infra--gradients-en-wgsl)
> : *« Pas de SkSL → WGSL transpilation. Chaque type de shader
> a son template WGSL. »* D2 fait l'équivalent côté raster —
> chaque "runtime effect" devient un type de shader / colorFilter
> / blender hand-porté en Kotlin, exactement comme l'a été
> `SkLinearGradient` / `SkRadialGradient` / `SkBitmapShader` en
> Phase 5 du master plan. La classe `SkRuntimeEffect` reste comme
> surface publique de dispatch (les ports de GMs upstream peuvent
> appeler `MakeForShader(skslString)` verbatim, le shim retrouve
> l'impl Kotlin via le SkSL canonique → hash). **Aucun parser ni
> VM SkSL n'est porté**, ni aujourd'hui ni plus tard — c'est un
> choix architectural du projet, pas un compromis.

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

### D3 — Image codecs (`Codec` + `encodeToData`) ✅ shipped

**Skia upstream files** :
- `include/codec/Codec.h`, `SkPngDecoder.h`, `SkJpegDecoder.h`,
  `SkWebpDecoder.h`, etc.
- `include/encode/SkPngEncoder.h`, `SkJpegEncoder.h`, etc.
- `src/codec/`, `src/images/`

**Kotlin target** :
- `kanvas-skia/src/main/kotlin/org/skia/codec/Codec.kt` (decoder
  facade)
- `kanvas-skia/src/main/kotlin/org/skia/encode/SkEncoder.kt` (encoder
  facade)
- Per-format implementations in `org/skia/codec/png/`, `jpeg/`, etc.

**Strategy** : prefer **JVM `imageio` adapters** for formats it
supports (PNG, JPEG, GIF, BMP) ; pure-Kotlin minimal codec for
WEBP and HEIF only when imageio doesn't ship them. This keeps LOC
manageable while reaching multi-format parity.

**Phase decomposition** :

- **D3.1** ✅ — `Codec` decoder facade + PNG. Package
  `org.skia.codec` ([Codec.kt](kanvas-skia/src/main/kotlin/org/skia/codec/Codec.kt))
  ships the facade (`Result` enum, `MakeFromData` / `MakeFromStream`,
  `getInfo` / `getEncodedFormat` / `getICCProfile` / `getPixels` /
  `getImage`) plus the `SkEncodedImageFormat` enum.
  [SkPngCodec.kt](kanvas-skia/src/main/kotlin/org/skia/codec/png/SkPngCodec.kt)
  registers as the first `Codec.Decoder`, sniffs the PNG signature,
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
  registers as the second `Codec.Decoder` (signature `FF D8 FF`),
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
  it is registered last in `Codec.Decoders` so every other
  format with a stronger magic gets first refusal.
  - **LOC** : ~298 main + ~219 test = 517 total (cf. plan estimate
    ~250 — overage covers the parallel-but-distinct kdoc per format
    and the WBMP VLQ header walker).
  - **Tests** : `SkGifCodecTest` (4), `SkBmpCodecTest` (4 — incl.
    24-bit BMP byte-identical round-trip), `SkWbmpCodecTest` (4 —
    incl. a "loose magic" rejection case for `00 00`-prefixed
    non-WBMP bytes). 12/12 green ; full kanvas-skia suite
    **2189 / 2189 green**.

- **D3.4** ✅ — WEBP via the
  [TwelveMonkeys `imageio-webp`](https://github.com/haraldk/TwelveMonkeys/tree/master/imageio/imageio-webp)
  ImageIO plugin (Option B from the plan — external Maven dep, no
  native libwebp / no FFI). The plugin auto-registers a
  `WebPImageReaderSpi` on classpath load, so `ImageIO.read`
  handles VP8 / VP8L / VP8X bitstreams transparently from the same
  call site the other D3 codecs use.
  [SkWebpCodec.kt](kanvas-skia/src/main/kotlin/org/skia/codec/webp/SkWebpCodec.kt)
  registers as the 5th `Codec.Decoder` (between BMP and WBMP),
  signature is the 12-byte `RIFF` + 4 size bytes + `WEBP` prefix,
  and the codec emits `kRGBA_8888 / kUnpremul / sRGB` like every
  other D3 sibling. **Read-only** — TwelveMonkeys ships no WEBP
  encoder, so the D3.5 encoder family is not extended. **No ICC
  profile** : VP8X can carry an `ICCP` sub-chunk but TwelveMonkeys
  doesn't surface it through the public API ; documented as a
  future-slice opportunity.
  - **Dep added** :
    `implementation("com.twelvemonkeys.imageio:imageio-webp:3.12.0")`
    (pulls in 5 transitive jars : `imageio-core`,
    `imageio-metadata`, `common-lang`, `common-io`,
    `common-image`).
  - **LOC** : ~111 main + ~104 test = 215 total + 340-byte
    fixture (`stoplight.webp`, copied from upstream Skia's
    `resources/images/`).
  - **Tests** :
    [SkWebpCodecTest.kt](kanvas-skia/src/test/kotlin/org/skia/codec/webp/SkWebpCodecTest.kt)
    (5 — signature dispatch incl. negative match for `RIFF…WAVE`
    containers, end-to-end decode of `stoplight.webp` confirming
    the TwelveMonkeys plugin is reachable on the runtime
    classpath, `kRGBA_8888 / sRGB / kUnpremul` tagging, geometry
    validation). 5/5 green ; full kanvas-skia suite
    **2229 / 2229 green**.

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

- **D3.6** ✅ — `SkImage.encodeToData(format, quality)` convenience.
  Single Kotlin extension function in
  [SkImageEncode.kt](kanvas-skia/src/main/kotlin/org/skia/encode/SkImageEncode.kt)
  that wraps the D3.5 encoders. Lives on the `org.skia.encode`
  side rather than as an instance method on [SkImage] so the
  `org.skia.foundation` package stays free of any dependency on
  the encoder layer (`encode` depends on `foundation`, not the
  other way round). Dispatches `kPNG → SkPngEncoder` and
  `kJPEG → SkJpegEncoder(Options(quality = quality))` ; every
  other [SkEncodedImageFormat] returns `null` (encoders ship for
  PNG / JPEG only — GIF / BMP / WBMP have decoders but no
  encoders, WEBP encoder is out of scope — D3.4 ships
  decode-only via TwelveMonkeys).
  - **LOC** : ~52 main + ~99 test = 151 total (cf. plan estimate
    ~50 main — close).
  - **Tests** :
    [SkImageEncodeTest.kt](kanvas-skia/src/test/kotlin/org/skia/encode/SkImageEncodeTest.kt)
    (4 — default-PNG byte-identical round-trip, JPEG `quality`
    plumbing via `lower q → fewer bytes`, unsupported-format
    `null` return, "thin wrapper" check that `encodeToData(kPNG)`
    bytes equal `SkPngEncoder.Encode(bitmap)` bytes). 4/4 green ;
    full kanvas-skia suite **2216 / 2216 green**.

D3 closes — see D3.4 above for WEBP via the TwelveMonkeys
plugin.

**Total LOC** : ~1000-1500 (with WEBP via opt-in dep ; +3000 if
pure-Kotlin WEBP).

**Validation** : decode + encode roundtrip = pixel-identical for
PNG (lossless) ; for lossy formats (JPEG / WEBP) within ~1 ulp per
channel after re-decode.

---

### D4 — DM sink architecture ✅ shipped

**Skia upstream files** :
- `dm/DM.cpp` (the runner)
- `dm/DMSrcSink.h`, `dm/DMSrcSink.cpp` (sink implementations)
- `dm/DMGpuSupport.h`

**Kotlin target** :
- `kanvas-skia/src/main/kotlin/org/skia/dm/Sink.kt` (interface)
- `kanvas-skia/src/main/kotlin/org/skia/dm/RasterSink8888.kt`
- `kanvas-skia/src/main/kotlin/org/skia/dm/RasterSinkF16.kt`
- `kanvas-skia/src/main/kotlin/org/skia/dm/PictureSink.kt`
- ~~`kanvas-skia/src/main/kotlin/org/skia/dm/PdfSink.kt`~~ — descoped
  along with B1 (no ported GM needs PDF, see B1 section)
- `kanvas-skia/src/main/kotlin/org/skia/dm/SvgSink.kt` (depends on B2)
- `kanvas-skia/src/main/kotlin/org/skia/dm/Runner.kt` (entry point)

**API surface** :
```kotlin
public interface Sink {
    public val tag: String  // e.g. "8888", "f16", "pic-8888", "svg"
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

- **D4.3** ✅ — `Runner` + `Report` JSON output mirroring upstream
  `dm/dm.json` format. Two main-side files :
  [Runner.kt](kanvas-skia/src/main/kotlin/org/skia/dm/Runner.kt)
  drives every `(GM × Sink)` combination, builds a [RunRecord]
  per result, and aggregates into a [Report] ;
  [Report.kt](kanvas-skia/src/main/kotlin/org/skia/dm/Report.kt)
  carries the records plus `properties` / `key` top-level
  metadata and emits upstream-shaped `dm.json` via a hand-rolled
  pretty-printed writer (no JSON-library dep).
  Each pass record carries a hex-encoded MD5 of the PNG-encoded
  output bytes (same convention as upstream — two sinks
  producing the same visible image yield the same MD5) ; failure
  records leave the bitmap-side fields empty and prefix the
  error in `"md5"` as `"error: …"`.
  Single-threaded by design — order-determinism makes
  report-diffing easier, and the parallel inner loop is a future
  slice when a use case shows up.
  - **LOC** : ~199 main + ~178 test = 377 total (cf. plan
    estimate ~300 — overage covers the per-`SkColorSpace` gamut
    / TF stringifier with named-profile fall-throughs and the
    JSON-escape walker).
  - **Tests** :
    [RunnerTest.kt](kanvas-skia/src/test/kotlin/org/skia/dm/RunnerTest.kt)
    (10 — `(GM × Sink)` matrix coverage, MD5 determinism,
    different GMs → different MD5, `Sink.Result.Error` →
    `failed` with carried-through message, **full
    upstream-shape JSON checks** (top-level keys + per-record
    nested shape), Rec.2020 gamut/TF named-profile recovery,
    JSON escape for `"` / `\\`). 10/10 green ; full kanvas-skia
    suite **2250 / 2250 green**.

- **D4.4** ✅ — CLI flags matching upstream's syntax. Two
  main-side files :
  [DmCli.kt](kanvas-skia/src/main/kotlin/org/skia/dm/DmCli.kt)
  parses `--config` / `--match` / `--skip` / `--key` /
  `--properties`, exposes `resolveSinks()` (tag → live `Sink`
  via the `8888` / `f16` / `pic-8888` / `pic-f16` registry),
  `shouldRun(name)` (port of upstream's
  `CommandLineFlags::ShouldSkip` with `~` / `^` / `$`
  syntax), `shouldSkipPair(gmName, sinkTag)` (the upstream
  `--skip <config> <src> <srcOptions> <name>` quadruple
  walker, with `_` wildcard and `~` negation per upstream's
  `match` helper) ;
  [DmMain.kt](kanvas-skia/src/main/kotlin/org/skia/dm/DmMain.kt)
  wires the parsed CLI into the D4.3 [Runner] and applies
  the post-run skip filter.
  Plan called the blacklist flag `--blacklist` ; we go with
  upstream's actual name `--skip`. Both `--flag value` (greedy
  multi-value, upstream style) and `--flag=value` (single,
  POSIX-y) syntaxes are accepted.
  - **LOC** : ~242 (DmCli) + ~98 (DmMain) + ~275 test = ~615
    total (cf. plan estimate ~200 — overage covers the
    upstream-faithful `ShouldSkip` algorithm, the per-pair
    skip filter, and the end-to-end `DmMain` orchestration).
  - **Tests** :
    [DmCliTest.kt](kanvas-skia/src/test/kotlin/org/skia/dm/DmCliTest.kt)
    (23 — flag parsing in both syntaxes, all 4 `--match`
    variants (`bare` / `^` / `$` / `^$` / `~`), order-dependent
    include/exclude semantics, `--skip` per-pair gating with
    wildcard, sink resolution incl. `null` for unknown tags,
    end-to-end `DmMain` runs the matrix and applies skip
    filter, `--key` / `--properties` wired into
    `Report.toJson()`, error paths : empty `--config`,
    unknown tag, odd-length pairs). 23/23 green ; full
    kanvas-skia suite **2292 / 2292 green**.

- **D4.5** ✅ — ~~`PdfSink`~~ (descoped along with B1) +
  [`SvgSink`](kanvas-skia/src/main/kotlin/org/skia/dm/SvgSink.kt)
  (shipped via B2.5). PDF half dropped per the B1 audit ; the SVG
  sink wires the SVG mini plan into the DM matrix.
  - Tagged `"svg"`, file extension `"svg"`. Registered with
    [DmCli](kanvas-skia/src/main/kotlin/org/skia/dm/DmCli.kt)'s
    `KNOWN_CONFIGS` so `--config svg` resolves.
  - Returns the new
    [Sink.Result.Bytes](kanvas-skia/src/main/kotlin/org/skia/dm/Sink.kt)
    variant (vector output — the [Runner](kanvas-skia/src/main/kotlin/org/skia/dm/Runner.kt)
    hashes the raw bytes, no PNG re-encode). The
    [Sink](kanvas-skia/src/main/kotlin/org/skia/dm/Sink.kt) interface
    also gained a `fileExtension` property so the per-record
    `RunRecord.extension` field stays accurate per sink kind.
  - **LOC** : ~147 main + ~221 test (cf. plan estimate ~80 main —
    overage covers the `Sink.Result.Bytes` data class with custom
    equals/hashCode, the Sink interface widening, the Runner's
    Bytes branch, the exhaustiveness updates in callers
    `TestUtils.runGmTest` / `SinkTest`).
  - See [MIGRATION_PLAN_SVG.md § B2.5](MIGRATION_PLAN_SVG.md) for
    the per-piece breakdown.

**Total LOC** : ~625-925 (excluding B2 ; B1 / PdfSink descoped).

**Validation** : run all GMs through all sinks ; for raster sinks,
output pixels should be ~identical to direct render (gives 100% iso
on Picture playback). For the SVG sink, compare with upstream-
generated reference SVGs (lower fidelity expected — vector format).
PDF sink dropped (see B1 audit).

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

### I5 — `drawPoints` / `drawAtlas` / `drawVertices` / `drawPatch` ✅ shipped

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

- **I5.4** ✅ shipped (commit `2de410e`) — `drawPatch` : Coons patch
  (cubic interp grid). Tessellates the 4 boundary cubics into an
  N×N grid (`PATCH_TESS_N = 8` → 128 triangles per patch) via the
  Coons surface formula `C(s, t) = Lc(s, t) + Ld(s, t) − B(s, t)`,
  then defers to `drawVertices`. Lives in
  [SkCanvas.drawPatch](kanvas-skia/src/main/kotlin/org/skia/core/SkCanvas.kt:948)
  with [coonsSurfaceAt](kanvas-skia/src/main/kotlin/org/skia/core/SkCanvas.kt:1012)
  / `cubicAt` helpers.
  - GMs ported : `PatchAlphaTestGM`, `PatchPrimitiveGM` (test
    runners in
    [Round17Test.kt](kanvas-skia/src/test/kotlin/org/skia/tests/Round17Test.kt)).
  - Unit test :
    [DrawPatchTest.kt](kanvas-skia/src/test/kotlin/org/skia/core/DrawPatchTest.kt).

**Total LOC** : ~1000 + GM ports.

**Validation** : ports of `gm/drawatlas.cpp`, `gm/vertices.cpp`,
`gm/patch.cpp`.

---

## Chantiers compléments core

### C1 — Image filters extras ✅ shipped

**See [MIGRATION_PLAN_C1_IMAGE_FILTERS.md](MIGRATION_PLAN_C1_IMAGE_FILTERS.md)**
for the close-out report. All 7 sub-slices shipped : **22 image
filter factories** matching upstream's `SkImageFilters.h` surface
(1 explicit descope, `RuntimeShader`, blocked on D2). Total :
**1501 main + 1473 test** (well under the ~2750 main audit
estimate, thanks to the C1.7 sealed-class compaction). ~30 GM
ports unblocked ; those land as separate `tests/<Name>GM.kt`
follow-up PRs.

**Skia upstream files** :
- `include/effects/SkImageFilters.h` (the 28 factories ; 6 already
  shipped)
- `src/effects/imagefilters/Sk*ImageFilter.cpp`

**Kotlin target** : extensions à `SkImageFilters` + nouvelles classes
internes dans `kanvas-skia/src/main/kotlin/org/skia/foundation/`.

**Active scope** : see
[MIGRATION_PLAN_C1_IMAGE_FILTERS.md](MIGRATION_PLAN_C1_IMAGE_FILTERS.md)
(C1.1 source / passthrough wrappers, C1.2 Tile + Magnifier, C1.3
Arithmetic family, C1.4 Morphology, C1.5 DisplacementMap, C1.6
MatrixConvolution, C1.7 Lighting full surface).

<details><summary>Original C1 scope (historical, ~1800 LOC, 4 slices, 11 of 22 factories)</summary>

**Per-filter scope** :

| Filter | Algorithm | LOC |
|---|---|---|
| ~~`Erode` / `Dilate`~~ | morphology : per-pixel min/max over circular kernel | ~150 chacun |
| ~~`DisplacementMap`~~ | sample from input via offset = displacement.{R,G} - 0.5 | ~200 |
| ~~`Lighting` (point/distant/spot)~~ | normal map from height map + Phong shading | ~600 ensemble (undersized — upstream has 6 lighting variants, not 3 ; mini plan budgets ~1200) |
| ~~`Magnifier`~~ | radial scale around centre | ~150 |
| ~~`Tile`~~ | sample input through tiled bounds | ~150 |
| ~~`Arithmetic`~~ | k1·src + k2·dst + k3·src·dst + k4 per channel | ~150 |
| ~~`Merge`~~ | concat N inputs via per-pixel max-alpha | ~150 |
| ~~`Image`~~ | use static SkImage as input source | ~80 |
| ~~`Picture`~~ | use SkPicture replayed into bitmap as input | ~100 |
| ~~`Shader`~~ | use SkShader as input source | ~80 |

**Missing from this old scope** (added in the mini plan) : Blend,
Crop, DropShadowOnly, Empty, MatrixConvolution, the **3 specular
lighting variants** (DistantLitSpecular / PointLitSpecular /
SpotLitSpecular), and an explicit RuntimeShader descope.

**Phase decomposition** :

- ~~**C1.1**~~ — Trivial filters (Image, Picture, Shader, Tile,
  Arithmetic, Merge, Magnifier). ~700 LOC.
- ~~**C1.2**~~ — Morphology (Erode, Dilate). ~300 LOC.
- ~~**C1.3**~~ — DisplacementMap. ~200 LOC.
- ~~**C1.4**~~ — Lighting (point/distant/spot). ~600 LOC.

**Total LOC** : ~1800. **Mini plan revises to ~2750 main + ~1440 test
across 7 slices.**

**Validation** : port `gm/imagefilters*` cluster (~15 GMs).

</details>

---

### C2 — Path effects extras ✅ shipped

**Two slices** :

#### C2.1 — `Sk1DPathEffect.Style.kMorph` ✅ shipped

[SkPath1DPathEffect.kt](kanvas-skia/src/main/kotlin/org/skia/foundation/SkPath1DPathEffect.kt)
— faithful port of upstream's `morphpath` + `morphpoints` from
[`src/effects/Sk1DPathEffect.cpp`](https://github.com/google/skia/blob/main/src/effects/Sk1DPathEffect.cpp).

**Mechanism** :
1. The input path is flattened into one **`ContourMeasure`** per
   `kMove`-rooted contour : a chord polyline with cumulative
   arc-length per vertex. `getPosTan(d)` answers position +
   tangent at any arc distance via binary search + linear
   interpolation, clamping `d` to `[0, length]`.
2. For each contour, stamps are placed at `initialOffset,
   initialOffset + advance, …` until the contour is exhausted
   (`MAX_ITERATIONS = 100000` governor mirrors upstream).
3. **`kMorph` per-vertex map** : each control point at stamp-local
   coords `(sx, sy)` is mapped to `pos(d + sx) + sy · normal(d + sx)`
   where `normal = (-tan.y, tan.x)`. Mirrors upstream's
   `morphpoints` matrix exactly.
4. **`kLine → kQuad` upgrade** : straight stamp segments are
   replaced by a degenerate quad (control = midpoint) before
   morphing so they bend with the input path's curvature. Mirrors
   upstream's `morphpath` line→quad path.
5. `kTranslate` and `kRotate` go through the same `ContourMeasure`
   parametriser — the previous per-segment chord walker has been
   replaced by a unified contour-level loop. Behaviour preserved
   (existing test suite stays green).

**Tests** ([SkPath1DPathEffectMorphTest](kanvas-skia/src/test/kotlin/org/skia/foundation/SkPath1DPathEffectMorphTest.kt)) :
8 tests — factory invariants, stamp count parity with `kTranslate`,
geometric parity with `kRotate` on straight inputs (when stamps
have `sx ≥ 0`), arc-bend on curved inputs (control points pulled
off the chord), per-contour reset, contour-start clamping
behaviour pinned.

**LOC** : ~370 main (refactored) + ~250 test.

#### C2.2 — `kStrokeAndFill_Style` ✅ already shipped

Verified pre-existing : `SkBitmapDevice.drawPathWithoutMaskFilter`
(branches at lines 744 / 1037 / 1113) already filled-then-stroked
the path when `paint.style == kStrokeAndFill_Style`. The plan's
"`Compose(stroke, fill)` recipe" is delivered by the upstream-
canonical paint-style dispatch — no new code needed.

Coverage in [SkBitmapDeviceStrokeTest](kanvas-skia/src/test/kotlin/org/skia/core/SkBitmapDeviceStrokeTest.kt#L101)
(`kStrokeAndFill renders both fill and stroke`).

**LOC** : 0 (verification only).

---

### C3 — `SkEmbossMaskFilter` ✅ shipped

[SkEmbossMaskFilter.kt](kanvas-skia/src/main/kotlin/org/skia/foundation/SkEmbossMaskFilter.kt)
— port of upstream's `src/effects/SkEmbossMaskFilter.cpp` +
`SkEmbossMask.cpp`. Computes per-pixel Lambertian + raised-cosine
specular lighting from the alpha-mask gradient and a directional
light, outputs three coverage planes.

**Mechanism** :
1. The mask filter's `format` is `k3D` ; the base
   [SkMaskFilter](kanvas-skia/src/main/kotlin/org/skia/foundation/SkMaskFilter.kt)
   gains an enum + a default `filterMask3D` fallback that
   degrades single-plane filters to "alpha = filterMask, multiply
   = 255, additive = 0" so the Bitmap device can always dispatch
   through the 3-plane path.
2. [SkBitmapDevice.drawPathWithMaskFilter](kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt)
   now switches on `maskFilter.format` : `kA8` keeps the legacy
   single-plane composite, `k3D` runs the per-pixel formula
   `dst.rgb = clamp(paint.rgb × multiply / 255 + additive)`,
   `dst.a = paint.a × alpha / 255`.
3. The emboss filter's algorithm :
   - Inner-blur the source coverage via [SkBlurMaskFilter] with
     [SkBlurStyle.kInner] to build the height field.
   - Per-pixel : compute the gradient `(nx, ny)`, project the
     normalised light direction onto the surface normal
     `(nx, ny, kDelta)`, derive the diffuse term
     `multiply = ambient + clamp(dot, 0, 255)`.
   - Specular : raised-cosine peak around the perfect-mirror
     reflection, dampened by `additive = hilite^(specular ÷ 16)`
     via repeated `div255` multiplication (matches upstream's 4.4
     fixed-point falloff).
   - Carry the original (unblurred) coverage as the alpha plane
     so path edge AA is preserved verbatim.

**API** :
```kotlin
public class SkEmbossMaskFilter private constructor(/* ... */) : SkMaskFilter() {
    public companion object {
        public fun Make(blurSigma: Float, light: Light): SkMaskFilter?
    }
    public data class Light(
        val direction: FloatArray, // 3-vector, normalised inside Make
        val ambient: Int,          // [0, 255]
        val specular: Int,         // 4.4 fixed-point in [0, 255]
    )
}
```

**Tests** : 14 in
[SkEmbossMaskFilterTest](kanvas-skia/src/test/kotlin/org/skia/foundation/SkEmbossMaskFilterTest.kt)
— factory invariants (null on bad sigma / zero direction,
direction normalisation), format declared `k3D`, plane sizes,
alpha plane preserved verbatim, flat-coverage uniform multiply,
non-flat coverage non-uniform multiply, specular falloff
monotonicity, end-to-end `drawRect` + emboss producing
non-uniform pixels (with a control assertion that `drawRect`
without emboss is uniform — guards against false positives in
the integration check).

**GM port deferred** — the upstream `EmbossGM` uses
`drawImage` with a mask filter (a code path not implemented in
`kanvas-skia`'s raster pipeline ; mask filters apply to paths
and rrects only). A faithful port needs image-blit-with-
maskFilter support first ; tracked separately if a workflow
demands it.

**LOC** : ~278 main (SkEmbossMaskFilter) + ~120 main delta
across SkMaskFilter (`Format` enum + `Sk3DMask` data class +
`filterMask3D` default) + SkBitmapDevice (3-plane dispatch
branch) + ~280 test = **~678 total** (cf. plan estimate ~400 ;
overage covers the SkMaskFilter interface widening — `Format`,
`Sk3DMask`, `filterMask3D` fallback — that lays the foundation
for any future multi-plane mask filter, plus the upstream-
faithful div255 specular falloff).

---

### C4 — Canvas opérations manquantes ✅ partially shipped

#### C4.1 — `drawAnnotation` ✅ shipped

[SkCanvas.drawAnnotation](kanvas-skia/src/main/kotlin/org/skia/core/SkCanvas.kt)
— a no-op extension slot on the raster canvas. PDF / SVG sinks
(or any future structured backend) can override to capture link
metadata, named destinations, or URL anchors. The default impl
silently drops `(rect, key, value)` — matches Skia's raster-device
behaviour exactly.

**LOC** : ~20 main + ~30 test (3 assertions in `SkDrawableTest` :
no-op preserves bitmap, null value tolerated, subclass override
captures every annotation).

#### C4.2 — `SkDrawable` + `drawDrawable` ✅ shipped

[SkDrawable.kt](kanvas-skia/src/main/kotlin/org/skia/core/SkDrawable.kt)
— faithful port of upstream's
[`include/core/SkDrawable.h`](https://github.com/google/skia/blob/main/include/core/SkDrawable.h) :

- Abstract `onDraw(canvas)` entry point.
- Optional `onGetBounds(): SkRect` (default = empty).
- Public `draw(canvas, matrix?)` and `draw(canvas, x, y)` overloads
  that wrap the call in `save` / `restoreToCount` so the canvas's
  matrix / clip / save-stack are guaranteed preserved on return.
- Process-wide unique `getGenerationID()` + `notifyDrawingChanged()`
  for downstream cache invalidation. Generation ids come from a
  shared `AtomicInteger`, matching the upstream contract.

[SkCanvas](kanvas-skia/src/main/kotlin/org/skia/core/SkCanvas.kt)
gains `drawDrawable(drawable, matrix?)` and `drawDrawable(drawable, x, y)`
overloads that delegate to `SkDrawable.draw`. Subclasses
(`SkRecordingCanvas`, `SkSVGCanvas`) may override to record /
serialise the drawable directly ; the default delegate is correct
for any backend supporting basic primitives.

**LOC** : ~115 main + ~120 test (9 tests in `SkDrawableTest` :
onDraw fires, save-count rebalance, matrix pre-concat, xy
overload, CTM preservation, gen-id stability, gen-id uniqueness,
default + overridden bounds).

#### C4.3 — `drawShadow` ❌ descoped

**Audit** (2026-05-08) — searched `gm/*.cpp` upstream for
`drawShadow` / `SkShadowUtils` references :

| Upstream GM | drawShadow role | Ported in `kanvas-skia` ? |
|---|---|---|
| `shadowutils.cpp` | Material-Design shadow rendering matrix | No |
| `androidshadowutils.cpp` | Android-flavoured shadow util GM | No |
| `shadowutils_occluders.cpp` | Occluder-aware shadow GM | No |

**Conclusion** : zero ported GMs in
`kanvas-skia/src/main/kotlin/org/skia/tests/` use
`SkCanvas::drawShadow` ; the only upstream GMs that exercise it
are the three above, none of which are on the migration path.
The Material Design shadow pipeline (spot + ambient projection
from path elevation, Tessellator-driven mesh emission, ~600 LOC)
is therefore deferred indefinitely.

If a Material-Design GM is added later :
- Skia upstream files : `include/core/SkCanvas.h` (declarations) +
  `src/utils/SkShadowUtils.cpp` + `src/utils/SkShadowTessellator.cpp`.
- Estimated port size : ~600 LOC main + ~250 test for tessellator
  + projection ; the `SkPath3DEffectGM` family stays out of scope
  unless Path3DEffect itself is also ported.

**LOC** : 0 (descoped).

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

### B1 — `SkPDF` ❌ descoped

**Status** : **dropped from the active migration plan**. No GM
currently in `kanvas-skia/src/main/kotlin/org/skia/tests/` requires
PDF output to pass its similarity ratchet — the GMs run through the
raster sink path (`RasterSink8888` / `RasterSinkF16`), and the
upstream PDF sink is an *additional* output channel, not a
prerequisite for any GM.

**Audit** (2026-05-08) — searched `gm/*.cpp` upstream for `PDF` /
`SkPDF` references :

| Upstream GM | PDF role | Ported in kanvas-skia ? |
|---|---|---|
| `internal_links.cpp` | PDF-only (`SkAnnotation::kLink_t` ; cliquer-pour-sauter, n'a aucun sens en raster) | No |
| `fadefilter.cpp` | "renders correctly in 8888, but fails in PDF" — known upstream PDF bug, GM passes in raster | **Yes** (`FadeFilterGM`) |
| `skbug_4868.cpp` | regression test for an SkPDF rounding bug | **Yes** (`Skbug4868GM`) |
| `strokes.cpp` | `#ifdef PDF_IS_FIXED_…` — sub-test gated on a PDF fix that never landed | **Yes** (`StrokesGM`) |
| `xfermodes.cpp` | "PDF has to play some tricks" — narrative comment only | **Yes** (`XfermodesGM`) |
| `clippedbitmapshaders.cpp` | known PDF clamp bug | No |
| `crbug_918512.cpp`, `skbug_5321.cpp` | PDF regression tests | No |

**Conclusion** : the only GM that *needs* PDF is `internal_links.cpp`
(annotation links), and porting it isn't on the critical path. Every
other PDF mention is either a comment or a regression test for an
upstream PDF bug — none of those require kanvas-skia to *emit* PDF.

**Implication for the `kanvas-skia/src/main/kotlin/org/skia/pdf/`
package** : the directory is not created.
[D4.5](#d4--dm-sink-architecture) is reduced to SVG-only.

**If PDF becomes necessary later** :
- Skia upstream files : `include/docs/SkPDFDocument.h`, `src/pdf/`
  (~50 files, ~15000 LOC).
- Strategy : pure-Kotlin writer mirroring Skia's structure
  (~10000 LOC) **or** an external lib (PDFBox via Maven) with a
  thin `SkCanvas` adapter on top (~500 LOC).
- Recommendation if revived : **PDFBox adapter** for v1 ; pure-Kotlin
  only if PDF iso-fidelity becomes critical.

---

### B2 — `SkSVGCanvas` ✅ shipped

**See [MIGRATION_PLAN_SVG.md](MIGRATION_PLAN_SVG.md)** for the
per-slice details. The mini plan delivered 1104 main + 1351 test
(estimated ~980 + ~550). The original 7-slice / ~3000 LOC scope
below is preserved as a historical reference (struck through) ;
the mini plan shipped by descoping text, filters, saveLayer,
color filters,
and non-clamp bitmap shaders until a use case demands them.

**Skia upstream files** :
- `include/svg/SkSVGCanvas.h`
- `src/svg/SkSVGCanvas.cpp`

**Strategy** : SkCanvas implementation that serializes ops to SVG XML.
Simpler than PDF (text-based).

**Active scope** : see [MIGRATION_PLAN_SVG.md](MIGRATION_PLAN_SVG.md)
(B2.1 skeleton + geometry, B2.2 paint, B2.3 clip, B2.4 image +
gradients, B2.5 D4.5 SvgSink wiring).

<details><summary>Original B2 scope (historical, ~3000 LOC, 7 slices)</summary>

- ~~**B2.1**~~ — Path → SVG `<path d="...">` serialization. ~500 LOC.
- ~~**B2.2**~~ — Solid fills + strokes → SVG `<g>` with style attrs. ~300.
- ~~**B2.3**~~ — Shaders (linear, radial) → SVG `<linearGradient>` /
  `<radialGradient>` defs. ~600.
- ~~**B2.4**~~ — Image embedding → `<image>` with base64 data URL. ~200.
- ~~**B2.5**~~ — Clipping → `<clipPath>` defs. ~400.
- ~~**B2.6**~~ — Text → `<text>` (defer SkTextBlob → SVG textPath
  extension). ~500. **Descoped** in the mini plan.
- ~~**B2.7**~~ — Filters → SVG filter primitives (`<feGaussianBlur>`,
  `<feOffset>`, ...). ~500. **Descoped** in the mini plan.

**Validation** : run all GMs through SvgSink, render the resulting
SVG via Batik or browser, compare with raster reference. **Mini plan
relaxes** to structural well-formedness via `DocumentBuilder` ;
pixel-level comparison deferred until a workflow demands it.

</details>

---

## Infrastructure & qualité

### Q1 — `SkAutoCanvasRestore` Kotlin idiom ✅ shipped

Implemented in
[SkAutoCanvasRestore.kt](kanvas-skia/src/main/kotlin/org/skia/core/SkAutoCanvasRestore.kt)
as `withSave` / `withLayer` extension functions on [SkCanvas].
Plus a Phase Q1.x clean-up that migrated 11 GMs to the
extension-function form (see commit `14748d5` "Phase Q1.x —
migrate 11 GMs to withSave / withLayer").

**LOC** : ~30 (shipped).

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

### Q2 — Canvas wrappers (`PaintFilter`, `NoDraw`, `Overdraw`) ✅ shipped

Three diagnostic [SkCanvas] subclasses live under
[`org.skia.utils`](kanvas-skia/src/main/kotlin/org/skia/utils/) :

- [SkNoDrawCanvas](kanvas-skia/src/main/kotlin/org/skia/utils/SkNoDrawCanvas.kt)
  — every `drawX` is a no-op ; the matrix / clip stack still works.
  Constructor takes `(width, height)`. Backed by a `1×1` dummy
  bitmap (the SkRecordingCanvas / SkSVGCanvas pattern) so the
  state machinery in [SkCanvas] applies without rendering.
  Use case : analysis passes that need [getTotalMatrix] but not the
  pixels (compute device-space bounds, time `onDraw` overhead, …).
- [SkPaintFilterCanvas](kanvas-skia/src/main/kotlin/org/skia/utils/SkPaintFilterCanvas.kt)
  — abstract proxy that forwards every draw to a wrapped target,
  invoking `onFilter(paint)` on a *copy* of the paint first.
  `onFilter` returns `true` to forward (with the possibly mutated
  paint) or `false` to drop the draw. State changes (save / restore
  / matrix / clip / saveLayer) are forwarded to **both** the
  wrapper's local stack (for accurate [getTotalMatrix] queries) and
  the target. `null`-paint image draws receive a synthesised default
  paint so subclassers always have something to inspect. A
  convenience `Companion.invoke` factory takes a lambda for one-shot
  use : `SkPaintFilterCanvas(target) { paint -> ... ; true }`.
- [SkOverdrawCanvas](kanvas-skia/src/main/kotlin/org/skia/utils/SkOverdrawCanvas.kt)
  — concrete subclass of [SkPaintFilterCanvas] that substitutes
  every paint with a fixed `(alpha=1, blend=kPlus)` source so each
  draw increments the destination's alpha by `+1`. Used to detect
  overdraw — situations where a single output pixel is rasterised
  multiple times. After a GM run, `bitmap.getPixel(x, y) >> 24` is
  the count of draws that touched `(x, y)` (saturating at 255).

**API** : `SkNoDrawCanvas(w, h)` ; `abstract SkPaintFilterCanvas(target)
{ protected abstract fun onFilter(paint: SkPaint): Boolean }` (or
the lambda factory) ; `SkOverdrawCanvas(target)`.

**Tests** :
[SkCanvasWrappersTest](kanvas-skia/src/test/kotlin/org/skia/utils/SkCanvasWrappersTest.kt)
(13) — NoDraw drops draws + keeps matrix stack ; PaintFilter
forwards filtered draws, drops on `false`, mutates the target's
paint, leaves the caller's paint untouched, synthesises a default
paint for `null`-paint image draws, end-to-end translate +
clipRect + drawPath chain ; Overdraw accumulates `+1` per draw,
saturates at 255, substitutes paint regardless of input
shader/filter, only touched pixels increment.

**LOC** : ~83 (NoDraw) + ~290 (PaintFilter) + ~80 (Overdraw) +
~270 (test) = **~723 total** (cf. plan estimate ~400 ; overage
covers the per-overload override matrix on PaintFilterCanvas —
3 clipRect / 2 clipPath / 2 clipRRect / 2 rotate / 3 saveLayer
arities — and the 13-test coverage matrix).

---

### Q3 — `SkBBHFactory` + Picture cull ✅ shipped

**Skia upstream files** :
- `include/core/SkBBHFactory.h` (interface)
- `src/core/SkRTree.{h,cpp}` (concrete bottom-up bulk-loaded R-tree)

**Algorithm** : the R-tree indexes recorded ops by their device-space
bounds. At playback time, [SkPicture.playback] queries the tree with
the canvas's local-space clip bounds and dispatches only the ops
whose bounds intersect — turning an `O(N)` walk into `O(log N + K)`
on tight clips.

**Shipped Kotlin** :
- [`kanvas-skia/src/main/kotlin/org/skia/core/SkBBoxHierarchy.kt`](kanvas-skia/src/main/kotlin/org/skia/core/SkBBoxHierarchy.kt)
  — abstract base : `insert(rects, n)`, `search(query): IntArray`,
  `bytesUsed`, plus a `Metadata(isDraw)` parity hook.
- [`kanvas-skia/src/main/kotlin/org/skia/core/SkRTree.kt`](kanvas-skia/src/main/kotlin/org/skia/core/SkRTree.kt)
  — concrete bottom-up bulk-loaded R-tree, faithful port of
  upstream's `bulkLoad` / `search` (branch factor `[6, 11]`,
  insertion-order preserved through the tree so [search] returns
  ascending-sorted indices). Empty rects skipped silently.
- [`kanvas-skia/src/main/kotlin/org/skia/core/SkBBHFactory.kt`](kanvas-skia/src/main/kotlin/org/skia/core/SkBBHFactory.kt)
  — `fun interface SkBBHFactory { fun create(): SkBBoxHierarchy }`
  (lambda-friendly SAM) plus `object SkRTreeFactory` mirroring
  upstream's default.
- [`kanvas-skia/src/main/kotlin/org/skia/core/SkPictureBoundsBuilder.kt`](kanvas-skia/src/main/kotlin/org/skia/core/SkPictureBoundsBuilder.kt)
  — walks the recorded `SkRecord` list once, tracking the CTM via
  a matrix stack, producing per-op device-space bounds (tight for
  geometric draws, paint-stroke-outset where applicable, picture's
  cullRect for state ops / text / `drawPaint` / `drawColor`).
- [`SkCanvas`](kanvas-skia/src/main/kotlin/org/skia/core/SkCanvas.kt)
  gains `getDeviceClipBounds()` and `getLocalClipBounds()`
  helpers (the latter inverts the CTM for the BBH query).
- [`SkPictureRecorder.beginRecording`](kanvas-skia/src/main/kotlin/org/skia/core/SkPictureRecorder.kt)
  accepts an optional `bbhFactory: SkBBHFactory? = null`. When
  non-null, `finishRecordingAsPicture` builds the BBH from the
  per-op bounds and bakes it into the picture.
- [`SkPicture.playback`](kanvas-skia/src/main/kotlin/org/skia/core/SkPicture.kt)
  short-circuits empty clips, falls back to a linear walk when the
  clip covers the full cullRect, and otherwise dispatches only the
  BBH-search hits in insertion order.

**Tests** (21 total, all green) :
- [`SkRTreeTest`](kanvas-skia/src/test/kotlin/org/skia/core/SkRTreeTest.kt)
  — 9 tests including the upstream-parity 100-iteration random
  benchmark (200 rects × 50 queries vs brute force), depth bound
  envelope, empty-insert / single-rect / empty-rect skipping,
  insertion-order sort, double-insert rejection, factory parity.
- [`SkPictureBBHCullTest`](kanvas-skia/src/test/kotlin/org/skia/core/SkPictureBBHCullTest.kt)
  — 12 tests covering: BBH presence flag, pixel parity under full
  and sub-rect clips, effective cull (counting canvas), CTM
  honoured during recording, never-under-includes (10×10 grid
  intersect verification), draw-order preservation, empty-clip
  short-circuit.

**LOC** : ~582 main + ~430 test = ~1012 total.

**Validation** : a 1000-op picture replayed under a tiny clip is
now O(log N + K) ; the counting-canvas tests demonstrate effective
cull (3-op picture under 20% clip → 1 op replayed ; 100-op grid
under 30% clip → 16 ops replayed).

---

### Q4 — `SkDeferredDisplayList` ✅ shipped

**Skia upstream reference** (the public `SkDeferredDisplayList` C++
headers were retired in Skia 4.x ; the modern surface lives in
[`include/private/chromium/`](https://github.com/google/skia/tree/main/include/private/chromium))
:
- `GrDeferredDisplayList.h` — value type holding the recorded ops
  + a `GrSurfaceCharacterization`.
- `GrDeferredDisplayListRecorder.h` — single-shot recorder vending
  an `SkCanvas` and a `detach()` snapshotter.
- `skgpu::ganesh::DrawDDL(SkSurface*, sk_sp<DDL>)` — the
  characterization-gated playback entry point.

**Shipped Kotlin** :
- [`kanvas-skia/src/main/kotlin/org/skia/core/SkSurfaceCharacterization.kt`](kanvas-skia/src/main/kotlin/org/skia/core/SkSurfaceCharacterization.kt)
  — value-type wrapping [SkImageInfo] (width / height / colorType /
  alphaType / colorSpace). Two characterizations with identical
  `imageInfo` are structurally equal. Factories : `Make(imageInfo)`
  and `From(surface)`. Compatibility check via `isCompatibleWith(surface)`.
- [`SkDeferredDisplayList.kt`](kanvas-skia/src/main/kotlin/org/skia/core/SkDeferredDisplayList.kt)
  — immutable `(characterization, SkPicture)` holder. Internal
  `playbackInto(canvas)` delegates to the wrapped picture. Public
  `opCount` for diagnostics.
- [`SkDeferredDisplayListRecorder.kt`](kanvas-skia/src/main/kotlin/org/skia/core/SkDeferredDisplayListRecorder.kt)
  — single-shot recorder wrapping [SkPictureRecorder].
  `getCanvas(): SkCanvas?` returns `null` after `detach()` ; second
  `detach()` throws `IllegalStateException`. Mirrors upstream's
  contract.
- [`SkSurface`](kanvas-skia/src/main/kotlin/org/skia/core/SkSurface.kt)
  gains `draw(ddl): Boolean` — checks the DDL's characterization
  against the surface's `imageInfo()`, plays back if match, returns
  `false` (untouched) on mismatch. Mirrors `skgpu::ganesh::DrawDDL`.

**Why DDL when [SkPicture] already exists** : the difference is the
characterization-locked playback. A DDL refuses to replay onto a
surface whose signature drifted from the recording-time
characterization (different dimensions, colour type, alpha type,
or colour space) ; a picture is surface-agnostic. The lock is the
primitive that lets clients tile a scene, record per-tile DDLs in
parallel ahead of time, and feed each DDL into its dedicated
surface confident no pixel-format mismatch will sneak in.

**Raster scope** — the upstream "thread-safe handoff" advertised
by GPU DDL is nominal in our single-threaded raster pipeline. The
API surface is preserved for compatibility with code that uses
DDL ; the implementation delegates to `SkPicture` verbatim.

**Tests** ([SkDeferredDisplayListTest](kanvas-skia/src/test/kotlin/org/skia/core/SkDeferredDisplayListTest.kt))
— 12 tests :
- Characterization : `Make` rejects empty, `Make` accepts and
  exposes fields, `From` snaps off a surface, structural equality,
  different dimensions → non-equal, `isCompatibleWith` mismatch.
- Recorder : canvas valid before detach + null after, second
  detach throws, opCount reflects ops.
- Playback : success on match (rasterised pixels), failure on
  mismatch (surface untouched), pixel-identical to direct draw,
  same DDL replays onto multiple compatible surfaces, empty DDL
  is a no-op.

**LOC** : ~290 main + ~240 test = **~530 total** (cf. plan
estimate ~400 main ; the surplus covers `SkSurfaceCharacterization`
which the original LOC estimate didn't budget separately).

**Validation** : a DDL recorded against a 40×40 N32-premul
characterization replays bit-identically onto a fresh 40×40
N32-premul surface ; the same DDL rejected (returns `false`) by a
50×50 surface without touching its pixels.

---

### Q5 — Linear sRGB working space (Phase 7e' réinvestigué) ✅ shipped

**Status** : diagnostic completed. **Upstream applies the colour
matrix in encoded sRGB** — the Phase 7e' regression hypothesis was
correct. The 30 % gap on `ColorMatrixGM` (current 69 % vs ratchet
95 %+) is **not** the gamma curve.

**Diagnostic** : the test
[ColorMatrixModeDiagnosticTest](kanvas-skia/src/test/kotlin/org/skia/diagnostics/ColorMatrixModeDiagnosticTest.kt)
samples 9 pixels per cell across the 12-cell ColorMatrixGM matrix
(2 source bitmaps × 6 colour matrices), computes the expected
output for both modes (encoded : `out = matrix × encoded_input` ;
linear : `out = encode(matrix × decode(encoded_input))`), and
sums the per-channel L1 distance against the upstream PNG
reference. Lower Σ = closer to upstream.

**Findings** (full report at `kanvas-skia/build/q5-colormatrix-mode-diagnostic.md`
after running the test) :

| Outcome | Count |
|---|---:|
| **encoded** wins | **5** cells |
| **linear** wins | **0** cells |
| ties (matrix is RGB-identity for the input — saturation × grey, identity matrix) | 7 cells |

**Aggregate Σ over 12 cells** : encoded = 4481, linear = 6332
(linear is 41 % worse).

**Strongest signal** : the `red→α` cells score **encoded Σ = 0 vs
linear Σ = 408 / 548**. That's pixel-identical agreement with
upstream when the encoded interpretation is used — a definitive
proof that upstream is encoded, not linear.

**Implication for the residual gap** : the 30 % delta is **not**
in the gamma curve. Candidate explanations to investigate next :

1. **Sampling precision** — `drawImageRect` uses `kFast`
   constraint by default ; upstream may pick a different sampler
   for `kSrc` blend mode that affects sub-pixel accuracy.
2. **Alpha-channel modulation order** — Skia's pipeline composes
   `paint.alpha × colorFilter × source` ; the order may differ
   slightly when both `paint.color.alpha < 255` and a colour
   filter are present.
3. **Working-space xform timing** — we apply the colour filter in
   sRGB (per Phase 7e) before the Rec.2020 working-space xform.
   Upstream may apply it after, producing small but accumulating
   differences on saturated colours.

Each follow-up is a separate slice ; Q5 itself stays closed
(diagnostic answered its question).

**Stale claim retracted** : the `ColorMatrixTest` kdoc previously
said "Skia evaluates the matrix in linear sRGB ; we still apply
it in encoded sRGB" — incorrect ; both Skia and us apply in
encoded sRGB. The kdoc is updated alongside this slice.

**LOC** : ~290 test (the diagnostic) — no main code change needed
since the encoded path is already correct.

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
                              ├─ B1 SkPDF ❌ descoped (no GM needs PDF)
                              │
                              ├─ B2 SkSVGCanvas (mini ; see MIGRATION_PLAN_SVG.md)
                              │    └─ Self-contained ; text + filters descoped
                              │
                              ├─ Q4 DeferredDisplayList
                              │    └─ Low priority (single-threaded)
                              │
                              └─ C2/C3/C4 misc completions
```

**Recommended ordering for "DM-ready iso" goal**.

✅ **shipped** (struck order matches what landed) :

1. ✅ **D4** Sink architecture (~700 LOC) — D4.1-D4.5 all shipped (PdfSink ❌ descoped per B1).
2. ✅ **Q1** AutoCanvasRestore (~30 LOC) — `withSave` / `withLayer` extension functions plus the Q1.x GM migration.
3. ✅ **C5** ARGB_4444 (~200 LOC, +1 GM bump) — partial (Display P3 / HDR PQ tests reste).
4. ✅ **I1** SkTextBlob (~600 LOC) — I1.1-I1.5.
5. ✅ **D3** image codecs (~1000 LOC) — D3.1 PNG / D3.2 JPEG / D3.3 GIF+BMP+WBMP / D3.4 WEBP / D3.5 encoders / D3.6 `SkImage.encodeToData`.
6. ✅ **I3** SkRegion (~3000 LOC) — I3.1-I3.3 (clipMask Phase 7q remplacé par SkAAClip).
7. ✅ **I5** drawPoints/Atlas/Vertices/Patch (~1000 LOC) — I5.1 / I5.2 / I5.3.a-c / I5.4.
8. ✅ **B2** SkSVGCanvas (~1104 main + ~1351 test) — see [MIGRATION_PLAN_SVG.md](MIGRATION_PLAN_SVG.md) ; mini plan delivered the full 5-slice scope.
9. ✅ **I2** Variable fonts + glyph cache (~700 LOC) — I2.1-I2.3.
10. ✅ **I4** SkShaper (~750 LOC) — I4.1-I4.3 (HarfBuzz parity hors scope).
11. ❌ **B1** SkPDF — descoped (no ported GM needs PDF ; see B1 section).

🔄 **in flight** :

12. 🔄 **D1** SkPathOps (~9000 LOC) — D1.0 + D1.1 ✅ ; D1.2 🔄 (g.* + h.0–h.6.4 + h.8 + h.9.0–h.9.2 shipped — `SkOpBuilder.resolve` chained-Op fallback, pathops GM harvest, `SkParsePath::FromSVGString` ; very active) ; D1.3 📋 piecewise alongside D1.2.h.5.* (Op end-to-end) and D1.2.h.6.* (Simplify end-to-end + AsWinding fast paths).

📋 **remaining** (independent of D1 ; can ship in parallel) :

13. ✅ **C1** Image filters extras (**1501 main + 1473 test across 7 slices**, see [MIGRATION_PLAN_C1_IMAGE_FILTERS.md](MIGRATION_PLAN_C1_IMAGE_FILTERS.md). 22 missing factories shipped ; RuntimeShader descoped on the D2 SkRuntimeEffect dependency. ~30 GM ports unblocked.).
14. ✅ **C3** SkEmbossMaskFilter (~278 main + ~120 delta + ~280 test) — 3-plane mask dispatch via `Sk3DMask` + `SkMaskFilter.Format` ; per-pixel Lambertian + specular lighting.
15. ✅ **Q2** Canvas wrappers (~453 main + ~270 test) — `SkNoDrawCanvas` + `SkPaintFilterCanvas` (abstract) + `SkOverdrawCanvas`.
16. ✅ **Q3** SkBBHFactory + Picture cull (~582 main + ~430 test) — `SkBBoxHierarchy` + `SkRTree` + `SkRTreeFactory` + `SkPictureBoundsBuilder` ; `SkPictureRecorder` builds the BBH from per-op device-space bounds, `SkPicture.playback` queries on sub-rect clips.
17. ✅ **Q5** Linear sRGB diagnostic (~290 test LOC) — diagnosis : upstream applies matrix in encoded sRGB ; gap is elsewhere.
18. ✅ **C2/C4** Misc completions (~505 main + ~400 test) — `Sk1DPathEffect.kMorph` (refactored around a `ContourMeasure` chord-polyline), `kStrokeAndFill_Style` already shipped ; `SkDrawable` + `SkCanvas.drawDrawable` + `SkCanvas.drawAnnotation` no-op slot. **`drawShadow` descoped** (no ported GM uses it).
19. 📋 **D2** SkRuntimeEffect façade + per-effect Kotlin ports (mini-planned ; **~3 700 main + ~2 200 test across 8 slices**, see [MIGRATION_PLAN_D2_RUNTIME_EFFECT.md](MIGRATION_PLAN_D2_RUNTIME_EFFECT.md). Hand-port chaque shader type comme `SkLinearGradient` etc. ; aligned avec la stratégie WGSL côté GPU. Débloque ~80 DEF_GM rows across 13 GM clusters.).
20. ✅ **Q4** DeferredDisplayList (~290 main + ~240 test) — `SkSurfaceCharacterization` + `SkDeferredDisplayList` + `SkDeferredDisplayListRecorder` ; `SkSurface.draw(ddl)` characterization-gated playback. Recording delegates to `SkPicture` infrastructure.
21. ✅ **D1.4** PathOps regression harvest — **shipped (extended + debug pass + pixel parity)** (~530 Python + ~470 Kotlin + ~22 300 JSON + 1 LOC delete dans `SkPathOps.kt`). 335 / 451 upstream fixtures extraites = **74 %**. Survival = **334 / 335 = 99.7 %** (floor 99 %). Pixel parity vs rasteriser-set-op oracle = **320 / 334 = 95.8 %** (floor 90 %). Reste : (a) `cubicOp35d` RETURNED_NULL ; (b) 14 fixtures pixel-divergent (cubic precision + inverse-fill) ; (c) ~30 fixtures non-extraites.

**Total estimated LOC remaining** : ~5 900 of new Kotlin code
(D2 ~3 700 main + ~2 200 test = ~5 900 total ; Q4 + D1.4 (extended)
shipped, everything else shipped or descoped, D1 in-flight LOC
tracked separately under the chantier's own slice budget ; D1.4
follow-ups counted on D1's own budget). Decomposes into ~8 PRs
(D2's 8 sub-slices per its mini plan, plus D1.4 follow-ups
piecewise).

**Total LOC delivered so far** : ~25 800 across the **17 shipped
chantiers** (B2 / C1 / C2 / C3 / C4 / C5 / D3 / D4 / I1 / I2 / I3 /
I4 / I5 / Q1 / Q2 / Q3 / Q4 / Q5) plus the D1.4 extended harness
(~810 main + ~22 300 JSON test data) — close to the 26 000 original
estimate, with B1 (~10 000 LOC) descoped and a few cumulative
overages on C1 lighting / Q3 / C2 / C4 absorbed. D1 is tracked
separately at ~9 000 LOC and currently sits ~60 % through D1.2.

**Estimated time remaining** : 4–8 weeks full-time on D2 (the last
core iso-fidelity item outside D1 — see its mini plan for the 8
sub-slice breakdown), plus 6–15 weeks on D1.4 if prioritised, plus
whatever D1.2 / D1.3 need to close. The "raster pipeline
completion" goal is **down to D1 + D2 + D1.4** — every other
chantier is shipped or formally descoped.

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
├── # pdf/  ❌ descoped       — B1 not created (no GM needs PDF)
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
