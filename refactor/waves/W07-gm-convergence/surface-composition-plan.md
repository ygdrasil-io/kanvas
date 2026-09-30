# Surface Composition Domain Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Livrer une composition SRGB_ENCODED publique cohérente du draw au snapshot, avec défaut LINEAR et pipeline partagé.

**Architecture:** Domaine immutable de cible, sources/proofs/store authentifiés
dans ce domaine, W3 pour Rect et W6a pour plain layer. Admission whole-frame
et refus terminal ; aucune route legacy encodée ou renderer parallèle.

**Tech Stack:** Kotlin/JVM, render-ir, gpu-plan, gpu-renderer/WebGPU, JUnit Surface/Picture.

**Spec:** [surface-composition-design.md](surface-composition-design.md).
Base `b1ba6d0f3622d8cab8f41605ddf6e3f404ddf8be`, parent PR2421.

## Global Constraints

- Fonts, codecs/décodage externe et jpg-color-cube hors périmètre.
- Aucun GM/adaptateur, PNG de référence, seuil, exclusion ou score historique modifié.
- Pas de test d'infrastructure ajouté : Surface/Picture publiques, pixels natifs, refus/sentinel/récupération, deuxième rendu.
- Géométrie dans math ; nomenclature I/F32/64 pour ses valeurs et types.
- Pas de plafond augmenté, epsilon ajouté, enveloppe élargie, seal/authentification assoupli ou formule WGSL indépendante de la preuve.
- CompositionDomain.LINEAR est le défaut ; SRGB_ENCODED explicite est porté par la cible, pas par SceneSnapshot/Picture.
- PixelFormat est exclusivement l'ordre des octets publics, pas un choix de domaine.
- Un seul runtime Gradle/GPU, tests bornés240s, --offline --no-daemon --no-build-cache ; native133 n'est pas GREEN.
- Terra implémente ; Sol ne sert qu'aux reviews ; Astra pour cette architecture et la revue finale, pas chaque micro-correction.
- Documents durables dans refactor, PR draft empilée, aucun merge/clôture W7.

## Review Focus

1. Domaine perdu au ré-emballage d'une occurrence enfant : witness layer + restore alpha dans Task1.
2. Snapshot mal étiqueté ou canaux BGRA changés : full/subset/copy/Picture/replay croisé coloré dans Task1.
3. Cache/proof identique malgré domaine distinct : alternance des mêmes ressources entre domaines dans Tasks1/2, audit de l'owner typé.
4. Opération exclue dissimulée après un draw valide : refus whole-frame + sentinel/discard/récupération dans Tasks1/2.
5. Double transfert/prémultiplication lors de alpha0, paint opacity ou store intermédiaire : witnesses indépendants dans Tasks1/2.

---

### Task 1: Composition solide, plain layer et snapshot end-to-end

**Files (primary seams; transport/exhaustive dispatch edits elsewhere must be named in the report):**
- Create: `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/CompositionDomain.kt`.
- Modify: `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/RenderBackend.kt`.
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/RenderConfig.kt`, `GPUColorFormat.kt`, `RenderResult.kt`, `Surface.kt`, `ImageEncoder.kt`.
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceRouter.kt`, `GPUPlanSurfaceCandidateGate.kt`, `GPUPlanRenderContextOwner.kt`, `GPUPreparedSurfaceColorMapping.kt`, `GPUPreparedSurfaceProductRouter.kt`.
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/CompositionAdmissionV1.kt` (shared scene-level encoded policy, not a compiler/backend).
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanCapabilities.kt`, `CapabilityCompilerChain.kt`, `W3SolidRectPlanCompiler.kt`, `SourceDeferredRenderConstructionV4.kt`, `FrameSourceLayoutV4.kt`, `OccurrenceSourceInputV1.kt`.
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ColorSourceProofCompilerV1.kt`, `ColorSourceProofV1.kt`, `NumericOperationGraphV1.kt`, `ImageSampleExecutionPlanV1.kt`, `ImageNumericOperationGraphV1.kt`.
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5eImagePlanCompiler.kt` (projection source-deferred des seules images admises, conservation de la cible).
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerPlanCompiler.kt`, `W6aLayerGraphConstruction.kt`, `W6aLayerGraphValidation.kt`, `W6PlainLayerCompositeRecipeV1.kt`.
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderBackend.kt`, `GpuFrameOutput.kt`, `GpuPlanTaskListLowerer.kt` and existing W6 plain-layer lowering.
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedSurfaceNativePreflight.kt`, `GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt` and target capability projection.
- Create tests: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7SurfaceCompositionPixelTest.kt`, `W7CompositionCpuOracle.kt`.
- Adjust existing public tests only where AUTO/explicit-format incompatibility intentionally changes their contract; retain behavioral assertions and report each adjustment. Do not add internal tests.

