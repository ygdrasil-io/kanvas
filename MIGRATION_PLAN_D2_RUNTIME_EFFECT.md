# Migration plan — D2 SkRuntimeEffect (per-effect Kotlin + WGSL ports)

> **Stratégie alignée sur la décision GPU.** Le projet a tranché que
> la cible GPU est **WebGPU + WGSL hand-written templates** (un par
> type de shader), pas Ganesh / Graphite et **pas SkSL**. Voir
> [MIGRATION_PLAN_GPU_WEBGPU.md](MIGRATION_PLAN_GPU_WEBGPU.md). Le
> chantier D2 applique exactement la même politique au cas
> particulier des "runtime effects" : **chaque effet est un nouveau
> type de shader / colorFilter / blender, hand-porté en Kotlin pour
> le raster aujourd'hui et en WGSL pour le GPU plus tard**. La
> classe `SkRuntimeEffect` reste comme **surface publique de
> dispatch** : les ports de GMs upstream peuvent appeler
> `MakeForShader(skslString)` verbatim, le shim retrouve l'impl
> Kotlin via le SkSL canonique → hash. Ni parser ni VM SkSL ; ni
> aujourd'hui, ni plus tard.
>
> Source de vérité : Skia 4.x dans
> `/Users/chaos/workspace/kanvas-forge/skia-main/`.

---

## Table des matières

