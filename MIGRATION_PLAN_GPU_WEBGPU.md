# GPU backend via WebGPU — plan de divergence

> **Statut.** Plan séparé du master [MIGRATION_PLAN.md](MIGRATION_PLAN.md). La ligne « **GPU** (`org.skia.gpu.*`, Ganesh, Graphite). Stripper les hooks GPU de la `GM` base class. » de la section *Explicitement reporté* est **remplacée** par ce plan.
>
> **Décision stratégique.** On **ne porte pas** Ganesh ni Graphite. Le module `kanvas/src/generated/gpu/` (534 fichiers Kt-stubs) reste en lecture seule comme curiosité historique. À la place, on construit un device GPU autonome ciblant **WebGPU** via [`wgpu4k/wgpu4k`](https://github.com/wgpu4k/wgpu4k) — le wrapper Kotlin Multiplatform idiomatique au-dessus de `wgpu4k-native` (lui-même un binding sur wgpu-rs/Mozilla). Format de shader : **WGSL** (au lieu de SkSL).
>
> Plan vivant — cocher au fur et à mesure.

## Pourquoi diverger

1. **Ganesh & Graphite sont énormes.** ~534 fichiers générés rien que pour `org.skia.gpu.*`, plus toute la pipeline Skia compiler (SkSL → SPIR-V/MSL/HLSL/GLSL). Le coût de portage est sans commune mesure avec le ratio de tests GM débloqués.
2. **Skia upstream lui-même migre vers Graphite** ; Ganesh est en sunset. Refaire Ganesh en Kotlin = courir après une cible morte ; refaire Graphite = courir après une cible mouvante non stabilisée.
3. **Une seule API GPU moderne suffit.** WebGPU couvre Vulkan / Metal / D3D12 / OpenGL via Dawn ou wgpu-native, et le navigateur via l'API standard. Un seul shader-language (WGSL), un seul model de pipeline. Pas besoin du multi-backend abstrait de Skia.
4. **Kotlin-friendly.** wgpu4k expose `Adapter` / `Device` / `Queue` / `ShaderModule` / `RenderPipeline` / `BindGroup` / `Buffer` / `Texture` en API idiomatique Kotlin (KMP), avec backings JVM (Windows/Linux/macOS via wgpu-native), JS/WASM (browser WebGPU natif), et Native (expérimental Android/iOS). Pas de glue JNI à écrire — wgpu4k l'enrobe déjà.
5. **WGSL au lieu de SkSL.** SkSL est un langage Skia-interne avec son propre compilateur (~30k LOC de C++). WGSL est standardisé, simple à parser/écrire, et n'a pas besoin de transpilation amont — on le génère ou on l'écrit à la main.

## Scope

**In :**
- Nouveau module séparé `kanvas-skia-gpu` (ou submodule `kanvas-skia/gpu/`) parallèle au raster device.
- Implémentation d'une `SkDevice` (nouvelle interface extraite de `SkBitmapDevice`) backée par WebGPU.
- Pipeline de rendu primitives : rect AA, path tessellé, image, gradient.
- Shaders WGSL hand-written ou générés (paint `color`, gradient linear/radial, image+sampling, blend modes).
- Cross-test : chaque GM exécutable sur les deux backends, comparé à la même référence `original-888/`.

**Out (consciemment) :**
- ❌ Tout port de `org.skia.gpu.*` (Ganesh / Graphite generated stubs).
- ❌ Tout port du compilateur SkSL (`org.skia.sksl.*` côté generated).
- ❌ SkVM / SkRasterPipeline GPU-side optimizations.
- ❌ Compute-shader path tessellation (Graphite-style). On commence CPU-tessellate.
- ❌ Multi-sampling MSAA agressif (4x raster soft AA reste la baseline parité).

## Choix techniques à acter avant Phase G0

### D1 — Backing wgpu4k ✅ (acté)

**Décision** : utiliser [`io.github.wgpu4k:wgpu4k`](https://github.com/wgpu4k/wgpu4k). C'est un wrapper Kotlin idiomatique KMP, pas juste une spec en interfaces — il enrobe déjà `wgpu4k-native` (lui-même un binding sur wgpu-rs Mozilla). Pas de glue JNI/Panama à écrire.

**Conséquences** :
- **Pas besoin de webgpu-ktypes en direct** : wgpu4k l'utilise probablement en interne (même org), mais on consomme l'API haut niveau.
- **Cible KMP** : wgpu4k est multiplatform (JVM/JS/Native). On commence en **JVM-only** (cohérent avec le reste de `kanvas-skia` aujourd'hui), JS/Native disponibles gratuitement si on convertit le module en KMP plus tard.
- **Maturité** : wgpu4k est en **Beta v0.1.1** (cf. [README](https://github.com/wgpu4k/wgpu4k)). Risque de breaking changes — voir R1 plus bas.
- **Action préalable G0** : récupérer les coordonnées Maven exactes (Maven Central ou GitHub Packages) et la version stable la plus récente. Pinner cette version.

### D2 — Path tessellation : CPU d'abord, migration GPU planifiée ✅ (acté)

**Décision** : commencer **CPU** en Phase G3, **migrer vers GPU compute** en Phase G8 si le profiling le justifie.

| Approche | Phase | Avantages | Inconvénients |
|---|---|---|---|
| **CPU tessellate (libtess2-like ou ear-clip)** | G3 (initial) | Port direct de `SkBitmapDevice.buildEdges` existant ; testable unitairement ; coût d'entrée faible | Re-tessellate à chaque draw, bande passante CPU↔GPU |
| **GPU compute tessellation (Graphite-style)** | G8 (migration) | Pas de transfert CPU↔GPU à chaque draw, scaling | Compute shaders, allocation GPU dynamique, port ambitieux |
| **Stencil & cover (Ganesh-style)** | reconsidéré en G8 | Pas de tessellation pure ; stencil pass + cover pass | Stencil buffer, math winding en WGSL, deux passes |

Phase G8 est ajoutée explicitement à la trajectoire (voir plus bas) avec ses **triggers de profiling** définis : on ne migre pas par esthétique, on migre quand la mesure montre que la tessellation CPU sature.

### D3 — Working colorspace GPU : aligné sur le raster, **modifiable plus tard** ✅ (acté)

**Décision initiale** : working space identique au raster — **F16 linear-Rec.2020** côté render target, présent pass final qui convertit en Rec.2020-encoded 8-bit pour matcher les références `original-888/`.

**Pourquoi commencer aligné** :
- **Cross-validation simple** : raster et GPU doivent produire des images bit-équivalentes (à ~2 ulps près) pour les mêmes GMs. Si on diverge sur le working space dès G0, on perd la possibilité de diff pixel-à-pixel pour debug.
- **Réutilisation directe** : `SkColorSpaceXformSteps` côté CPU pré-calcule les transformations de stops gradient et de pixels bitmap dans la working space (Phase 5/6 raster). Les mêmes données alimentent les uniforms shader sans recalcul.
- **Tests inchangés** : les ratchets ratchet contre `original-888/` (PNG en Rec.2020 encoded). Le présent-pass produit du Rec.2020 encoded peu importe la working space interne — le test compare l'output final.

**Est-ce modifiable plus tard ? OUI.** Le working space n'est **pas hardcodé dans l'architecture** — il est cantonné à 3 emplacements bien définis :

| Point de vérité | Côté | Effort de changement |
|---|---|---|
| **Format de la render target** (`rgba16float` linear-Rec.2020) | GPU | Changer le `TextureFormat` à la création de `SkWebGpuDevice` |
| **Pré-transformation des inputs** (gradient stops, bitmap pixels) | CPU dans `SkShader.setupForDraw` | Déjà paramétré par `SkColorSpaceXformSteps` — passer une autre destination space |
| **Présent-pass final** (linear-Rec.2020 → output encoding) | GPU (fragment quad ou compute) | Recalculer les coefficients de la matrice de transfert |

Aucun shader template (gradient, bitmap, blend) ne hardcode "Rec.2020". Ils reçoivent juste des couleurs **premul-linear** dans la working space courante.

**Ce qui change si on diverge plus tard** (par exemple migration vers linear-sRGB) :
- ✅ **Tests vs références `original-888/`** : continuent à fonctionner — l'output final reste Rec.2020-encoded.
- ⚠️ **Cross-validation raster ↔ GPU bit-équivalente** : casse — il faudrait soit aligner aussi le raster, soit augmenter la tolérance.
- ⚠️ **Drift sur les opérations dépendantes du working space** : le lerp gradient en linear-sRGB produit des intermédiaires différents qu'en linear-Rec.2020 (cf. post-mortem Phase 5h du master plan). Précédent à ne pas oublier.

**Trigger pour rouvrir D3** : si un GM montre un score < 95% en GPU **alors que** le raster passe à ≥ 99%, et que le diagnostic isole le working space comme cause dominante (vs sampling, vs précision FP32 fragment).

---

## Phase G0 — Bootstrap module GPU ✅

**But** : un module qui compile, dépend de wgpu4k, instancie un `GPUAdapter` / `GPUDevice` "headless", et rend un quad full-screen rouge dans une render target offscreen lue en RAM.

### Build & dépendances
- [x] ~~Créer `kanvas-skia-gpu/build.gradle.kts`~~ — **décision G0 reportée** : la surface GPU initiale (~250 LOC, scopée test/) reste dans `kanvas-skia` pour éviter un refactor build prématuré. Le passage en module séparé sera tranché en G1 quand le code GPU touchera le main classpath (extraction `SkDevice` interface). Dépendance `io.ygdrasil:wgpu4k-toolkit:0.2.0-SNAPSHOT` ajoutée dans [kanvas-skia/build.gradle.kts](kanvas-skia/build.gradle.kts), snapshot repo Sonatype activé dans [settings.gradle.kts](settings.gradle.kts).
- [x] Tests JUnit5 + `kotlinx-coroutines-core` 1.10.2 (wgpu4k expose `suspend` sur `mapAsync` / `requestDevice` ; les coroutines sont en `implementation` côté wgpu4k, donc invisibles en compile classpath — déclarées explicitement en `testImplementation`).
- [x] CI : test marqué via `Assumptions.assumeTrue` — skip propre si le driver natif est absent. Module-level optional reporté à la décision G1.

### Bootstrap WebGPU
- [x] [WebGpuContext.kt](kanvas-skia/src/test/kotlin/org/skia/gpu/webgpu/WebGpuContext.kt) — wrapper sur `glfwContextRenderer(deferredRendering = true)`. **Bootstrap pur-surfaceless impossible** sur wgpu4k 0.2.0 : `WGPU.requestAdapter` déréférence inconditionnellement `surface.handler` (cf. commonNativeMain/WGPU.kt). Le path "deferred GLFW" reste headless en pratique : la fenêtre GLFW est `GLFW_VISIBLE = FALSE`, jamais mappée — elle existe uniquement pour fournir une `NativeSurface` à l'adapter request. Le rendu va dans une texture séparée gérée par `HeadlessTarget` (on contourne `TextureRenderingContext` du toolkit qui hard-code 256×256 par bug).
- [x] Appel explicite à `ffi.LibraryLoader.load()` avant `WGPU.createInstance()` — sans ça, le static init de `io.ygdrasil.wgpu.Functions` lève `UnsatisfiedLinkError: unresolved symbol wgpuCreateInstance`.
- [x] Aucun singleton statique pour l'instant — chaque test instancie + close son contexte. Optim reportée tant que la latence d'init (~700 ms sur Apple M2) ne sature pas les itérations.

### Premier render headless
- [x] Shader [clear_red.wgsl](kanvas-skia/src/test/resources/shaders/clear_red.wgsl) — full-screen Bjorke triangle, fragment `vec4(1,0,0,1)`. **ASCII strict obligatoire** : caractères Unicode dans les commentaires WGSL truncatent le code transmis au compilateur (bug FFI string length wgpu4k 0.2.0).
- [x] `HeadlessTarget` ([HeadlessTarget.kt](kanvas-skia/src/test/kotlin/org/skia/gpu/webgpu/HeadlessTarget.kt)) — texture `rgba8unorm` (`RenderAttachment | CopySrc`), staging buffer `MapRead | CopyDst`, padding 256 bytes/row WebGPU-compliant, de-padding en `readPixels()`.
- [x] [ClearRedTest.kt](kanvas-skia/src/test/kotlin/org/skia/gpu/webgpu/ClearRedTest.kt) — render-pass `loadOp=Clear(black)` + draw 3 verts du fullscreen tri rouge, puis assertion `RGBA=(255,0,0,255)` strict sur les 4096 pixels.

### Configuration JVM des tests (kanvas-skia/build.gradle.kts)
- [x] `--add-opens=java.base/java.lang=ALL-UNNAMED` — Rococoa utilise CGLib qui réfléchit sur `ClassLoader.defineClass`. JVM 17+ refuse sans `--add-opens`.
- [x] `--enable-native-access=ALL-UNNAMED` — silence le warning `System.loadLibrary` depuis `ffi.LibraryLoaderKt`.
- [x] `-XstartOnFirstThread` (macOS only) — GLFW exige la AppKit main thread (NSApp thread 0). `-XstartOnFirstThread` colle la JVM main thread sur thread 0, JUnit 5 `SameThreadHierarchicalTestExecutorService` enchaîne les tests dessus.

### .gitignore
- [x] `libWGPU-*.dylib` / `*.so` / `*.dll` ignorés — wgpu4k-native extrait le binaire next-to-cwd quand les paths Java Extensions ne sont pas writable (cas par défaut macOS).

### Vérification G0
- [x] `./gradlew :kanvas-skia:test --tests "org.skia.gpu.webgpu.ClearRedTest"` PASS sur macOS arm64 (Apple M2 Max), 3 runs consecutifs sans flake.
- [x] Aucune régression sur les 4623 tests raster — `PerspShadersAaGM` / `PerspShadersBwGM` failures pré-existantes sur la branche, validées via `git stash -u` baseline.
- [x] Test marqué SKIPPED (pas FAILED) quand `WebGpuContext.createOrNull()` retourne null (par exemple : binaires natifs absents, GLFW init impossible sans main thread).

### Post-mortem G0 — surprises notables
1. **wgpu4k 0.2.0 n'a pas de path "surfaceless"**. Toute la trajectoire G1+ doit assumer une dépendance GLFW transitive (sauf à patcher wgpu4k upstream).
2. **`TextureRenderingContext` du toolkit hard-code 256×256** — bug connu, à signaler upstream. Pour G1+ on continue à créer la texture nous-mêmes.
3. **JVM 25 + Rococoa CGLib** nécessite `--add-opens`. Lifting à confirmer sur Linux/Windows si on y déploie.
4. **WGSL parser sensible aux non-ASCII** — discipline à appliquer à tous les shaders Phase G2+.

---

## Phase G1 — Device abstraction + premier rect

**But** : extraire `SkDevice` comme interface, garder `SkBitmapDevice` comme impl raster, ajouter `SkWebGpuDevice` comme impl GPU. `SkCanvas` accepte les deux. `BigRectGM` passe sur les deux backends.

Découpée en 3 PRs (G1.0 → G1.1 → G1.2+G1.3) pour rester revue-friendly :

### G1.0 — Module split ✅
- [x] Nouveau module `:gpu-raster` (PR [#458](https://github.com/ygdrasil-io/kanvas/pull/458)). Les 4 fichiers G0 (`WebGpuContext`, `HeadlessTarget`, `ClearRedTest`, `clear_red.wgsl`) déplacés depuis `kanvas-skia/src/test/`. Wgpu4k-toolkit + coroutines testImpl + JVM args (`-XstartOnFirstThread` + `--add-opens`) côté `gpu-raster/build.gradle.kts`.

### G1.1 — Interface `SkDevice` ✅
- [x] Interface [SkDevice](kanvas-skia/src/main/kotlin/org/skia/core/SkDevice.kt) extraite (7 méthodes : `width`, `height`, `deviceClipBounds`, `drawRect`, `drawPaint`, `drawPath`, `drawImageRect`). `compositeFrom` est intentionnellement **hors interface** — elle prend un `SkBitmapDevice` concret en paramètre (le restream pixel-à-pixel suppose un device raster) ; généralisation reportée à G2+ quand la cross-backend composition deviendra utile.
- [x] [SkBitmapDevice](kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt) `: SkDevice` — uniquement les 7 méthodes interface taguées `override`, zéro changement de comportement.
- [x] [SkCanvas](kanvas-skia/src/main/kotlin/org/skia/core/SkCanvas.kt) refactor :
  - Constructeur primaire : `rootDevice: SkDevice` (ouvre la porte au `SkWebGpuDevice` de G1.2).
  - Storage public `device: SkDevice` (était `SkBitmapDevice`).
  - State stack `var device: SkDevice` (était `SkBitmapDevice`).
  - **Helper de cast** `SkDevice.requireBitmap(op)` top-level — lève une erreur claire si un non-raster device entre dans un chemin raster-only.
  - 7 call sites passent par `requireBitmap(...)` : `bitmap` accessor, restore-imageFilter-snapshot, restore-composite, bindClip (setActiveClip + setActiveClipShader), drawVertices-textured, drawVertices-colored, saveLayer-parent + saveLayer-backdrop + saveLayer-Layer.
  - Constructeurs secondaires `SkCanvas(bitmap)` et `Layer.parentDevice` restent `SkBitmapDevice` (la composition layer est raster-only par construction).
- [x] Restauré `include(":gpu-raster")` dans [settings.gradle.kts](settings.gradle.kts) — le include avait été dropped au squash-merge de G1.0.
- [x] **Vérification** : `./gradlew :kanvas-skia:test :cpu-raster:test :gpu-raster:test` — 0 régression sur les suites raster (10 min, 4000+ tests), `ClearRedTest` GPU toujours vert.

### G1.2 — `SkWebGpuDevice` premier squelette ✅
- [x] [WebGpuContext](gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/WebGpuContext.kt) et [HeadlessTarget](gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/HeadlessTarget.kt) promus de `src/test/` à `src/main/`. La dépendance `kotlinx-coroutines-core` correspondante est devenue `implementation` (était `testImplementation`).
- [x] [SkWebGpuDevice](gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt) `(ctx: WebGpuContext, width: Int, height: Int) : SkDevice` :
  - Texture color `RGBA8Unorm` (`rgba16float` reporté à G6 avec la conversion colorspace), via `HeadlessTarget`.
  - Pipeline single-color SrcOver (`solid_color.wgsl` : full-screen Bjorke triangle + uniform vec4 color), bind group layout = 1 uniform buffer en fragment stage.
  - `drawRect(rect, clip, paint)` accumule dans une liste de `RectDraw` (scissor int + RGBA float) avec le même `pixelEdge(c) = floor(c + 0.5)` que `SkBitmapDevice` — non-AA pixel parity exacte.
  - Guards explicites : `require(!isAntiAlias)`, `require(style == Fill)`, `require(alpha == 0xFF)` avec messages renvoyant vers la phase G2+ qui levera la restriction.
  - `drawPaint` / `drawPath` / `drawImageRect` : `TODO("G2+/G3/G5")` (interface implémentée, body laissé pour la phase qui débloque le besoin).
  - `flush(): ByteArray` rejoue les draws : 1 render-pass par draw (WebGPU interdit `queue.writeBuffer` entre 2 draws d'un même render-pass — workaround : uniform buffer + bind group par draw, dynamic-offset reporté à G2+). Le 1er render-pass clear au background ; les suivants `loadOp = Load`. Readback via `copyTextureToBuffer` + `mapAsync` → bytes RGBA tightly-packed.
- [x] **Ajustement G1.1** : `SkCanvas.bindClip` rendu permissif (`as? SkBitmapDevice ?: return`) pour les non-raster devices. Le clip rect entier (`s.clip`) continue à être propagé en paramètre à chaque draw donc `clipRect` basique fonctionne, mais `setActiveClip(SkAAClip)` et `setActiveClipShader` sont raster-only et silencieusement skippés sur GPU. Couverture AA-clip + clip-shader GPU = G2+.

### G1.3 — Cross-test ✅
- [x] **Choix de scope** : pas de port BigRectGM (264 rects/call avec stroke + AA + extreme coords + `translate` + `clipRect` mid-draw — tout est out-of-scope G1.2). À la place, [RectFillCrossTest](gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/RectFillCrossTest.kt) : 2 tests minimaux qui valident exactement ce que G1.2 livre.
  - Test 1 : single blue rect on white, raster vs GPU, comparaison bytes RGBA byte-for-byte.
  - Test 2 : rect partiellement hors viewport, même comparaison — sanity-check sur la math `pixelEdge` + `coerceAtMost(width)`.
- [x] Pas de ratchet `gpu-raster/test-similarity-scores-webgpu.properties` créé : sur un single rect aligné non-AA opaque la similarité est 100% exacte, le ratchet sera utile dès G2 (AA introduit du drift FP/quantization).
- [ ] BigRectGM port + harness `runGmTest` à DeviceFactory : déplacés à G2 (une fois AA / strokes disponibles).

### Vérification G1
- [x] `RectFillCrossTest` PASS sur macOS arm64 (Apple M2 Max) — raster et GPU produisent des bytes RGBA identiques.
- [x] Aucune régression sur les suites raster (vérifié `:kanvas-skia:test :cpu-raster:test :gpu-raster:test`).

---

## Phase G2 — AA + clip + blend modes simples

**But** : rect AA (Phase 2 master plan), `clipRect`, et 9 modes Porter-Duff de base (kSrc / kSrcOver / kDstOver / kSrcIn / kDstIn / kClear / kPlus / kModulate / kScreen).

- [ ] **AA rect** — 2 stratégies à arbitrer en début de phase :
  - (a) Native MSAA 4×, render target multisamplée, resolve final.
  - (b) Coverage analytique en fragment shader (port direct de la coverage axis-aligned du raster). Plus fidèle au raster, pas de dépendance MSAA.
  - Recommandation : **(b)** parce qu'elle maintient pixel-equivalence avec le raster en working space linear.
- [ ] **Clip** — support `kIntersect` rect-only. Stocké en uniform `vec4f` (clip rect en device coords) + `discard` dans le shader.
- [ ] **Blend modes Porter-Duff de base** — WebGPU a `BlendComponent` natif pour kSrcOver/kDstOver/kSrc/kClear. Pour kPlus/kScreen/kModulate, basculer sur fragment-side blending (lecture render target via `loadOp = load` + manual blend). Documenter quel mode est natif vs manual.
- [ ] **Tests** : `ThinRectsGM`, `ClipStrokeRectGM`, `ScaledRectsGM` (déjà 87.79% en raster avec kPlus).

### Vérification G2
- [ ] Les 4 GMs Phase 1-2 raster portent sur GPU avec scores ≥ 90% chacun.

---

## Phase G3 — Path tessellation + fill

**But** : `drawPath` GPU. Décision D2 = CPU tessellate.

- [ ] **`PathTessellator`** dans `kanvas-skia-gpu` :
  - [ ] Réutilise `SkBitmapDevice.buildEdges` (flatten Bézier en polylines) — extraire en helper public.
  - [ ] Triangulation polygone-à-trous via libtess2 algorithme (ou ear-clipping si convex-only initial slice). Sortie : `FloatArray` de positions (x, y) en device coords.
  - [ ] Cache intra-frame : si le même path est dessiné plusieurs fois, ne pas re-tessellate.
- [ ] **Upload + draw** : `GPUBuffer` vertex, draw triangles, fragment = solid color (réutilise pipeline Phase G2 + remplace input quad par triangles).
- [ ] **AA paths** — coverage edge en fragment shader via distance-to-edge ; ou distance field si MSAA off. À profiler sur `ConcavePathsGM` (cible Phase 3a master).
- [ ] **Stroke** — réutilise `SkStroker` (Phase 3c master) côté CPU pour produire le path outline → tessellate comme un fill normal.
- [ ] **Tests** : `ConcavePathsGM`, `ConvexPathsGM`, `ArcOfZorroGM`, `crbug_*` family.

### Vérification G3
- [ ] ≥ 5 path GMs verts sur GPU avec scores ≥ 85%.

---

## Phase G4 — Shader infra : gradients en WGSL

**But** : porter `SkLinearGradient` + `SkRadialGradient` (Phase 5a/5b master) en WGSL. **Ce slice acte le remplacement définitif du format shader Skia (SkSL) par WGSL.**

### Contrat shader
- [ ] **Pas de SkSL → WGSL transpilation.** Chaque type de shader (`SkLinearGradient`, `SkRadialGradient`, `SkBitmapShader`, future composite) a son **template WGSL** (~30-80 lignes), instancié au moment du draw avec :
  - [ ] Stops uniformes : `array<vec4f, MAX_STOPS>` + `array<f32, MAX_STOPS>` positions + `count`.
  - [ ] Inverse local-matrix uniform.
  - [ ] Tile mode comme constante de spécialisation (kClamp / kRepeat / kMirror / kDecal — 4 variantes compilées séparément).
- [ ] **Précompilation** : pipeline cache keyed par `(shader-type, tileMode-X, tileMode-Y, blend-mode, ...)`. Évite recompile par draw.

### Détails
- [ ] `SkLinearGradient.wgsl` : projection `t = dot(p - p0, dir) / |dir|^2`, lookup binary search dans le stops array, lerp premul (mêmes maths que `lookupStopF16` côté raster).
- [ ] `SkRadialGradient.wgsl` : `t = length(p - center) / radius`, idem lookup.
- [ ] **Linear-premul output** : working space identique au raster (D3) — le shader retourne directement en F16 linear-Rec.2020.

### Tests
- [ ] Tous les GMs gradient déjà raster (ports Phase 5a-5g du master) re-runnables sur GPU.

### Vérification G4
- [ ] FillrectGradientGM, ClampedGradients, OvalGM gradient row, AnalyticGradients verts sur GPU.

---

## Phase G5 — Bitmap shader + drawImage

**But** : porter `SkBitmapShader` (Phase 5g master) + `drawImage` direct sur GPU. Première utilisation de `GPUTexture` comme source d'échantillonnage.

- [ ] **Image upload** : `SkImage` → `GPUTexture` cache (`weakHashMap<SkImage, GPUTexture>`).
- [ ] **`SkBitmapShader.wgsl`** : sampler 2D + tile-mode constantes spec, `kNearest` / `kLinear`, mêmes règles de pixel-center que le raster.
- [ ] **`drawImage` / `drawImageRect`** : pipeline simple qui réutilise `SkBitmapShader.wgsl` avec local-matrix dérivée de `(src, dst)`.
- [ ] **Color management** : la texture peut être uploadée en sRGB ou Rec.2020 — passer le profil en uniform et linearize dans le shader. Réutilise `SkColorSpaceXformSteps` côté CPU pour produire les coefficients.

### Tests
- [ ] `DrawBitmapRect3`, `BigMatrixGM`, `TilemodesAlphaGM`, `BitmapShaderGM`, `TinyBitmapGM`.

---

## Phase G6 — Color management GPU

**But** : finir la convergence avec le raster F16 (master plan Phase 6a/6b/6c). Le rendu GPU doit produire des images bit-équivalentes (à 1 ulp près) du rendu raster pour les GMs où le seul shader est `paint.color`.

- [ ] **Render target F16 linear-Rec.2020** confirmé.
- [ ] **Final present pass** : compute ou fragment quad qui :
  1. Lit le F16 linear-Rec.2020.
  2. Applique la matrice de transfert vers Rec.2020 encoded (gamma).
  3. Écrit dans `rgba8unorm` SRGB-aware (Rec.2020 8-bit packing).
- [ ] **Validation pixel-à-pixel** : pour les GMs qui passent en raster avec ≥ 99%, le GPU doit aussi atteindre ≥ 99% à `tolerance=1` ou justifier le drift.

---

## Phase G8 — Migration path tessellation vers GPU compute (conditionnelle)

**But** : si la tessellation CPU (Phase G3) devient le bottleneck, migrer vers compute-shader tessellation. **Cette phase n'est PAS planifiée d'office** — elle se déclenche sur trigger de profiling.

### Triggers de migration (au moins un)

- **T1** — Profiling montre que `PathTessellator.tessellate()` représente > 30% du temps total `runGmTest` sur les GMs path-heavy (`ConvexPathsGM`, `ArcOfZorroGM`, `crbug_*`).
- **T2** — Use-case réel ajouté au scope (animation interactive, scroll fluide) où chaque frame tessellate des centaines de paths complexes et le frame budget < 16ms est dépassé.
- **T3** — Bande passante CPU↔GPU mesurée saturante (> 100 MB/frame de buffer vertex transmis).
- **T4** — Tests cross-validation montrent un drift de précision dû à la tessellation CPU FP32 vs GPU FP32 (improbable, mais à surveiller).

### Approches candidates (à arbitrer si déclenchée)

- **Compute tessellation Graphite-style** : compute shader produit triangles directement dans un `Buffer` GPU, pas de roundtrip CPU. Référence : papers Skia Graphite + [Vello](https://github.com/linebender/vello).
- **Stencil & cover Ganesh-style** : pas de tessellation pure ; passe stencil pour winding count, passe cover pour fill. Plus simple à implémenter que compute-tessellate, mais demande stencil buffer.
- **Hybrid** : tessellate les paths simples (≤ 16 verbs) sur CPU, déléguer les paths complexes au GPU compute.

### Pré-requis avant déclenchement

- [ ] Benchmarks micro reproductibles sur les paths-heavy GMs, baseline CPU-tessellate documentée.
- [ ] WebGPU compute shader hello-world validé sur les drivers cibles (vérifier les limites `maxComputeWorkgroupSize`).
- [ ] Décision entre compute-tessellate vs stencil-and-cover sur la base d'un PoC limité (~1 GM).

### Vérification G8
- [ ] Speedup mesuré ≥ 2× sur les GMs path-heavy.
- [ ] Aucune régression de score similarité (drift ≤ 1 ulp acceptable).

---

## Phase G7 — Cross-validation harness + documentation API

**But** : pérenniser le double-backend en infrastructure de test, documenter les divergences attendues.

- [ ] **`runGmTest` factorisé** : prend un `DeviceFactory` au lieu d'un device concret. Chaque GM-test génère 2 résultats (raster + GPU) et publie 2 entrées dans le ratchet.
- [ ] **Diff visuel** : si raster et GPU divergent de > 2%, sauvegarder les deux PNGs côte-à-côte sous `kanvas-skia/build/debug-images/<test-name>-{raster,gpu,diff}.png`.
- [ ] **CI matrix** : raster (toujours), GPU (skip si driver absent — `Assumptions.assumeTrue(WebGpuContext.isAvailable())`).
- [ ] **Documentation** : nouveau `kanvas-skia-gpu/README.md` qui explique :
  - Choix d'arch (pas de Ganesh, pas de SkSL, WGSL hand-written, parité linear-Rec.2020).
  - Mapping `SkShader` → templates WGSL.
  - Comment ajouter un nouveau type de shader (snippet template + slot dans le pipeline cache).

---

## Travaux parallèles possibles

- **Texte sur GPU** — réutilise le pipeline texte (master plan T1-T5) en uploadant les glyphes raster en atlas GPU. Peut être livré après G5.
- **WebAssembly / Native targets** — wgpu4k est KMP (JS/WASM browser, Native expérimental). Si on convertit `kanvas-skia-gpu` en module KMP (ajouter un `commonMain` + `jvmMain`), on a *gratuitement* les autres cibles. Reporté tant que le projet reste JVM-only ; budget bas pour la conversion (la majorité du code est multiplatform-friendly).

---

## Trajectoire de progression

| Phase | But | Cible | État |
|-------|-----|-------|------|
| G0    | Bootstrap module + headless WebGPU + clear red | clear test vert | ✅ |
| G1    | `SkDevice` interface + `SkWebGpuDevice` + BigRectGM | 1 GM GPU | ⬜ |
| G2    | AA rects + clip + 9 blend modes de base | 4 GMs Phase 1-2 GPU | ⬜ |
| G3    | Path tessellation CPU + fill + stroke | ≥ 5 path GMs GPU | ⬜ |
| G4    | Shaders WGSL : LinearGradient + RadialGradient (templates) | gradient GMs GPU | ⬜ |
| G5    | BitmapShader + drawImage | image GMs GPU | ⬜ |
| G6    | Color management F16 linear-Rec.2020 GPU | parité raster pixel | ⬜ |
| G7    | Cross-validation harness | CI matrix raster+GPU | ⬜ |
| G8    | Migration GPU compute tessellation (conditionnelle, sur triggers) | speedup ≥ 2× sur path-heavy GMs | ⬜ déclenché sur profiling |

---

## Risques & ouvertures

- **R1** — wgpu4k est en **Beta v0.1.1**. Risque de breaking changes amont. Mitigation : pinner une version. **Pas de façade additionnelle** côté `kanvas-skia-gpu` — wgpu4k utilise déjà `webgpu-ktypes` comme couche d'abstraction interne, dupliquer cette indirection ne fait qu'ajouter du bruit. Si un breaking change amont casse, on absorbe le diff au point d'usage.
- **R2** — driver WebGPU pas dispo en CI Linux headless (Mesa Vulkan / lavapipe peut suffire ; Dawn peut headless via SwiftShader). Plan B : tests GPU optionnels marqués `@Tag("gpu")`, exécutés seulement en CI macOS (Metal toujours dispo) + dev locale.
- **R3** — divergence bit-equivalence raster ↔ GPU due aux différences de précision flottante GPU (FP32 fragment vs FP64 CPU intermediates dans `SkColorSpaceXformSteps`). Mitigation : tolérance ≤ 2 ulps acceptée, documenter au cas par cas.
- **R4** — wgpu4k JVM dépend de binaires natifs (`wgpu4k-native` qui wrappe wgpu-rs). Verifier la disponibilité des classifiers Maven pour macOS arm64 / x86_64, Linux x86_64, Windows x86_64. Ajouter un fallback message clair si binaire absent.

---

## Sources de référence

- [`kanvas/src/generated/gpu/`](kanvas/src/generated/gpu/) — 534 fichiers Kt-stubs Skia GPU (lecture seule, jamais portés ; utiles seulement comme contre-exemple de complexité).
- [WebGPU spec](https://www.w3.org/TR/webgpu/) — référence canonique des sémantiques.
- [WGSL spec](https://www.w3.org/TR/WGSL/) — référence shaders.
- [wgpu4k](https://github.com/wgpu4k/wgpu4k) — wrapper KMP idiomatique (D1 acté).
- [wgpu4k-native](https://github.com/wgpu4k) — binding bas niveau sur wgpu-rs (transitif via wgpu4k).
- [`kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt`](kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt) — surface raster à émuler côté GPU (signatures + sémantiques pixel-center).
- [`kanvas-skia/src/main/kotlin/org/skia/foundation/SkShader.kt`](kanvas-skia/src/main/kotlin/org/skia/foundation/SkShader.kt) + `SkLinearGradient.kt` + `SkRadialGradient.kt` + `SkBitmapShader.kt` — sémantiques de shading raster que les templates WGSL doivent reproduire.