**Interfaces:**
- Produces `enum class CompositionDomain { LINEAR, SRGB_ENCODED }` in render-ir.
- Produces last `compositionDomain: CompositionDomain = LINEAR` on RenderConfig and RenderTargetDescriptor; `GPUColorFormat.AUTO` default with strict resolution from the spec.
- Produces last `premultiplication: ImagePremultiplicationV1 = TRANSFER_ENCODED_LINEAR_PREMUL` on RenderResult and GpuFrameOutput.of, explicitly populated from the authenticated completed target.
- Produces `PlanLogicalColorFormat.RGBA8_UNORM_ENCODED_SRGB_PREMUL`; target resolution maps domain↔logical/native format without guessing from PixelFormat.
- Produces `ColorSourceProofV1.compositionDomain`; private issuance, source deferred construction and native joins bind/check it. LINEAR identities unchanged; encoded identities cannot alias.
- Consumes existing SOURCE_SPACE/PREMUL/SRGB image semantics; only DrawOrigin.IMAGE + GeometryNode.ImagePatch, integer source/destination of equal extent, nearest1:1 and admitted CTM. ImageShader/nine/lattice/atlas excluded; no new archive version or image representation enum member.
- Tests produce oracle helpers using existing Interval/DrawResult primitives: `solid(color: ColorARGB, domain: CompositionDomain): Array<Interval>`, `srcOver(source: Array<Interval>, destination: Array<Interval>): Array<Interval>`, `store(value: Array<Interval>, domain: CompositionDomain): DrawResult`, and `storedSample(value: DrawResult, domain: CompositionDomain): Array<Interval>`. These helpers model each actual UNORM store, no production evaluator.
- Intermediate Task1 intentionally still refuses gradients in the encoded domain; Task2 enables them after this task review.

- [ ] **Step 1: Public RED and oracle preflight.** Add API transport minimally with default historical behavior, without claiming a capability. Write `encodedSolidSrcOverDiffersFromLinearAndIgnoresByteLayout`: 1×1 white then black `ColorARGB.of(128,0,0,0)`, non-AA/SrcOver, both domains and both PixelFormats. Compute all expected intervals before render; encoded center is 127, linear center187. Require bounded, disjoint oracle RGB sets; do not pick tolerances from actual output. Assert all channels through `W5fSurfacePixelFixtures.assertNativePixels`, Render/Readback, and identical second render. Run this test to a genuine behavior/refusal RED (compile failure is not RED).

- [ ] **Step 2: Target/domain source-to-native path.** Resolve AUTO at the Surface boundary; reject target contradictions before fallback. Apply CompositionAdmissionV1 to the complete encoded scene before general compiler selection and resource acquisition. Carry domain through target, candidate, deferred source and occurrence child target reconstruction. Parameterize existing W3/solid/image proof graphs and plain W6 layer joins, logical/native capability, materialization/cache and output; keep excluded recipes closed. Parameterize the separate W3 recognizeDrawColor path, preserving its identity-only CTM constraint. Carry the image target through W5e's source-deferred projection; admission must distinguish ImagePatch from Rect FILL without admitting other image origins. Encoded image loading retains swizzle, coordinate/texel guards and ZERO_ALPHA_GUARD; omit only unpremultiply/EOTF/repremultiply. Native RGBA8Unorm receives encoded-premul values, not linear values with a changed format. Every accepted source/target domain pair is authenticated.