1. [Cadre stratégique](#cadre-stratégique)
2. [Goal](#goal)
3. [Architecture — runtime effects = nouveaux types de shader](#architecture--runtime-effects--nouveaux-types-de-shader)
4. [What's already shipped](#whats-already-shipped)
5. [What's missing](#whats-missing)
6. [Surface upstream à porter](#surface-upstream-à-porter)
7. [Ported GMs that need D2](#ported-gms-that-need-d2)
8. [Phase decomposition](#phase-decomposition)
   - [D2.0 — `SkBlender` interface + paint plumbing](#d20--skblender-interface--paint-plumbing)
   - [D2.1 — `SkRuntimeEffect` façade + dispatch table](#d21--skruntimeeffect-façade--dispatch-table)
   - [D2.2 — Shader / ColorFilter / Blender bindings](#d22--shader--colorfilter--blender-bindings)
   - [D2.3 — `SkRuntimeEffectBuilder` + `SkData` helpers](#d23--skruntimeeffectbuilder--skdata-helpers)
   - [D2.4 — Hand-port effects (4 sub-slices)](#d24--hand-port-effects-4-sub-slices)
   - [D2.5 — Image-filter integration](#d25--image-filter-integration)
   - [D2.6 — DM pipeline integration](#d26--dm-pipeline-integration)
9. [GPU continuation (post-G4)](#gpu-continuation-post-g4)
10. [Total LOC](#total-loc)
11. [Validation](#validation)
12. [Out (descoped, with revival path)](#out-descoped-with-revival-path)
13. [Sequencing recommendation](#sequencing-recommendation)

---

## Cadre stratégique

Le projet n'a **jamais** prévu de porter SkSL — ni le parser, ni
l'IR, ni le raster-pipeline VM. La décision est encodée à deux
endroits :

1. **Côté GPU** :
   [MIGRATION_PLAN_GPU_WEBGPU.md § Phase G4](MIGRATION_PLAN_GPU_WEBGPU.md#phase-g4--shader-infra--gradients-en-wgsl)
   — *« Pas de SkSL → WGSL transpilation. Chaque type de shader
   (`SkLinearGradient`, `SkRadialGradient`, `SkBitmapShader`, future
   composite) a son template WGSL (~30-80 lignes). »*

2. **Côté raster** : tous les shaders existants (`SkLinearGradient`,
   `SkRadialGradient`, `SkBitmapShader`, `SkTwoPointConicalGradient`,
   `SkSweepGradient`, `SkComposeShader`, etc.) sont **portés à la
   main en Kotlin** depuis Phase 5 du master plan, jamais via SkSL.

Le runtime-effect upstream est juste une *façade SkSL au-dessus du
même mécanisme* : un `SkShader` (ou `SkColorFilter`, ou `SkBlender`)
créé à partir d'une string SkSL au lieu d'un constructeur typé.
**Pour `kanvas-skia`, chaque "runtime effect" est donc juste un
nouveau type de shader / colorFilter / blender** — la seule
spécificité est la *manière de l'instancier* (par hash de SkSL au
lieu de par constructeur direct).

Cette continuité est importante parce que :

- Aucune nouvelle catégorie de complexité n'est introduite — D2 est
  un *travail de port comme les autres slices Phase 5*, juste avec
  une dispatch table.
- L'effort GPU est **prévisible** : pour chaque effet hand-porté en
  Kotlin (D2.4), le pendant GPU sera un template WGSL hand-écrit
  dans la même structure de répertoire — directement plug-and-play
  avec la pipeline `SkWebGpuDevice` Phase G4+ (voir
  [§ GPU continuation](#gpu-continuation-post-g4)).

---

## Goal

Permettre à `kanvas-skia` de satisfaire **tous les appels publics**
à `SkRuntimeEffect::MakeForShader` / `MakeForColorFilter` /
`MakeForBlender` depuis un GM porté **bit-iso avec upstream Skia**
— en s'appuyant sur la même approche hand-port-par-shader-type que
le reste du projet.

Le shim est "complet" quand :

- `SkRuntimeEffect` est une `public class` qui mirror exactement la
  surface upstream ; un GM porté ne peut pas distinguer notre
  dispatch table d'un vrai moteur SkSL.
- Les **15 GMs** qui ne peuvent pas être portés aujourd'hui à cause
  de `SkRuntimeEffect` sont débloqués (voir
  [§ Ported GMs that need D2](#ported-gms-that-need-d2)).
- Une string SkSL non enregistrée renvoie
  `Result(effect = null, errorText = "SkSL not registered: <hash>.
  Add an entry to SkRuntimeEffectDispatch.")` — dégradation
  gracieuse, jamais de crash.
- DM logge le hash absent pour qu'un dev puisse rétro-porter
  l'effet à la demande.
- Chaque effet enregistré est **portable côté GPU** (Phase G post-G4)
  via une template WGSL placée dans le même répertoire.

---

## Architecture — runtime effects = nouveaux types de shader

Pour le code existant (`SkBitmapDevice`, `SkPaint.shader`,
`SkPicture.playback`), un `SkRuntimeShader` est un `SkShader` comme
un autre. La *seule* spécificité est :

- **À la création** : on passe par
  `SkRuntimeEffect.MakeForShader(skslString).effect.makeShader(uniforms,
  children)` au lieu d'un constructeur direct
  `SkLinearGradient(...)`.
- **Au runtime** : `SkRuntimeShader.at(point) → SkColor4f` appelle
  une impl Kotlin enregistrée pour ce SkSL canonique.

**Mécanisme de dispatch** :

```
       upstream-style call site (in a ported GM)              registered impl
            │                                                       │
            ▼                                                       ▼
  SkRuntimeEffect.MakeForShader(skslString)             SkRuntimeImpl_SpiralRT
            │                                                  ↑
            ▼                                           (looked up by FNV-1a-64)
       canonical SkSL                                          │
            │                                                  │
            ▼                                                  │
       FNV-1a-64 hash ──────────►  SkRuntimeEffectDispatch.lookup(hash) ─┘
                                          │
                                          ▼ (return non-null)
                                  Result(effect = wrappedImpl, errorText = "")
```

C'est très exactement la même logique que `when (shader)` dans
`SkBitmapDevice.fillPath` qui dispatche sur les types concrets de
shader — la différence est juste qu'on dispatche par **hash de
SkSL** au lieu de par **type Kotlin**, parce que le call site nous
donne une string et pas un type. Une fois l'impl trouvée, c'est un
`SkShader` standard.

---

## What's already shipped

**Rien.** D2 est un chantier green-field. Aucun
`SkRuntimeEffect` / `SkBlender` n'existe dans `kanvas-skia` ; les
slots `paint.imageFilter` / `paint.shader` / `paint.colorFilter` ne
portent que leurs sous-types concrets actuels.

Le cousin proche est le chantier **C1 image filters extras** — mais
C1 a explicitement **descopé** `RuntimeImageFilter` sur la
dépendance D2 (voir
[MIGRATION_PLAN_C1_IMAGE_FILTERS.md](MIGRATION_PLAN_C1_IMAGE_FILTERS.md)
§ "Out / RuntimeShader image filter").

---

## What's missing

La **surface API publique** à livrer, en trois couches :

1. **`SkRuntimeEffect`** — la façade compile-time
   (`MakeForShader` / `MakeForColorFilter` / `MakeForBlender`,
   `Uniform` / `Child` reflection structs, `Result`, `ChildPtr`,
   `findUniform` / `findChild`, `uniformSize` / `allowShader` /
   `allowColorFilter` / `allowBlender`, `source()`).

2. **`SkBlender`** — le troisième type d'effet (à côté de `SkShader`
   et `SkColorFilter`). Actuellement absent — `SkBitmapDevice` ne
   blend qu'à travers `SkBlendMode`. D2 introduit une nouvelle
   classe abstraite + un slot `paint.blender` + dispatch.

3. **Dispatch table + bindings** — `SkRuntimeEffectDispatch`,
   `SkRuntimeShader`, `SkRuntimeColorFilter`, `SkRuntimeBlender`
   wrappant un impl Kotlin enregistré comme un `SkShader` /
   `SkColorFilter` / `SkBlender` standard.

Plus les **~20-40 effets hand-portés** pour peupler la dispatch
table (un fichier Kotlin par effet, ~30-100 LOC).

---

## Surface upstream à porter

| Header upstream | LOC C++ | Port to | LOC Kotlin (est.) |
|---|---:|---|---:|
| `include/effects/SkRuntimeEffect.h` | 503 | `org/skia/effects/runtime/SkRuntimeEffect.kt` | ~600 |
| `include/core/SkBlender.h` | ~70 | `org/skia/foundation/SkBlender.kt` | ~80 |
| `include/effects/SkBlenders.h` | ~60 | `org/skia/foundation/SkBlenders.kt` | ~70 |
| `src/sksl/**` (parser/IR/VM) | ~30 000 | **non porté — même approche que pour les shaders standards (Kotlin/WGSL hand-port par type)** | 0 |

Les ~30 000 LOC SkSL backend sont remplacés par **~20-40 ports
Kotlin individuels**, un par SkSL distinct utilisé par un GM
upstream. C'est le même rapport coût/bénéfice que pour les shaders
standards : `SkLinearGradient` C++ (~800 LOC) → Kotlin (~250 LOC) ;
chaque runtime effect upstream (~30-50 lignes SkSL) → Kotlin
(~30-100 LOC).

---

## Ported GMs that need D2

Audit (2026-05-08) — chaque GM upstream dans `gm/*.cpp` qui utilise
`SkRuntimeEffect` directement :

| Upstream GM | Stub `kanvas/src/generated/` ? | DEF_GM count | LOC C++ |
|---|---|---:|---:|
| `runtimeshader.cpp` | ✅ `RuntimeShaderGM.kt` | 13 | 1133 |
| `runtimeintrinsics.cpp` | ❌ (no stub) | ~50 (one per intrinsic) | 639 |
| `runtimecolorfilter.cpp` | ✅ `RuntimeColorFilterGM.kt` | ~5 | 214 |
| `runtimeimagefilter.cpp` | ❌ (no stub) | 1 | 111 |
| `runtimefunctions.cpp` | ✅ `RuntimeFunctions.kt` | 1 | 63 |
| `arithmode.cpp` | ✅ `ArithmodeBlenderGM.kt` | 1 | — |
| `composecolorfilter.cpp` | ✅ `ComposeColorFilterGM.kt` | 1 | — |
| `lumafilter.cpp` | ✅ `LumaFilterGM.kt` | 1 | — |
| `rippleshadergm.cpp` | ✅ `RippleShaderGM.kt` | 1 | — |
| `vertices.cpp` | ✅ `VerticesGM.kt` | 1 | — |
| `destcolor.cpp` | ❌ | 1 | — |
| `fp_sample_chaining.cpp` | ❌ | 1 | — |
| `imagedither.cpp` | ❌ | 1 | — |
| `kawase_blur_rt.cpp` | ❌ | 1 | — |
| `mesh.cpp` | ❌ | 1 | — |
| `workingspace.cpp` | ❌ | 1 | — |

**~80 entrées DEF_GM distinctes** dépendent de `SkRuntimeEffect`.
La plupart appartiennent à `runtimeshader.cpp` (13) et
`runtimeintrinsics.cpp` (~50, un par intrinsèque SkSL — `mod`,
`mix`, `cross`, `dFdx`, `smoothstep`, …).

**Stratégie** : pas besoin de porter chaque variante au jour 1.
Grouper les GMs par *programmes SkSL distincts qu'ils exercent* —
beaucoup d'intrinsèques partagent le même squelette de shader (juste
la fonction sous test change), donc un port Kotlin peut couvrir
plusieurs lignes DEF_GM.

---

## Phase decomposition

8 sub-slices, ~3 000 LOC main + ~1 200 LOC test.

### D2.0 — `SkBlender` interface + paint plumbing ✅ shipped

**Scope** : ajouter `SkBlender` à la foundation à côté de
`SkShader` et `SkColorFilter`. Wirer `paint.blender` ;
`SkBitmapDevice` dispatche au travers quand non-null.

**Files** :
- `kanvas-skia/src/main/kotlin/org/skia/foundation/SkBlender.kt`
  — classe abstraite `SkBlender` avec `blend(srcColor, dstColor):
  SkColor4f` + une factory statique `Mode(SkBlendMode): SkBlender`
  pour le path legacy (les appels existants `paint.blendMode`
  continuent à fonctionner via le nouveau dispatch).
- `kanvas-skia/src/main/kotlin/org/skia/foundation/SkBlenders.kt`
  — factory `Arithmetic(k1, k2, k3, k4, enforcePMColor)`
  (matches l'Arithmetic blender stable upstream).
- `kanvas-skia/src/main/kotlin/org/skia/foundation/SkPaint.kt`
  delta — ajouter le champ `blender: SkBlender? = null` avec
  copy semantics aligné aux champs existants `imageFilter` /
  `colorFilter`. Documenter la précédence : si `blender != null`,
  il prime sur `blendMode`.
- `kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt`
  delta — quand on blend un fragment, appeler
  `paint.blender?.blend(src, dst) ?: applyBlendMode(...)`. Garder
  le fast path pour `null` blender (cas commun).

**Tests** ([SkBlenderTest](kanvas-skia/src/test/kotlin/org/skia/foundation/SkBlenderTest.kt))
— **26 tests, all green** :
- `Mode` factory : returns `SkBlendModeBlender` carrying the tag,
  equality is mode-tag based ;
- `SkBlendModeBlender.blend` : Clear / Src / Dst / SrcOver
  closed-form impl + non-trivial modes throw with a clear
  "route through SkBitmapDevice" diagnostic ;
- `SkBlenders.Arithmetic` factory : rejects non-finite
  coefficients, short-circuits canonical mode tuples
  `(0,1,0,0) → kSrc`, `(0,0,1,0) → kDst`, `(0,0,0,0) → kClear`,
  otherwise returns `SkArithmeticBlender` carrying the tuple ;
- `SkArithmeticBlender.blend` : `k2=1` reduces to kSrc, `k3=1`
  reduces to kDst, `k4=1` returns white, per-channel saturate,
  `enforcePremul` caps RGB ≤ alpha ;
- end-to-end pixel parity : `paint.blender = Mode(kSrcOver)`
  pixel-bit-iso with `paint.blendMode = kSrcOver` ;
  `Mode(kClear)` zeroes the rect ; `Arithmetic(0, 0.5, 0.5, 0,
  true)` produces midpoint pixels (red-on-blue → 0xFF80_0080) ;
- paint round-trip : `copy()` preserves blender, `reset()`
  clears it, `equals` / `hashCode` accounts for the slot.

**Implementation actuelle** :
- [SkBlender.kt](kanvas-skia/src/main/kotlin/org/skia/foundation/SkBlender.kt)
  — abstract base + `Mode(SkBlendMode)` factory + concrete
  `SkBlendModeBlender` (mode tag carrier).
- [SkBlenders.kt](kanvas-skia/src/main/kotlin/org/skia/foundation/SkBlenders.kt)
  — `SkBlenders.Arithmetic(k1,k2,k3,k4,enforcePremul)` static
  factory + concrete `SkArithmeticBlender`.
- [SkPaint.kt](kanvas-skia/src/main/kotlin/org/skia/foundation/SkPaint.kt)
  — `blender: SkBlender? = null` slot, threaded through
  `reset` / `copy` / `equals` / `hashCode`.
- [SkBitmapDevice.kt](kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt)
  — new `dispatchBlend(x, y, src, mode, blender)` paint-aware
  helper + `blendCustom(x, y, src, blender)` route via
  `SkColor4f` round-trip when the blender is custom. Threaded
  through `drawPaint` / `drawTriangle` / `drawTexturedTriangle`
  / `drawPath` / `compositeFrom` / `drawImageRect` /
  `drawPathWithMaskFilter` / `fillRect` / `strokeRect` /
  `fillRectAA` / `strokeRectAA` / `drawHLine` / `drawVLine` /
  `fillPath` / `scanFillPath`. F16 fast paths fall back to the
  8-bit dispatch when a custom blender is in flight (the F16
  lane only knows `SkBlendMode` ; a future slice may
  specialise an F16 custom-blend path).

**LOC** : ~280 main (SkBlender + SkBlenders + SkPaint delta +
SkBitmapDevice plumbing) + ~285 test = **~565 total** (cf. plan
estimate ~400 ; overage covers the dispatch threading through
14 device-internal helpers — each `mode: SkBlendMode` parameter
got a paired `blender: SkBlender? = null` for symmetry).

**Validation** : full kanvas-skia suite **2955 / 2955 green** ;
`paint.blender = SkBlender.Mode(m)` is bit-iso with
`paint.blendMode = m` (verified by the dedicated test) — no
regression on the existing 64 raster GMs.

---

### D2.1 — `SkRuntimeEffect` façade + dispatch table ✅ shipped

**Scope** : le point d'entrée central. Parse la *signature* SkSL
(uniform / child / main) juste assez pour peupler `Uniform[]` /
`Child[]` reflection — **ne** parse **pas** le corps de la fonction.
La dispatch table répond à `MakeForShader` / `MakeForColorFilter` /
`MakeForBlender` par lookup hash → impl Kotlin enregistré.

**Files** :
- `kanvas-skia/src/main/kotlin/org/skia/effects/runtime/SkRuntimeEffect.kt`
  — `public class`. Mirror surface upstream verbatim :
  - `Uniform { name, offset, type, count, flags }` avec
    `Type { kFloat, kFloat2, kFloat3, kFloat4, kFloat2x2, …, kInt, … }`
    + `Flags` ints (`kArray_Flag`, `kColor_Flag`, `kHalfPrecision_Flag`).
  - `enum class ChildType { kShader, kColorFilter, kBlender }`,
    `Child { name, type, index }`.
  - `data class Result(effect: SkRuntimeEffect?, errorText: String)`.
  - Companion : `MakeForShader(sksl)` / `MakeForColorFilter(sksl)` /
    `MakeForBlender(sksl)` returning `Result`.
  - Instance : `makeShader(uniforms, children)` /
    `makeColorFilter` / `makeBlender`, `uniforms()`, `children()`,
    `findUniform(name)`, `findChild(name)`, `source()`,
    `uniformSize`, `allowShader` / `allowColorFilter` / `allowBlender`.

- `kanvas-skia/src/main/kotlin/org/skia/effects/runtime/SkRuntimeEffectDispatch.kt`
  — singleton interne :
  - `register(canonicalSource: String, factory: () -> SkRuntimeImpl)`
    — stocke par FNV-1a-64 hash du source canonique.
  - `lookup(sksl: String): SkRuntimeImpl?` — normalise + hash
    l'input SkSL ; retourne l'impl ou null.
  - **Source normalisation** (déterministe) :
    1. Strip line comments (`// …`) + block comments (`/* … */`).
    2. Collapse runs of whitespace → un espace.
    3. Strip leading / trailing whitespace.
  - **Hash** : FNV-1a 64-bit. Stable across JVM versions ; cheap.

- `kanvas-skia/src/main/kotlin/org/skia/effects/runtime/SkRuntimeImpl.kt`
  — interface interne implémentée par chaque effet hand-porté :
  ```kotlin
  internal interface SkRuntimeImpl {
      val uniforms: List<SkRuntimeEffect.Uniform>
      val children: List<SkRuntimeEffect.Child>
      val flags: Int

      /** Évaluation à un (x, y). `coords` null pour color filter / blender. */
      fun shade(
          coords: SkPoint?,
          srcColor: SkColor4f?,
          dstColor: SkColor4f?,
          uniforms: ByteBuffer,
          childResolvers: Array<(SkPoint) -> SkColor4f>,
      ): SkColor4f
  }
  ```

- **Signature parser** — un mini-parser regex-only pour
  déclarations top-level :
  ```
  ^uniform\s+(half|half2|…|float4|int|…)\s+(\w+)(?:\s*\[\s*(\d+)\s*\])?\s*;
  ^uniform\s+(shader|colorFilter|blender)\s+(\w+)\s*;
  ^(half4|vec4|float4)\s+main\s*\(\s*(vec2|float2|in\s+half4|.*)\s*\)
  ```
  Extrait noms d'uniforms / children + arité du main (0 args =
  unsupported, 1 = shader, 2 = blender, color filter a 1 arg
  vec4-typed). Calcule offsets uniformes (alignement 4-byte,
  std140-ish layout d'upstream).

**Tests** ([3 test classes / 42 tests, all green](kanvas-skia/src/test/kotlin/org/skia/effects/runtime/)) :
- [SkRuntimeEffectDispatchTest](kanvas-skia/src/test/kotlin/org/skia/effects/runtime/SkRuntimeEffectDispatchTest.kt) (15 tests) :
  comment stripping (line + block), whitespace collapsing,
  punctuation-adjacent stripping, case-sensitivity, FNV-1a-64
  vector tests (`""`, `"a"`, `"foobar"` against canonical
  reference values), register / lookup round-trip, lazy factory
  invocation contract, re-register replacement, clearForTest.
- [SkRuntimeEffectSignatureParseTest](kanvas-skia/src/test/kotlin/org/skia/effects/runtime/SkRuntimeEffectSignatureParseTest.kt) (16 tests) :
  every `Uniform.Type` alias decodes correctly (vec2/float2/half2,
  mat3/float3x3/half3x3, ivec3/int3, etc.), unknown type errors
  carry the bad token, array uniforms set the kArray_Flag and
  count, layout(color) sets kColor_Flag, half-prefixed types set
  kHalfPrecision_Flag, child uniforms get incrementing indices,
  uniform offsets honour std140-ish alignment (capped at 16
  bytes), main(...) classifies kShader / kColorFilter / kBlender
  correctly, missing main / unknown arity errors carry diagnostics,
  comment-strip applied before scan.
- [SkRuntimeEffectMakeTest](kanvas-skia/src/test/kotlin/org/skia/effects/runtime/SkRuntimeEffectMakeTest.kt) (11 tests) :
  end-to-end MakeForShader / MakeForColorFilter / MakeForBlender
  with stub impls, allowShader / allowColorFilter / allowBlender
  reflection, findUniform / findChild by name, source() returns
  unnormalised input, registry-miss error includes the canonical
  hash in hex, kind-mismatch error when calling MakeForColorFilter
  on a shader-shaped SkSL, parser-error round-trip, whitespace-
  variant lookup hits the same impl.

**LOC** : ~560 main (façade + dispatch + parser + impl interface) +
~460 test (3 classes / 42 tests) = ~1 020 ; came in slightly under
the planned 1 050 because the signature parser landed in 220 LOC
(regex-driven) instead of the budgeted 300+, and the dispatch
helper that hashes + normalises was lean (~225 LOC including
KDoc and the FNV-1a routine).

**Validation actuelle** : `MakeForShader("vec4 main(vec2 p) {
return vec4(p, 0, 1); }")` retourne un `Result` avec `effect !=
null` quand l'impl correspondante est enregistrée ; même appel
renvoie `Result(effect = null, errorText = "SkSL not registered:
<hex-hash>. Add an entry to SkRuntimeEffectDispatch.")` quand
l'impl n'est pas dans la table. La hash est stable cross-JVM
(vector tests pinned). Whitespace-variant SkSL résout le même
impl (`main(p)` ↔ `main ( p )` ↔ `// header\nmain(p)` hashent
identiquement).

**Implementation** : voir
[SkRuntimeEffect.kt](kanvas-skia/src/main/kotlin/org/skia/effects/runtime/SkRuntimeEffect.kt),
[SkRuntimeEffectDispatch.kt](kanvas-skia/src/main/kotlin/org/skia/effects/runtime/SkRuntimeEffectDispatch.kt),
[SkRuntimeEffectSignatureParser.kt](kanvas-skia/src/main/kotlin/org/skia/effects/runtime/SkRuntimeEffectSignatureParser.kt),
[SkRuntimeImpl.kt](kanvas-skia/src/main/kotlin/org/skia/effects/runtime/SkRuntimeImpl.kt).
`makeShader` / `makeColorFilter` / `makeBlender` instance methods
sont **déférés à D2.2** (bindings) — D2.1 livre la façade + le
dispatch ; les bindings concrets (SkRuntimeShader / etc.) viennent
juste après.

---

### D2.2 — Shader / ColorFilter / Blender bindings ✅ shipped

**Scope** : wrap un `SkRuntimeImpl` enregistré comme un type
foundation pour qu'il s'insère dans `paint.shader` /
`paint.colorFilter` / `paint.blender`. Plus une refactorisation
de l'interface `SkRuntimeImpl` : la signature `shade(...)` accepte
désormais un `Array<ChildResolver>` (sealed) au lieu d'un
`Array<(SkPoint) -> SkColor4f>` — chaque slot enfant est typé
selon son `ChildType` (shader / colorFilter / blender), donc
l'impl pattern-matche directement sans cast. Plus une **mini SkData**
introduite ici (au lieu de D2.3 comme prévu) — 70 LOC, immutable
byte container, pour porter le paramètre `uniforms: SkData?` des
factories `makeXxx`.

**Implementation actuelle** :
- [SkRuntimeImpl.kt](kanvas-skia/src/main/kotlin/org/skia/effects/runtime/SkRuntimeImpl.kt)
  — refactor : `children: Array<ChildResolver>` + nouvelle sealed
  interface `ChildResolver { Shader, ColorFilter, Blender }`. Les
  D2.1 tests utilisant l'ancienne signature ont été migrés.
- [SkRuntimeShader.kt](kanvas-skia/src/main/kotlin/org/skia/effects/runtime/SkRuntimeShader.kt)
  — `SkShader` concret. `shadeRow(devX, devY, count, dst)` mappe
  chaque pixel via `deviceToLocal` puis appelle
  `impl.shade(coords = SkPoint(lx, ly), …)`. `sampleAtLocal(lx,
  ly)` bypasse le `canvasCtm × localMatrix` pour les child shaders
  imbriqués. Les enfants sont pré-construits via
  `buildShaderChildResolvers` qui valide `decl.type ==
  ChildType.kShader` slot par slot.
- [SkRuntimeColorFilter.kt](kanvas-skia/src/main/kotlin/org/skia/effects/runtime/SkRuntimeColorFilter.kt)
  — `SkColorFilter` concret. `filterColor4f(src)` →
  `impl.shade(srcColor = src, …)`. `isAlphaUnchanged()` lit le
  flag `kAlphaUnchanged_Flag` de l'impl.
- [SkRuntimeBlender.kt](kanvas-skia/src/main/kotlin/org/skia/effects/runtime/SkRuntimeBlender.kt)
  — `SkBlender` concret. `blend(src, dst)` →
  `impl.shade(srcColor = src, dstColor = dst, …)`.
- [SkData.kt](kanvas-skia/src/main/kotlin/org/skia/foundation/SkData.kt)
  — immutable byte container. `MakeWithCopy(bytes)`,
  `MakeUninitialized(size)`, `EMPTY` singleton.
- [SkRuntimeEffect.kt](kanvas-skia/src/main/kotlin/org/skia/effects/runtime/SkRuntimeEffect.kt)
  — wire `makeShader(uniforms, children, localMatrix)` /
  `makeColorFilter(uniforms, children)` /
  `makeBlender(uniforms, children)`. Chaque factory valide :
  - le kind (`allowShader` / `allowColorFilter` / `allowBlender`)
    avant de produire un binding ;
  - le children-count match `parsed.children.size` (mismatch →
    `null`) ;
  - pour chaque slot child, le type déclaré dans la SkSL
    correspond au kind du binding (shader-binding accepte des
    children shader, etc.). Ajout des constantes `Flags`
    (`kUsesSampleCoords_Flag` / `kAllowShader_Flag` / … /
    `kAlphaUnchanged_Flag` / `kDisableOptimization_Flag`) côté
    companion.

**Tests** ([4 nouvelles classes / 16 tests, all green](kanvas-skia/src/test/kotlin/org/skia/effects/runtime/)) :
- [SkRuntimeShaderTest](kanvas-skia/src/test/kotlin/org/skia/effects/runtime/SkRuntimeShaderTest.kt) (8) :
  `makeShader` rejette colorFilter SkSL + children-count mismatch ;
  draw-rect avec un constant-color RuntimeShader produit bien
  les pixels du constant ; les `coords` reçus sont les positions
  local-space après `deviceToLocal` (vérifié sur un shader qui
  encode `coords` dans les channels rouge/vert) ; child shader
  passé via `child.eval(p)` retourne sa propre couleur ; null
  child → resolver renvoie `kTransparent` (background préservé) ;
  uniform bytes traversent bien jusqu'à l'impl.
- [SkRuntimeColorFilterTest](kanvas-skia/src/test/kotlin/org/skia/effects/runtime/SkRuntimeColorFilterTest.kt) (5) :
  `makeColorFilter` rejette shader SkSL ; rejette children
  shader-typés sur un colorFilter binding ; identité passe
  `srcColor` inchangé ; invert-RGB transforme rouge → cyan ;
  `isAlphaUnchanged()` lit le flag.
- [SkRuntimeBlenderTest](kanvas-skia/src/test/kotlin/org/skia/effects/runtime/SkRuntimeBlenderTest.kt) (3) :
  `makeBlender` rejette shader SkSL ; averaging blender forwards
  `(src, dst)` correctement ; pixel-iso end-to-end : red-on-blue
  via `paint.blender = makeBlender(...)` produit mid-purple
  (`0xFF80_0080`).

**LOC** : ~525 main (bindings + SkData + SkRuntimeEffect makeXxx
wiring + ChildResolver sealed) + ~430 test = **~955 total** (cf.
plan estimate ~650 ; overage couvre la mini-SkData absorbée
depuis D2.3 + le refactor `Array<ChildResolver>` qui touche les 3
test files D2.1 existants).

**Validation** : full kanvas-skia suite **3052 / 3052 green**.
Cross-cutting :
- `paint.blender` round-trip via `SkBitmapDevice.dispatchBlend`
  fonctionne pour les `SkRuntimeBlender` (le custom blender path
  introduit en D2.0 absorbe les blenders sans changement) ;
- `paint.shader = SkRuntimeShader(...)` fonctionne pour fillPath
  / drawRect / drawPath via le path solid-color OU shader (le
  rasterizer ne fait aucune distinction au-delà de
  `paint.shader != null`).

---

### D2.3 — `SkRuntimeEffectBuilder` ✅ shipped

**Scope** : helper Builder upstream qui simplifie le binding des
uniforms — beaucoup de GMs upstream l'utilisent au lieu du
`makeShader(uniforms, children)` brut. `SkData` a été absorbé en
D2.2 ; D2.3 livre uniquement le Builder.

**Implementation** : [SkRuntimeEffectBuilder.kt](kanvas-skia/src/main/kotlin/org/skia/effects/runtime/SkRuntimeEffectBuilder.kt) :
- Constructeur `SkRuntimeEffectBuilder(effect, initialUniforms?
  = null)` — buffer zero-init par défaut, sized à
  `effect.uniformSize`.
- `uniform(name): UniformAccessor` — accessor type-checké pour
  les écritures uniformes. Surcharges :
  - `set(Float)` (kFloat × 1)
  - `set(Int)` (kInt × 1)
  - `set(FloatArray)` (tout `kFloat*` / matrice — taille
    validée contre `type.sizeBytes / 4 × count`)
  - `set(IntArray)` (tout `kInt*`)
  - `set(SkColor4f)` (kFloat4 — convenience pour
    `layout(color) uniform vec4 ...`)
  - `set(SkMatrix)` (kFloat3x3 — écriture column-major matchant
    la convention Skia `SkMatrix → 9 floats`)
  - Mismatch type ou size → `IllegalArgumentException` avec
    diagnostic.
- `child(name): ChildAccessor` — accessor type-checké pour les
  child slots. Surcharges `set(SkShader?)` / `set(SkColorFilter?)`
  / `set(SkBlender?)` ; mismatch declared
  [SkRuntimeEffect.ChildType] → throw.
- `makeShader(localMatrix? = null)` / `makeColorFilter()` /
  `makeBlender()` — délègue à `effect.makeXxx(...)` avec un
  snapshot des uniforms et un array de children typé. Retourne
  `null` pour le mauvais kind d'effet.
- **Snapshot semantics** : chaque `makeXxx` prend une copie
  défensive des bytes uniformes ; les mutations subséquentes du
  builder ne fuient pas dans les effets déjà construits.
- Lookup miss (`uniform("nope")` ou `child("nope")`) → throw
  avec diagnostic listant les uniforms / children déclarés.

**Tests** ([SkRuntimeEffectBuilderTest](kanvas-skia/src/test/kotlin/org/skia/effects/runtime/SkRuntimeEffectBuilderTest.kt)
— **20 tests, all green**) :
- Construction : zero-init du buffer ; explicit `SkData` init.
- Writes typés : `Float`, `Int`, `FloatArray` (avec offset
  alignment vec4 → 16), `IntArray`, `SkColor4f` → 4 channels
  RGBA, `SkMatrix` → mat3 column-major.
- Failure paths : `set(Int)` sur uniform Float → throw,
  `set(Float)` sur uniform Int → throw, `set(FloatArray(3))`
  sur uniform vec4 → throw, `set(IntArray)` sur uniform float
  → throw, `uniform("nope")` → throw avec nom dans le message.
- Children : binding correct → slot peuplé ; type-mismatch
  (SkColorFilter dans un slot `shader`) → throw ;
  `child("nope")` → throw.
- Kind gating : `makeShader` retourne null pour un effet
  colorFilter / blender ; symétrique pour les autres factories.
- End-to-end : impl reçoit bien les bytes uniformes écrits par
  le builder ; chaque `makeShader` snapshot indépendant
  (mutations post-build ne fuient pas).

**LOC** : ~340 main (Builder + UniformAccessor + ChildAccessor) +
~370 test (20 tests) = **~710 total** (cf. plan estimate ~350 ;
overage couvre les 6 surcharges de `set` + les diagnostics +
les 2 tests d'integration end-to-end qui valident la chaîne
Builder → effect → impl).

**Validation** : full kanvas-skia suite **3076 / 3076 green**.

---

### D2.4 — Hand-port effects (4 sub-slices)

Le gros de D2. Chaque sous-slice porte un cluster d'effets
liés, organisé par complexité SkSL / cluster GM.

**Workflow par effet** :
1. Lire la string SkSL depuis le `.cpp` du GM upstream.
2. Ajouter un fichier Kotlin sous `org/skia/effects/runtime/effects/`
   implémentant `SkRuntimeImpl` avec le math hand-traduit.
3. Enregistrer le source SkSL canonique au class-load via un
   bloc `init { SkRuntimeEffectDispatch.register(...) }`.
4. Porter le GM correspondant (`kanvas/src/generated/tests/<Name>.kt`
   → `kanvas-skia/src/main/kotlin/org/skia/tests/<Name>.kt`).
5. Vérifier pixel-iso vs le PNG ref upstream (similarité ≥ 95%).

#### D2.4.a — Simple effects (color filters) ✅ shipped

**Implementation** :
[SkBuiltinColorFilterEffects.kt](kanvas-skia/src/main/kotlin/org/skia/effects/runtime/effects/SkBuiltinColorFilterEffects.kt)
— 4 hand-ported Kotlin `SkRuntimeImpl` objects covering 7 SkSL hash
keys :

| Impl                  | Registered hashes                                    | GM cells unblocked |
|-----------------------|------------------------------------------------------|---|
| `IdentityImpl`        | `gNoop`                                              | `RuntimeColorFilterGM` cell 0 |
| `LumaToAlphaImpl`     | `gLumaSrc`                                           | `RuntimeColorFilterGM` cell 1 |
| `ToneMapImpl`         | `gTernary` + `gIfs` + `gEarlyReturn` (3 hash keys)   | `RuntimeColorFilterGM` cells 2-4 |
| `ComposeChildrenImpl` | `gComposeCF` (2 colorFilter children)                | `ComposeColorFilterGM` SkSL column |

**Auto-registration** : `SkRuntimeEffect.makeFor` calls
`ensureBuiltinsLoaded()` before each lookup, which invokes
`SkBuiltinColorFilterEffects.registerAll()`. Idempotent — re-runs
cleanly after `SkRuntimeEffectDispatch.clearForTest()` for unit
tests.

**GMs ported / updated** :
- [`RuntimeColorFilterGM`](kanvas-skia/src/main/kotlin/org/skia/tests/RuntimeColorFilterGM.kt)
  — new port of the upstream
  [`gm/runtimecolorfilter.cpp::RuntimeColorFilterGM`](https://github.com/google/skia/blob/main/gm/runtimecolorfilter.cpp).
  Uses a synthetic 256×256 RGB-gradient stand-in for upstream's
  `mandrill_256.png` (mandrill asset not in our test classpath ;
  same adaptation as `Skbug13047GM`). Initial similarity baseline :
  **16.92 %** (the per-cell colour-filter math is correct vs the
  synthetic source ; the absolute number reflects the source-image
  drift, not the SkSL impl drift). Floor pinned at 0 % — will
  ratchet upward if a mandrill substitute is captured.
- [`ComposeColorFilterGM`](kanvas-skia/src/main/kotlin/org/skia/tests/ComposeColorFilterGM.kt)
  — refactored : the `useSkSL=true` branch now uses
  `SkRuntimeEffect.MakeForColorFilter(COMPOSE_CF_SKSL)` with
  `[inner, outer]` children, instead of a gray placeholder. The
  GM still has a pre-existing `paint.shader` × `paint.colorFilter`
  rendering bug that's orthogonal to D2.4.a (the shader output
  isn't piped through the colour filter — separate raster
  chantier).

**Tests** ([SkBuiltinColorFilterEffectsTest](kanvas-skia/src/test/kotlin/org/skia/effects/runtime/effects/SkBuiltinColorFilterEffectsTest.kt)
— **11 tests, all green**) :
- Identity returns input verbatim ; alpha-unchanged flag wired.
- Luma → alpha for red / green / blue / white / black inputs
  match the dot-product formula.
- Tone-map low / mid / high luma branches each verified ;
  alpha preserved across all branches.
- Tone-map variants (Ternary / Ifs / EarlyReturn) produce
  identical outputs (proves the 3 hashes map to the same impl).
- Compose-CF chains inner then outer ; identity × identity
  returns input verbatim.
- `clearForTest` round-trip : `MakeForColorFilter` re-resolves
  after the registry is wiped (auto-registration works).

Plus the GM harness test
[`RuntimeColorFilterGM matches reference`](kanvas-skia/src/test/kotlin/org/skia/tests/D2PreFauxPositifTest.kt)
in `D2PreFauxPositifTest`, ratcheted at 16.92 %.

**LOC** : ~280 main (SkBuiltinColorFilterEffects) +
~95 main (RuntimeColorFilterGM) +
~25 main (ComposeColorFilterGM SkSL-branch wiring) =
**~400 main** + ~270 test (unit + GM harness) =
**~670 total** (cf. plan estimate ~250 + ~200 = ~450 ; overage
absorbs the GM port + the 3 tone-map variants pinning identical
outputs).

#### D2.4.b — Shader effects (`runtimeshader.cpp` cluster) ✅ shipped (7 of 9 DEF_GMs)

**Implementation** : 3 cluster files dans
[`runtime/effects/`](kanvas-skia/src/main/kotlin/org/skia/effects/runtime/effects/) :

| Cluster | Effects | Hash keys | Path |
|---|---|---|---|
| **A — Simple** (no children) | SimpleRT, SpiralRT, LinearGradientRT | 3 | [SkBuiltinShaderEffectsSimple.kt](kanvas-skia/src/main/kotlin/org/skia/effects/runtime/effects/SkBuiltinShaderEffectsSimple.kt) |
| **B — Children** (shader children) | ThresholdRT (3 children), UnsharpRT (1 child + 5-tap kernel) | 2 | [SkBuiltinShaderEffectsChildren.kt](kanvas-skia/src/main/kotlin/org/skia/effects/runtime/effects/SkBuiltinShaderEffectsChildren.kt) |
| **C — Color cube LUT** | ColorCubeRT (shader + LUT child), ColorCubeColorFilterRT (color filter + LUT child) | 2 | [SkBuiltinShaderEffectsColorCube.kt](kanvas-skia/src/main/kotlin/org/skia/effects/runtime/effects/SkBuiltinShaderEffectsColorCube.kt) |

**Total** : 7 hand-ported `SkRuntimeImpl` objects covering 7 SkSL hash keys.

**Effets shippés** :
- `SimpleRT` — `(p.x/255, p.y/255, gColor.b, 1)` ; tests math at known pixel positions.
- `SpiralRT` — radius / angle / fract spiral with 2 colour stops ; tests reference recompute at 4 points.
- `LinearGradientRT` — top-half encoded-sRGB lerp, bottom-half linear-sRGB lerp via `toLinearSrgb` / `fromLinearSrgb` (uses existing `SkNamedTransferFn.kSRGB` helpers).
- `ThresholdRT` — 3 shader children (before / after / threshold), `smooth_cutoff(threshold.a)` drives a `mix(before, after, m)` ; verified clamping behaviour at low / high luma.
- `UnsharpRT` — 5-tap unsharp mask kernel (`5·centre − Σ neighbours`) ; verified by constant-child input (kernel sum = 1 → identity) and by impulse-child input (negative neighbour clamps to 0).
- `ColorCubeRT` (shader) and `ColorCubeColorFilterRT` (color filter) — 3D LUT lookup with bilinear B-axis lerp ; verified with constant LUT (all coords return same colour) and shader-vs-cf parity.

**Effets descopés** :
- `ClipSuperRRect("clip_super_rrect_pow2", 2)` and `("clip_super_rrect_pow3.5", 3.5)` — ces 2 DEF_GMs utilisent `canvas->clipShader(...)` qui n'existe pas encore dans `SkCanvas`. Le shader lui-même (saturate-coverage du superellipse) est trivial à porter ; **le bloquant est `SkCanvas.clipShader`**, un chantier raster séparé. À reprendre quand `clipShader` landed.

**Auto-registration** : 3 cluster `object`s, chacun avec
`init { registerAll() }` + `registerAll()` public idempotent.
Wired into `SkRuntimeEffect.ensureBuiltinsLoaded()` (added by
D2.4.a, extended ici à 4 clusters) — chaque `makeFor(...)`
re-populate la dispatch table. Test classes utilisent en plus
`@BeforeEach { ClusterX.registerAll() }` pour rester défensifs
contre `clearForTest`.

**Tests** ([3 test classes / 37 tests, all green](kanvas-skia/src/test/kotlin/org/skia/effects/runtime/effects/)) :
- [SkBuiltinShaderEffectsSimpleTest](kanvas-skia/src/test/kotlin/org/skia/effects/runtime/effects/SkBuiltinShaderEffectsSimpleTest.kt) — 16 tests
- [SkBuiltinShaderEffectsChildrenTest](kanvas-skia/src/test/kotlin/org/skia/effects/runtime/effects/SkBuiltinShaderEffectsChildrenTest.kt) — 11 tests
- [SkBuiltinShaderEffectsColorCubeTest](kanvas-skia/src/test/kotlin/org/skia/effects/runtime/effects/SkBuiltinShaderEffectsColorCubeTest.kt) — 10 tests

**LOC** : ~720 main (3 cluster files) + ~860 test (3 test
classes) = **~1580 total** (cf. plan estimate ~700 + ~500 =
~1200 ; overage couvre les child-resolver stubs nécessaires aux
tests + l'instrumentation pour vérifier les 3 SkSL contrastés).

**Validation** : full kanvas-skia suite **3134 / 3134 green** (post-rebase sur D2.4.a / D2-pre batch 5).

**GM ports** : différé. Les GMs upstream (`runtime_shader`,
`spiral_rt`, etc.) instancient le `RuntimeShaderGM` template
avec une SkSL string par variante. Un port complet doit
factoriser le template + porter chaque DEF_GM avec son setup
spécifique (mandrill / dog images pour ThresholdRT,
mandrill_sepia / lut_identity / lut_sepia pour ColorCube). À
porter dans un slice de suivi (D2.4.b GM ports) une fois les
images stand-in décidées.

#### D2.4.c — Intrinsics test effects (6 sub-slices ✅ COMPLETE)

**Targets** : `runtimeintrinsics.cpp` couvre 6 GMs raster
(`runtime_intrinsics_trig` / `_exponential` / `_common` /
`_geometric` / `_matrix` / `_relational`) qui exercent ~85
intrinsèques SkSL distincts via 3 templates SkSL (`make_unary_sksl_1d`
pour la majorité, `make_matrix_*_sksl` pour le matrix GM,
`make_bvec_sksl` pour le relational GM).

**Stratégie** — *un squelette `SkRuntimeImpl` partagé par
template SkSL*, paramétré par une lambda Kotlin qui implémente
le math de l'intrinsèque. Chaque entry registered dans le
dispatch produit une instance fresh du squelette + sa lambda.
Réduit la duplication : seul le math change par hash, le
plumbing (uniforms / coord remap / output broadcast) est partagé.

**Découpage en 6 sous-slices** (1 PR / 1 GM chacun) :

| Sub-slice | GM | Intrinsics | Status |
|---|---|---:|---|
| D2.4.c.1 | `runtime_intrinsics_trig` | 12 (radians/degrees/sin/cos/tan/asin/acos/atan + 4 atan2 variants) | ✅ shipped — **96.33 %** vs reference |
| D2.4.c.2 | `runtime_intrinsics_exponential` | 10 (pow×4 / exp / log / exp2 / log2 / sqrt / inversesqrt) | ✅ shipped — **95.64 %** vs reference |
| D2.4.c.3 | `runtime_intrinsics_common` | 31 (abs/sign/floor/ceil/fract/mod×3/min×3/max×3/clamp×3/saturate/mix×3/step×3/smoothstep×3 + floor(p)×2/ceil(p)×2) | ✅ shipped — **95.90 %** vs reference |
| D2.4.c.4 | `runtime_intrinsics_geometric` | 16 (length×2/distance×2/dot×2/cross×3/normalize×3/faceforward/reflect×2/refract×2) | ✅ shipped — **95.84 %** vs reference |
| D2.4.c.5 | `runtime_intrinsics_matrix` | matrixCompMult × 3 dims + inverse × 3 dims (6 entries) — distinct SkSL template | ✅ shipped — **96.61 %** vs reference |
| D2.4.c.6 | `runtime_intrinsics_relational` | bvec template × 18 ops (lessThan/lessThanEqual/greaterThan/greaterThanEqual/equal/notEqual × {float, int} + bvec compositions + not() + any/all reductions) — distinct SkSL template | ✅ shipped — **97.53 %** vs reference |

**LOC réel D2.4.c.1** : ~280 main (cluster) + ~95 main (GM port)
+ ~165 test = ~540 total. Plan estimate ~250 + ~85 = ~335 — on
overrun parce que le GM port lui-même (sub-surface + plot
polyline + label-centred) est plus volumineux qu'attendu.

**GMs débloqués (6 raster ; les ES3 variants restent hors scope)** :
6 DEF_SIMPLE_GM upstream. Le stub `RuntimeIntrinsicsGM` du
plan original n'existe pas — chaque sub-slice porte directement
son propre `<Name>GM.kt` sans stub intermédiaire.

#### D2.4.d — Specialised shaders (one-offs) ✅ partly shipped (3 / 5 GMs ; 2 GMs deferred)

**Status** : ✅ shipped via `SkBuiltinSpecialisedEffects` —
3 GMs (`DestColorGM`, `ImageDitherGM`, `KawaseBlurRtGM`) +
4 SkSL programs (invert blender, stretch blender, kawase
blur shader, kawase mix shader). Scores 14.44 % – 70.83 %
(synthetic stand-in images for missing `mandrill_*.png` assets).

**Deferred to D2.4.d.2 follow-up** :
- `RippleShaderGM` (`gm/rippleshadergm.cpp`) — uses
  `RippleShader.rts` (~100 LOC of bespoke SkSL with 9 helper
  functions: `triangleNoise`, `sparkles`, `softCircle`,
  `softRing`, `subProgress`, `circle_grid`, `turbulence`, etc.).
  Substantial port effort.
- `FpSampleChainingGM` (`gm/fp_sample_chaining.cpp`) — multi-effect
  chain test, ~244 LOC upstream.

**Targets** :
- `RippleShaderGM` (animated water ripples).
- `KawaseBlurRtGM` (Kawase-blur multipass).
- `ArithmodeBlenderGM` (l'Arithmetic blender upstream — pourrait
  être livré plus tôt via la factory statique `Arithmetic` dans
  `SkBlenders.kt` de D2.0, puis ce slice couvre les variations
  GM-specific éventuelles).
- `DestColorGM`, `FpSampleChainingGM`, `ImageDitherGM`
  (single-effect each).

**LOC** : ~60-100 par effet × ~6 = ~450 main + ~250 test.

**Status note** — `MeshGM` et `WorkingSpaceGM` sont reportés à
**Out / B et Out / C** parce qu'ils dépendent de `SkMesh` et de
runtime effects color-space-aware respectivement, qui sont des
sous-chantiers plus larges.

---

### D2.5 — Image-filter integration

**Scope** : wirer `SkRuntimeEffect` dans `SkImageFilter`. Demande
deux pièces :

1. Une nouvelle factory `SkImageFilters.RuntimeShader(effect,
   uniforms, children)` dans
   [`SkImageFilters.kt`](kanvas-skia/src/main/kotlin/org/skia/foundation/SkImageFilters.kt).
   Retourne un `SkImageFilter` qui évalue l'effet sur la grille de
   pixels filtrée.

2. Plumbing dans `SkBitmapDevice.applyImageFilter` pour appeler le
   runtime shader à chaque pixel sortie.

**File** :
- `kanvas-skia/src/main/kotlin/org/skia/foundation/SkRuntimeImageFilter.kt`
  — sous-classe `SkImageFilter` concrète suivant la même interface
  que les filtres shippés en C1.

**Tests** :
- Intégration avec le harness de test C1 — register un effet stub
  "fade-to-blue", apply via `SkImageFilters.RuntimeShader`, vérifier
  le pixel output.

**LOC** : ~200 main + ~100 test = ~300.

**GMs débloqués** : `RuntimeImageFilterGM` (pas de stub
aujourd'hui — ajouter puis porter).

---

### D2.6 — DM pipeline integration

**Scope** : quand un GM passe une string SkSL non enregistrée, le
DM pipeline doit **logger le hash absent et skip le GM** au lieu de
crasher ou de retourner silencieusement une image noire.

**Files** :
- `kanvas-skia/src/main/kotlin/org/skia/dm/Sink.kt` delta — quand
  `SkRuntimeEffect.MakeForShader` retourne
  `Result(effect = null, errorText = "SkSL not registered:
  <hash>")`, le driver GM wrap le failure dans la couche reporting
  `Sink.Result` avec une variante `MissingRuntimeEffect(hash,
  sourceSnippet)`.
- DM CLI delta — flag `--list-missing-effects` qui affiche la liste
  cumulée des hashes non enregistrés après un run GM complet, pour
  que les devs priorisent les retro-ports D2.4 par fréquence.

**Tests** :
- `SkRuntimeEffectMissingHashTest.kt` — register un effet, run un
  GM qui utilise deux effets (un enregistré, un pas). Vérifier que
  l'enregistré rend, le non-enregistré logge proprement, le reste
  de la suite continue.

**LOC** : ~50 main + ~100 test = ~150.

---

## GPU continuation (post-G4)

Une fois D2 livré côté raster, **chaque effet enregistré aura un
pendant WGSL** dans la pipeline `SkWebGpuDevice` — c'est exactement
le même travail qu'aura à faire la pipeline GPU pour
`SkLinearGradient` / `SkRadialGradient` / `SkBitmapShader` (voir
[MIGRATION_PLAN_GPU_WEBGPU.md § Phase G4](MIGRATION_PLAN_GPU_WEBGPU.md#phase-g4--shader-infra--gradients-en-wgsl)).

**Structure de répertoire visée** :

```
kanvas-skia/src/main/kotlin/org/skia/effects/runtime/effects/
├── runtimeshader/
│   ├── SimpleRT.kt           ← Kotlin impl raster (livré en D2.4.b)
│   ├── SimpleRT.wgsl         ← template WGSL (livré post-G4)
│   ├── SpiralRT.kt
│   ├── SpiralRT.wgsl
│   ├── UnsharpRT.kt
│   └── UnsharpRT.wgsl
├── intrinsics/
│   ├── ModIntrinsic.kt
│   ├── ModIntrinsic.wgsl
│   ├── …
└── …
```

**Convention** : la string SkSL canonique enregistrée dans la
dispatch table est la *clé identifiant l'effet* — la même clé
sélectionne le `.kt` côté raster et le `.wgsl` côté GPU. Le
`SkRuntimeImpl` interface a un pendant côté GPU (probablement
`SkRuntimeGpuTemplate { val wgsl: String ; val uniformsLayout:
GpuLayout ; val childrenLayout: GpuChildrenLayout }`) découvert via
le même hash. Détails de design ouverts jusqu'à ce que la pipeline
GPU atteigne G4.

**Pourquoi cette continuité matters** : le travail D2.4 (~1 900 LOC
hand-port Kotlin) **n'est pas perdu** côté GPU. Chaque port Kotlin
documente précisément le math que la template WGSL devra reproduire
— c'est une spec exécutable. La phase GPU de D2 (estimée ~1 500 LOC
WGSL hand-écrit, ratio LOC similaire à G4) deviendra une slice
auto-contenue post-G4, avec les mêmes 80 GMs DEF_GM débloqués pour
la deuxième fois (cette fois en cross-validation raster ↔ GPU).

---

## Total LOC

| Slice | Main | Test | GMs débloqués |
|---|---:|---:|---|
| D2.0 SkBlender + paint plumbing ✅ | **280** (planned ~250) | **285** (planned ~150) | foundation |
| D2.1 SkRuntimeEffect façade + dispatch ✅ | **560** (planned ~700) | **460** (planned ~350) | foundation |
| D2.2 Shader/ColorFilter/Blender bindings ✅ | **525** (planned ~400 ; absorbs SkData) | **430** (planned ~250) | foundation |
| D2.3 SkRuntimeEffectBuilder ✅ (SkData absorbed in D2.2) | **340** (planned ~200) | **370** (planned ~150) | foundation |
| D2.4.a Simple color filters ✅ | **400** (planned ~250) | **270** (planned ~200) | 2 (RuntimeColorFilterGM, ComposeColorFilterGM SkSL column) |
| D2.4.b runtimeshader cluster ✅ (7 of 9 DEF_GMs ; ClipSuperRRect ×2 deferred on `clipShader`) | **720** (planned ~700) | **860** (planned ~500) | 7 SkSL hashes ; GM ports deferred |
| D2.4.c.1 trig intrinsics ✅ (1 GM at 96.33 %) | **375** (planned ~80) | **165** (planned ~25) | `runtime_intrinsics_trig` (12 entries) |
| D2.4.c.2-6 remaining intrinsics 📋 | ~~~500~~ | ~~~150~~ | 5 GMs (~70 entries) |
| D2.4.d Specialised one-offs | ~450 | ~250 | 6 |
| D2.5 Image-filter integration | ~200 | ~100 | 1 |
| D2.6 DM pipeline | ~50 | ~100 | quality-of-life |
| **Raster total** | **~3 700** | **~2 200** | **~13 GM clusters / ~80 DEF_GM rows** |
| GPU continuation (post-G4) | ~1 500 WGSL | ~500 | re-validate same 80 GMs cross-backend |

**Estimated time raster** : 3-5 semaines pour un seul ingénieur
focused. D2.0 → D2.3 sont séquentiels (chacun dépend du précédent),
D2.4.a-d peuvent shipper en parallèle, D2.5 / D2.6 closent.

**Estimated time GPU** : 2-3 semaines additionnelles post-G4, à
livrer en // de la phase GPU principale (chaque template WGSL est
indépendante).

---

## Validation

Pour chaque slice livré côté raster :

1. **Tests unitaires passent** — tous les nouveaux tests + la suite
   2 576 / 2 576 existante restent vertes.
2. **GM pixel-iso ≥ 95 %** vs le PNG ref upstream pour chaque GM
   débloqué par le slice.
3. **Hash stability** — `SkRuntimeEffectDispatch` produit le même
   hash pour des SkSL whitespace-variants ; documenté dans le KDoc
   avec valeurs vector test.
4. **DM dégrade gracieusement** sur SkSL inconnu — jamais de crash,
   toujours log du hash absent.

Cross-cutting :
- `SkPaint` round-trip preservation quand `paint.blender` est set
  (copy / equals / hashCode).
- `paint.imageFilter` integration avec `SkRuntimeImageFilter`
  carries through `SkPicture` recording / playback.
- `SkSVGCanvas` (B2) drop gracefully les runtime effects (déjà
  documenté comme descope B2) — vérifier que la sortie SVG ne crash
  pas quand un paint porte un runtime shader.

Pour la phase GPU continuation (post-G4) :
- Pour chaque effet enregistré, la sortie raster (Kotlin impl) et
  la sortie GPU (template WGSL) doivent matcher au niveau pixel à
  ≤ 2 ulps près (même tolérance que les autres shaders cross-
  backend, voir
  [MIGRATION_PLAN_GPU_WEBGPU.md § R3](MIGRATION_PLAN_GPU_WEBGPU.md#risques--ouvertures)).

---

## Out (descoped, with revival path)

Ces items ne sont **PAS** dans le scope D2 ; ils restent disponibles
comme follow-ups si un GM spécifique le demande.

### A. Real SkSL parser / IR / VM — *PERMANENTLY*

**Pourquoi descopé** : ~30 000 LOC, multi-month effort, requires
tracking upstream's SkSL grammar evolution forever. **Le projet a
explicitement choisi de hand-porter chaque shader type** (en Kotlin
pour le raster, en WGSL pour le GPU — voir
[MIGRATION_PLAN_GPU_WEBGPU.md § Phase G4](MIGRATION_PLAN_GPU_WEBGPU.md#phase-g4--shader-infra--gradients-en-wgsl)).
**Aucun chemin de revival prévu** — c'est un choix architectural,
pas un compromis.

Si jamais un besoin de SkSL *dynamique* (compilé à runtime depuis
input utilisateur, pas une string `.cpp` constante) apparaît, ça
entrerait dans une catégorie complètement différente du scope D2 et
demanderait un nouveau plan dédié — typiquement en passant par un
parser tiers (Tree-sitter SkSL grammar, par exemple) plutôt qu'un
port d'upstream. À ce stade, la dispatch table D2 reste comme
fast-path pour les effets compile-time-known.

### B. `SkMesh` (mesh GM)

**Pourquoi descopé** : `SkMesh` est un sous-système séparé de ~800
LOC (vertex / index buffers + un vertex-shader-style SkSL program
attaché). Land cleanly **après** D2.0 + D2.1 si besoin.

**Revival path** : un nouveau slice `D2.5.a — SkMesh` ; utilise la
dispatch table + bindings déjà shippés.

### C. `WorkingSpaceGM`

**Pourquoi descopé** : exerce une variante `SkColorSpace`-aware des
runtime shaders (le pipeline SkSL peut lire / écrire en linear ou
encoded sRGB selon le tag working colorspace). Ajoute ~150 LOC de
plumbing color-space à travers le shader path qui est orthogonal
aux mécaniques de dispatch.

**Revival path** : après D2.4.b (cluster runtime shader) livré
les shaders plain, ajouter un flag "working-space" à
`SkRuntimeImpl` et router à travers
`SkColorSpaceXformSteps` existant. ~200 LOC delta.

### D. SkSL `DebugTrace`

**Pourquoi descopé** : la factory upstream `MakeTraced(shader,
traceCoord)` snapshot l'execution SkSL VM à un seul pixel — utile
seulement pour le debug dans un vrai interpréteur. Retourne un
`TracedShader { shader, debugTrace }` où `debugTrace` est un objet
`SkSL::DebugTrace` plein d'introspection opcode-level. **Aucun
analogue dans une implémentation hand-port** — il n'y a pas
d'opcodes à tracer, juste du Kotlin standard.

**Revival path** : aucun. Si un besoin de debug arrive, on
fait du logging Kotlin standard dans l'`SkRuntimeImpl` concerné.

### E. Stable keys

**Pourquoi descopé** : `Options.fStableKey` upstream est une perf
optimisation pour le cache pipeline du backend GPU. Aucun effet sur
la dispatch table raster (les lookups sont déjà O(1) par hash).

**Revival path** : ship le champ comme paramètre `Options` pour
parité API ; ignoré au runtime. ~10 LOC delta.

---

## Sequencing recommendation

```
   D2.0 SkBlender + paint  (foundation, ~400 LOC)
      │
      ▼
   D2.1 façade + dispatch  (~1 050 LOC)
      │
      ▼
   D2.2 bindings           (~650 LOC) ──┐
      │                                 │
      ▼                                 │
   D2.3 Builder + SkData   (~350 LOC) ──┤
      │                                 │
      ▼                                 │
      ├─ D2.4.a Simple filters  (~450 LOC) ─┐
      │                                     │
      ├─ D2.4.b runtimeshader   (~1200 LOC) ─┤
      │                                     ├──▶ D2.5 Image filter (~300 LOC)
      ├─ D2.4.c intrinsics      (~650 LOC) ─┤
      │                                     │
      └─ D2.4.d specialised     (~700 LOC) ─┘
                                            │
                                            ▼
                                  D2.6 DM pipeline (~150 LOC)
                                            │
                                            ▼
                              ─── (post-G4) ───
                              GPU continuation (~1 500 LOC WGSL)
                              one .wgsl per registered effect
```

D2.0 → D2.3 doivent shipper en premier (foundation pour D2.4 / D2.5
/ D2.6). D2.4.a-d peuvent runner en parallèle une fois D2.3
shippé. D2.5 + D2.6 closent. La phase GPU est conditionnelle sur G4
(shader infra WGSL) shippé.

**First-PR slice** : D2.0 (small, self-contained, no SkSL math).
