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

### D2.0 — `SkBlender` interface + paint plumbing

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

**Tests** :
- `SkBlenderTest.kt` — `Mode(kSrcOver)` matche bit-pour-bit le
  legacy ; `Arithmetic(0, 0, 0, 1)` retourne dst constant ;
  `Arithmetic(0, 1, 0, 0)` retourne src ; null blender → fall back
  to `paint.blendMode`.

**LOC** : ~250 main + ~150 test = ~400.

**Validation** : `XfermodesGM` continue à passer (legacy path
inchangé) ; `Arithmetic(...)` round-trip à travers paint copy.

---

### D2.1 — `SkRuntimeEffect` façade + dispatch table

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

**Tests** :
- `SkRuntimeEffectDispatchTest.kt` — register / lookup hit ;
  lookup miss → null ; whitespace-variant hashe pareil ; comment-
  strip OK ; FNV-1a vector test contre une référence connue.
- `SkRuntimeEffectSignatureParseTest.kt` — reconnaît chaque
  `Uniform.Type` ; rejette `vec5` malformé ; uniform offsets
  corrects ; child types `shader` / `colorFilter` / `blender`
  parsés avec leurs indices.

**LOC** : ~700 main + ~350 test = ~1 050.

**Validation** : `MakeForShader("vec4 main(vec2 p) { return
vec4(p, 0, 1); }")` retourne un `Result` avec `effect != null`
quand l'impl correspondante est enregistrée ; même appel renvoie
`Result(effect = null, errorText = "SkSL not registered:
<hash>")` quand l'impl n'est pas dans la table.

---

### D2.2 — Shader / ColorFilter / Blender bindings

**Scope** : wrap un `SkRuntimeImpl` enregistré comme un type
foundation pour qu'il s'insère dans `paint.shader` /
`paint.colorFilter` / `paint.blender`.

**Files** :
- `kanvas-skia/src/main/kotlin/org/skia/effects/runtime/SkRuntimeShader.kt`
  — sous-classe concrète `SkShader`. `at(point: SkPoint)` appelle
  `impl.shade(coords = point, …)` avec les uniforms / child
  resolvers liés.
- `kanvas-skia/src/main/kotlin/org/skia/effects/runtime/SkRuntimeColorFilter.kt`
  — sous-classe concrète `SkColorFilter`. `apply(SkColor4f)`
  appelle `impl.shade(coords = null, srcColor = input, …)`.
- `kanvas-skia/src/main/kotlin/org/skia/effects/runtime/SkRuntimeBlender.kt`
  — sous-classe concrète `SkBlender`. `blend(src, dst)` appelle
  `impl.shade(coords = null, srcColor = src, dstColor = dst, …)`.

Chaque binding contient :
- La référence `SkRuntimeImpl`.
- Un snapshot `ByteBuffer uniforms` pris à la construction (defensive
  copy du `SkData` du caller).
- Un `Array<SkChild>` de children résolus (`SkShader` pour les
  slots child shader, `SkColorFilter` / `SkBlender` pour les
  autres). Chaque child est converti en lambda `(SkPoint) ->
  SkColor4f` passée au `shade(...)` interne.

**Tests** :
- `SkRuntimeShaderTest.kt` — register un effet stub qui retourne
  `vec4(0.5, 0.0, 0.0, 1.0)`, build un shader, drawRect →
  pixel = 0xFF800000.
- `SkRuntimeColorFilterTest.kt` — register un filtre "invert
  RGB", apply à un paint rouge → output vert.
- `SkRuntimeBlenderTest.kt` — register un blender "average",
  draw red-on-blue → output mid-purple.

**LOC** : ~400 main + ~250 test = ~650.

---

### D2.3 — `SkRuntimeEffectBuilder` + `SkData` helpers

**Scope** : le helper Builder upstream qui simplifie le binding
des uniforms — beaucoup de GMs l'utilisent au lieu du
`makeShader(uniforms, children)` brut. Plus le foundation
`SkData` (wrapper byte-buffer immutable) si pas déjà présent.

**Files** :
- `kanvas-skia/src/main/kotlin/org/skia/foundation/SkData.kt`
  (uniquement si absent — quick check d'abord ; déjà shippé via
  D3 / D4 peut-être).