- [ ] **Step 3: Layer and snapshot public witnesses.** Add:
  - `plainLayerPreservesDomainAndAppliesRestoreOpacityOnce`: 2×2, opaque white root; layer containing overlapping red A128 then blue A64; restore alpha255 and128. Model intermediate quantification; assert each expected interval, alpha, native Render/Readback and second render. Empty layer remains transparent. Bounds integer and an integer translation/hard clip variant exercise occurrence rebasing.
  - `encodedFullSubsetSnapshotsPreserveRepresentationThroughPicture`: 3×2, red A128 and (R128,G64,B32,A128), full/subset, RGBA/BGRA; `assertEquals(SOURCE_SPACE,snapshot.premultiplication)`; full/subset/copy retain exact byte order. Public memory+wire Picture playback to encoded Surface matches the independent oracle, second render included. Old LINEAR snapshot still reports TRANSFER_ENCODED_LINEAR_PREMUL.
  - `encodedSnapshotReplaysInLinearWithoutRetagging`: nearest1:1 over blue, use existing W5e SOURCE_SPACE oracle for LINEAR. Red source yields near (188,0,187,255) rather than encoded (128,0,127,255). Add non-saturated source and zero alpha.
  - `surfaceDomainsAlternateWithoutCrossTargetCacheReuse`: reuse the same color/image resources in LINEAR→ENCODED→LINEAR→ENCODED, prove each native result in its domain.
  - `drawColorPreservesDomainDirectAndThroughLayer`: colored semi-transparent drawColor, identity CTM, direct and plain layer, independent domain oracle and second render. A translated drawColor is a geometry refusal even if its translation is integer.
  - `ordinaryEncodedImageWithZeroAlphaCannotLeakRgb`: ordinary SOURCE_SPACE/PREMUL/SRGB pixels with nonzero RGB and A0, both public layouts, over a colored destination; must remain transparent. Do not substitute a normalized transparent snapshot.
  - `compatibleExplicitFormatsMatchAutoForColoredPixels`: colored semi-transparent source, AUTO versus explicit RGBA8_UNORM_SRGB/LINEAR or RGBA8_UNORM/SRGB_ENCODED, RGBA/BGRA assertions and second render. Extend an existing public LINEAR fixture reaching legacy continuation if eligible outside exclusions; otherwise report the missing witness and statically verify single target resolution and output swizzle along that path.
  Existing toImage/full/subset must consume actual RenderResult metadata; recording-only encoded image snapshot refuses rather than minting an ambiguous no-pixel Image.

- [ ] **Step 4: Closed scope, transactional refusal and budget.** `excludedCompositionFrameRefusesBeforeReadbackAndRecovers` appends an excluded operation after a valid encoded draw; test AA, stroke, path, Src, non-integer transform, translated drawColor, imageFilter, colorFilter, nested and sibling layers, linear-premul old snapshot, non-nearest/scaled image, ImageShader/nine/lattice/atlas, and target-format contradictions with the spec's diagnostic suffix. For each frame: readPixels throws, destination sentinel unchanged, discard then valid encoded draw renders twice; configuration-invalid surfaces remain invalid and a separate valid Surface recovers. Test recording-only snapshot refusal without native work. Derive one B formula from exact resource descriptors before its first run and persist it in report; B succeeds, B−1 refuses transactionally with the existing resource diagnostic, then smaller frame recovers.

- [ ] **Step 5: GREEN and commit.** Run W7SurfaceCompositionPixelTest plus W7GradientAlphaSurfacePixelTest, the three baseline W6a methods and two W5e snapshot methods. Archive full command, exit.json, XML identities and failure names. Correct implementation defects, not oracle tolerance. Commit product/tests only after green; report all touched transport files, native target/domain joins and any intentional compatibility changes. Task review must approve spec and quality before Task2.

### Task 2: LinearGradient dans le même domaine de composition

**Files:**
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/CompositionAdmissionV1.kt`, `MaterialSourceConstructionV4.kt`, `ColorSourceProofCompilerV1.kt`, `ColorSourceProofV1.kt`, `FrameSourceLayoutV4.kt`, and prepared gradient definition/program identity owners as required by the existing interfaces.
- Modify tests: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7SurfaceCompositionPixelTest.kt`, `W7CompositionCpuOracle.kt`.

