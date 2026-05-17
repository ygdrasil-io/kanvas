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

**But** : rect AA (Phase 2 master plan), `clipRect` non-axis-aligned, et 9 modes Porter-Duff de base (kSrc / kSrcOver / kDstOver / kSrcIn / kDstIn / kClear / kPlus / kModulate / kScreen).

Découpée en 3 sous-PRs (G2.1 → G2.2 → G2.3) :

### G2.1 — Translucent SrcOver ✅
- [x] [solid_color.wgsl](gpu-raster/src/main/resources/shaders/solid_color.wgsl) premultiplie maintenant la couleur source dans le fragment shader (`vec4f(c.rgb * c.a, c.a)`). Le pipeline existant `(src=One, dst=OneMinusSrcAlpha)` consomme désormais des valeurs premul et donne la math SrcOver correcte pour n'importe quelle alpha.
- [x] Drop du `require(SkColorGetA(color) == 0xFF)` côté `SkWebGpuDevice.drawRect`.
- [x] [TranslucentSrcOverTest](gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/TranslucentSrcOverTest.kt) — translucent blue (alpha=0x80) over opaque red, vérifie le pixel intérieur (127, 0, 128, 255) avec tolérance ±1.
- [x] Render target stocke des valeurs **premul** (conséquence du shader premul + SrcOver). Différence vs `SkBitmap.pixels8888` (non-premul par convention) à signaler : un pass present unpremul est reporté à G6 avec la conversion colorspace.

### G2.2 — Multi-mode pipeline cache + Porter-Duff natifs ✅
- [x] Cache `Map<SkBlendMode, GPURenderPipeline>` dans [SkWebGpuDevice](gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt). Pipeline créé lazy par mode via `pipelineFor(mode)` ; `blendStateFor(mode)` factore la table de blend factors WebGPU.
- [x] 4 modes WebGPU-natifs : kSrc (One, Zero), kSrcOver (One, OneMinusSrcAlpha), kDstOver (OneMinusDstAlpha, One), kClear (Zero, Zero). Helper `blendAddBoth(src, dst)` puisque color et alpha utilisent les mêmes factors pour la formulation premul standard.
- [x] `RectDraw` enrichi d'un champ `mode: SkBlendMode` ; `drawRect` capture `paint.blendMode` ; `flush` route via `pipelineFor(draw.mode)` à chaque render-pass.
- [x] `close()` itère et ferme tous les pipelines cachés.
- [x] [BlendModeTest](gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/BlendModeTest.kt) — 5 tests : kSrc replace, kClear zero, kSrcOver via le nouveau path, kDstOver (visible sur transparent + invisible sur opaque), unsupported mode (kPlus) throw avec message qui pointe G2.2.
- [x] **Note** : kPlus / kScreen / kModulate / kSrcIn / kDstIn / etc. throw avec message explicite — leur support demande fragment-side blending (`loadOp=Load` + manual blend) et arrive en G2.3 ou suivant.