- `kanvas-skia/src/main/kotlin/org/skia/effects/runtime/SkRuntimeEffectBuilder.kt`
  — class avec accessors nommés :
  ```kotlin
  public class SkRuntimeEffectBuilder(public val effect: SkRuntimeEffect) {
      public fun uniform(name: String): UniformAccessor
      public fun child(name: String): ChildAccessor
      public fun makeShader(localMatrix: SkMatrix? = null): SkShader?
      public fun makeColorFilter(): SkColorFilter?
      public fun makeBlender(): SkBlender?
  }
  ```
  Chaque `uniform("name") = floatArrayOf(...)` écrit les bytes
  dans le buffer interne au bon offset / type-checké contre le
  record `Uniform` reflection.

**Tests** :
- `SkRuntimeEffectBuilderTest.kt` — set uniforms par nom → bytes
  layout corrects ; type mismatch (écrire 3 floats dans un
  uniform vec4) throw ; `makeShader()` retourne un shader
  fonctionnel.

**LOC** : ~200 main + ~150 test = ~350.

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

#### D2.4.a — Simple effects (color filters, no children)

**Targets** (~5-8 effets de `runtimecolorfilter.cpp` +
`runtimefunctions.cpp`) :
- Identity color filter (`vec4 main(vec4 c) { return c; }`).
- Channel swap (`return c.bgra;`).
- Brightness multiply (`return c * uniformBrightness;`).
- Les 1-2 effets dans `runtimefunctions.cpp` (free-standing
  helpers + un `main`).

**LOC** : ~30-50 par effet × ~6 = ~250 main + ~200 test (un GM
par effet).

**GMs débloqués** : `RuntimeColorFilterGM`, `RuntimeFunctions`,
`LumaFilterGM`, `ComposeColorFilterGM` (les cellules runtime-effect).

#### D2.4.b — Shader effects (`runtimeshader.cpp` cluster)

**Targets** : les 13 DEF_GM de `runtimeshader.cpp` :
- `SimpleRT`, `ThresholdRT`, `SpiralRT`, `UnsharpRT`,
  `ColorCubeRT`, `ColorCubeColorFilterRT`, `ClipSuperRRect`
  (×2), `LinearGradientRT`, plus 2-3 variantes child-using.

Chaque a un fichier Kotlin dans
`org/skia/effects/runtime/effects/runtimeshader/`. Le math va
de trivial (`SimpleRT` = solid color) à non-trivial
(`UnsharpRT` = kernel unsharp-mask 5×5 appelant `child.eval(...)`).

**LOC** : ~50-100 par effet × 13 = ~700 main + ~500 test.

**GMs débloqués** : `RuntimeShaderGM` (chaque variante).

#### D2.4.c — Intrinsics test effects

**Targets** : `runtimeintrinsics.cpp` exerce ~50 intrinsèques
SkSL (`mod`, `mix`, `cross`, `dFdx`, `smoothstep`, `packFloat`,
`clamp`, `min`, `max`, `length`, `normalize`, `dot`, `reflect`,
`refract`, `faceforward`, …).

Chaque test rend une grille 256×256 où chaque pixel applique
l'intrinsèque à une fonction de `(x, y)`. Le port Kotlin est
**un helper par intrinsèque** (~10-30 LOC chacun) plus un seul
squelette `SkRuntimeImpl` "render-grid" partagé.

**LOC** : ~500 main (50 × 10 LOC) + ~150 test = ~650.

**GMs débloqués** : `RuntimeIntrinsicsGM` (besoin d'un stub —
absent de `kanvas/src/generated/tests/` aujourd'hui, donc
ajouter le stub + porter).

#### D2.4.d — Specialised shaders (one-offs)

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
| D2.0 SkBlender + paint plumbing | ~250 | ~150 | foundation |
| D2.1 SkRuntimeEffect façade + dispatch | ~700 | ~350 | foundation |
| D2.2 Shader/ColorFilter/Blender bindings | ~400 | ~250 | foundation |
| D2.3 SkRuntimeEffectBuilder + SkData | ~200 | ~150 | foundation |
| D2.4.a Simple color filters | ~250 | ~200 | 4 |
| D2.4.b runtimeshader cluster | ~700 | ~500 | 1 (13 variants) |
| D2.4.c intrinsics test effects | ~500 | ~150 | 1 (~50 variants) |
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