**Interfaces:**
- Consumes Task1's CompositionDomain, typed target/proof/output contract and whole-frame admission.
- Produces sRGB/CLAMP LinearGradient in SRGB_ENCODED, both existing GradientAlphaModes, directly `C_srgb * alpha`; no EOTF round trip and no new interpolation semantics.
- Oracle adds `gradient(left: ColorARGB, right: ColorARGB, tF32: Float, alphaMode: GradientAlphaMode, domain: CompositionDomain): Array<Interval>` using independent published interpolation equations and existing directed primitives. Source-only formula is then fed to Task1's SrcOver/store helpers.

- [ ] **Step 1: RED.** `gradientAlphaModeIsIndependentFromCompositionDomain` uses t=.5, stops white255→black0 alpha0, then red A128→blue A64; all two alpha modes × two domains. Compute and require bounded expected outputs before GPU, with white destination; assert disjoint relevant alternatives. A valid but unsupported encoded gradient must RED before enabling it. If the existing envelope cannot bound a proposed fixture, report the CPU evidence before substituting a finite a-priori witness; never widen it.
- [ ] **Step 2: Source graph and identities.** Carry the target domain into the existing prepared definition and graph compiler; select encoded straight/premul output before domain transfer. Keep physical stop/slab preparation independent of execution domain; include domain in execution/proof/native identities. Domain guard before one-stop collapse and after wrapper unwrapping; reject unadmitted interpolation/tile/source wrappers instead of falling to legacy.
- [ ] **Step 3: Integration witnesses.** `gradientDomainSurvivesLayerPictureAndRepeatedTargets` reuses the same gradient in direct root, one plain layer, memory and wire Picture; validates both alpha modes, paint opacity, mutated original stop list after recording, second render and alternating Surface domains. `encodedGradientHardStopsDegenerateAndZeroAlpha` covers equal-position stops, last-stop selection for degenerate CLAMP, both-zero and alpha1 endpoints. Negative LINEAR interpolation/REPEAT/MIRROR/sweep/radial/composed shader cases retain transactionality. No new AA/stroke support.
- [ ] **Step 4: Final targeted GREEN and commit.** Run the entire Task1 selection plus updated W7SurfaceCompositionPixelTest; verify no omitted prior identity, every XML/exit, budget/refusal recovery. Commit changes; scoped task review, correction/re-review if needed.

## Commands and controller delivery

Use a new archive per run; the wrapper refuses overwrites:

```sh
rtk proxy ruby /private/tmp/kanvas-w7-composition.25GaUn/bounded-run.rb /private/tmp/kanvas-w7-composition.25GaUn/RUN_NAME 240 ./gradlew :kanvas:test --offline --no-daemon --no-build-cache --tests org.graphiks.kanvas.surface.W7SurfaceCompositionPixelTest -I /private/tmp/kanvas-w7-image-opacity.YmtXjI/isolated.init.gradle -Pw7.validationDir=/private/tmp/kanvas-w7-composition.25GaUn/RUN_NAME --console=plain
```

Replace RUN_NAME with the evidence stage, add explicit --tests selections as
listed in each step, preserve exact command in report. No parallel Gradle/GPU.

- [x] Baseline19/19, three XML classes, exit0 on b1ba6d0f3, before source changes.
- [ ] Both tasks spec/quality reviewed; public assertions green, no infrastructure test added.
- [ ] One global :kanvas:test bounded240s, compare named identities to preceding global, distinguish inherited/new failures and interruptions. Do not call a native crash GREEN.
- [ ] Same corpus631/443, timeout30s retained, compare all198 old RGBA/results/diagnostics/reference hashes; no GM gain assumed for unused opt-in.
- [ ] One final whole-branch Astra review, one correction wave and one scoped re-review if necessary.
- [ ] Update pilotage/status with actual measured outcome/reserves; stacked draft on #2421, never merge or close W7. Hairline geometry and later GM port remain explicitly separate.