### G2.3a — AA rect via coverage analytique ✅
- [x] [solid_color.wgsl](gpu-raster/src/main/resources/shaders/solid_color.wgsl) unifié : un seul shader pour AA et non-AA. Uniform passe de `(color: vec4f)` à `(color: vec4f, bounds: vec4f)` = 32 bytes.
- [x] Fragment stage calcule la coverage analytique axis-aligned : `cov_x = clamp(min(pos.x + 0.5, r) - max(pos.x - 0.5, l), 0, 1)` (intersection length entre le pixel `[p, p+1]` et le rect, comme `SkBitmapDevice`). `coverage = cov_x * cov_y`. Le shader applique `coverage` à l'alpha premul.
- [x] Drop du `require(!isAntiAlias)`. [SkWebGpuDevice.drawRect](gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt) branche maintenant 2 chemins :
  - **AA** : bounds = rect fractionnel ; scissor conservateur = `(floor(l), floor(t), ceil(r) - floor(l), ceil(b) - floor(t))` ; les pixels edge sont visités et reçoivent leur coverage.
  - **Non-AA** : pixelEdge rounding (`floor(c + 0.5)`) ; bounds = mêmes ints ; scissor = `(l, t, r - l, b - t)`. La formule de coverage collapse à 1.0 pour les pixels intérieurs (centre du pixel à ≥ 0.5 d'une edge int) — output **byte-identique** au pre-G2.3a.
- [x] [AaRectFillTest](gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/AaRectFillTest.kt) — 2 sous-tests :
  - Rect à edges half-integer (10.5, 10.5, 30.5, 30.5) : edge pixel = coverage 0.5 → `(128, 128, 255, 255)`, corner = coverage 0.25 → `(191, 191, 255, 255)`, interior = coverage 1 → `(0, 0, 255, 255)`.
  - Rect AA à edges integer : sanity-check que coverage = 1 partout (degenerate vers le path non-AA).
- [x] **Backward compat** : 9 tests pré-G2.3a (ClearRedTest + RectFillCrossTest×2 + TranslucentSrcOverTest + BlendModeTest×5) toujours verts byte-pour-byte.

### G2.3b — Cross-test harness + ThinRectsGM ✅ (pivoté depuis "rotated clip")

**Pivot du scope.** Un audit des 4 GMs cibles G2 (BigRectGM, ThinRectsGM, ClipStrokeRectGM, ScaledRectsGM) montre que :
- **ThinRectsGM** n'a aucun blocker contre la surface G2.3a actuelle (fill+AA seul).
- **BigRectGM** et **ClipStrokeRectGM** sont bloqués par **stroke** (3/4 GMs).
- **ScaledRectsGM** est bloqué par `drawPaint` + CTM rotated (1/4 GMs).
- **Rotated clip** ne débloque que ScaledRectsGM ET ne suffit pas seul (drawPaint manque aussi).

Le slice G2.3b vise désormais le déblocable immédiat (cross-test harness + premier vrai GM vert) au lieu du rotated clip qui ne paie pas. Le rotated clip est reporté à G4+ (quand un GM en scope l'exigera).

- [x] `:gpu-raster` ajoute `testImplementation(project(":cpu-raster"))` + sourceSet `srcDir("../kanvas-legacy/src/test/resources")` pour accéder aux GMs portés et aux PNG de référence `original-888/`.
- [x] [WebGpuSink](gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuSink.kt) — équivalent GPU de `RasterSinkF16`. `WebGpuSink.draw(context, gm) -> SkBitmap (kRGBA_8888)`. Convertit les bytes RGBA premul du readback en ARGB ints SkBitmap (correct byte-pour-byte pour les GMs n'utilisant que des couleurs opaques — premul == non-premul).
- [x] [ThinRectsWebGpuTest](gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/ThinRectsWebGpuTest.kt) — rend `ThinRectsGM` via `WebGpuSink`, compare via `TestUtils.compareBitmapsDetailed` à `original-888/thinrects.png` avec tolerance 8 (= `TEXTUAL_GM_TOLERANCE` raster pour absorber l'AA edge drift).
- [x] [test-similarity-scores-webgpu.properties](gpu-raster/test-similarity-scores-webgpu.properties) ratchet créé. Format = même que `:cpu-raster`. Score initial : **`ThinRectsGM=90.890625`** (juste au-dessus du floor G2 de 90%).
- [x] Floor 90.0 hardcodé dans le test pour l'instant ; un `SimilarityTracker` GPU-side viendra avec la 2e/3e GM (G3+).

### Note : pourquoi le score n'est "que" 90.89% ?

Drift dominant : la référence PNG est encodée en **DM unified Rec.2020**, le GPU sort actuellement du sRGB-direct RGBA8Unorm. Sur les couleurs saturées (GREEN dans ThinRectsGM), le canal R a une diff jusqu'à 145 entre les 2 encodages. La convergence working-space linear-Rec.2020 GPU est planifiée pour **G6** (cf. D3 du plan). En attendant, tolerance=8 + une majorité de pixels exacts (69807/76800) reste au-dessus du floor.

### G2.3c (futur) — rotated clip (reporté à G4+)

Reporté à plus tard, à arbitrer quand un GM en scope exige rotated clip et que le payoff justifie le travail (uniform supplémentaire + discard fragment-side). Pas besoin pour le scope G2 actuel ; et tant que SkCanvas projette le clip rotated en AABB conservatif, l'output GPU est correct (over-conservative) pour les GMs qui ne combinent pas rotate + clipRect.

### Vérification G2
- [x] G2.1 : `TranslucentSrcOverTest` PASS, aucune régression `:gpu-raster:test` (ClearRedTest + RectFillCrossTest toujours verts).
- [x] G2.2 : 4 modes Porter-Duff natifs verts via `BlendModeTest` (5 sous-tests dont l'erreur explicite sur mode non-supporté), G2.1/G1.x toujours verts (9 tests total `:gpu-raster:test`).
- [x] G2.3a : AA rect via coverage analytique vert via `AaRectFillTest` (2 sous-tests sur edges half-integer + integer), backward compat préservée (11 tests total).
- [x] G2.3b : cross-test harness en place + 1er vrai GM vert (`ThinRectsGM` à 90.89%, floor 90%), 12 tests total.
- [x] **≥ 4 GMs Phase 1-2 à ≥ 90%** : **4/4 atteint** post-G6.0 (ThinRectsGM 100 %, ClipStrokeRectGM 100 %, ScaledRectsGM 100 %, BigRectGM 99.90 %). Bonus : ThinStrokedRectsGM 94.21 %, Skbug12244GM 90.33 %. **6/6 ratchet entries au-dessus du target G2 90 %**.

---

## Phase G3 — Path tessellation + fill

**But** : `drawPath` GPU + stroke. Décision D2 = CPU tessellate (pour le path générique).

### G3.1.1 — AA stroke rect via annular coverage ✅ (correctness improvement)

Refactor du chemin AA stroke (et AA hairline) pour matcher exactement `SkBitmapDevice.strokeRectAA`'s annular formulation : `coverage = outer_cov - inner_cov` en un seul draw, au lieu des 4 edge fills de G3.1.

- [x] `solid_color.wgsl` : uniform bumpé de 32 à 48 bytes ; ajoute `innerBounds: vec4f`. Coverage = `max(0, outer_cov_x * outer_cov_y - inner_cov_x * inner_cov_y)`. Fills passent une `innerBounds` dégénérée (l>r, t>b) qui collapse `inner_cov` à 0 → output identique à l'ancien chemin.
- [x] `drawStrokeRect` (AA path) route vers `drawAnnularStrokeRect` : un seul draw avec `outer = rect ± half_sw`, `inner = rect ∓ half_sw`.
- [x] `drawHairlineRect` (AA path) route vers `drawAnnularStrokeRect` avec `effective sw = 1` (mirror `SkBitmapDevice.strokeRectAA`'s `w = if (sw <= 0f) 1f else sw`).
- [x] Non-AA paths inchangés : 4-edge fill pour stroke sw>0, floor-snapped 1-pixel pour hairline sw≤0.
- [x] **Impact mesuré sur les ratchets** : `ThinStrokedRectsGM` 87.13 → 87.19 (+0.06%). `BigRectGM` 70.70 inchangé — confirme que **colorspace est le drift dominant**, pas l'AA stroke corner ou l'AA hairline. La refacto reste un correctness improvement (math alignée sur raster), pas un score bump majeur.

### G3.1 — Rect stroke (sans path tessellation) ✅

**Shortcut payant.** L'audit des 4 GMs cibles G2 a montré que stroke bloque 2/4 GMs (BigRectGM, ClipStrokeRectGM) et drawPath aucun. Pour les **rect-strokes axis-aligned**, on peut décomposer en 4 fill-rects sans passer par SkStroker — match exact de [SkBitmapDevice.strokeRect](kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt) (annular outer/inner pour `sw > 0`, 4 1-pixel edges sur `floor(c)` pour hairline `sw <= 0`).

- [x] [SkWebGpuDevice.drawRect](gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt) dispatch sur `paint.style` : `kFill_Style` → `drawFillRect` (G2.3a) ; `kStroke_Style` → `drawStrokeRect` ; `kStrokeAndFill_Style` → les deux.
- [x] `drawStrokeRect` : outer = rect ± half-strokeWidth, inner = rect ∓ half. 4 fill sous-rects (top/bottom/left/right) avec corners couverts par top/bottom uniquement. Si inner empty → un seul fill sur outer.
- [x] `drawHairlineRect` (`sw <= 0`) : 4 1-pixel edges sur `floor()` integer coords, forcés non-AA. Match `SkScan::HairLineRgn`. AA hairline (sub-pixel coverage) reporté en follow-up.
- [x] [RectStrokeTest](gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/RectStrokeTest.kt) — 3 sous-tests : hairline, thin (sw=2), thick (sw=50, inner empty).
- [x] [BigRectWebGpuTest](gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/BigRectWebGpuTest.kt) — cross-test contre `original-888/bigrect.png`. Score **70.70%** — debloqué mais en dessous du G2 90% target. 3 sources de drift restantes : AA hairline approximé non-AA, stroke-AA corner convention, colorspace sRGB vs Rec.2020 (G6).
- [x] Ratchet `BigRectGM=70.7` ajouté.

### G3.2 — drawPaint + ClipStrokeRectGM cross-test ✅

**Bundle de quick wins.** Iter 4 a déplacé les GMs depuis `:cpu-raster` vers le nouveau module `:skia-integration-tests` — `:gpu-raster` ajoute son testImpl. Avec stroke (G3.1) en place, ClipStrokeRectGM devient testable ; et `drawPaint` reste un TODO trivial — on bundle les deux.

- [x] [SkWebGpuDevice.drawPaint](gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt) : route vers `drawRect` sur le clip rect. Pas de CTM (drawPaint opère en device coords par contrat). Toutes les optims G2.x (alpha, blend mode, AA) appliquent uniformément.
- [x] `testImplementation(project(":skia-integration-tests"))` ajouté (Iter 4 a déplacé les GMs).
- [x] [DrawPaintTest](gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/DrawPaintTest.kt) — 2 sous-tests : full viewport fill + post-`clipRect` partial fill.
- [x] [ClipStrokeRectWebGpuTest](gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/ClipStrokeRectWebGpuTest.kt) — cross-test sur `original-888/clip_strokerect.png`. Score **96.60%**, **au-dessus du target G2 90%**.
- [x] **Confirmation** : `clipRect(rect, doAntiAlias = true)` sur un rect axis-aligned integer ne crée PAS d'`SkAAClip` côté SkCanvas — la soft-skip de `bindClip` n'est pas déclenchée.
- [x] Ratchet `ClipStrokeRectGM=96.605` ajouté.

### G3.3a.1 — kPlus blend mode + ScaledRectsGM cross-test ✅ (suite à G3.3a)

Petit follow-up à G3.3a. La note plan G2 disait "kPlus / kScreen / kModulate need fragment-side blending", mais pour kPlus avec premul-in / premul-out (notre convention), `BlendOperation.Add(srcFactor=One, dstFactor=One)` évalue exactement `clamp(src + dst, 0, 1)` = kPlus. Pas besoin de fragment-side blending.

- [x] `blendStateFor(kPlus) = blendAddBoth(One, One)`. Premier mode au-delà des 4 natifs Porter-Duff.
- [x] `BlendModeTest.unsupported blend mode` migre de kPlus → kModulate (qui reste effectivement bloqué — sa formule `r = s*d` n'a pas de mapping direct sans fragment-side blending).
- [x] `BlendModeTest.kPlus adds source onto destination with channel saturation` — kSrc(translucent red) puis kPlus(translucent blue), vérifie magenta saturé (128, 0, 128, 255) au centre.
- [x] [ScaledRectsWebGpuTest](gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/ScaledRectsWebGpuTest.kt) — score **87.79%**, **3e GM cible G2 qui run sans crash**. Drift profile = même bande que BigRectGM (sRGB vs Rec.2020 + edge convention parallelogrammes rotated + pas d'AA polygon coverage encore). En dessous du target G2 90%.
- [x] Ratchet `ScaledRectsGM=87.79` ajouté.

### G3.3a — drawPath skeleton : convex polygons, non-AA ✅

Premier `drawPath` GPU. Scope minimal mais utile : single-contour convex polygon paths (Move + Line + Close verbs), non-AA, fill only. Fan tessellation depuis le premier vertex. Débloque les rect-under-rotated-CTM (qui transitent via `drawPath` dans SkCanvas) + les paths convexes simples.

- [x] [solid_polygon.wgsl](gpu-raster/src/main/resources/shaders/solid_polygon.wgsl) : nouveau shader avec vertex stage qui transforme device-pixel coords → NDC (Y-flip), fragment stage premul color. Uniform = `(color: vec4f, viewport: vec4f)` = 32 bytes.
- [x] `pending: List<PendingDraw>` (sealed) avec 2 variantes : `RectDraw` (full-screen tri + scissor + coverage, comme G2.3a) et `PolygonDraw` (vertex buffer triangle list, scissor pour clip). Ordre de draw préservé pour la composition.
- [x] 2e pipeline cache `polygonPipelineCache: Map<SkBlendMode, GPURenderPipeline>` + `polygonBindGroupLayout` (visibility Vertex | Fragment pour le viewport uniform) + `polygonPipelineLayout`.
- [x] [SkWebGpuDevice.drawPath](gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt) : walk verbs, transforme par CTM, fan-tesselle. Throws sur `kQuad` / `kConic` / `kCubic` (pointe G3.3b) + `isAntiAlias` (G3.3b) + style ≠ Fill (G3.4).
- [x] `flush()` dispatch sur `is RectDraw` / `is PolygonDraw` ; chaque polygon draw a son propre vertex buffer + uniform buffer + bind group.
- [x] [PolygonFillTest](gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/PolygonFillTest.kt) — 3 sous-tests : quad (4 vertices), triangle (3 vertices), curve verb throws.

**Note**. Le clip côté polygon est `setScissorRect` axis-aligned int — comme pour les rects. Pas de point-in-polygon test fragment-side ; la AABB du polygon est honorée par le scissor.

### Suivi GMs (post-G3.3b.1) — ThinStrokedRectsGM ratché ✅

Petit ramassage de quick wins une fois les curves dispo : exploration ciblée de GMs simples côté `:skia-integration-tests` qui devraient marcher avec la surface actuelle.

- [x] [ThinStrokedRectsWebGpuTest](gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/ThinStrokedRectsWebGpuTest.kt) — 7×8 grid de stroke rects axis-aligned avec sw ∈ {4, 2, 1, 0.5, 0.25, 0.125, 0}, tous AA. Score **87.13%** (drift = colorspace + sub-pixel edge conventions). Ratchet `ThinStrokedRectsGM=87.13` ajouté.
- [x] [Skbug12244WebGpuTest](gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/Skbug12244WebGpuTest.kt) — single `drawPath` avec **multi-contour** line-only (2 contours fermés, l'intérieur est un "hole"). Pre-G3.3b.2, le fan tess flatten les 2 contours en un seul polygon → le hole est rempli. Baseline **70.87%** (la majorité de la bitmap = bg blanc inchangé). Attendu de grimper avec G3.3b.2.
- [x] **SimpleRectGM dropped**. 10 000 `drawRect` calls saturent le chemin "per-draw render-pass + uniform buffer + bind group" de `flush()`. Le test hangs ou prend des minutes. Plan : ajouter un **bulk-draw path** quand le besoin arrive (instancing ou vertex-buffer with per-vertex color). Cf. note ci-dessous.

#### Follow-up : bulk rendering pour GMs high-draw-count

Le `flush()` actuel alloue 1 uniform buffer + 1 bind group + 1 render-pass par `RectDraw` ou `PolygonDraw`. C'est correct (et simple) pour la majorité des GMs (≤ 200 draws), mais s'effondre au-delà de quelques milliers. Options :

1. **Dynamic-offset bind groups** : un seul big uniform buffer, chaque draw bind avec offset différent. Reste un render-pass par draw.
2. **Instancing** : duplicate la pipeline avec per-instance uniforms via vertex attributes step-mode = Instance. 1 draw call pour N rects de même config.
3. **Per-vertex color attribute** : vertex buffer porte la color, fragment lit la color interpolée. 1 draw call par batch homogène (même blend mode / AA).

Le déclencheur : un GM en scope dont le draw count > ~500 et qui motive le travail.

### G3.3b.1 — Bezier flattening ✅

Port des algorithmes de subdivision adaptative De Casteljau (quadratic + cubic) et de stepping uniforme (conic) depuis `SkBitmapDevice.buildEdges`. Mêmes constantes (`PATH_FLATNESS = 0.25`, `PATH_FLATNESS_SQ = 0.0625`, `PATH_MAX_DEPTH = 18`, `CONIC_STEPS = 32`).

- [x] `flattenQuadInto` / `flattenCubicInto` dans le companion de `SkWebGpuDevice` — subdivision récursive jusqu'au chord-error ≤ 0.25 px (squared-comparison sans sqrt).
- [x] `flattenConicInto` — uniform parametric stepping (32 steps), même algo que raster.
- [x] [`drawPath`](gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt) gère maintenant `kMove` / `kLine` / `kQuad` / `kCubic` / `kConic` / `kClose`. Le `else -> error` ne se déclenche plus que pour des verbes inattendus.
- [x] `PolygonFillTest` migré : le test "curve verb throws" devient deux tests positifs (quadratic + cubic) qui vérifient que la courbe flatten en segments rendables.

**Limitation restante (G3.3b.2)** : la fan tessellation depuis le 1er vertex reste convex-correct uniquement. Pour un path concave (avec courbe ou pas), les triangles du fan vont traverser hors du polygone. ConcavePathsGM scorera bas tant que G3.3b.2 ne livre pas une triangulation propre (ear-clipping ou libtess2-like).

### G3.3b.2a — AA polygon coverage (convex, single-contour) ✅

Fragment-side analytical edge coverage pour les paths convexes. `paint.isAntiAlias = true` sur `drawPath` ne throw plus — single-contour convex polygons obtiennent une coverage smooth sur leurs bords.

- [x] [`aa_polygon.wgsl`](gpu-raster/src/main/resources/shaders/aa_polygon.wgsl) — fragment shader qui itère sur les edges du polygon perimeter (uniforme `array<vec4f, 256>` de coefficients `(a, b, c, _)`), calcule `coverage = min over edges of clamp(signed_dist + 0.5, 0, 1)`, applique au premul `color.a`.
- [x] Nouveau variant `AaPolygonDraw : PendingDraw` avec edges array + edgeCount. 2e polygon pipeline cache (`aaPolygonPipelineCache`) keyed par blend mode.
- [x] `buildPerimeterEdges(devVerts, out)` — détecte le winding (signed area en screen coords : positive = CW visuellement → flip orient), produit les edge equations normalisées avec "signed_dist > 0 = inside".
- [x] **Key trick (rasterizer corner-case)** : l'AA path rend la **bounding box du polygon** (2 triangles axis-aligned, inflated par 1 pixel) au lieu du fan tess. Sinon, la rasterization GPU exclut les pixels exactement sur les bords des triangles (top-left edge rule), volant au fragment shader la chance de calculer leur coverage. La bbox garantit que tous les pixels près du polygon sont visités ; le shader masque la bbox vers la forme exacte.
- [x] Restrictions G3.3b.2a : single-contour seul + `n ≤ MAX_AA_EDGES (256)`. Multi-contour ou très grand path → fallback non-AA fan tess (G3.3b.2b lifterait ces restrictions).
- [x] [AaPolygonFillTest](gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/AaPolygonFillTest.kt) — 2 sous-tests : half-integer edges (fractional coverage attendu) + integer edges (full coverage partout dedans).
- [x] **Caveat sur le bump des scores existants** : ScaledRectsGM et Skbug12244GM ne bénéficient PAS — leurs paints n'ont pas `isAntiAlias` explicite, et le défaut SkPaint est `false`. L'infrastructure AA est là pour les futurs GMs qui demandent l'AA path explicitement.

### G3.3b.2b — Multi-contour via stencil-and-cover ✅

Au lieu d'ear-clipping côté CPU (~200-300 LOC d'algorithme géométrique), j'utilise l'approche **stencil-and-cover** GPU-native (Ganesh's standard pour les paths arbitraires). Plus simple à coder, gère naturellement les holes via le winding count.

- [x] **Depth-stencil texture** ajoutée à `SkWebGpuDevice` (format `Depth24PlusStencil8`, dimension viewport, usage `RenderAttachment`). Depth never used (`depthCompare = Always`, `depthWriteEnabled = false`) — 8 stencil bits seuls.
- [x] **stencilWritePipeline** : color writes off (`writeMask = None`), front-face `IncrementWrap`, back-face `DecrementWrap`, compare `Always`. Track winding count.
- [x] **coverPipelineCache** (par blend mode) : color writes on, stencil compare `NotEqual` avec reference value 0. Fill pixels where winding count != 0 (kWinding fill rule).
- [x] **`StencilCoverPolygonDraw : PendingDraw`** : `stencilVerts` (per-contour fan tessellation concaténée) + `coverVerts` (bbox 2-triangle quad) + scissor + color + mode.
- [x] **`fanTessellateContours`** helper : fan-tess chaque contour depuis son propre 1er vertex, concatenated triangles. La winding du contour (CW vs CCW) → triangle face → stencil incr ou decr → holes naturellement gérés.
- [x] **`drawPath` dispatch** : `contourStarts.size > 1` → stencil & cover ; single contour → existing fan path (G3.3a). AA multi-contour : encore throws (G3.3b.3).
- [x] **`flush()` render pass** : single render pass avec color + depth-stencil attachments, 2 draws : stencil pass puis cover pass. `setStencilReference(0u)` avant cover. depth-stencil cleared (`Clear` loadOp), discarded after (`Discard` storeOp).
- [x] **Impact mesuré** : Skbug12244GM **90.33 → 99.29 (+8.96 %)**. Le hole est correctement exclu. Reste ~160 pixels (0.71 %) — AA edge mismatch (reference rasterized avec AA, notre fill non-AA binary). G3.3b.3 (AA stencil-and-cover) closerait ce gap.
- [x] **Backward compat** : single-contour paths inchangés (gardent fan-tess + AA path G3.3a). Tous les 29 tests `:gpu-raster:test` PASS.

### G3.3b.2c — Cross-test BatchedConvexPathsGM ✅

Validation end-to-end du feature stack convex AA (G3.3b.1 + G3.3b.2a) par un GM upstream non trivial.

- [x] **`BatchedConvexPathsWebGpuTest`** : 10 single-contour convex cubic-Bezier polygons, scales décroissants, translucent SrcOver stacking on black. Exercise G3.3b.1 cubic flattening + G3.3b.2a AA polygon coverage + G2.1 translucent SrcOver + G6.0/G6.1 colorspace en un seul GM.
- [x] **Score** : **99.94 %** (~160 pixels de drift, sub-channel, sur les bords AA des cubiques). Ratché à floor=99.85.
- [x] **FillTypeGM / ConcavePathsGM** : tentés, bloqués par features deferred — FillTypeGM exerce `kInverseWinding` (cf G3.3b.2b throw line 1005), ConcavePathsGM exerce AA multi-contour (cf G3.3b.2b throw line 998). Repris dans G3.3b.3.

### G3.3b.2d — Suivi GMs (post-G3.3b.2c) — 3 nouveaux cross-tests ✅

Quick wins additionnels sur la surface actuelle, zéro modif au device. Pure ajout de tests pour élargir le harness.

- [x] [FiddleWebGpuTest](gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/FiddleWebGpuTest.kt) — `onDraw` no-op, BG blanc seul. Exerce le path "no draws → background-only clear" et le post-process G6.0/G6.1 sur une couleur uniforme. Score **100.00 %**. Ratché à floor=99.99.
- [x] [ClipDrawDrawWebGpuTest](gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/ClipDrawDrawWebGpuTest.kt) — crbug.com/423834 repro : séquences `clipRect + drawRect + drawRect` non-AA testant la cohérence d'arrondi integer-edge entre `clipRect` et `drawRect`. Pure rect fast-path (G1.2 scissor + G2.3a non-AA fill). Score **100.00 %**. Ratché à floor=99.99.
- [x] [Bug7792WebGpuTest](gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/Bug7792WebGpuTest.kt) — 16 paths line-only multi-contour exerçant moveTo/close edge cases (skbug.com/40039046). kWinding non-AA fill via stencil-and-cover (G3.3b.2b). Score **99.99 %** (~70 pixels drift sub-channel, AA edge mismatch identique à Skbug12244, closé par G3.3b.3). Ratché à floor=99.94.
- [x] **SimpleRectGM / DrawRegionGM** : tentés mais 10 000 `drawRect` calls saturent le path "per-draw render-pass" (cf. note bulk rendering §G3.3b.1 ↑). Test hang. Reportés à un bulk-draw follow-up.

### G3.3b.3a — AA multi-contour via stencil-and-cover ✅

Lift le throw `require(!paint.isAntiAlias)` sur le branch multi-contour : la passe stencil reste identique (winding count), la passe cover utilise un nouveau pipeline AA dont le fragment shader produit la fall-off de bord à partir des segments d'arête de TOUS les contours.

- [x] **`aa_stencil_cover.wgsl`** : nouveau shader, même layout uniform que `aa_polygon.wgsl` (`color + viewport + edgeCount + edges[256]`), mais les `edges[i]` portent `(Ax, Ay, Bx, By)` au lieu de `(a, b, c, _)`. Le fragment itère les segments, calcule `dist = length(p - clamp_to_segment(p, A, B))`, prend le min, et produit `coverage = clamp(minDist + 0.5, 0, 1)`. Robuste sur concave/multi-contour parce qu'on mesure aux segments, pas aux droites infinies.
- [x] **`StencilCoverAaPolygonDraw : PendingDraw`** : `stencilVerts` (fan tess concaténée comme G3.3b.2b) + `coverVerts` (bbox inflated par 1px) + `edges`/`edgeCount`. Construit par `buildContourEdgeSegments` (helper companion).
- [x] **`aaStencilCoverPipelineFor(mode)`** : layout `aaPolygonPipelineLayout` (réutilisé), stencil state identique à `coverPipelineFor` (NotEqual-0), shader AA. Pipeline cache par blend mode.
- [x] **`drawPath` dispatch** : `contourStarts.size > 1 && paint.isAntiAlias && totalVerts <= MAX_AA_EDGES` → nouveau path. Sinon fall-through vers le path non-AA existant (G3.3b.2b).
- [x] **Trade-off documenté** : l'AA capture la moitié intérieure de la fall-off (1.0 à 0.5px à l'intérieur du contour, 0.5 sur le bord), la moitié extérieure est perdue parce que la stencil rejette les fragments outside-path. Boundaries un demi-pixel plus durs que le reference. Acceptable vs throw.
- [x] **Test** : [ConcavePathsWebGpuTest](gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/ConcavePathsWebGpuTest.kt) — 29 cells (mix multi-contour et single-contour concave AA). Score **93.23 %**. Les cells multi-contour rendent bien ; les cells single-contour concave restent fausses (toujours sur la min-coverage shader G3.3b.2a, valide convex uniquement). Closing the rest demanderait soit router single-contour concave vers le nouveau path stencil-and-cover, soit triangulation concave.

### G3.3b.3b — kEvenOdd + inverse fill types ✅

Généralise les deux pipelines `coverPipelineCache` et `aaStencilCoverPipelineCache` pour porter les 4 valeurs de `SkPathFillType`. La pipeline change par `(blend mode, fill type)` :

  - `readMask` : `0xFF` (kWinding) / `0x01` (kEvenOdd)
  - `stencilCompare` : `NotEqual` (non-inverse) / `Equal` (inverse) contre ref 0

Le cover quad sélectionné par `drawPath` :

  - Non-inverse : bbox path (1px inflated)
  - Inverse : viewport plein (clip scissoré par la passe)

- [x] **`SkPathFillType` import** + ré-keying des deux caches en `Pair<SkBlendMode, SkPathFillType>`.
- [x] **`viewportTrianglesFor(w, h)`** : helper compagnion, 2-triangle quad spanning toute la device.
- [x] **`drawPath` dispatch** : retire la garde `require(!path.fillType.isInverse())`, passe `path.fillType` à `StencilCoverPolygonDraw` / `StencilCoverAaPolygonDraw`, sélectionne le cover quad selon `isInverse()`. Single-contour convex non-inverse garde son chemin rapide (`AaPolygonDraw` / `PolygonDraw`) ; inverse OR concave route vers stencil-and-cover.
- [x] **Test** : [FillTypeWebGpuTest](gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/FillTypeWebGpuTest.kt) — grille 4×4 des 4 fill types × 2 scales × AA on/off sur multi-contour. Score **99.55 %** (ratché à floor=99.5).

### G3.3b.3d — Two-pass AA cover (outside-half AA loss) ✅

Close la perte du demi-pixel extérieur de l'AA falloff sur le path stencil-and-cover : après la passe stencil, le cover phase émet maintenant DEUX sub-draws partageant les mêmes edge data + cover quad.

  - **Inside half** : pipeline existant (compare op par fill-type, entry point `fs_inside` = ex-`fs_main`). Coverage = `clamp(minDist + 0.5, 0, 1)` sur les fragments que le stencil compte comme inside.
  - **Outside half** : nouveau pipeline avec compare op FLIPPÉE (kWinding/kEvenOdd → `Equal` 0, inverse → `NotEqual` 0) et entry point `fs_outside`. Coverage = `clamp(0.5 - minDist, 0, 1)` sur les fragments que le stencil compte comme outside.

Les deux sub-draws sont mutuellement exclusifs au niveau fragment (le stencil aiguille chaque fragment vers exactement un d'eux), donc jamais de double-cover. La somme intègre au bon profil AA across la half-pixel boundary.

- [x] **`aa_stencil_cover.wgsl`** : factor `minSegmentDistance(p)`, splitté en `fs_inside` (ancien `fs_main`) + `fs_outside` (formule miroir). Pas de changement de layout uniform.
- [x] **`CoverageSide { Inside, Outside }`** + ré-keying de `aaStencilCoverPipelineCache` en `Triple<SkBlendMode, SkPathFillType, CoverageSide>`. `aaStencilCoverPipelineFor(mode, fillType, side)` sélectionne compare op + entry point selon `side`.
- [x] **`flush` dispatch** : après `setVertexBuffer(coverVB)`, deux `setPipeline(...)` + `draw(...)` consécutifs (Inside puis Outside).
- [x] **Tests** : ConcavePathsGM **98.90 → 99.31 %** (+0.41, ratché floor 99.25). FillTypeGM stable à 99.55 % (les non-AA cells dominent le mismatch résiduel à tolérance 8). BatchedConvexPathsGM (route `AaPolygonDraw`, pas touchée) et Skbug12244GM (non-AA) inchangés.

### G3.3b.3 — Reste à faire

- [ ] **Cache intra-frame** : si le même path est dessiné plusieurs fois, ne pas re-tessellate.
- [ ] **Tests futurs** : `ConvexPathsGM`, `ArcOfZorroGM`, `crbug_*` family, `FillTypePerspGM`.

### G3.4.1 — Stroke générique via SkStroker (skeleton + premier GM) ✅

Lift le throw `require(paint.style == kFill_Style)` sur `drawPath`. Quand `paint.style` n'est pas Fill, on appelle `SkStroker.fromPaint(paint, resScale).stroke(path)` en source-space pour produire l'outline path, puis on récurse dans `drawPath` avec une copie du paint forcée à `kFill_Style`. Le CTM s'applique ensuite à l'outline pendant la rasterisation, comme côté CPU (`SkBitmapDevice.drawPath`).

- [x] **`drawPath` dispatch** : retire le `require(kFill_Style)`. `kStroke_Style` → outline → recurse fill. `kStrokeAndFill_Style` → fill puis outline → recurse fill (la séquence canonique Skia).
- [x] **`resScale`** : `ctm.getMaxScale().coerceAtLeast(1f)` (même formule que `SkBitmapDevice`). Garantit que le chord error du flattening en source-space reste sous 0.25 px en device-space sous CTM x1000.
- [x] **Routing aval** : l'outline d'un contour fermé est multi-contour (outer + reversed inner) → stencil-and-cover kWinding (G3.3b.2b/G3.3b.3a). L'outline d'un contour ouvert est single-contour concave (left + cap + reversed-right + cap) → AA stencil-and-cover via détection convexité (G3.3b.3a.2). Le shader/pipeline existant traite tout.
- [x] **Test** : [CubicStrokeWebGpuTest](gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/CubicStrokeWebGpuTest.kt) — 3 strokes AA cubic Bezier (sub-1 % stroke-width variation). Score **98.57 %**. Premier GM non-rect stroke qui ne throw plus.

### G3.4.3 — True hairlines via `1 / resScale` stroke width sur drawPath ✅

`SkStroker.fromPaint` traite `paint.strokeWidth ≤ 0f` comme `1f` en source-space — donc sous `scale(0.5, 0.5)` on obtient un trait de 0.5 device-pixel, sous `scale(2, 2)` un trait de 2 device-pixels. Pas un vrai hairline Skia, qui doit toujours faire **exactement 1 device-pixel** quel que soit le CTM.

- [x] **Fix** : dans `SkWebGpuDevice.drawPath`, juste avant `SkStroker.fromPaint(...)`, si `paint.strokeWidth ≤ 0f`, on synthétise une copie du paint avec `strokeWidth = 1f / resScale`. `SkStroker` produit alors un outline de `1 / resScale` unités source = 1 device-pixel après le CTM. Sous CTM identité (`resScale = 1f`) c'est `strokeWidth = 1f`, comportement identique au passé. Sous heavy scale (`BigRectGM` à 1000×) c'est `strokeWidth = 0.001f` et le shader AA gère la sub-pixel coverage.
- [x] **`paint.copy()`** existe sur `SkPaint` ([SkPaint.kt:401](kanvas-skia/src/main/kotlin/org/skia/foundation/SkPaint.kt)). `SkPaint` est mutable → `.apply { strokeWidth = ... }` après copy.
- [x] **`SkStroker` non-touched** : `fromPaint` voit toujours une `strokeWidth > 0` (notre synthèse). Pas besoin de toucher `SkStroker`.
- [x] **Test** : [PathHairlineStrokeTest](gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/PathHairlineStrokeTest.kt) — open polyline (10,20)→(50,20) avec `strokeWidth = 0f`, non-AA, asserte qu'au moins un pixel sur la ligne (row 19 ou 20) est peint et que 5 rows au-dessus / en-dessous restent background.
- [x] **GM impact** : aucun GM existant exercice les hairlines via `drawPath` (BigRectGM hairline passe par `drawRect`, qui a son propre chemin). PathInvFillGM est ratcheté 99.50 → 99.52 mais l'amélioration est antérieure au fix (mesure stale, vérifié par run sur master). G3.4.2 prépare le terrain pour futurs GMs hairline-path.

### G3.4 — Reste à faire (caps/joins, hairlines, plus de GMs)

- [ ] **Caps** : butt OK (par défaut du flatten polyline) ; square + round caps livrés par `SkStroker` mais à valider sur GMs ciblés (`StrokeRectGM`, `OverStrokeGM`).
- [ ] **Joins** : miter avec bevel fallback OK ; bevel + round joins à valider (`InnerJoinGeometryGM`, `FatPathFillGM`).
- [x] **Hairlines drawPath** (`strokeWidth ≤ 0`) : G3.4.3 — synthèse `strokeWidth = 1f / resScale` avant `SkStroker.fromPaint`, donne ~1 device-pixel sous tout CTM. `drawRect` hairlines restent sur leur fast-path inchangé.
- [ ] **Tests futurs** : `OverStrokeGM`, `EmptyStrokeGM`, `StrokeRectGM`, `ScaledStrokesGM`, `ArcOfZorroGM`.

### Vérification G3
- [x] G3.1 : 4 tests neufs (3 unit stroke + 1 cross-test BigRectGM 70.7%). Stroke axis-aligned débloqué.
- [x] G3.2 : 3 tests neufs (2 drawPaint + 1 ClipStrokeRectGM 96.6%). drawPaint TODO clos, 2e GM cible G2 vert.
- [x] G3.3a : 3 tests neufs (PolygonFillTest : quad + triangle + curve-throws). drawPath skeleton convex polygon débloqué. ScaledRectsGM toujours bloqué par kPlus.
- [x] G3.3b.1 : Bezier flattening (Quad / Cubic / Conic). 1 test converti + 1 test neuf. Curves rendent. Concave + AA = G3.3b.2.
- [x] G3.3b.2a : AA polygon coverage (convex, single-contour) via bbox + fragment edge-distance. 2 tests neufs (AaPolygonFillTest). Pas de bump immédiat sur les ratchet entries (les GMs existants n'ont pas isAntiAlias=true sur leurs paths).
- [x] G3.3b.2b : multi-contour via stencil-and-cover (winding count, holes naturally handled). Skbug12244GM 90.33 → 99.29 (+8.96).
- [x] G3.3b.2c : cross-test `BatchedConvexPathsGM` 99.94 % — feature stack convex AA validé end-to-end sur 10 cubic Bezier polygons translucides.
- [x] G3.3b.2d : 3 cross-tests neufs (FiddleGM, ClipDrawDrawGM, Bug7792GM) avg 99.99 % — zéro changement device, élargissement du harness.
- [x] G3.3b.3a : AA multi-contour via stencil-and-cover + AA edge-segment shader. ConcavePathsGM débloqué à 93.23 %.
- [x] G3.3b.3a.2 : single-contour concave routé vers stencil-and-cover AA via détection convexité cross-product. ConcavePathsGM **93.23 → 98.90 (+5.67%)**.
- [x] G3.3b.3b : kEvenOdd + inverse fill types via `coverPipelineCache: Pair<SkBlendMode, SkPathFillType>`. FillTypeGM **99.55 %** (4×4 grille des 4 fill types × scales × AA).
- [x] G3.3b.3c : 4 cross-tests neufs (AnalyticAntialiasInverseGM 99.98 %, PathInvFillGM 99.50 %, ConvexLineOnlyPathsFillGM 98.75 %, Crbug1472747GM 98.16 %) avg **99.10 %** — zéro changement device, élargissement du harness post-G3.3b.3b sur inverse fills + kEvenOdd multi-contour conic.
- [x] G3.3b.3d : two-pass AA cover (inside + outside) ferme la perte du demi-pixel extérieur sur stencil-and-cover. ConcavePathsGM **98.90 → 99.31 (+0.41 %)**.
- [x] G3.4.1 : SkStroker integration skeleton. drawPath accepte kStroke/kStrokeAndFill via outline → recurse fill. CubicStrokeGM 98.57 % (premier GM non-rect stroke).
- [x] G3.4.2 : 5 cross-tests stroke neufs post-G3.4.1 (SmallArcGM 99.80 %, ArcOfZorroGM 99.73 %, Bug6987GM 99.77 %, ScaledStrokesGM 96.49 %, AddArcGM 93.30 %) avg **97.82 %** — zéro changement device, élargissement du harness sur stroke + cubic flattening (resScale 8× et 50000×, drawArc(useCenter=false), addArc, multi-shape + multi-scale).
- [x] G3.4.3 : true hairlines via `strokeWidth = 1 / resScale` synthèse avant `SkStroker.fromPaint`. 1 unit test neuf (PathHairlineStrokeTest). Aucun GM existant exercice ce chemin (les GMs hairline passent par drawRect) — préparation.
- [x] G3.4.4 : 6 cross-tests stroke caps/joins neufs (Bug12866GM 95.24 %, InnerJoinGeometryGM 98.71 %, QuadCapGM 99.80 %, ZeroLineStrokeGM 94.05 %, StrokerectAnisotropicGM 98.11 %, RectPolyStrokeGM 98.21 %) avg **97.35 %** — zéro changement device, élargissement du harness sur caps (butt/round) et joins (miter/round/bevel) variants, plus stroker resScale extrême (1200) et CTM anisotrope.
- [ ] G3.4 : plus de GMs (StrokeRectGM bloqué par drawPoints non-implémenté côté device, WideButtCapsGM bloqué par `c.clear()` qui passe par bitmap.eraseColor).

---

## Phase G4 — Shader infra : gradients en WGSL

**But** : porter `SkLinearGradient` + `SkRadialGradient` (Phase 5a/5b master) en WGSL. **Ce slice acte le remplacement définitif du format shader Skia (SkSL) par WGSL.**

### Contrat shader
- [ ] **Pas de SkSL → WGSL transpilation.** Chaque type de shader (`SkLinearGradient`, `SkRadialGradient`, `SkBitmapShader`, future composite) a son **template WGSL** (~30-80 lignes), instancié au moment du draw avec :
  - [x] Stops uniformes : `array<vec4f, MAX_STOPS>` + `array<f32, MAX_STOPS>` positions + `count` (G4.1, MAX_STOPS = 16).
  - [ ] Inverse local-matrix uniform.
  - [ ] Tile mode comme constante de spécialisation (kClamp / kRepeat / kMirror / kDecal — 4 variantes compilées séparément).
- [ ] **Précompilation** : pipeline cache keyed par `(shader-type, tileMode-X, tileMode-Y, blend-mode, ...)`. Évite recompile par draw.

### G4.1 — Linear gradient (kClamp, drawRect)

Premier slice gradient end-to-end : `SkLinearGradient` en kClamp uniquement, route `drawRect`/`SkPath.isRect` sous CTM axis-aligned. Les autres tile modes (kRepeat/kMirror/kDecal) throw avec un message explicite — couverts par G4.1.x.

- [x] `:gpu-raster` ajoute `implementation(project(":cpu-raster"))` pour voir `SkLinearGradient` côté main classpath (les classes gradient vivent dans `:cpu-raster`).
- [x] [shaders/linear_gradient.wgsl](gpu-raster/src/main/resources/shaders/linear_gradient.wgsl) — ~80 lignes. Uniforms : `startEnd` (vec4), `viewport`, `countPad` (count en bits f32), `positions[16]` (vec4 each, .x = pos), `colors[16]` (vec4 premul). Fragment : `t = clamp(dot(p - start, dir) / |dir|^2, 0, 1)`, scan linéaire des stops, lerp premul `(1-u) * A + u * B`.
- [x] `SkWebGpuDevice` :
  - nouvelle `PendingDraw` variant `LinearGradientRectDraw` (scissor + endpoints device-space + stops packés + blend mode).
  - pipeline cache `linearGradientPipelineCache` keyed par `SkBlendMode` (les stops varient par-draw via uniform, seul l'état de blend différencie les pipelines compilés).
  - `drawPath` détecte `paint.shader is SkLinearGradient && path.isRect() != null && ctm.isAxisAligned` et route vers `drawLinearGradientFillRect` ; chemin existant `drawFillRect` solid-color inchangé (SkCanvas envoie les rects shaded ici parce que le fast path `device.drawRect` exige `paint.shader == null`).
- [x] [LinearGradientRectTest](gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/LinearGradientRectTest.kt) — unit test : red->blue gradient horizontal sur un rect 60px, sample 3 colonnes (gauche/milieu/droite) pour vérifier l'interpolation.
- [x] [ShallowGradientLinearWebGpuTest](gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/ShallowGradientLinearWebGpuTest.kt) — cross-test GM : `ShallowGradientLinearGM` (800×800, drawRect, kClamp, `0xFF555555 -> 0xFF444444`). Score : **100.00 % (byte-exact, 640000/640000 pixels matching)**.

### Détails restants
- [x] `SkLinearGradient.wgsl` : élargir aux tile modes kRepeat / kMirror / kDecal (G4.1.1) ; routes via `drawPath`/`drawPaint` quand la géométrie n'est pas un rect (G4.1.2).
- [ ] `SkRadialGradient.wgsl` : `t = length(p - center) / radius`, idem lookup (G4.2).
- [ ] **Linear-premul output** : working space identique au raster (D3) — le shader retourne directement en F16 linear-Rec.2020.

### Tests
- [ ] Tous les GMs gradient déjà raster (ports Phase 5a-5g du master) re-runnables sur GPU.

### Vérification G4
- [x] G4.1 : `ShallowGradientLinearGM` 100.00 % sur GPU (premier gradient GM vert).
- [x] G4.1.1 : 4 cross-tests neufs (ShallowGradientLinearNoditherGM 100.00 %, AnalyticGradientShaderGM 100.00 %, Crbug938592GM 99.80 %, ConvexPathsGM 99.85 %) avg **99.91 %** — zéro changement device, élargit le harness sur 2 gradients linéaires kClamp supplémentaires (dont 16-stop AnalyticGradients qui sature MAX_GRADIENT_STOPS), valide path.isRect sous scale(±1) axis-aligned (Crbug938592), et apporte enfin un GM convex-paths riche (38 shapes mixant rect/circle/oval/rrect/cubic/quad/conic/arc).
- [x] G4.1.2 : linear gradient sur path non-rect via AA stencil-and-cover. Nouveau shader [shaders/aa_stencil_cover_gradient.wgsl](gpu-raster/src/main/resources/shaders/aa_stencil_cover_gradient.wgsl) — 8 entry points (2 sides × 4 SkTileMode), partage `aaPolygonBindGroupLayout` + `polygonShader` pour la stencil pass via layout `(color, viewport, ...)` byte-compat. Côté device : nouveau `PendingDraw` `StencilCoverAaGradientDraw` + cache `aaStencilCoverGradientPipelineCache` keyed `(blend, fillType, tileMode, side)`. dispatch : `paint.shader is SkLinearGradient && paint.isAntiAlias && ctm.isAxisAligned` route TOUS les AA paths (multi-contour, single-contour concave, single-contour convex) vers le pipeline gradient — factoring "b" du plan G4.1.2 (les paths convexes AA non-gradient gardent le fast path `AaPolygonDraw`). Tests : 4 unit tests neufs dans [LinearGradientPathTest](gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/LinearGradientPathTest.kt) (kClamp/kRepeat/kMirror/kDecal sur un circle), pas de cross-test GM dans cette slice (aucun GM en scope ne dessine un linear gradient sur un path non-rect sans maskFilter/colorFilter/pathEffect).
- [x] G4.1.3 : 6 cross-tests neufs (ShallowGradientRadialNoditherGM 100.00 %, ClampedGradientsGM 100.00 %, HardstopGradientShaderGM 100.00 %, TestGradientGM 99.96 %, FillrectGradientGM 95.77 %, FillCircleGM 98.58 %) avg **99.05 %** — zéro changement device, élargit le harness sur radial-on-rect avec offset-center (ClampedGradients), premier multi-tile-mode GM cross-test (HardstopGradientShaderGM exerce kRepeat/kMirror/kClamp sur 8 stop configs), gradient + drawCircle/RRect/RoundRect mixed (TestGradientGM), corner-case stop layouts (FillrectGradientGM : sub-range / single-stop / disjoint / unsorted / ignored duplicates sur linear + radial), et oval-fill stack sous scale axis-aligned (FillCircleGM).
- [x] G4.2.2 : radial gradient sur path non-rect via AA stencil-and-cover. Mirror exact de G4.1.2 pour `SkRadialGradient` : nouveau shader [shaders/aa_stencil_cover_radial_gradient.wgsl](gpu-raster/src/main/resources/shaders/aa_stencil_cover_radial_gradient.wgsl) — même 8 entry points, même uniform leading `(color, viewport, ...)` pour partage de bind-group avec la stencil pass, slot 2 swap `startEnd` → `centerRadius` (cx, cy, radius). Côté device : nouveau `PendingDraw` `StencilCoverAaRadialGradientDraw` + cache `aaStencilCoverRadialGradientPipelineCache` keyed `(blend, fillType, tileMode, side)`. dispatch : `paint.shader is SkRadialGradient && paint.isAntiAlias && ctm.isAxisAligned` route TOUS les AA paths non-rect vers le pipeline gradient ; le rect rapide G4.2 reste prioritaire. Tests : 4 unit tests neufs dans [RadialGradientPathTest](gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/RadialGradientPathTest.kt) (kClamp/kRepeat/kMirror/kDecal sur un circle), pas de cross-test GM (même blocage qu'en G4.1.2 — pas de GM radial-gradient-sur-non-rect sans maskFilter/colorFilter/pathEffect dans le scope actuel).
- [x] G4.1.4 : 1 cross-test neuf (BeziersGM 96.98 %) — zéro changement device, exerce G3.4.1 SkStroker sur 20 paths quad/cubic AA strokes à widths variables.
- [x] G4.1.5 : 1 cross-test neuf (B119394958GM 93.82 %) — premier GM combinant drawArc(useCenter=false) + kRound_Cap. Round-cap endpoints émis comme demi-cercles par SkStroker.
- [x] G4.1.6 : 1 cross-test neuf (ArcCircleGapGM 99.14 %) — stroked circle + tangent-arc à huge radius (~1097), hairline strokeWidth=0 via G3.4.3.
- [x] G1.4.1 : 1 cross-test neuf (AnalyticAntialiasConvexGM 99.90 %) — premier GM utilisateur de `canvas.clear()` débloqué par G1.4 (#524). Sous rotate(1°), 5 configs convex-fill AA.
- [ ] OvalGM gradient row, autres tile-mode GMs sur path non-rect.

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

### G6.0 — CPU-side colorspace post-process en WebGpuSink ✅

Slice probe pour valider l'hypothèse "colorspace est le drift dominant" avant d'engager la refacto F16 + render-target. Hypothèse confirmée largement.

[`WebGpuSink.draw`](gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuSink.kt) applique maintenant per-pixel après readback :

1. **sRGB byte → linear sRGB float** : sRGB transfer inverse (`x <= 0.04045 ? x/12.92 : ((x + 0.055) / 1.055)^2.4`).
2. **linear sRGB → linear Rec.2020** : 3×3 BT.2020 primaries matrix.
3. **linear Rec.2020 → Rec.2020-encoded byte** : Rec.2020 OETF (`L < 0.0181 ? 4.5*L : 1.0993 * L^0.45 - 0.0993`).

Reference `original-888/*.png` est encodée en `DM_REFERENCE_COLOR_SPACE = Rec.2020 primaries + Rec.2020 transfer` (confirmé via `TestUtils.kt:50`). Le transform aligne le comparison space.

**Impact mesuré (massif) :**

| GM | Avant G6.0 | Après G6.0 | Delta |
|---|---|---|---|
| ThinRectsGM | 90.89% | **100.00%** | +9.11 |
| ClipStrokeRectGM | 96.60% | **100.00%** | +3.40 |
| ScaledRectsGM | 87.79% | **100.00%** | +12.21 |
| BigRectGM | 70.70% | **99.90%** | +29.20 🔥 |
| ThinStrokedRectsGM | 87.19% | **94.21%** | +7.02 |
| Skbug12244GM | 70.87% | **90.33%** | +19.46 |

**Vérification G2 : 4/4 GMs cibles ≥ 90 % atteint** (4 à 100 %, BigRectGM à 99.90 %). Le target G2 historique est tenu.

Scope appliqué uniquement aux **cross-tests via WebGpuSink** ; les unit tests (`RectFillCrossTest`, `BlendModeTest`, etc.) lisent les bytes raw du device et continuent à comparer dans l'espace pré-transform.

### G6.1 — Transform dans le pipeline GPU ✅

Le CPU loop de G6.0 est déplacé dans un GPU present pass. Architecture cleane : le device sort directement en Rec.2020-encoded, WebGpuSink devient trivial (juste byte → bitmap repack).

- [x] [`present_pass.wgsl`](gpu-raster/src/main/resources/shaders/present_pass.wgsl) : fragment shader qui `textureLoad`s un pixel de l'intermediate texture, applique sRGB-inverse → BT.2020 primaries matrix → BT.2020 OETF, écrit le résultat. Vertex stage = full-screen Bjorke triangle.
- [x] [`present_identity.wgsl`](gpu-raster/src/main/resources/shaders/present_identity.wgsl) : variant identité (passthrough) pour les unit tests qui veulent les raw bytes sans transform.
- [x] `SkWebGpuDevice` ajoute un nouveau paramètre `applyColorspaceTransform: Boolean = false` (default raw sRGB pour les unit tests). `WebGpuSink` passe `true` pour le cross-test path.
- [x] `intermediateTexture: GPUTexture` (RenderAttachment | TextureBinding) ajouté. Tous les draws (rect / polygon / aa-polygon) targetent désormais ce texture. Le present pass à la fin de `flush()` lit l'intermediate via `textureLoad`, applique le transform (ou pas), et écrit dans `target.colorTexture` qui est ensuite copié au staging buffer pour readback.
- [x] `WebGpuSink` drop le CPU loop ; juste `rgbaBytesToBitmap` maintenant.
- [x] **Scores identiques à G6.0** : 4/6 à 100 %, BigRectGM 99.90 %, ThinStrokedRectsGM 94.21 %, Skbug12244GM 90.33 %. Math bit-équivalente, perf+ (le shader évalue le transform en parallèle vs CPU loop séquentiel), cohérence pipeline (architecture alignée sur le plan G6 original).
- [x] **Bug WGSL non-ASCII** : le shader d'origine avait `→` dans un commentaire. Le parser WGSL de wgpu4k 0.2.0 truncate le code à la première frontière non-ASCII (bug connu G0 post-mortem #4). Fix : ASCII strict appliqué.

### G6.2 — F16 intermediate render target (livré, sans switch colorspace)

L'intermediate texture est passé de `RGBA8Unorm` à `RGBA16Float`. Les shaders continuent à émettre des valeurs premul **sRGB-coded** (pas linear) — la math de blending et l'encodage du readback restent identiques à G6.1. Le bénéfice réel : la précision intermédiaire passe de 8 bits à F16, ce qui aide les futurs cas où les valeurs intermédiaires accumulent du drift (gradient lerps, image filters, blends translucents stackés). Les scores cross-test sont conservés (un seul GM à -0.01 %, re-ratchet trivial).

- [x] `intermediateTexture` en `RGBA16Float` au lieu de `RGBA8Unorm`. Toutes les pipelines de draw (rect / polygon / aa-polygon / stencil-cover / gradients) targetent ce format. Le `target` final (readback) reste `RGBA8Unorm` ; le present pipeline écrit en `RGBA8Unorm`.
- [x] **Format configurable au constructeur** : `SkWebGpuDevice(..., intermediateFormat: GPUTextureFormat = GPUTextureFormat.RGBA16Float)`. Default F16 (comportement actuel), callers sur drivers sans blend F16 ou en contraintes mémoire peuvent passer `RGBA8Unorm` pour retomber sur G6.1.
- [x] **Pas de linéarisation au shader output** — option (a) du plan initial testée et abandonnée : linéariser à l'output fait passer la WebGPU blend hardware en linear blending, ce qui diverge de 30 à 65 points de pourcentage du référence cross-test sur les GMs à stacking translucents (`BatchedConvexPathsGM`, `ClipDrawDrawGM`, `FillTypeGM`). Voir le bloc kdoc de `intermediateTexture` dans `SkWebGpuDevice.kt` pour le détail.
- [x] `present_pass.wgsl` et `present_identity.wgsl` inchangés (la sémantique des valeurs lues reste sRGB-coded comme en G6.1).
- [x] **Scores cross-test** : 1 GM re-ratché (B119394958 93.75 % → 93.74 %, drift -0.01 % attribué à la précision F16 sur une edge pixel). Tous les autres scores byte-équivalents.
- [x] La précision F16 dans l'intermediate sera exploitable par les futurs slices (gradients haute-précision, image filters) sans toucher au format.

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
