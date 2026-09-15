# W5g Composed and Procedural Materials Implementation Plan

## Current execution checkpoint — Tasks1–4 accepted; convergence Task5 continues sequentially

Task4 est acceptée. Le commit Noise `9a14bdf96` puis le correctif `b63f4ceb3` conservent exactement les 62 chemins autorisés et les 5 témoins vérifiés. Le `final4` exécuté une seule fois sur la source figée enregistre 197/197 PASS publics : Noise Surface57, Noise Picture10, Blend Surface125 et Blend Picture5, 0 failure/error/skip. Gradle1/Executor242 exit133 reste de cause `UNKNOWN` et n'est pas une preuve native green. La même review Sol clôt I1 (lecture des transforms de clip en schema 5) et I2 (singleton typé FLOOR partagé par fraction, proof, emitter et corners) comme `ADDRESSED`, sans nouveau finding, `Ready to proceed: Yes`. Le test schema 5 prouve honnêtement le roundtrip déterministe et le refus public borné ; il ne prétend pas produire des pixels de clip complexe. Les gaps génériques `Picture.playback(Canvas)`/`SetClip` et composite complex-clip restent hors scope et devront être résolus seulement si les tests d'intégration Skia l'exigent. Task5 peut maintenant démarrer depuis `b63f4ceb3` ; final19, cinq compiles, review globale et Draft PR W5g restent ouverts.

R40 modifie seulement le child Fractal de la fixture de mutation Picture de 8 à 2 octaves. La matrice mutable, les deux familles/seeds, le Perlin stitched, le contrôle bounded/disjoint avant Surface, le snapshot, les replays original/décodé et repeat2 restent identiques. Le 8 local traversait plusieurs floors sur le domaine conservateur 1x1 et laissait légitimement alpha inclure zéro avant Matrix Divide ; ce stress est sans lien avec le contrat mutation. Les octaves8/255 restent obligatoirement couvertes ailleurs pour BOTH familles/BOTH géométries et stitch. Coût : moins de stress haute-octave dans ce seul test Picture ; final4 doit vérifier la matrice complète. Aucun guard/proof/production/tolérance/wire change, aucun nouvel owner/scope.

Epoch 7 : 9 cas ciblés, 8 PASS/1 FAIL, 0 erreur/skip, 27 s. R39 rend vertes les six compositions ; R37/R38 rendent verts Stroke/recovery et la frame mixed-lane. Seule la mutation Picture Fractal8 refuse encore. Gradle1/Executor232 exit133, cause `UNKNOWN`; 62 sources identiques pre/post.

R39 resserre seulement deux expressions Noise à opérandes répétés après exécution de la preuve ordinaire : `f*f*(3-2*f)` sur le même `f`, puis `a+(b-a)*t` avec le même objet `a` et le smooth original `t`. Extrema dirigés, gamma existant et coûts DAZ/FTZ explicites sont intersectés dans des copies fraîches des contextes d'identité ; toute intersection vide refuse. Le graph, le WGSL, les opcodes, les coordonnées/adresses, la tail 255, les guards Divide et les tolérances restent inchangés. Coût si erreur : over-admission par amplification sous-estimée, mauvaise identité ou branche négative/DAZ omise ; toutes les preuves publiques et la review Sol restent requises. Aucun nouvel owner/scope.

R38 corrige seulement la fixture mixte déjà owned : `clipRect(..., antiAlias=false)` conserve le hard clip admis par W4d au lieu du défaut public AA=true, sans promouvoir l'AA ni toucher au fallback/preflight. R37 aligne seulement l'attente Stroke sur la frontière publique existante `unsupported.material.composed.slice`, atteinte dans `CapabilityCompilerChain` avant le diagnostic bare-Noise. Coût si erreur : perte du discriminant mixed-lane ou validation d'un ordre de refus déplacé ; les trois lanes, les refus/récupérations répétés, final4 et la review Sol restent obligatoires. Aucun nouvel owner : Task4 reste 39 Modify, 5 Kotlin Create, 21 assets et 2 producteurs éphémères.

Epoch 6 nonfinale : 22 tests, 13 PASS/9 FAIL, 0 erreur/skip ; six domaines 2/8/255, trois routes work/refusal/recovery, trois mixes Noise/gradient/image et le budget public 32 KB passent. Restent sept refus `numeric-domain-unbounded`, un refus mixed `uniform32` causé par la fixture AA et une attente Stroke erronée. Gradle1/Executor231 exit133, cause `UNKNOWN`; sources identiques pre/post. L'optimisation R36 remplace seulement la matérialisation de jusqu'à 65 536 couples par une union `BooleanArray` bornée et le shortcut exact y=all256, sans changer l'ensemble d'adresses ni la preuve.

R36 précise la preuve commune sans changer le programme : `q-floor(q)` n’est resserré que lorsque le même FLOOR original, DAZ négatif inclus, est singleton ; les adresses de gradient énumèrent au plus 256 `floor+corner`, avec floor BigInteger négatif vers −∞, période BigInteger/euclidean remainder et `(permutation[x]+y)&255`, puis lisent les U16 du slab authentifié. Les domaines larges gardent all256 et toutes les opérations/guards WGSL/R28 restent identiques. Coût si erreur : over-admission par oubli DAZ ou erreur signed/corner/period/permutation ; les preuves publiques négatives, transforms, stitch, seeds, Matrix/mutation, final4 et review Sol restent obligatoires. Aucun nouvel owner/scope : Task4 reste39 Modify/5 Kotlin Create/21 assets/2 producteurs éphémères.

R35 — `GPUFramePreflighter.hasExactW3SessionScratch` et ses callers direct/composite rejoignent Task4 : l’enveloppe exacte W3 doit reconnaître l’unique allocation Noise R34 seulement si le même slab packet-stage et sa preuve/ranges l’authentifient, avec bytes/limits/category/kind/lifetime/peak exacts. Les deux preparations target/staging et tous les guards scratch/geometry/blend/clip/readback/Gradient restent inchangés. Coût si erreur : refus direct/composite, slab contrefait ou double charge. Scope Task4 : 39 Modify, 5 Kotlin Creates, 21 assets, 2 producteurs historiques éphémères ; aucun test d’infrastructure/harness.

R34 — Five exact downstream owners are added so R33's authenticated `NoiseTableData` row survives W3/W5b lowering and composite joining without duplication: `GpuPlanTaskListLowerer`, `W5bNativeGeometryGraphLowerer`, `W5bAnalyticRectGraphLowerer`, `W5aCompositeGraphLowerer` and `GPUCorePrimitivePreparedFrameTaskListBuilder`. Geometry-only counts exclude exactly the Gradient/Noise source rows; W3 peak/category/allocation equality includes the one Noise slab; the exact W3 packet envelope authenticates it; W5b uses one graph-derived label; composite deduplicates that same physical row once; the already-owned combined native budget verifies the declaration and adds zero duplicate. All original geometry/capability/scratch/destination/gradient/AA/clip/lifetime/refusal gates remain unchanged. Cost if wrong: direct-W3 rejection, undercount, counterfeit admission or per-lane duplicate charge; public work/storage/recovery, frozen final4 custody and sole Sol review remain required. Task4 scope is now 38 Modify, 5 Kotlin Creates, 21 assets and 2 ephemeral historical producers; no infrastructure test or harness/native change.

R33 — Classify ONLY gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraph.kt: its construct function may attach the SAME authenticated complete frame NoiseTableSlabV1 as one NoiseTableData Buffer resource when an actual final material source consumes it, and the existing single-sample/four-sample explicit-path inventory functions may recognize ONLY that sealed source-consumed role alongside GradientStopData. MAIN FULL complete construct/validateConstructionTopology, validateExplicitAa4PathContracts and both complete single/four-sample functions plus actual constructBound/constructLaneBound callers, complete PreparedComposedSourceV5/range ownership and FrameSourceLayoutV4.checked inventory read; original owner currentSHA==BASEd1 blobSHA56b217e67466d23e31e2e31d0061fda667d22932602efcd447eba65b66a595f6 BEFORE authorization. Why: R31's distinct role is otherwise unused, so native Noise storage/explicit frame reservation alone omit the logical graph's physical resource/lifetime inventory. Bind the exact issued source definition/frame owner/ranges to the SAME complete physical slab, validate original storage/copy capabilities and full checked bytes before attaching it, keep original PlanResource.of shape/usage/lifetime guards and exact peak(resources,passes) equality. Noise's source-consumption exception is not permission for arbitrary unreferenced buffers or role-name-only authority. Charge the complete physical slab ONCE across metadata/frame/native inventory, never per seed/node/lane duplication or an added second preparation/allocation; identify any required further owner BEFORE edit. Preserve ALL original gradient allocation contracts, geometry/reference/dependency/AA/clip/witness checks and old refusal priority unchanged; recognizing the resource in AA inventory does NOT promote Noise AA4/H or relax a material/geometry gate. Cost if wrong: missing/counterfeit source resource, duplicate slab charge, peak/lifetime mismatch or accidental AA/H admission can conceal ownership defects or break admitted gradients; exact same issued resource/range/frame/native mapping, complete prepermit inventory, authentic public Noise/work/storage/recovery and retained historical/final4/frozen-source/commit custody plus sole Sol audit remain mandatory. Existing owned FrameSourceLayoutV4 may reconcile this SAME accounting; no new API/numerical policy/allocator/cache/native lifecycle/harness/infrastructure test or native experiment is authorized. Exact Task4 Modify32→33;5KotlinCreates/21assets/2R29ephemeral owners unchanged.

R32 — Authorize ONLY the six already-classified format-{8,9,10}-noise-{nan,infinity}.base64 Create assets as deliberately malformed documented-old-wire constructions, NEVER genuine writer-produced payloads. Start from each ROOT-verified genuine553byte integral archive; preserve every other byte including KPIC/version/schema/cull/commands. Replace only the four tile-width F32 word ranges at zero-based offsets138/267/368/497 with big-endian0x7fc00000 (NaN) or0x7f800000 (+Infinity), retaining heights0x40800000, original base/octave/seed/optional tags and both Perlin13/Fractal14 copies. Why: all three actual public old captures reject non-finite-value before bytes; MAIN FULL pinned8/9/10 material/size/f32 writer and reader functions plus public Picture facade/complete decode boundary read, and independent actual553byte source/header/tag/width receipts verify the requested fields (tags117/246/347/476). A normal read-only Node byte assembly may print candidate base64/SHA for apply_patch; no new source/helper path, file-write trick, private API, current11 writer, version/header substitution, native/harness/Gradle or existing-fixture overwrite. Cost if wrong: wrong offsets or provenance labels could test a different corruption and mask legacy tile validation; preserve original/new full SHA and actual changed offsets, compare all15 genuine assets unchanged, then test ONLY public Picture.fromByteArray returning null for invalid archives with valid public replay/recovery. Genuine supported absent/zero/integral8/9/10 replay and new11 roundtrip, fractional/out-of-range/constructor boundaries and full Noise/final4/Sol/convergence/final19 gates remain mandatory; constructed rejection fixtures close no positive historical compatibility or static-only test gate. Exact32Modify/5KotlinCreates/21assets/2R29ephemeral owners unchanged.

R31 — Classify ONLY gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanResources.kt to append PlanResourceRole.NoiseTableData after DecodedImageV1, preserving every existing role/order/name and all PlanResource.of/allocator/format/size/usage/lifetime guards unchanged. Why: MAIN FULL actual224line owner and complete PlanIdentity.kt read; existing resource IDs use role.name plus resource ordinal, and a distinct authenticated U32 Noise slab cannot borrow GradientStopData provenance. Cost if wrong: a new enum case can break exhaustive consumers or create an unauthenticated/uncharged graph resource; identify each exact further owner/affected complete function BEFORE edit, preserve original gradient allocation readers, and authenticate complete Noise/gradient/image/uniform/final-snapshot inventory through SAME proof/frame/native mapping and public Noise/work/storage/recovery plus sole Sol review. No other caller, allocator, GPU ABI, numerical policy, lifetime/harness or infrastructure test is authorized by this append-only owner ruling. Task4 Modify set31→32;5KotlinCreates/21archive-assets/2ephemeralR29producer owners unchanged.

R30 — Classify ONLY signature-compilation adaptation in existing kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceRouterTest.kt: add the same typed MaterialFrameLimits trailing parameter to the8 existing GPUPlanSurfacePort.plan overrides at original279/335/390/480/542/576/695/751; the final capabilityChainPort override MUST forward that supplied snapshot to executor.plan. Fully qualified type may avoid import changes; no added/defaulted override argument, no assertion/mock/counter/body rewrite or execution of this infrastructure class. Why: actual complete8override bodies, port interface/production forwarding and public executor.plan FULL-read MAIN; approved exact immutable work-limit transport changes the implemented interface, and overload fallback could discard nondefault/zero limits. Cost if wrong: compilation/source compatibility break or dropped snapshot could conceal work-budget defects; only9 narrow hunks, SAME snapshot through production compiler AND submitted-plan authentication, public Noise/work/refusal/recovery and Sol source audit remain required. This is necessary compile maintenance, not new infrastructure tests, a new public API policy or native/budget workaround. Exact Task4 Modify set grows30→31; the21classified archive assets and5Kotlin Create owners remain separate, no other test owner authorized.

R29 — Classify only the genuine historical Noise fixture producer /private/tmp/kanvas-noise-history.g1YWEJ/capture/HistoricalNoiseCapture.kt and disposable /private/tmp/kanvas-noise-history.g1YWEJ/capture.init.gradle, plus21 exact picture asset paths (format8/9/10 × noise-absent/zero/integral/fractional/nan/infinity/out-of-range.base64). The producer calls ONLY public PictureRecorder/Canvas.drawRect/finishRecordingAsPicture/toByteArray on clearly owned private exact-commit8/9/10 source exports, a serialized JavaExec/compile attachment for fixture capture, not an infrastructure/source/private test/native hook and not a current build/harness change. Fixed2x1/BOTHfamilies/.125,.25/oct1/seed7/SRC/noAA and null/(0,4)/(8,4)/(.5,4)/(NaN,4)/(+Inf,4)/(2147483648f,4) inputs; preserve public rejection honestly. Why: genuine old writer payloads must exist before tile/wire migration, no unrelated image fixture or v11 header substitution can prove compatibility. Cost if wrong: accidental current-writer classpath/cache substitution or bad capture inputs can fake historical provenance and mask reader regression; record exact old source commit/outputSHA/fullinputs/actual producer command and source identity, no copy of current build products, no existing fixture overwrite, no privateAPI/version-byte patch/assertions/cache purge/native workaround/helper agent. Valid accepted payloads may populate only classified paths; if a malformed input is rejected by the old public writer, leave that exact fixture pending and report original rejection BEFORE any documented-old-wire alternative is selected. Allgenuinevalid8/9/10/new11/publicmalformedreplay gates remain, no static-only closure or Noise acceptance.

Task4 production now authorized SAME Astra after genuineRED MAIN custody:12unique expected production-refusalFAIL0E/S, no oraclefailure; originalreport902/actualXML534 byte-exact,SHAa1a41071,29production==BASEd1blob==preRED at that gate,3testSHA exactpre/post/current. FAILED9s/Gradle1/noobserved133/nativeGREEN; bootstrappermissionfailureNOTRED. Original30Modify plus R30/R31 exact2owners =32Modify,5KotlinCreate/21assets/2R29ephemeral owners; any further owner beforeedit. Historical capture report now1663FULL MAIN/SHA3a9da8,8/9/10 realwriters exit0 in49/9/12s,9export/blobs exact,15decodedassets byte-exact to fullconsoles/SHA/lengths and producer/initSHA independently verified ROOT; not public replay/PASS. Six realpublic NaN/Infinity rejections preserved; R32 selects explicitly malformed old-wire assets only. Archive/publicwork/storage/final4/commit/Sol gates remain; Noise not accepted, no ROOT production/test fix or concurrent Gradle.

R28 — Approve SAME Task4 coherent102line derivation a8864216ed3d5d400f801d57e74cc1dad38fcbd3c6958177882968df077e6d60 after FULL MAIN numerical review and independent unchanged30source custody at BASEd1d3807a62af4c78ef56ff4343313dcf532c6df8. Keep original contextual F32 q until BOTH exact-bit-integral; exact lattice/corners/periods use four little-endian U32 limbs (<2^128) plus separate sign, signed Euclidean modulo before low8, checked conversion/increment/doubling and explicit carry-out handling in remainder arithmetic, never saturation/truncation. The input-bit quantum certificate K<=149 (tighter min149/max0/25-e) is a proof witness, not an octave ceiling or program variant. Under R2 the integral-phase abstraction eliminates ONLY dead q/floor/corner/mod/period work before conceptual overflow; retain ALL requested0..255 signed-or-absolute-zero term multiplications, accumulator additions and amplitude halves, including subnormal sum FTZ. It is not literal overflowing-F32 emulation; extreme mixed domains unprovable before BOTH-integral remain NumericDomainUnbounded, NOT valid-family closure. Resolve unspecified bilerp scalar expansion with pinned Skia Add(a,Multiply(Subtract(b,a),t)), x then y; decoded |gradient|<2, integral dots<5/differences<11 certify exact zero endpoints without 0*infinity. Freeze CPU RN-F32 stitch candidates/ratios, strict<LOW elseHIGH/low0HIGH, and exact positive-half-up unsaturated integer period. SAME typed Phase(q) preserves original floor/subtract identity and validates original finite operands, including negative-subnormal Floor-input DAZ then retained subtraction; correlated fraction hull[-2^-126,1] is an operation certificate, NOT a clamp/color box/second source evaluator, and shared generic Floor/IntegerModulo guards remain unchanged. Why: finiteF32 grid/exactdoubling, finitegradient endpoint proof, preserved tail operations and actual pinned source schedule close the concrete numeric design ambiguity without epsilon/lower ceiling/lifted q. Cost if wrong:128-bit carry/remainder/corner or correlated DAZ/phase defects can select wrong gradients, over-admit or over-refuse domains; extra integer/proof/branch code cost, scalar schedule can shift bytes. Genuine independently bounded public zero/nonzero RED and ROOT XML/unchanged-production epoch custody BEFORE production, mandatory BOTHfamilies3routes nonzero255/stitch/transforms/negative/clamp/work/resources/genuine8/9/10 archives, frozen final4/commit hashes/soleSol and convergence storage/final19/whole gates remain binding. No global numerical/geometry/API/harness change, helper/new reviewer or family acceptance.

Historical test-only authorization, superseded ONLY by the explicit genuine-RED production gate above: Authorization boundary: this approves the concrete representation/operation certificate, NOT implemented proof correctness, mandatory-positive evidence, Task4 acceptance or any production edit yet. SAME Astra may write only the classified public test/oracle paths and run a serialized focused genuine zero/nonzero RED epoch on unchanged production. Append exact inputs, independent bounded bytes computed before Surface/Picture, complete command/output/XML/source SHA to SAME report. ROOT independently verifies fresh actual XML and unchanged production custody before explicit production authorization. No staging/commit/helper/reviewer/Gradle outside the focused task; all remaining gates stay mandatory.


Task4 fresh adapted Astra/high /root/w5g_noise_impl runs numeric-derivation FIRST on acceptedBASEd1d3807a62af4c78ef56ff4343313dcf532c6df8 after MAIN FULL completed222line owning brief. No Noise production before concrete phase/stitch/zero-tail/accumulator-rounding derivation FULL MAIN approval, then authentic public RED custody. No helper/reviewer/concurrent Gradle/new policy/userpause; all remaining wholeW5g gates unchanged.

Task3 is functionally accepted after the original sole Sol review and SAME reviewer scoped fix1 closure: I1 ADDRESSED, Spec Compliance COMPLIANT, Task quality APPROVED,0Critical/0Important/0newMinor. MAIN FULL-read the entire updated review and technically checked original component identity/reachability/unchanged numeric guards plus original public render helper. Source48b0bb4f4681d240ea87ac54b81696d8b08065e2 (parentb5af) contains ONLY20 added proof.contexts lines (R27), no arithmetic/test change; all18 tested/current/committed hashes match. Tasks1–3 deliver the complete Blend family within their explicitly bounded admitted domains, NOT general cubic/transparent-edge or all H/ISO admission. Noise4 numeric derivation is next, then implementation, convergence5, whole Sol gate and ONE stacked Draft on unchangedW5f#2400; no user continuation pause.

Correction1 Epoch16 now verifies the unchanged required method:1PASS0F/E/S, all original CTM/no-filter/WITH-restoring Rect/direct/stencil routes twice. FULL MAIN report5286–5504EOF and actualXML/SHA4af6451d… independently verified. Proof60619cda… is the ONLY changed source, original17 other source hashes exactEpoch15 and fixture unchanged. FAILED13s/Gradle1/native223exit133 causeUNKNOWN, not native-processGREEN. ROOT frozen18-source pre-custody verified; original final5 ONCE is authorized on amended source. Exact post-custody/coherentfixcommit and SAME Sol scoped I1+introduced-breakage review remain required before Task3/Blend acceptance or Noise.

Correction1 Epoch17 final5 verified MAIN:529 unique methods,529PASS0F/E/S, EXACT all529 original classname/name pairs,0missing/extra. All5 actual XML FULL EOF, exact report bytes/SHA verified; report FULL7557EOF. Independent18 source pre/post/current/committed SHA equal,proof60619cda… is the sole fix,fixtureb0cdaf31 unchanged. Command FAILED18m8/Gradle1/native224exit133 causeUNKNOWN,55tasks6EXEC49UP, not nativeGREEN or five separate compiles. SAME Sol scoped review closes I1 without confirmed new breakage; MAIN acceptance closes Task3/bounded Blend, not Noise, convergence, whole/native GREEN. All failed epochs, conservative cubic/optional SRC_OVER cells and R1–27 costs remain recorded.

R27 — Authorize ONLY existing ColorSourceProofV1.kt ColorRoundedGraphProofV1.prove.contexts comparison handling to preserve reachable original BranchComponent arm facts. MAIN FULL-read correction trace5264–5285EOF and independently checked complete original contexts/evaluators, matrix straightInput, coordinate compiler, binding words and public fixture: unchanged FTZ Min/Max keeps both projective-valid arms; opaque sample alpha1 versus transparent alpha0 is hulled to[0,1], and downstream alpha==0/1 guards lose that disjunction, reaching an impossible Divide with a zero-containing denominator. Enumerate original predicate reachable yes/no contexts for an unconditioned compared BranchComponent, evaluate the same corresponding arm under each and retain its bound keyed by SAME scalar identity before reapplying the original comparison; avoid re-expansion of already conditioned identities and validate original predicate operands/reachable operations. All numerical evaluators/guards, Min/Max FTZ, ProjectiveValid/Divide, sampler/decoder/coordinate graph, scalar identity and emitted WGSL remain unchanged. No alpha gap/epsilon/finite box/authority downgrade or fixture modification. Cost if wrong: extra context branching can grow proof cost or lose correlation/validate an unreachable operation incorrectly; unchanged required public method must pass all3routes twice, all529 original methods/refusals retained in frozen final5, exact fix SHA/commit custody and SAME Sol scoped I1+fix-introduced-breakage review mandatory. SAME Astra fixround1, FIX_BASE b5af755e1e1b1be2cc31435faeaa7589eb32c610; no new owner/helper/reviewer, Noise before acceptance or native workaround.

Historical original Task3 Epoch15 frozen final5:529 unique methods,528PASS/1knownFAIL/0ERROR/0SKIP.
Surface125=124P/1F,Picture5P,Image27P,ImageFilter92P,Interpolation280P.
Required restoring-filter invalid projective positive remains OPEN; identical
no-filter context and CTM-once pass. R25 required coverage partition passes.
Command FAILED17m47/Gradle1/native222exit133 causeUNKNOWN; no clean-build claim.
MAIN FULL report through5190EOF and all5 actual XML/SHA/byte-exact report copies
verified. Actual18 source SHA equal workerPre=ROOTpreFrozen=workerPost=current;
all18 within45classified owners,HEAD343/indexempty,ROOT3docs excluded,diffcheck0.
ROOT authorized exact18-path provisional commit for sole fresh Sol task review,
NOT Task3/Blend acceptance or Noise; committed-blob custody/review follow.

### Task3 R20/R21 amendments — no numerical-policy expansion

R26 — Bound the current projective investigation by using the already prescribed sole Task3 Sol gate, not a new helper/review loop. Finish the one R24 isolation, required-contract R25 update and other targeted cells; if no cause-specific defect is established, retain the unchanged failing transparent fixture as OPEN. Freeze the coherent source with ROOT custody, run mandatory final five classes once with all failures/native exits reported honestly, and provisionally commit only exact verified Task3 paths for the sole fresh Sol spec+quality review. DONE_WITH_CONCERNS is a review handoff, NOT functional Task3/Blend acceptance or permission to start Noise. Sol independently traces code/requirements; corrections stay SAME implementer rounds1–3 and SAME reviewer scoped rereview, all original transparent and full acceptance gates remain. Cost: review may explicitly reject incomplete work, but replaces blind fixture guessing/private instrumentation with one bounded independent causal audit; no test/result waiver or new numeric authority. ROOT still never fixes production/tests or runs concurrent Gradle; stage only after ROOT exact-path/SHA receipt, and no acceptance before actual required assertions and review close. No extra implementer/reviewer, new owner, native workaround, parent/PR mutation or discretionary user continuation pause.

| Ruling | Reason | Cost / required evidence |
| --- | --- | --- |
| R26 | If one public isolation cannot establish projective cause, prescribed sole Sol task audit can independently trace coherent source instead of blind fixture/probe loops. | Known assertion retained OPEN, mandatory final5 run and custody before provisional review commit; review may reject. Same-seat corrections/same-reviewer rereview, no functional acceptance or Noise before all gates close. |

R25 — Correct ROOT's own additional test cross-product from R22/R23, not the approved shared acceptance contract. ROOT reread authoritative W5 spec §§15–16.1 and original shared fixture/filter-placement table: each of the five wrong-order boundaries must have a meaningful bounded/disjoint native witness, and SRC_OVER/SRC_IN/DIFFERENCE must have positive final-blend coverage; the approved contract does NOT require every wrong-order boundary under every final mode. Keep all five boundaries at opacity0/.5,paint127,Blue,three routes twice in the already admitted SRC_IN and DIFFERENCE image controls. For SRC_OVER retain a genuine bounded positive image-restoration/paint/external-once witness with at least one useful bounded/disjoint order counterfactual (existing boundaries0/1), both opacity0/.5 and the same native routes, instead of forcing the unprovable extra child-filter-pushed fixed-function counterfactual. Preserve all failed epochs and exact optional SRC_OVER/0/2 preflight gap; no skip annotation, tolerance, source/proof arithmetic change or all-combinations claim. The common source computation is identical before the separate final tail; static Sol audits that shared ordering, while all five wrong orders remain publicly discriminated under both other final modes and legacy fixed-function/color controls stay unchanged. Cost: less redundant cross-product coverage, explicitly disclosed, not an omitted required ordering or waived renderer behavior. This supersedes only ROOT-added every-boundary/every-final clauses in R22/R23; original shared contract and final5/whole19 gates remain binding. No further blind projection/native search; one coherent required-contract test update then complete remaining causal projective investigation and frozen final covering.

| Ruling | Reason | Cost / required evidence |
| --- | --- | --- |
| R25 | Original authoritative shared contract requires five meaningful order discriminants and three final-mode positives, not ROOT-added exhaustive boundary×final cross-product. | Keep all5 under SRC_IN/DIFFERENCE and strong bounded SRC_OVER positive/counterfactual, all opacity/paint/routes; explicitly fewer redundant combinations, failedoptionalSRC_OVER/0/2 retained. Static source-order Sol audit and final gates unchanged. |

R24 — Permit one public causal isolation of the CURRENT unchanged exactly-zero swap/clamp context, keeping the same V5 Blend(dst, invalidImage) carrier: compare without restoring filter against the original with restoring filter. Do not test the invalid Image as an ordinary V3 source (different authority), invent a third context, touch proof/numerical arithmetic, or add private/internal diagnostics. Before either Surface, independently bound the corresponding Blend(dst, Transparent) and Blend(dst, Filter(Transparent)) expected pixels; both retain original CTM/context/leaf/validity and three native routes twice where admitted. Preserve original failing restoration fixture and every command/refusal if isolation still fails; this is diagnostic public behavior, not a waiver or permission to turn required transparent handling into NumericDomainUnbounded acceptance. ROOT consumed original capture/context/expression/bind-input and complete ProjectiveDivide/ProjectiveValid/branch/eager/point-arithmetic proof regions; no narrower transport cause established statically. Cost: one bounded public diagnostic epoch to locate whether failure precedes or follows restoration, no blind fixture cycle; cause-specific SAME-worker correction within current owners only after actual evidence, report exact remaining gap if unresolved. No new owner, probe, test infrastructure or numeric policy.

| Ruling | Reason | Cost / required evidence |
| --- | --- | --- |
| R24 | Two exactly-zero public projective fixtures refuseNumeric; static original context/proof/binding read did not establish narrower cause. | One same-V5-context filter/no-filter public isolation, pre-capture bounded expected pixels; original failure/transparent requirement retained, cause-specific correction only, no private probe or third blind context. |

Nonfinal12:4PASS0F/E/S, all16cubic tile topology pairs including7interiorDECAL, originalvaryalpha typedrefusal/recovery, aggregate512/3MB Binding controls. ROOT fullreport2584–2790 and actualXML/SHA verified. FAILED32s/native219133UNKNOWN, source not frozen; R23 order and R24 projective isolation precede final5/review.

R23 — Replace the coupled nondiscriminant channels of ONLY the still-frozen final SRC_OVER image-order projection with exact red0 and blue1; retain a genuinely nonzero green input coefficient and independent wanted/counterfactual disjointness before capture, all five boundaries, opacity0/.5, paint127, Blue destination and three routes twice. This is a deliberate fixture design correction, not reopening R22's alpha-only search: ROOT read the complete existing finish/colorThenBlend and fixed-function source/factor precision and transfer enclosure. A child-filter-pushed counterfactual remains nonopaque after paint127, so interior red=.125/blue0 can exceed the strict complete-pixel bound in nondiscriminant channels; source SRC/DIFFERENCE do not use that same fixed-function schedule. Stable endpoint channels are intended to remove that irrelevant coupling, not change the arithmetic envelope or make green constant. This remains a hypothesis until independent full byte sets and focused public evidence validate it. Keep SRC_IN/DIFFERENCE and old scalar/gradient controls untouched. One focused coherent changed-fixture epoch; if no valid discriminating complete pixel can be derived, preserve exact gap and no further blind candidate/native loop. Cost: changing only test projection endpoints may reduce nondiscriminant color coverage, so green must still discriminate each required wrong order and legacy color controls remain; no waived boundary, tolerance, private probe, helper arithmetic or production numerical modification. No new path/policy.

| Ruling | Reason | Cost / required evidence |
| --- | --- | --- |
| R23 | Existing nonopaque fixed-function counterfactual requires whole-pixel bound; interior nondiscriminant projection channels can couple irrelevant precision error. | Exact red0/blue1 with nonzero green independently disjoint for every wrong order, all cells retained, one focused evidence epoch; narrower nondiscriminant colors covered by unchanged legacy controls. No numeric change or blind loop. |

Nonfinal11: single512image admitted3MB thenpair authentic wrongW5eFrameBudget vsW5gBinding; R21 fix follows same seat. R22 SRC_OVER/0/2 still preflight, exactzero projective alsoNumeric refusal under diagnosis;5tests2PASS3FAIL0E/S, FAILED1m35/native218133UNKNOWN. ROOT fullreport2293–2583 and actualXML/SHA verified; no freeze/acceptance. R20 later interior receipt chronology honestly disclosed.

R20 bounded DECAL receipt: opaque4×4/local(2,2), all16 real manual taps and bounded/disjoint nearest alternatives is an allowed interior cubic DECAL positive. All16 both-axis topology pairs retained; nearest/linear and unchanged V3 cubic history own boundary-crossing controls. Original varying-alpha V5 numeric refusal/recovery retained. Limitation: no general transparent-edge/varying-alpha cubic admission; no additional inadmissible crossproduct or numeric relaxation.

R22 — After three recorded image-order fixture preflight attempts, authorize ONLY a bounded projection refinement in existing imageRestorationAndExternalFilterKeepFinalOrder for final SRC_OVER: allow an independently chosen nonopaque external constant-alpha projection (.25,.5,.75, alongside existing1), preserving the same projection in wanted and each relevant counterfactual and all five order boundaries at opacity0/.5, paint127, Blue destination, three native routes twice. SRC_IN/DIFFERENCE accepted targeted controls and original scalar/gradient witnesses remain unchanged. ROOT FULL-read epochs7–9 and actual epoch9 XML/SHA: the remaining failure is SRC_OVER/0/2 before Surface;0/1 and full SRC_IN/DIFFERENCE pass. Forced-alpha1 in the wanted final source versus external pushed into both children then paint127 uses different nonopaque SRC_OVER rounding paths; this is a hypothesis for independently bounded fixture construction, NOT an established production bug or discard-count measurement. Derive all wanted/wrong singleton-or-two-adjacent disjoint byte sets before capture; no empirical native tuning, widened tolerance, simplified constant-output witness, removed boundary, production/helper arithmetic modification or private instrumentation. Run one focused changed-fixture epoch; if it still fails, retain exact cause and do not reopen an unbounded search loop. Cost: additional authentic projection choices/evidence rather than a waived order cell; incorrect selection could mask ordering, so pre-capture independent disjointness and sole Sol audit remain mandatory. No extra path or new numeric policy.

| Ruling | Reason | Cost / required evidence |
| --- | --- | --- |
| R22 | Remaining SRC_OVER/0/2 preflight after three attempts; opaque external projection couples distinct rounding paths, hypothesis not renderer defect. | Nonopaque projection independently bounded/disjoint before capture, all order cells retained, one focused changed-fixture epoch not open-ended search. Public pixels and Sol audit required. |

Nonfinal9:3tests2PASS1preflightFAIL0E/S, SRC_IN/DIFFERENCE full order controls pass; image working domains passed8. Command FAILED1m7/Gradle1/native216133UNKNOWN. ROOT full report1134–2057 and actual9XML EOF/SHA verified; no source freeze/task acceptance.

R20 — Keep common sampler/proof arithmetic and numeric restrictions unchanged. Use an independently bounded/disjoint fixed-alpha existing cubic/manual-tap positive domain instead of declaring all varying-alpha cubic graphs admissible. Retain the original varying-alpha mixed graph as an explicit unchanged NumericDomainUnbounded public refusal with valid same-owner/runtime repeated recovery; do not delete, skip, weaken or broaden its expected bytes. Nearest/linear retain the original varying-alpha 2×1 data. Both-axis tile coverage, including an actual bounded cubic DECAL positive domain, mixed working domains, restoration and final-blend cells remain mandatory. Epoch6 fixed-alpha control is positive supporting evidence, not strict one-variable causal isolation: discriminatingProjection searches again, and ROOT has not verified equal frozen external matrices. Qualify that claim or freeze one identical independently valid projection before asserting strict causality; no private instrumentation or rerun merely for exit0. ROOT read the complete original alpha relational proof and historical admitted fixed-alpha cubic public test: a varying-alpha hull/deviation can cross zero before working-domain unpremultiply, so useful graphs may remain unprovable under the unchanged common V5 proof. Cost: a narrower disclosed cubic numeric admission and additional authentic refusal/recovery witness; never full-cubic/global ISO closure or causal inheritance from V3. No extra path, finite color box, epsilon, recipe, tolerance or new numeric authority.

R21 — Within the existing FrameSourceLayoutV4.checked owner, validate the COMPLETE simultaneously declared fragment texture inventory for each actual V5 source draw, using original final-draw/lane binding facts: all composed texture rows plus the actual DestinationReadV1 snapshot and, only when that actual composition ABI binds it, scalar coverage texture. Existing native ABI3 binds snapshot1; ABI4 binds snapshot+coverage2. Authenticate the source-to-final-draw mapping and ABI against actual lane/pass construction; if a source descriptor can be reused under distinct final draw bindings, check every occurrence, not merely a canonical/unique source. Do not infer coverage from clip existence, count attachment/stencil/prefix textures not bound to this source stage, take a frame-wide union, add unconditional2, or let a child Blend request a snapshot. Preserve existing destination-alone checks, real snapshotted caps and typed binding/min-group checks; no invented cap or sampler. Preserve original V3/legacy nonUniform physical aggregation and its exact diagnostic first, then charge NEW V5 identity-reserved complete texture/staging additions with W5g Binding on unavailable/exceeded physical facts or additive physical-envelope failure. Local checked-arithmetic failure in that new physical addition belongs to Binding, without changing the global legacy catch or leaf-invalid priority. Composed uniform and shared storage additions retain their distinct Uniform/Storage codes; original leaf validation/capture diagnostics and legacy-only cost/order remain unchanged. ROOT read complete destination sizing/render binding construction, checked image physical helper and actual native snapshot/coverage declarations/pipeline mapping before ruling. Cost: undercounted combined shaders, wrong reuse/ABI, premature preparation or legacy diagnostic regression if incorrect; exact original source/draw inventory, checkedI64 before prepare, public authentic aggregate budget/refusal/recovery plus static Sol audit required. No extra owner, cap, byte default, cache policy or infrastructure test.

| Ruling | Reason | Cost / required evidence |
| --- | --- | --- |
| R20 | Existing common rounded alpha proof cannot establish all useful varying-alpha cubic working domains; bounded fixed-alpha positive witness available. | Narrower explicit cubic admission, original varying-alpha refusal/recovery retained; DECAL positive and all domain/final-blend cells remain required. No epsilon/finite box/strict same-projection causal claim without evidence. |
| R21 | Source textures and actual final snapshot/ABI4 coverage consume the same fragment-stage cap; NEW V5 physical additions require their own diagnostic. | Exact per-occurrence source/final-draw ABI and complete prepermit inventory; preserve legacy priority and distinct uniform/storage budgets. Public aggregate refusal/recovery and Sol audit required. |

Task3 nonfinal epoch5: nearest/linear16tile pairs, mixed frame and retained mutable pixels/filter Surface/Picture PASS, cubic varying-alpha useful graph refused NumericDomainUnbounded. Epoch6 fixed-alpha cubic control1PASS on3routes twice. Commands remain FAILED39s/native212133 and FAILED5s/native213133,causeUNKNOWN. ROOT read both full reports and actual XML while current; epoch5 SHA independently checked, epoch6 actual XML subsequently overwritten before independent SHA, so no frozen-file SHA claim for6. Later epochs and final covering/review still OPEN.

User explicitly requests ALL W5g, without stopping at the next lot. Task3 images/full Blend is RUNNING on fresh sole Astra/high `/root/w5g_image_impl`, accepted clean BASE `343519021388bddba8b18c461ddb3ffe0b7702b0`. ROOT completed/FULL-read its same owning brief with accepted V5/gradient/frame interfaces and43 finite existing Modify paths. Test-only genuine public two-image RED must be independently ROOT-verified before production. Tasks4 Noise and5 convergence follow accepted3 automatically, then one whole Sol review/at most one complete Astra fix wave/scoped Sol re-review and one stacked Draft PR. All numerical/archive/storage gates remain required; no new plan loop, discretionary continuation pause or repeated Task1–2 baseline. ROOT owns only these three durable docs; source/test edits and Gradle belong to the sole implementer.

Task3 production is now authorized after independently verified public RED: FULL261-line report and actual fresh XML20:13:18.777Z/SHA9181c44c…,1failure/0error-skip,expected composed.slice at Surface.render Rect after four bounded/disjoint counterexamples. Two test/oracle pre/post/current SHA match, BASE343/index empty/all production unchanged. Command BUILDFAILED3s/Gradle1, native Render/Readback not reached/no133 claim. Initial wrapper-cache permission and tool serialization epochs are retained, not RED. Worker continues the same Task3 ownership; no source/test fix or parallel Gradle by ROOT.

R18 cache receipt preserves the unchanged content-addressed native cache, separately from original descriptor/request ownership. NEW V5 prepermit admission pessimistically reserves each authentic captured immutable identity's full existing texture/staging cost; post-seal content equality cannot drop planned siblings/charged bytes. Exact descriptor→issued upload/request→all typed rows reconciles independently of cache hits. Old V3 canonical dedup remains exact. Limitation: content-equal distinct captures may be refused earlier than a canonical-minimum reservation; no prepermit hash/copy or new cache policy. ROOT fully read actual cache/upload/request/frame joins and supplied this receipt before inventory edits. Worker applied its full message before reading the actual brief amendment immediately afterward; honest procedural chronology retained, no retrospective before-edit read. Task3 epoch3 compilation exits0, compile-only.

Task3 epoch4 first native two-image positive is NONFINAL:1PASS/0failure-error-skip, Rect/direct/stencil Path native Render/Readback twice. ROOT FULL-read report436–657 and actual fresh XML20:33:04.660Z/SHAee7968f3… to EOF. Command remains BUILDFAILED15s/Gradle1/nativeExecutor211exit133,causeUNKNOWN,55tasks12EXEC43UP. Full mixed/Picture/tile/domain/aggregate-budget/history/custody covering and sole task Sol review remain open; no full Blend/W5g/nativeGREEN closure or rerun for exit0.

Task2 contextual gradients/shared stops is functionally accepted after its sole fresh Sol task review on `0ebe1b655bbab4d2e6cf103c220d755d62124ccf`→`3fbafaf99647d3586a8b38ebbce32f3446952fc6`: Spec Compliance COMPLIANT, Task quality APPROVED,0Critical/0Important/0newMinor, no correction requested. The coherent commit changes17classified code paths1024+/93−, no ROOT documents/artifacts. UNIQUE final five-class covering17 has463registered/462PASS/1authenticW5dAA4skip/0failure-error. ROOT FULL-read the6599-line report,48-line review and all5actual XML; complete XML bytes/counts/names/timestamps/SHA match, and all17committed blobs equal workerPre/ROOTfrozen/workerPost/current. Command remains FAILED25m38s/Gradle1/native209exit133,causeUNKNOWN; final compiles UP, executed incremental compilation belongs to16. No native-cause/whole-branch/global claim. Full Blend waits for Task3; Noise255/archive/storage and wholeW5g remain OPEN. Parent W5f#2400 is unchanged, no W5g push/PR yet.

Task1 uniform-only ordered Blend is functionally accepted after its sole Sol task review and SAME-seat scoped fix round1 re-review: Spec Compliance COMPLIANT, Task quality APPROVED,0Critical/0Important/0new actionable Minor. I2's all29/all12 witness gap and ROOT-confirmed C1's historical invalid-gradient diagnostic masking are ADDRESSED. The unsupported alpha127-and255 matrix claim was withdrawn by the reviewer; alpha127/255 is a normalized rational.

Task1 production commits are `a9e167e3832bedf5e0453dfd3dbcfde23af83010` and bounded fix `bb0bffaa9906b20f1cb886de12c43908db19da89`, from recorded BASE `0b533d31e8f730c2837d585cf04660147de5e882`. ROOT FULL-read reports/review and independently verified all42committed code blobs equal the frozen tested source. The fix changes exactly2files182+/11−, no native/image/Picture/oracle change. Final amended-source covering has98PASS (Surface95+Picture3),0failure/error/skip, fresh XML17:46:01.074Z–17:54:11.598Z. The genuine initial scalar RED and genuine C1RED, all failed fixture/search epochs and historical six-class covering206registered/205PASS/1unchangedW5aAA4skip remain recorded, not relabeled final.

Command remains BUILD FAILED8m14s/Gradle1/native executor195exit133. Repeated exit133 is observed; cause and causal identity with earlier executors remain unknown. No native GREEN/Ready-to-merge/ISO claim. The native-cause investigation remains an explicit outside-slice gap, not silently closed by disclosure. Compressed ComposedMaterialPlanV5 formatting remains a nonblocking readability observation for an in-scope later edit/whole-branch triage.

Task2's retained genuine public RED was ROOT-verified on unchanged production before the fresh adapted Astra/high implementation: freshXML18:10:32.012Z/SHA9d2af78f…,1failure/0error-skip,expected composed.slice at first Surface.render after bounded/disjoint context/order/sample-reuse expectations. Command BUILDFAILED4s/Gradle1, no reported native133 inRED; cache permission failure was NOTRED. Its42exact Modify-path brief and R14–16 preserve the same V5/gradient/numeric/native authorities. Only17paths were changed. One serialized Gradle writer, Sol review-only, no production/test fix by ROOT.

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` to implement this plan task-by-task with the session's adapted Astra implementers and Sol review-only allocation. Steps use checkbox (`- [ ]`) syntax for tracking. ROOT first reads this entire plan and the approved spec, performs the self-review, and obtains the requested fresh Astra plan review. This document does not authorize implementation before that gate.

Task2 final17 covers W5gSurface111+Picture4+W5fGradientInterpolation280+W5dAddressing41(40PASS/1AA4skip)+W5c27, fresh XML19:12:41.615Z–19:29:16.624Z. Shared branch samples/order, all4families/all5domains,17stops, local/clamp/CTM-once/projective, degenerate tiles/conical/singleton, restoring wrappers/paint127/external-filter-once/final SRC_OVER,SRC_IN,DIFFERENCE, mixed ordinary Rect/composed direct+stencil frame, limits/H/invalid-leaf controls and separate mutable-stops-only/filter-only retained Surface/Picture original+decoded witnesses pass on ONE frozen source. All94unchanged Task1 Surface names and all3Picture names remain; only the formerly pending valid-gradient refusal becomes a positive gradient control while the image refusal remains exact. Earlier failed fixture/build/allocation/ABI and oracle epochs plus nonfinal positives remain recorded separately; no tolerance widening or disappearance of failures. Exact W3/composite readers and allocation seals stay unchanged (R16), complete slab charged once. Existing large common owners/V3 pre-inventory-copy/lifecycle debt and warnings remain outside this accepted slice. Tasks3–5 and whole-branch review/gates are not accepted by this verdict.

**Goal:** Render ordered blend children and shared material subtrees, then Perlin/Fractal `NoiseV1`, through the existing common material authority with public Rect and Path-fill pixel evidence.

**Architecture:** Extend the existing metadata-first source construction, whole-frame layout, operation graph, proof and packing permit. A composed source adds explicit ordered child references to the existing material plan, with per-evaluation coordinate context and immutable resource owners; it does not introduce another material hierarchy or materializer. The renderer emits the exact sealed operation graph and consumes only its declared bindings.

**Tech Stack:** Kotlin/JVM; existing math geometry/matrix/color modules; render-ir, gpu-plan, gpu-renderer and kanvas; WebGPU/WGSL; JUnit5; independent outward-rounded public-input CPU oracles.

**Spec:** `refactor/specs/2026-09-09-w5-material-graph-design.md`, read completely, especially §§4–6,10–11,15–18. The current final checkpoint at the top of `refactor/plans/2026-09-14-w5f-color-filters-implementation-plan.md` is the inherited state; older chronological OPEN paragraphs are history.

## Global constraints and execution boundary

- Base: W5f final HEAD `55e4992d5aeb34412189d3bb52bcf2784468318c`. ROOT created `codex/w5g-composed-procedural-materials` at that unchanged commit in the existing app-owned worktree. One eventual Draft PR targets `codex/w5f-color-filters`, parent PR #2400. Do not mutate or merge the parent.
- W5f functional slices/reviews are CLOSED: 0 Critical, 0 Important, 2 inherited nonblocking Minor. Final covering was 14 classes, 690 methods, 688 PASS, 2 known AA4 skips, 0 assertion failures/errors; the command remained FAILED, Gradle exit1/native174 exit133. Five separate incremental compiles exited0. Preserve that distinction, all existing numerical restrictions, V3 pre-inventory-copy debt, owner/lifecycle gaps and warnings. No native/global ISO claim.
- `GraphLimits(maxDepth=64,maxNodes=4096)` remains exact. Preserve first-owner refusal priority and existing occurrence-based capture accounting; do not silently turn graph-node limits into unique-object limits while adding memoization.
- Only real public `Surface`, `Canvas`, `Picture`, render pixels, typed diagnostic boundaries, and native Render/Readback evidence are tests. No infrastructure, source-shape, private/internal, ABI, reflection, counter, cache-handle, injected-capability, fake-device, mock or structural-step assertions. Static architecture/ownership checks belong to review.
- Compute independent expected byte sets before creating a Surface or recorder. A set is a singleton or exactly two adjacent bytes justified by `WgslFloatEnvelopeV1`; wider/unbounded/nonadjacent sets are invalid fixtures. Never use similarity or an empirical tolerance.
- Every promoted family covers Rect and Path fill, including existing direct and stencil Path-fill routes where applicable. RRect, Path stroke, Point(s), Text, Vertices/Mesh and A8-image-origin composed/noise sources remain explicitly named H cells for W5h. RGBA image origins do not evaluate the paint shader. A composition containing an image shader on a Rect/Path is in scope; that is distinct from promoting A8 image origins.
- Retain all previous admitted W5a–f cases and their exact diagnostics. An H source cannot become an admitted unfiltered gradient by looking through Blend, and an ordinary previously admitted gradient cannot become refused merely because V5 exists.
- Internal order is material children/wrappers, paint alpha, origin composition, external paint color filter once, final draw blend, then geometry*clip coverage. A child blend is source computation and never requests a target snapshot by itself. Final destination-read still uses the existing versioned target ordering.
- Preserve wrapper-local coordinates and outermost working-interpolation precedence independently along each child edge. Do not multiply paint alpha once per child or move filters across a blend/opacity.
- Geometry and transformation values remain in `:math:geometry`/`:math:matrix` using I32/I64/F32/F64 names. `SizeI32` already exists in `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/SizeF32.kt`; reuse it.
- No runtime-effect admission (W5h), spatial filters/layers/backdrop (W6), external image decoding/encoding, font/glyph generation, GM, dashboard, renders, references, scores, baselines, `jpg-color-cube`, global suites or native harness/security changes. No reset/purge/teardown/budget workaround.
- Serialize all Gradle executions. Shell commands use `rtk` or `rtk proxy` and the explicit workdir `/Users/chaos/.codex/worktrees/cbf6/kanvas`. No implementation task may run an unrelated broad suite.
- Five sequential meaningful implementation tasks below; Tasks1–3 collectively deliver the COMPLETE Blend family, Task4 Noise and Task5 convergence. Setup/contracts/tests travel with each functional slice. This execution-size refinement retains the original three family deliveries and ALL requirements. Task reviews are bounded. End with ONE whole-branch Sol review, at most ONE complete Astra fix wave and ONE scoped Sol re-review, then ONE Draft PR. Do not repeat the historical review/test loop.
- Durable progress is limited to this plan, `refactor/waves/W05-material-graph/status.md` and `refactor/README.md`, owned by ROOT. This planning worker owns only this document. No packet/ledger/artifact proliferation in this planning turn.

## Concrete design decisions and one numerical decision gate

1. **Explicit V5 composition in the existing hierarchy.** Add `ComposedMaterialProgramV5 : MaterialProgramPlan`, `ComposedMaterialBindingV5 : MaterialBindingPlan`, and `PlanDrawMaterialAuthority.MaterialV5`. Preserve V1–V4 historical layouts. A V5 root owns an ordered DAG of references to existing leaf/wrapper execution recipes; child topology is explicit, never `root.indexI32 - 1`. This is an extension of the common material contract, not a second material system.
2. **Sharing separates values from evaluation context.** Snapshot identity sharing survives public Shader → immutable MaterialNode. The same immutable payload can share one physical range; two evaluations with different accumulated local matrices, clamps or working domains remain different evaluation nodes. Canonical equality alone cannot certify that two captured objects have the same immutable owner. Program identity contains topology/order, operators, domains and binding types; values, seed, frequency, octave count, table bytes, colors and resource IDs remain dynamic.
3. **Keep capture limit semantics.** Memoize completed captures but still charge the existing occurrence traversal and check active-path cycles/depth before memo lookup returns a node. Do not switch to unique-node graph limits. Resource tables and actual evaluation DAG accounting are separately deduplicated only where their owner/context identities permit it.
4. **Nullable historical constructors.** Use the new five-argument `SizeI32?` constructor, a deprecated five-argument `SizeF32?` constructor, and a third five-argument `Nothing?` constructor delegating literal null explicitly to `SizeI32?`. ROOT's local Kotlin probe verified positional/named null, typed nullable old/new sizes, and old nullable-null variables. A trailing default Unit overload was disproved and is not the design. Task4 still compiles the real four declarations (Shader and MaterialNode, both noise kinds). Preserve historical named arguments. Generated `copy(tileSize=SizeF32)` is a source migration to the new typed property, not silently promised binary compatibility.
5. **Archive versioning is intrinsic.** Picture11/schema5 writes noise tile axes as I32 and new noise canonical domains. Read supported old Picture8/9/10 and schema versions with their actual old F32 layout, validating before explicit conversion. Do not reinterpret old bits or drop Picture10 from the accepted-version branch. Other geometry `SizeF32` fields keep their wire type.
6. **Noise numerical gate, before Task4 production edits.** The approved recurrence is F32 `q=(P+.5)*frequency`, doubling q and halving amplitude, with integer stitching periods doubled each octave. A naive255-iteration F32/I32 implementation overflows for admitted finite inputs. This plan does not invent an unreviewed multiword ABI or claim a future certificate. Task4 must present a safe-phase/true-integral-zero-tail derivation preserving approved operations/rounding. Charge all requested octaves even with a proven zero tail. A new numerical policy requires ROOT's precise exposed decision; numeric-domain-unbounded refusal never closes that valid domain or family. Blend Tasks1–3 remain independently executable; W5g closure cannot bypass this gate.

The last item is an actual unresolved implementation-design obligation, not a scope waiver. Mandatory 255-octave and positive-tile public witnesses below remain required. This document is complete as the Blend-first execution plan and as the explicit contract/gate for the remaining Noise delivery; it is not evidence that the unresolved representation already exists.

## Actual code map and ownership

Paths below are repository-relative exact paths; prefix abbreviations in later tables expand to these directories only:

```text
IR = render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/
PLAN = gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/
GPU = gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/
API = kanvas/src/main/kotlin/org/graphiks/kanvas/
TEST = kanvas/src/test/kotlin/org/graphiks/kanvas/
```

| Existing file | Actual responsibility and required join |
| --- | --- |
| `API/canvas/DisplayOpSnapshot.kt` | Its Shader snapshot already uses `IdentityHashMap` and preserves shared captured children. Noise leaves are already immutable and reused; do not add artificial leaf copies. Its first-owner preflight must acquire complete noise scalar validation. |
| `API/render/ir/PaintSceneAdapter.kt` | Current recursive `Shader.toMaterial` loses sharing; Blend calls both children with `preserveW5dMatrices=false`. Replace this capture traversal with bounded ordered memoization, carrying the real capture context and preserving matrix semantics across Blend. |
| `API/render/ir/ColorFilterCapturePreflight.kt` | Current occurrence traversal has active-path detection but no completed memo. Preserve charging/refusal priority while avoiding duplicate retained snapshots. |
| `IR/MaterialNode.kt`, `IR/SceneArchiveCodec.kt` | Existing Blend and noise semantic nodes; current noise `SizeF32?`, current schema4. Change the noise value and canonical/wire domains only in Task4. |
| `PLAN/MaterialSourceConstructionV4.kt` | Pending original metadata has no table/proof/tuple; current gradient wrappers and image child are unary. Extend this owner to composed metadata instead of preparing child tables early. |
| `PLAN/FrameSourceLayoutV4.kt` | `standalone`, `ordinaryComposite`, `nativeComposite`, checked inventory and prepare/bind are the actual full-frame gate. Add child DAG inventory before any new stop/noise preparation or packing. |
| `PLAN/MaterialPlan.kt` | Current opacity/filter/image child access and V4 interner use adjacent refs. Add explicit V5 traversal/authentication/remapping without changing historical adjacency contracts. |
| `PLAN/ColorOperationGraphV1.kt`, `ColorSourceProofCompilerV1.kt`, `ColorSourceProofV1.kt` | Existing graph is shared by color proof/emission; proofs retain child/filter/binding identity but assume one chain/image. Compose actual child graphs and complete contextual provenance, not component bounding boxes. |
| `PLAN/BlendFormulaProgramV1.kt`, `BlendFormulaOperationGraphV1.kt` | Existing shared 29-mode formula authority. Reuse it for child blends; do not copy a second blend implementation. |
| `PLAN/MaterialSourceFootprintV4.kt`, `RawMaterialRequirementsV2.kt` | Exact measured payload/physical requirements and packing permit. Extend existing permit to the final DAG layout and unique resources. |
| `PLAN/CapabilityCompilerChain.kt`, `SourceDeferredRenderConstructionV4.kt` | Composite selection and deferred construction converge here. No new lane-specific material compiler. |
| `GPU/materials/W5aMaterialSourceStage.kt`, `W5fColorOperationEmitterV1.kt` | Current source stage has one image execution and optional stop slab. Emit V5 through this stage with explicit physical resource mapping; multiple image children cannot alias the single `w5eTexture`. |
| `GPU/execution/GPUW5aSourceStageNativeV2.kt`, `GPU/materials/W5aFrameMaterialBudgetV2.kt` | Actual native source owner and combined memory inventory currently retain one image lease/request and select a single storage/texture binding. Extend these same owners with the final ordered typed V5 resource mapping; no second materializer or lifecycle redesign. |
| `API/surface/gpu/GPUPlanSurfaceCandidateGate.kt`, `W5dGradientCandidateV2.kt` | Existing single admission gate and unary candidate reader. Extend bounded recognition with explicit family/geometry facts, including recognized-invalid input ownership. |

Before editing a reader, inspect its whole function and callers. A sealed-interface compiler error is not H promotion authority. The shared Blend reader list below is finite; each current task's subset is classified before edits, never a wildcard future permission.

## Shared Blend authority and complete-family acceptance contract

**Outcome across Tasks1–3:** A complete public Blend delivery for all29 child modes, noncommutative children, shared immutable payloads, local coordinates, existing gradient/image/filter combinations, capture/replay and checked resources. The original monolithic implementation handoff obtained genuine RED but no production; ROOT split its50+-owner unit into three functional slices (R11). This shared contract is not a claim that Task1 alone implements every resource family. Blend is CLOSED only after all three slices and their reviews; the numerical Noise gate remains independent.

**Files — Create:**

- `PLAN/ComposedMaterialPlanV5.kt`: explicit immutable evaluation DAG, binding layout and V5 program/binding members.
- `PLAN/W5gPlanDiagnostics.kt`: exact new classification/schema/budget diagnostics.
- `TEST/surface/W5gComposedMaterialSurfacePixelTest.kt`: public Blend/child/coordinate/mutation/limits witnesses.
- `TEST/picture/W5gComposedMaterialPictureTest.kt`: public retained and decoded Picture playback.

**Files — Modify:**

- `API/render/ir/PaintSceneAdapter.kt`, `API/render/ir/ColorFilterCapturePreflight.kt`: preserve ordered shared capture and context without relaxing first-owner limits.
- `PLAN/MaterialPlan.kt`, `MaterialSourceConstructionV4.kt`, `FrameSourceLayoutV4.kt`, `MaterialSourceFootprintV4.kt`, `RawMaterialRequirementsV2.kt`, `EffectiveMaterialPlanner.kt`, `ColorOperationGraphV1.kt`, `ColorSourceProofCompilerV1.kt`, `ColorSourceProofV1.kt`: exact shared source inventory, graph, proof, physical layout and V5 publication.
- `PLAN/ImageNumericOperationGraphV1.kt`: ONLY Task3's TexelRead constructor/identity/rebase, SampledRegion bind/rebase and narrowly necessary sampledTexelGraph logical-resource transport; preserve exact legacy upload-bound paths and numerical sampler/decoder/kernel equations (R10).
- `PLAN/CapabilityCompilerChain.kt`, `SourceDeferredRenderConstructionV4.kt`, `RenderGraph.kt`, `RenderGraphConstruction.kt`, `PlanPasses.kt`, `W5aMaterialGraphContract.kt`, `W5bDestinationGraph.kt`, `W5bGeometryLanePlanV3.kt`, `W4dRenderGraphCanonicalSeal.kt`, `W4dGeneralRenderGraphCanonicalSeal.kt`: transport/authenticate new authority through existing frames/seals.
- `API/surface/gpu/GPUPlanSurfaceCandidateGate.kt`, `W5dGradientCandidateV2.kt`: exact Rect/Path-fill admission and owned refusal.
- `GPU/materials/W5aMaterialSourceStage.kt`, `W5aPacketMaterialSourceV2.kt`, `W5fColorOperationEmitterV1.kt`, `GPU/planning/W5aMaterialPlanLowerer.kt`, `W4aAnalyticRectGraphLowerer.kt`, `W4cPathFillGraphLowerer.kt`, `W4dGeneralPathGraphLowerer.kt`, `W5bAnalyticRectGraphLowerer.kt`, `W5bNativeGeometryGraphLowerer.kt`, `GpuPlanTaskListLowerer.kt`, `GPU/passes/W5aMaterialPlanAuthorityV2.kt`, `GPUPlanW4dGeneralPreparedAuthority.kt`: common V5 lowering, actual bindings and witness custody on existing eligible geometry.
- `GPU/execution/GPUW5aSourceStageNativeV2.kt`, `GPU/materials/W5aFrameMaterialBudgetV2.kt`: complete declared-resource inventory, per-slot native binding/reflection and retained image/slab ownership through the existing source materializer.
- `TEST/surface/W5fColorCpuOracle.kt`: extend the existing independent public-Shader interpreter with ordered Blend and public image/coordinate child recipes; do not import production plans/formulas/proofs.

**Files — Audit/reuse, no write unless a concrete missed V5 transport is identified to ROOT:**

- `API/canvas/DisplayOpSnapshot.kt`, `API/render/ir/SceneDisplayOpAdapter.kt`, `API/picture/Picture.kt`, `IR/MaterialNode.kt`, `IR/SceneArchiveCodec.kt`: existing immutable Blend round-trip and snapshots. No version bump solely for Blend.
- `PLAN/BlendFormulaProgramV1.kt`, `BlendFormulaOperationGraphV1.kt`, `ColorFilterExecutionPlanV1.kt`, `ColorNumericAuthorityV1.kt`, `GradientInterpolationPlanV4.kt`, `ImageNumericAuthorityV1.kt`, `ImageAtlasBlendNumericAuthorityV1.kt`: reuse actual equations/leaves; no duplicated numerical authority. ImageNumericOperationGraphV1 numerical regions remain read-only; its explicitly classified Task3 transport regions are the sole exception.
- `PLAN/W4bAnalyticRRectPlanCompiler.kt`, `W4dPathStrokePlanCompiler.kt`, `W4eNativePayloadPlan.kt`, `W5eImagePlanCompiler.kt`; `GPU/planning/W4bAnalyticRRectGraphLowerer.kt`, `W4dPathStrokeGraphLowerer.kt`, `W5bAnalyticRRectGraphLowerer.kt`; `GPU/execution/GPUW5eImageNativeV1.kt`, `GPU/passes/W5ePreparedFrameWitnessV1.kt`: exhaustive recognition must preserve historical support and explicit H refusal; no automatic composed-source promotion.
- `TEST/surface/W5fSurfacePixelFixtures.kt`, `WgslFloatEnvelopeV1Oracle.kt`, `W5dGradientAddressingSurfacePixelTest.kt`, `W5eImageShaderSurfacePixelTest.kt`: public native assertion/oracle/fixture patterns, not production-derived expected values.

**Interfaces:** Existing consumed entry points include `MaterialSourceConstructionV4.capture(draw: DrawNode, coordinates: SourceCoordinatesV4, bounds: RectF32, blend: BlendPlan, imageMaskChild: Boolean=false)`, `FrameSourceLayoutV4.nativeComposite(lanes: List<SourceDeferredRenderConstructionV4>)`, `MaterialPlanTable.colorSourceProofV4(root: MaterialPlanRef)`, and `ColorSourceProofV1.authenticates(table, root, coordinates)`. Preserve their old callers. Introduce these V5 contracts in the existing plan domain:

```kotlin
public data class MaterialEvaluationRefV5(public val indexI32: Int)
public class ComposedMaterialProgramV5 internal constructor(
    public val structuralId: MaterialProgramPlanId,
    internal val operationGraph: ColorOperationGraphV1,
) : MaterialProgramPlan
public class ComposedMaterialBindingV5 internal constructor(
    internal val definition: PreparedComposedSourceV5,
    public val sourceProof: ColorSourceProofV1,
) : MaterialBindingPlan
// Added to existing PlanDrawMaterialAuthority:
public data class MaterialV5(public val ref: MaterialPlanRef) : PlanDrawMaterialAuthority
// Added to existing MaterialPlanTable:
public fun colorSourceProofV5(root: MaterialPlanRef): ColorSourceProofV1

internal class MaterialEvaluationDagV5 private constructor(
    val entries: List<Entry>, val root: MaterialEvaluationRefV5,
) {
    class Entry internal constructor(
        val ownerNodeIndexI32: Int,
        val children: List<MaterialEvaluationRefV5>,
        val coordinates: SourceCoordinatesV4,
        val program: MaterialProgramPlan,
    )
}
internal class PreparedComposedSourceV5 private constructor(
    val capturedIdentity: String,
    val frameOwner: FrameSourceLayoutV4,
    val evaluation: MaterialEvaluationDagV5,
    val layout: ComposedBindingLayoutV1,
    val operationGraph: ColorOperationGraphV1,
)
```

`ComposedBindingLayoutV1` is added under existing `MaterialBindingPlan`, as specified in global §12's common layout contract; only built-in slots are delivered here, no runtime schema. It records ordered owner/local→physical offsets, total aligned uniform bytes and typed resources. Immutable constructors validate references and ownership before publication. Definition/proof issuance remains module-controlled. These signatures describe the new contract; implementations must supply all existing `MaterialProgramPlan` members, including version5 and the actual shared numeric graph bridge.

The program retains code-shaped topology/operators, coordinate-operation shape and binding types only. The contextual `MaterialEvaluationDagV5` and all coordinate/value/resource owners stay in `PreparedComposedSourceV5`/bindings and are authenticated by the value-dependent proof; never cache this dynamic DAG in a shared program. Fixed logical resource minima and dynamic complete physical slab sizes are distinct.

### Task1 built-in composed layout and diagnostic contract

The §12 common layout below is binding for built-in V5 sources, not runtime-effect admission. Define the layout under the EXISTING `MaterialBindingPlan`; keep all native handles/leases outside it and authenticate dynamic resource ownership separately. The logical schema remains the versioned existing leaf/wrapper `MaterialProgramPlan`.

For the complete ordered material DAG, prefix traversal assigns `ownerNodeIndexI32` on first visit; a later reference to the SAME captured shared node reuses that owner and logical ranges. Evaluation-context refs remain separate. The material group is1, uniform binding0. For each logical node block, `baseOffsetBytesI32=alignUp(cursor,16)`; physical field offset is base+local. Uniform mapping rows contain EXACTLY `ownerNodeIndexI32: Int`, `localOffsetBytesI32: Int`, `physicalOffsetBytesI32: Int`, `sizeBytesI32: Int`, `alignmentBytesI32: Int`. The total uniform size is aligned16 and stored/checked in I64 before narrowing. Never retain an unvalidated mutable list.

Resource rows retain `ownerNodeIndexI32: Int`, `logicalSlotI32: Int`, `groupI32: Int`, `bindingI32: Int`, `visibilityFlagsU32: UInt`, `kindTagU32: UInt`. Binding assignment starts at1 in declared prefix order. Each row has exactly ONE present typed layout option:
- buffer: `bufferTypeTagU32: UInt`, `minBindingSizeBytesI64: Long`, `hasDynamicOffset: Boolean=false`;
- texture: `textureViewDimensionTagU32: UInt`, `textureSampleTypeTagU32: UInt`, `multisampled: Boolean=false`;
- sampler option is ABSENT for all W5g built-ins; no hardware sampler/runtime slot/array is admitted.

The two other layout options are absent, not sentinel values. Fixed published tags are resource kind STORAGE_BUFFER=1/SAMPLED_TEXTURE=2, visibility FRAGMENT=0x2, buffer UNIFORM=1/STORAGE_READ_ONLY=2, dimension D2=1 and sample type FLOAT_FILTERABLE=1. Stop/noise storage is read-only; decoded image sampling uses explicit textureLoad taps. Slot logical minimum and complete dynamic slab/allocation byte size are separate authenticated facts; check the COMPLETE latter against actual capabilities/budgets before preparation. No default physical capability is invented.

The `composedBindingLayoutHash` preimage uses domain `kanvas-material-binding-layout-v1` then00, in EXACT order:
1. groupI32=1,bindingI32=0,visibilityFlagsU32=0x2,bufferTypeTagU32=1,minBindingSizeBytesI64=aligned uniform size,hasDynamicOffset=false,then that total sizeI64;
2. the ordered uniform mappings, each owner's index/local offset/physical offset/size/alignment;
3. the ordered physical resources, each owner/logicalSlot/group/binding/visibility/kind then exactly one present buffer/texture layout option above, others absent.

Use approved `CanonicalHashBytesV1` encoding: integers little-endian I/U32/64, bool/option tags00/01, lists countU32, domain ASCII terminated00, UTF8 strings lengthU32. Every count/length/sum is checkedI64 and representableU32 before hash/allocation; enums use published tags, never ordinal/name/native values. SHA256 lowercase enters assembled program identity only for the actual structural layout. Never include colors/stops/pixels/seed/ranges/value ownership or other dynamic data when binding ABI is identical. WGSL reflection/stage/native mapping must agree with the same sealed rows; immutable resource-owner/range/frame proof remains separate from this hash. No runtime ABI catalog/hash registration is introduced.

Task1's new diagnostic names/strings are EXACTLY:

```kotlin
public object W5gPlanDiagnostics {
    public const val Schema: String = "invalid.material.composed.schema"
    public const val NumericDomainUnbounded: String = "unsupported.material.composed.numeric-domain-unbounded"
    public const val Binding: String = "resource-limit.w5g.composed-binding"
    public const val Uniform: String = "budget.w5g.composed-uniform"
    public const val Storage: String = "budget.material.composed.storage"
    public const val Unpromoted: String = "unsupported.material.composed.slice"
}
```

Schema owns invalid composed topology/child refs/owner/layout authentication; NumericDomainUnbounded owns a useful unprovable COMPOSED output; Binding owns unavailable/exceeded physical resource/binding facts; Uniform and Storage distinguish composed uniform versus shared storage footprint overruns; Unpromoted owns composed H geometry or an explicitly pending child family. Preserve FIRST preexisting filter/gradient/image/opacity/capture diagnostics for invalid leaves and old admitted cases. Forward codes through the EXISTING typed boundary, no new exception/fallback. No new numeric byte budget/default/ceiling in Tasks1–3: use snapshotted PlanBudget.maxFrameLocalBytes, real physical limits, GraphLimits and checkedI64. Noise diagnostics/work limit are Task4-only.


- [ ] **Step 1 — Write RED public children/order/capture witnesses.** Join the existing test-only independent equations into ONE point-aware public-Shader interpreter. Preserve existing `expectedShaderTree` callers by appending defaulted arguments; replace its disconnected local solid-only `evaluate` with delegation to `shaderSource`. The concrete callable contract is:

```kotlin
fun expectedShaderTree(
    shader: Shader, paintAlphaF32: Float = 1f, external: ColorFilter? = null,
    destination: ColorARGB = ColorARGB.Transparent, finalBlend: BlendMode = BlendMode.SRC,
    devicePointF32: Point2F32 = Point2F32(.5f,.5f),
    canvasMatrixF32: Matrix3x3F32 = Matrix3x3F32(),
): WgslFloatEnvelopeV1Oracle.DrawResult
```

`devicePointF32` is the device-space sample BEFORE inverse canvas CTM and shader-local matrices, not a secretly pretransformed local point. Start with two `Interval.input` coordinate scalars; independently invert/project the canvas CTM once as the existing coordinate contract specifies, then evaluate its normalized F32 map with outward-rounded arithmetic. Carry a branch-local pending list of uninterrupted local matrices: `WithLocalMatrix` appends frozen coefficients, rather than independently inverting/mapping each edge. Compose the pending segment in declared order in F64, invert and project its coefficients ONCE, then apply the normalized F32 map at the same semantic flush boundary as the existing authority: immediately before `CoordClamp` or at the leaf/end. `CoordClamp` then applies at its original position. Blend clones pending segment/context into each child without introducing a new flush boundary; value/filter/opacity/working wrappers do not arbitrarily flush either. Independently duplicate these published composition/inversion/projection equations, including F32 normalized coefficients and allowed WGSL rounding; never call production `MaterialCoordinatePlanV2`, matrix decomposition, coordinate-plan or proof helpers. Keep branch-local outermost interpolation precedence. Do not collapse uncertain coordinates into a Point2F32 singleton. Ambiguous validity/predicate branches remain alternatives or an unbounded fixture. No new geometric value type outside math is needed: coordinate uncertainty is two numeric Interval scalars.

The private common `shaderSource` handles Solid, Opacity, WithColorFilter, WithWorkingColorSpace, WithLocalMatrix, CoordClamp, ordered Blend, existing gradient-family equations/stops/domains, and Shader.Image. Blend is `blend(shaderSource(src,context),shaderSource(dst,context),mode)` using the existing independent published formula oracle. For image SHADER children use the existing independent `sampledImage` equations with branch-local coordinates, tile modes and sampling, not image-ORIGIN paint/mask composition. Extend sampler/gradient coordinate inputs to carry the same two Interval scalars; keep old Point2F32 entrypoints as delegates. General stop sequences use independently bounded parameter/tile/segment decisions and the published interpolation equations, including >16-stop fixtures; no two-stop-only shortcut or production parameter graph import.

After this common source evaluation, apply paint alpha once, external filter once, final draw blend and attachment encoding through the existing independent `finish`. This sequence is shared by the Task1/Task3 fixtures. Production `BlendFormulaProgramV1` and plans/formulas/proofs remain forbidden in tests; expose no production test hook.

```kotlin
@Test fun orderedBlendChildrenRenderOnRectAndPath() {
    val dst = Shader.Opacity(Shader.SolidColor(ColorARGB.Blue), .5f)
    val src = Shader.Opacity(Shader.SolidColor(ColorARGB.Red), .25f)
    val shader = Shader.Blend(BlendMode.SRC_OVER, dst, src)
    val expected = W5fColorCpuOracle.expectedShaderTree(shader,
        paintAlphaF32 = 1f, destination = ColorARGB.Transparent,
        finalBlend = BlendMode.SRC)
    W5fSurfacePixelFixtures.requireBounded(expected)
    for (path in listOf(false, true)) {
        val surface = Surface(1, 1)
        surface.canvas {
            val paint = Paint(shader = shader, blendMode = BlendMode.SRC, antiAlias = false)
            if (path) drawPath(Path().apply {
                moveTo(-10f,-10f); lineTo(40f,-10f); lineTo(-10f,40f); close()
            }, paint) else drawRect(RectF32.ofLTRB(0f,0f,1f,1f), paint)
        }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(), listOf(expected)) }
    }
}
```

The exact expected source before final encoding is `(0.25,0,0.375,0.625)` linear-premul; reversed children give `(0.125,0,0.5,0.625)`. Require disjoint bounded red/blue counterfactual sets before Surface. Complete the matrix with all `BlendMode.entries` (29), alpha0/.25/.5/1, and final SRC_OVER/SRC_IN/DIFFERENCE over Blue. A mode with a wider fixture is unresolved until a different discriminating bounded input is derived; do not lower the mode count.

- [ ] **Step 2 — Run only the new public RED class.**

```text
rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W5gComposedMaterialSurfacePixelTest
```

Expected before production: existing owned material refusal for nontrivial Blend; preserve the actual diagnostic/log. Oracle-unbounded is not RED evidence. A build error from a misspelled oracle parameter is not behavioral RED; use the existing `expectedShaderTree` signature exactly.

- [ ] **Step 3 — Implement shared capture and full-frame composed construction together.** Preserve `dst` then `src` prefix owner assignment and postorder evaluation. The active-path check precedes completed memo reuse; keep occurrence-based GraphLimits while memoizing captured payloads. Use a capture-context key that includes `preserveW5dMatrices`; do not share a differently interpreted snapshot. Branch coordinate stacks are copied by immutable metadata references, not mutated while processing siblings.

```text
capture metadata in declared dst/src order
  -> assign first-visit owner index; retain ordered child edges
  -> collect every child leaf's stops/images/filter records and contextual evaluation
  -> join ALL lanes' metadata and checked physical requirements
  -> issue the existing frame preparation permit for this exact inventory
  -> prepare unique leaf resources and rebase every child reference/range
  -> compose shared operation graphs + BlendFormulaProgramV1
  -> seal proof to original capture, final frame owner, exact layout and coordinates
  -> issue packing permit -> pack -> publish Ready -> native lowering
```

Check `base+count`, alignment and total bytes in checked I64 and prove U32 representability before casts. Shared stop values are frame-local; image cache requests retain existing device-generation/lease contracts and pessimistic byte costs. For two image children, declare separate typed physical texture slots unless immutable identity and physical facts permit one slot. Physical binding assignment follows prefix order from binding1; the uniform block stays group1/binding0. No child preparation is permitted while a later sibling/frame lane can still exceed a budget. Do not convert preexisting V3 copy debt into permission for new copies.

Transport the SAME ordered typed physical-resource rows from `ComposedBindingLayoutV1` to stage manifest, parser reflection/layout validation, native bind entries and source-partition ownership validation in `GPUW5aSourceStageNativeV2`. Each row identifies its kind/physical binding, immutable owner and final complete resource: image rows authenticate cache request→lease and generation; storage rows identify the actual shared slab, complete byte size and declared base/count range. Remove singular `.single` resource selection only for V5; historical layouts remain exact. Coordinate consumption follows the V5 graph's actual inputs, including any coordinate-dependent child, not the old stops-or-single-image heuristic. Extend `W5aFrameMaterialBudgetV2` to inventory every unique declared V5 request/slab with existing pessimistic texture/upload costs before materialization. Retain all handles through the SAME existing completion/rollback owner, in acquisition order; do not change dispatcher, pool, harness or lifecycle policy.

Proofs retain the exact child graph/condition facts, immutable dynamic owner, coordinate context, final physical mapping and frame owner. Never concatenate child uniform arrays and attach a proof that still references their old offsets. Rebased graphs and proofs must use the same final table; a change to values invalidates the old value-dependent proof without changing structure-only program identity.

- [x] **Step 4 — Complete meaningful positive and negative public cells.** Tasks1–3 accepted on their bounded domains; R20/R25 and authentic H restrictions remain explicit. Use these fixed fixture families, expected from public inputs before capture:

| Fixture | Values / counterfactual | Required observations |
| --- | --- | --- |
| Shared diamond | `shared=Opacity(Solid(Red),.5)`; `Blend(SRC_OVER, WithColorFilter(shared,Matrix(scaleR=.5)), shared)` | Rect/direct Path/stencil Path; distinguish swapped children and accidental first-child output reuse. |
| Shared mutable gradient | 2 stops Black/White alpha128, x range `.5..1.5`; shared leaf in two children with local translations `-.25` and `+.25` | Independent .25/.75 samples, outer rotation/translation order, mutation of stops after Surface and Picture capture. Add >16-stop child from existing W5c public pattern. |
| Filter placement | Matrix with RGB translation `.125,0,0`, alpha translation `.25`; child Opacity0/.5 and external filter | Distinguish filter-before-blend, paint-alpha-per-child, filter-twice and opacity erasing a later restoring filter. |
| Two image children | `Image.fromPixels(2,1,[255,0,0,255, 0,0,255,128])` and reversed bytes, nearest then linear/cubic existing sampler fixtures | Rect and Path; mutate original arrays after capture, repeated render; no external codec. |
| Working domains | existing five W5f bounded two-stop domain fixtures inside opposite blend children | Outer domain precedence remains local to each branch; no scalar-only domain substitution. |
| Graph limits | 65 nested wrappers and 4097 counted occurrences, with shared leaf variants | Exact first capture diagnostic, valid append to the same recording after refusal, bounded native recovery. Do not change limits or assert internal node identity. |
| H ownership | RRect, Path stroke, Point(s), Text and Mesh family classification of an actual Blend source | Preserve exact typed H refusal where publicly reachable without font generation; static review for unavailable public pre-resolved text route. No claim of H promotion. |

Picture test code follows the existing public sequence `PictureRecorder.beginRecording(rect)` → draw → `finishRecordingAsPicture()` → mutate → `Picture.fromByteArray(picture.toByteArray())` → `replay.playback(surfaceCanvas)` → repeated native pixel assertion. Use a single shared mutable ColorMatrixF32/filter in both branches, with actual mutable stops where applicable, and prove expected differs from mutated/reversed outputs before recording. Matrix3x3F32 local transforms are immutable values (R15), not manufactured in-place mutation fixtures. No serialization shape/identity assertions.

- [x] **Step 5 — At Task3 acceptance, verify the complete composed class pair and affected historical public classes, transport and ownership.** Epoch17 is the exact five-class covering prescribed by the completed Task3 brief and Task3 Step3 below; ImageFilter is the directly affected historical class. FilterOrdering remains in the mandatory final19, not removed from branch regression. Sole initial/scoped Sol gates and source custody closed; command/native failure remains disclosed.

```text
rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W5gComposedMaterialSurfacePixelTest --tests org.graphiks.kanvas.picture.W5gComposedMaterialPictureTest --tests org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest --tests org.graphiks.kanvas.surface.W5fImageFilterSurfacePixelTest --tests org.graphiks.kanvas.surface.W5fGradientInterpolationSurfacePixelTest
rtk git diff --check
rtk git commit -m 'feat(material): execute ordered shared blend sources'
```

For every slice commit, stage only its actual classified paths after ROOT confirms. Never stage workspace/ROOTdocs. Preserve command exit/XML separately. Each Sol review covers that slice's capture/proof/physical/H joins; Critical/Important blocks its acceptance. This full-family checkpoint is reached only after Task3.

## Task 1: Uniform-only ordered Blend, capture and replay

**Outcome:** Independently complete public Rect/direct-Path/stencil-Path Blend for SolidColor, Opacity and the twelve existing non-runtime WithColorFilter kinds, all29 child modes, immutable shared subtrees, external paint filter/alpha/final blend, limits and capture/replay. No gradient/image/coordinate-dependent child is claimed promoted by this slice. Existing ordinary W5a–f cases remain unchanged; pending composed gradient/image and H geometry receive owned composed.slice refusal, never fallback. Full Blend-family closure waits for Tasks2–3.

**Create:** `PLAN/ComposedMaterialPlanV5.kt`, `PLAN/W5gPlanDiagnostics.kt`, `TEST/surface/W5gComposedMaterialSurfacePixelTest.kt`, `TEST/picture/W5gComposedMaterialPictureTest.kt`.

**Modify:** Shared contract's API capture/preflight/gate, PLAN material/source/layout/footprint/requirements/operation/proof and exact existing sealed transport readers, GPU common source stage/emitter/packet/lowerers/authority, and `TEST/surface/W5fColorCpuOracle.kt`. Only the uniform-only V5 path is implemented here. Native multi-image/storage owners and ImageNumericOperationGraphV1 remain read-only until Tasks2–3; no new materializer/texture/slab is needed for this slice. A sealed-reader hunk preserves H and historical branches; it is not promotion authority. Report exact actual changed readers before staging.

**Interfaces/implementation boundary:** Use the V5 contracts and exact built-in layout/diagnostics above. The root V5 entry owns the explicit ordered scalar evaluation DAG and its prepared immutable bindings; do not append unary children and infer root-1. The program owns only DynamicF32/code-shaped operators/refs, no source value owners. Its binding/proof owns original capture, final frame definition/layout/uniform mappings and contextual DAG (SourceCoordinatesV4.None for these coordinate-independent leaves). Extend FrameSourceLayoutV4 metadata inventory/prepare/bind and ColorSourceProofV1 authentication together for THIS scalar DAG. Issue full-frame checked preparation/packing permits before new uniform preparation; preserve historical linear owners. Existing native uniform binding0/source completion owner can transport the resulting stage unchanged. Gradient/image proof constructors/leases/resources are neither reused inside this program nor migrated in this slice.

- [x] **Step1 — Preserve verified genuine RED and complete independent scalar witnesses.** The retained initial public test and W5f oracle delegate already produce bounded/disjoint child-order expectations before Surface; actual RED at2026-09-14T16:10:26.396Z is1failure0error/skip/Gradle1, owned unsupported.material.w5a.kind, no production/native pixels. Do not rerun solely to recreate this evidence. Extend the ONE existing independent shaderSource for scalar ordered Blend/unary filter equations; preserve old callers. Device/canvas defaulted parameters may be declared now, but coordinate-dependent interpretation is Task2, never an unverified pretransformed sample.

```kotlin
val dst = Shader.Opacity(Shader.SolidColor(ColorARGB.Blue),.5f)
val src = Shader.Opacity(Shader.SolidColor(ColorARGB.Red),.25f)
val shader = Shader.Blend(BlendMode.SRC_OVER,dst,src)
val wanted = W5fColorCpuOracle.expectedShaderTree(shader,finalBlend=BlendMode.SRC)
W5fSurfacePixelFixtures.requireBounded(wanted)
```

Complete all BlendMode.entries, alpha0/.25/.5/1, noncommutative order, shared diamond and restoring-filter placement from the shared fixture table. Each useful expected set is singleton/two-adjacent and counterfactuals disjoint BEFORE Surface/recorder. Use all twelve existing filter fixture recipes (not runtime), paint alpha127/255 and final SRC_OVER/SRC_IN/DIFFERENCE on Blue. For each promoted family use actual direct AND stencil fill fixtures from existing public W5f patterns, not two identical triangle routes mislabeled.

- [x] **Step2 — Implement the metadata-only scalar DAG→final uniform layout→same graph/proof→Ready/native slice.** Preserve prefix dst/src owner assignment, occurrence GraphLimits, completed capture memo/context facts, immutable sibling metadata and original capture identity. Exact uniform/schema/binding/numeric diagnostics come from the contract above. No value-dependent program key, retained color/upload owner, second source compiler or per-child paint alpha. Only bounded scalar leaf/filter recipes are admitted; structurally recognized pending resources/H are terminally owned.
- [x] **Step3 — Complete public retained replay/mutation and refusal recovery.** Same shared mutable ColorMatrixF32/table/filter in both branches, derive original/mutated/reversed bounded outputs before capture, then Surface/Picture original+decoded replay twice. Keep existing Picture10/schema4; Blend needs no wire change. Deep/shared occurrence graphs must reject at exact existing first-capture typed boundary, then valid append/render on SAME recorder/Surface. Name pending gradient/image and H geometry controls separately; don't remove any later positive requirement.
- [x] **Step4 — Run only the two new classes plus affected scalar/filter history on final source.**

```text
rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W5gComposedMaterialSurfacePixelTest --tests org.graphiks.kanvas.picture.W5gComposedMaterialPictureTest --tests org.graphiks.kanvas.surface.W5fFilterOrderingSurfacePixelTest --tests org.graphiks.kanvas.surface.W5fColorFilterSurfacePixelTest --tests org.graphiks.kanvas.surface.W5bBlendSurfacePixelTest --tests org.graphiks.kanvas.surface.W5aMaterialSurfacePixelTest
rtk git diff --check
rtk git commit -m 'feat(material): execute uniform-only ordered blend DAGs'
```

Stage only actual classified task paths after ROOT confirms; preserve assertion XML/native command distinction and full report/source epoch. ONE Sol spec+quality review closes this scalar slice only. No full Blend/W5g closure.

## Task 2: Contextual gradient children and shared stop resources

**Outcome:** Extend Task1's SAME V5 DAG to all four existing gradient families, stop sequences including>16, five working domains, local matrices/CoordClamp and shared leaf payloads evaluated in different branch contexts, on Rect/direct/stencil Path fill. Existing ordinary gradient RRect/stroke lanes stay admitted; composed H lanes remain refused.

**Modify:** Task1 common scalar owners/transport/oracle/classes plus existing gradient-source metadata/layout/footprint/proof/stage readers in the shared contract. `GPU/materials/W5aFrameMaterialBudgetV2.kt` and `GPU/execution/GPUW5aSourceStageNativeV2.kt` may change ONLY for exact declared shared-stop inventory/binding and actual coordinate-input consumption; no image/logical-texel migration or Noise. Reuse existing gradient numeric/interpolation authorities. No extra production file without exact ROOT classification.

**Interfaces:** Task1's program/binding/proof/layout remain. Complete common expectedShaderTree(devicePointF32,canvasMatrixF32) and private interval coordinate/gradient-stop interpreter specified above. Pending local segments clone per child; independently compose/invert/project once in F64 at clamp/end, preserving actual normalized F32 map rounding and outermost branch working precedence. Shared physical stops do not imply shared evaluated samples.

- [x] **Step1 — Write genuine RED gradient/context expectations before production.** Use shared two-stop Black/White alpha128,x=.5..1.5,local translations±.25, plus>16-stop existing W5c recipe; all four family fixtures and five domains use independently bounded/disjoint outputs. Force different branch sample/context/order, mutation/Picture original+decoded retention and old uncomposed controls. Wider fixtures stay unresolved, not empirical tolerance.
- [x] **Step2 — Inventory ALL child stops/context uniform rows before preparation; bind final shared slab ranges and issue the SAME contextual graph/proof.** Physical range sharing needs immutable owner/value proof; evaluation refs include normalized coordinate operation/domain context. Neither old offsets nor adjacent-ref inference may survive rebasing into V5. Keep program topology/code-shaped and actual coordinate inputs.
- [x] **Step3 — Complete shared-contract gradient/domain/clamp/mutation/limits public cells and run both W5g classes plus W5fGradientInterpolationSurfacePixelTest,W5dGradientAddressingSurfacePixelTest,W5cGradientSurfacePixelTest.** Retain every Task1 scalar case; freeze final source, full command/XML/native report, provisional coherent commit and ONE Sol review. This does not close image-child Blend.

## Task 3: Image children and complete mixed Blend resources

**Outcome:** Two decoded image children and mixed image/gradient/filter/shared DAGs on Rect/direct/stencil Path fill, nearest/linear/cubic/manual taps, immutable pixels/matrices and Picture retention. After this review Tasks1–3 collectively satisfy the full shared Blend acceptance contract. No external codec/A8-origin promotion.

**Modify:** Task1–2 common owners/oracle/classes, exact native source+combined memory owners, and ONLY the classified ImageNumericOperationGraphV1 transport regions above. Reuse image sampler/decoder/authority/cache/lease equations and existing device-generation/completion owners.

**Interfaces:** TexelRead V5 logical resource refs retain ownerNodeIndex/logicalSlot/code-shaped type facts, never ImageUploadPlanV1/pixel/value owner in shared program. Legacy upload-bound paths stay exact. Binding-owned PreparedComposedSource/frame mapping resolves original immutable request/upload,dynamic dimensions,final physical slot and frame/generation for BOTH proof and WGSL emission of the SAME logical operation graph. Omitting contentIdentity alone is not payload removal. Same ordered typed rows pass layout→manifest→reflection→native bind entries→retained request/lease/slab custody and complete aggregate inventory.

- [ ] **Step1 — Write independently bounded RED two-image/mixed expectations.** Use the shared two2×1 image fixture bytes, reversed pixels/order, nearest then existing bounded linear/cubic/tile/domain samples, sibling local contexts and mixed gradient child. Common shaderSource calls independent sampledImage with interval coordinates, not image-origin paint/mask equations. Expected/mutated/swapped outputs precede Surface/Picture.
- [ ] **Step2 — Implement logical image-read transport/proof resolver plus final typed resource inventory/native mapping together.** Validate every image slot against exact immutable cache request/lease/generation and storage slot against complete slab/range; no single texture/storage selector or hidden sampler. Admit/copy/pack only after all later siblings/frame lanes pass the existing checked permit. Preserve V3 pre-inventory debt without new unauthorized copy.
- [ ] **Step3 — Complete original shared-contract image/filter/working/capture/limits/H cells; run both W5g classes plus W5eImageShaderSurfacePixelTest,W5fImageFilterSurfacePixelTest,W5fGradientInterpolationSurfacePixelTest.** Retain all scalar/gradient cases; full epoch/native report, provisional coherent commit and ONE Sol spec+quality review. ROOT may then close the Blend family, not Noise or allW5g.

## Task 4: NoiseV1, integral tile API and public archive compatibility

**Outcome:** Both noise families on Rect and Path fill through Tasks1–3's existing composed source authority, with seeded tables, stitching,0..255 octave contract, constructor/wire compatibility, finite proof and work-budget refusal/recovery. Begins after Task3 acceptance and the numerical decision gate is resolved to a concrete reviewed derivation.

**Files — Create:**

- `PLAN/NoiseTableV1.kt`: deterministic immutable 4352-byte table recipe/result, normalized seed key, checked frame ownership.
- `PLAN/NoiseOperationGraphV1.kt`: typed noise loop/phase operations consumed by existing color graph proof/emission; no independent material evaluator authority.
- `TEST/surface/W5gNoiseCpuOracle.kt`: independent PRNG/table construction and outward-rounded published noise equations from public inputs.
- `TEST/surface/W5gNoiseSurfacePixelTest.kt`: both families, coordinates/stitching/limits/mutation/public native pixels.
- `TEST/picture/W5gNoisePictureCompatibilityTest.kt`: constructor surface and old/new public Picture replay.

**Files — Modify:**

- `API/paint/Shader.kt`, `IR/MaterialNode.kt`, `API/render/ir/PaintSceneAdapter.kt`, `SceneDisplayOpAdapter.kt`, `API/render/ir/ColorFilterCapturePreflight.kt`, `API/canvas/DisplayOpSnapshot.kt`: SizeI32 semantic type/bridges, earliest complete scalar validation, homonymous round-trip. DisplayOpSnapshot changes only actual validation traversal, not immutable leaf copying.
- `API/picture/Picture.kt`, `IR/SceneArchiveCodec.kt`: explicit Picture11/schema5 write and old-version F32 readers.
- Task1's `PLAN/ComposedMaterialPlanV5.kt`, `MaterialSourceConstructionV4.kt`, `FrameSourceLayoutV4.kt`, `MaterialSourceFootprintV4.kt`, `RawMaterialRequirementsV2.kt`, `ColorOperationGraphV1.kt`, `ColorSourceProofCompilerV1.kt`, `ColorSourceProofV1.kt`, `W5gPlanDiagnostics.kt`; `GPU/materials/W5aMaterialSourceStage.kt`, `W5fColorOperationEmitterV1.kt`: noise metadata/resource inventory, exact graph/proof/emission/packing through the same owners.
- `GPU/execution/GPUW5aSourceStageNativeV2.kt`, `GPU/materials/W5aFrameMaterialBudgetV2.kt`: extend Task1's SAME typed mapping/native ownership/inventory with the complete frame-local noise slab alongside any gradient slab and image slots; no single-storage-resource alias.
- `TEST/surface/W5fColorCpuOracle.kt`: join the independent Noise source intervals into Task1's common Shader interpreter, preserving its wrapper/coordinate/paint/external-filter/final-blend sequence.
- `PLAN/PlanBudget.kt`: add snapshotted software material work limits with a default preserving existing callers.
- `API/surface/RenderConfig.kt`, `API/surface/gpu/GPUPlanSurfaceRouter.kt`, `GPUPlanRenderContextOwner.kt`, `GPU/planning/GpuRenderContext.kt`, `GpuRenderBackend.kt`: transport public software noise-work limit to the existing PlanBudget equality/admission checks.
- `API/surface/gpu/GPUPlanSurfaceCandidateGate.kt`, `W5dGradientCandidateV2.kt`: owned Rect/Path noise recognition using the same gate.

**Audit/reuse:** Existing `SizeI32` declaration (no new geometry file); existing native buffer allocation/submission/completion owners and frame memory categories; existing Scene archive public validation and Picture malformed-input boundary; previous image external-codec exclusions. Any necessary additional resource-role/exhaustive transport file is identified by exact caller before edits; no native lifecycle patch is implied.

**Interfaces:**

```kotlin
// Both Shader and MaterialNode noise classes retain their existing names/field names.
data class PerlinNoise(
    val baseX: Float, val baseY: Float, val numOctaves: Int,
    val seed: Int, val tileSize: SizeI32?,
) : Shader {
    @Deprecated("Use SizeI32 for an integral noise tile")
    constructor(baseX: Float, baseY: Float, numOctaves: Int, seed: Int, tileSize: SizeF32?) :
        this(baseX, baseY, numOctaves, seed, checkedNoiseTileI32(tileSize))
    constructor(baseX: Float, baseY: Float, numOctaves: Int, seed: Int, tileSize: Nothing?) :
        this(baseX, baseY, numOctaves, seed, null as SizeI32?)
}
// FractalNoise has the same three signatures. MaterialNode uses the same scheme.
public data class MaterialFrameLimits(
    public val maxNoiseOctaveEvaluationsI64: Long = 1L shl 30,
)
public data class PlanBudget(
    public val maxFrameLocalBytes: Long,
    public val materialFrameLimits: MaterialFrameLimits = MaterialFrameLimits(),
)
// Added RenderConfig field; public software budget, not an injected device capability:
val maxNoiseOctaveEvaluationsI64: Long = 1L shl 30

internal class NoiseTableV1 private constructor(
    val normalizedSeedI32: Int, val bytes: ImmutableUBytes,
) {
    companion object {
        fun prepare(normalizedSeedI32: Int, owner: FrameSourceLayoutV4): NoiseTableV1
    }
}
```

Require noise-work limit >=0, so zero legitimately permits zero-octave noise and rejects requested nonzero work. Default `2^30` is the explicit software policy of this plan and must be acknowledged in ROOT/Astra plan review; no preexisting device fact is claimed. `checkedNoiseTileI32` is a shared render-ir semantic conversion function reused by public adapters/readers; it accepts null, otherwise validates each F32 with `isFinite`, `>=0`, `toDouble()<=Int.MAX_VALUE.toDouble()`, and `value.toDouble()==floor(value.toDouble())` BEFORE `toInt()`. In particular `2147483648f`, NaN, infinity, negative or fractional axes are invalid. Reuse `SizeI32` after validation. Do not validate via `Int.MAX_VALUE.toFloat()` (it rounds out of range).

- [x] **Step 1 — Close the numeric representation decision before code.** Write the exact operation sequence and proof that it preserves the approved q/floor/fract/stitch recurrence, including its branch decisions, overflow/FTZ possibilities and loop bounds. An integral-zero tail may be skipped only if BOTH coordinate fractions are exactly zero for every permitted rounding branch and all subsequent contributions are identically zero, not merely small. One integral axis alone is insufficient. No epsilon tail truncation, lower semantic octave ceiling or successful fallback. If no such representation is derived, report that precise blocker and leave Noise unimplemented; do not run speculative native experiments to select a numerical rule. R28 concrete derivation approved MAIN; this design gate does not claim implemented correctness or public evidence.

- [x] **Step 2 — Write RED public zero-octave and seeded nonzero fixtures, including constructor calls.** Genuine12production-refusal RED invocations, public old null constructors compiled; ROOT actualXML/source custody closed. Full SizeI32/F32/Nothing? migration cases remain mandatory Step5, not claimed here.

```kotlin
@Test fun zeroOctavesAreDifferentMaterialsOnBothRequiredGeometries() {
    val shaders = listOf(
        Shader.PerlinNoise(.125f, .25f, 0, 7, null),
        Shader.FractalNoise(.125f, .25f, 0, 7, null),
    )
    val expected = shaders.map { W5gNoiseCpuOracle.expected(it, Point2F32(.5f,.5f)) }
    expected.forEach(W5fSurfacePixelFixtures::requireBounded)
    for (path in listOf(false,true)) shaders.forEachIndexed { index, shader ->
        val surface = Surface(1,1)
        surface.canvas {
            val paint = Paint(shader=shader, blendMode=BlendMode.SRC, antiAlias=false)
            if (path) drawPath(Path().apply {
                moveTo(-10f,-10f); lineTo(40f,-10f); lineTo(-10f,40f); close()
            },paint) else drawRect(RectF32.ofLTRB(0f,0f,1f,1f),paint)
        }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected[index])) }
    }
}
```

`W5gNoiseCpuOracle.source(shader: Shader, localPoint: Array<WgslFloatEnvelopeV1Oracle.Interval>): Array<WgslFloatEnvelopeV1Oracle.Interval>` is the independent raw linear-premul source recipe for the two Noise leaf kinds; it consumes the two outward-rounded coordinate scalars without narrowing them. Task1's common `shaderSource` adds both Noise leaf cases delegating to this test-only function. `W5gNoiseCpuOracle.expected(shader: Shader, pointF32: Point2F32, paintAlphaF32: Float=1f, destination: ColorARGB=ColorARGB.Transparent, finalBlend: BlendMode=BlendMode.SRC, external: ColorFilter?=null, canvasMatrixF32: Matrix3x3F32=Matrix3x3F32()): WgslFloatEnvelopeV1Oracle.DrawResult` delegates the full public shader tree to `W5fColorCpuOracle.expectedShaderTree`, passing `pointF32` as `devicePointF32` and every named paint/filter/blend/CTM argument. It is callable for mixed Blend/Noise wrappers, not a second wrapper evaluator. All independent PRNG/table/noise equations remain in W5gNoiseCpuOracle; neither oracle imports production plans/formulas/proofs. At zero octaves the raw source is exact linear-premul Perlin `(0,0,0,0)` and Fractal `(.25,.25,.25,.5)`, before common paint/filter/final attachment equations. Do not turn Fractal into an sRGB `SolidColor(.5)` before linearization.

```text
rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W5gNoiseSurfacePixelTest --tests org.graphiks.kanvas.picture.W5gNoisePictureCompatibilityTest
```

- [ ] **Step 3 — Implement deterministic tables only after full-frame metadata admission.** The pinned references were actually read at Skia commit `70977ebbdbc111776199920c8c25243ba5dc71db`: [noise header](https://github.com/google/skia/blob/70977ebbdbc111776199920c8c25243ba5dc71db/src/shaders/SkPerlinNoiseShaderImpl.h) blob `51772235080948e81580f4f9d3feeeed80753de8`, [noise implementation](https://github.com/google/skia/blob/70977ebbdbc111776199920c8c25243ba5dc71db/src/shaders/SkPerlinNoiseShaderImpl.cpp) blob `beee8413ae7afb66b8efa2edfa9bebe99cb61a26`, and [SkPoint normalization](https://github.com/google/skia/blob/70977ebbdbc111776199920c8c25243ba5dc71db/src/core/SkPoint.cpp) blob `d4fa426112a83ed6615d6dd0c85346e89df86505`.

```text
m=2147483647, q=127773, r=2836, multiplier=16807
s = seed<=0 ? -(seed % 2147483646)+1 : min(seed,2147483646)
next: t=16807*(s%127773)-2836*(s/127773); s=t<=0 ? t+m : t
perm[i]=i
channel0..3, i0..255: raw[channel][i]=(next()%512,next()%512)
i255 downTo1: swap(perm[i],perm[next()%256])
gradient[channel][i] comes from raw[channel][perm[i]]
x=(rawX-256)/256; y=(rawY-256)/256
normalize as pinned SkPoint: Double sqrt(x*x+y*y), Double reciprocal,
  project x*scale,y*scale toF32; zero vector -> (0,0)
quantize each component using pinned F32 add/multiply then positive half-up round
  (zero normalized component becomes U16 32768)
bytes0..255 = permutation; remaining4096 = channel-major/i-major/x,y U16 LE
total4352 bytes = 1088 packed U32 words
```

The application seed is I32 per approved spec; do not cast it through upstream's historical SkScalar seed. CPU and GPU consume the same prepared permutation/U16 bytes. The test oracle independently constructs its own bytes from public seed inputs, with no production table/helper import. U16 extraction is integer little-endian. Decode a component by the approved inverse normalization `(u16/32767.5)-1` with its real WGSL division/subtraction envelope; 32768 is not silently changed into exact zero.

- [ ] **Step 4 — Connect the reviewed loop and exact resource/work checks.** Keep all q/smooth/dot/bilerp/abs/sum/clamp/premultiply operations in the typed graph consumed by `ColorSourceProofV1` and `W5fColorOperationEmitterV1`. The finite loop uses dynamic requested octaves; seed/frequency/tile/octave values do not create shader variants. Allocate ONE read-only frame-local noise slab, with one 4352-byte, 16-byte-aligned range per unique normalized seed. Every node carries a dynamic base range; the same/different seed count changes slab bytes and numeric proof identity, never the program key or binding topology. Check each range and the complete physical slab, storage limits/stage count, bind-group count and total frame bytes before table preparation. Tables live through last consumer completion using existing ownership; no new cache or per-node hidden native allocation.

For each contextual noise evaluation, charge checked `ceil(conservativeWidth)*ceil(conservativeHeight)*requestedOctaves*4`, with bounds conservatively intersected with the real target as in existing geometry admission. Sum across actual draws/lane consumers; shared bytes do not imply shared evaluations across draws or different coordinates. Reused same-context DAG evaluation may count once only when the emitted evaluation actually occurs once. Compare to the snapshotted `MaterialFrameLimits` before `Ready`, before bulk tables/packing/native resources; overflow is a typed budget refusal.

Use diagnostics `invalid.material.noise.parameters`, `invalid.material.noise.tile`, `unsupported.material.composed.slice`, `unsupported.material.noise.slice`, `unsupported.material.noise.numeric-domain-unbounded`, `budget.material.noise.octave-evaluations`, and `budget.material.noise.storage` at their actual existing typed boundaries. Archive malformed conversion returns the existing invalid-archive public boundary with the precise conversion reason, rather than a raw cast or accepted saturated tile. Do not use string matching as a replacement for available typed recording diagnostics.

For positive axes adjust frequency exactly as global §11.2: zero base stays zero; choose floor/tile versus ceil/tile by the strict ratio comparison, tie/low0 choose high. Freeze the uniform decision; do not recompute it differently in WGSL. Wrap all four lattice corners before permutation lookup; double the integer period at each octave by the reviewed safe representation. An absent or zero-axis tile disables stitching.

- [ ] **Step 5 — Complete mandatory public fixture matrix and archive compatibility.**

| Coverage | Concrete inputs and required discriminants |
| --- | --- |
| Seed normalization | `Int.MIN_VALUE,-7,0,1,7,Int.MAX_VALUE`; compare equal-normalized pairs and distinct-seed nonzero noise outputs using independently constructed tables. |
| Octaves | `0,1,2,8,255` for BOTH families and BOTH required geometries; `.125,.25`, seed7, points `.5,.5` and `1.5,.5`; 255 case must be actual nonzero initial work, not merely base0. |
| Stitch | `SizeI32(8,4)`, `.2,.3`, seed7, octaves2/8/255; compare local P and P+(8,0)/(0,4), plus independent absolute expected pixels; low0 `.03125` on tile8, exact frequency `.25`, tie boundary derived from the strict ratios. |
| Disabled stitch | null, `(0,4)`, `(8,0)`, `(0,0)`; same public pixels as absent tile. |
| Local coordinates | inherited W5d transform order `C*T*R`, sibling-local clamps, negative local P and lattice boundaries. Shared noise leaf under two different matrices cannot reuse the same sampled result. |
| Composition | Perlin as dst/Fractal as src, and the reverse, inside opacity `.5`; external Matrix restoring alpha, paint alpha127/255, final SRC_IN/DIFFERENCE on Blue. Require bounded order and alpha counterfactuals before Surface. |
| Invalid parameters | negative/NaN/infinite base, octaves-1/256, I32 negative tile, historical F32 axis `.5`, `-1`, NaN, infinity, `2147483648f`; reject at capture/constructor boundary before snapshot allocations, then valid capture/render recovery. |
| Constructors | named/positional literal null, `SizeF32?=null`, `SizeF32?=SizeF32(8f,4f)`, `SizeI32?`, both families, public render and Picture playback; source compilation itself checks overload resolution without an ABI/private test. |
| Archives | frozen genuine old Picture8/9/10 noise payloads with absent/zero/integral tile; each new11 round-trip; malformed old fractional/nonfinite/out-of-range F32 tile rejected. Retain existing old8/9 image fixtures unchanged. |

Historical noise fixtures must come from the actual parent public Picture writer before Task4 changes, with fixed bytes/provenance. Use kanvas/src/test/resources/picture/format-10-noise-integral.base64 and format-10-noise-absent.base64; older8/9 require actual old writer/documented public wire fixture, never v11 version-byte substitution. Missing genuine payloads remain an exact compatibility gap, not coverage from unrelated image fixtures.

- [ ] **Step 6 — Run the two complete public noise classes and Task1's two classes, then commit.** Preserve source epoch, actual XML names/byte bounds and command exit separately. Compile modified math modules on JVM/JS only if new public math functions were actually necessary; merely reusing SizeI32 does not justify new math infrastructure tests. Use a coherent commit message `feat(material): add NoiseV1 and integral tile compatibility` after the reviewed source passes its targeted functional assertions.

## Task 5: Mixed-frame work/storage refusal and retained recovery

**Outcome:** Publicly validate the whole-frame sharing/work/ownership contract after both families exist, preserving old sources in the same frame. This is a behavioral integration delivery, not a scaffolding or bookkeeping task.

**Files — Create:** `TEST/surface/W5gConvergenceSurfacePixelTest.kt`.

**Files — Audit/reuse:** Task1–4 production owners, existing public W5a–f classes and native readback helpers. A causal production defect is fixed only in its already-classified owner; identify an extra owner to ROOT before expanding the file set. No pool/budget/native workaround.

**Interfaces consumed:** Public `RenderConfig(maxNoiseOctaveEvaluationsI64=...)`, `Surface(width,height,config)`, `canvas { ... }`, `render()`, Shader Blend/Noise constructors, Picture capture/replay; the common Blend Shader interpreter completed by Tasks1–3 and Task4's independent Noise oracle. No new production API.

- [ ] **Step 1 — Write public work-budget boundary and same-runtime recovery.** The calculation is exact: 1×1, one same-context noise evaluation, 2 octaves ×4 channels =8. A second distinct-coordinate evaluation costs another8; same bytes do not waive it. An alternating Rect/Path frame has two draws and costs16 even when their seed/table is shared.

```kotlin
@Test fun workBudgetChargesBothGeometryConsumersAndRecovers() {
    val shader = Shader.FractalNoise(.125f,.25f,2,7,null)
    val expected = W5gNoiseCpuOracle.expected(shader,Point2F32(.5f,.5f))
    W5fSurfacePixelFixtures.requireBounded(expected)
    val healthy = Surface(1,1,config=RenderConfig(maxNoiseOctaveEvaluationsI64=16))
    healthy.canvas {
        drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(shader=shader,blendMode=BlendMode.SRC,antiAlias=false))
        drawPath(Path().apply { moveTo(-10f,-10f); lineTo(40f,-10f); lineTo(-10f,40f); close() },
            Paint(shader=shader,blendMode=BlendMode.SRC,antiAlias=false))
    }
    W5fSurfacePixelFixtures.assertNativePixels(healthy.render(),listOf(expected))
    val refused = Surface(1,1,config=RenderConfig(maxNoiseOctaveEvaluationsI64=15))
    refused.canvas {
        drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(shader=shader,blendMode=BlendMode.SRC,antiAlias=false))
        drawPath(Path().apply { moveTo(-10f,-10f); lineTo(40f,-10f); lineTo(-10f,40f); close() },
            Paint(shader=shader,blendMode=BlendMode.SRC,antiAlias=false))
    }
    repeat(2) {
        val failure = assertFailsWith<IllegalStateException> { refused.render() }
        assertEquals("budget.material.noise.octave-evaluations",failure.message.orEmpty().substringBefore(':'))
        W5fSurfacePixelFixtures.assertNativePixels(healthy.render(),listOf(expected))
    }
}
```

The healthy Surface uses the same existing runtime, not an invented configurable failure injector. Also prove recovery on the same recording owner after invalid capture followed by a valid append. Do not claim same-target numeric rollback or native device-loss/quarantine coverage from the separate healthy Surface.

- [ ] **Step 2 — Add causal byte sharing and mixed ordering cases.** On a 29×1 target use64 alternating Rect/Path draws. In BOTH one-seed and64-distinct-normalized-seed controls retain a non-seed per-draw discriminator: the existing constant-output Matrix pattern uses RGB coefficient rows0, red translation `2f+indexI32/128f`, green/blue translations0, alpha coefficient row0/translation1. Clamp gives Red, but each of the64 source values remains distinct in BOTH controls. Use this external filter at the same position and with the same80-byte record in both, preserving graph topology, source-uniform allocation count/size, coordinates, geometry, work and all other resources. Derive the bounded Red output independently through the common Shader oracle before recording; do not assert internal identity/counts in tests.

Compare seed7 in all64 draws versus64 seeds with independently verified distinct normalized keys. First document both COMPLETE declared physical inventories, including64 distinct source uniforms, equal geometry/filter/coordinate records and pessimistic residency costs. Their only difference must be63 additional16-byte-aligned4352-byte table ranges (`63*4352=274176`); changing seed/source canonical identity alone without fixing the other inventory is not a causal test. Then analytically derive a reachable public frame budget between those inventories, before native execution. Do not reuse1,585,000 blindly or tune a budget until a desired error appears. Require shared-table positive control, precise distinct-table storage refusal and valid repeated native recovery. If no safe public causal window exists, the mandatory public storage witness remains OPEN; static ownership review may disclose the gap but cannot pass or close it. Do not fabricate a capability.

Mix ordered Blend, both Noise kinds, >16-stop gradient and two decoded image children across Rect/direct Path/stencil Path, interleaving ordinary old W5a–f draws and final destination-read. Compute the entire paint-order oracle independently; ensure later child preparation cannot evade earlier or later frame-wide budgets. Mutate the original stops/matrices/pixels after capture, replay original and decoded Picture, and render twice. Keep unsupported H, AA4 and spatial/runtime refusal controls distinct from positive cells.

- [ ] **Step 3 — Run the five new W5g public classes as one targeted covering, then the exact inherited fourteen-class covering plus these five classes once on the frozen final source.** ROOT read the actual final06 argv in `/private/tmp/w5f-task8-fix1-06-final-fourteen-forced-command.txt`; the explicit final19 command below preserves those fourteen names/order and adds the five new classes. `W5ePictureImageSamplingTest` exists but was not in that recorded fourteen-class covering; do not silently replace the historical set with a wildcard. No global `:kanvas:test` without `--tests` and no GM suite. Every previous public name is retained; compare actual fresh XML names, failures/errors and skips. Report each actually observed native exit133 separately from assertions, with causeUNKNOWN unless concrete evidence establishes it; recurrence does not prove an inherited cause. No rerun solely to relabel the native command green.

```text
rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W5gComposedMaterialSurfacePixelTest --tests org.graphiks.kanvas.picture.W5gComposedMaterialPictureTest --tests org.graphiks.kanvas.surface.W5gNoiseSurfacePixelTest --tests org.graphiks.kanvas.picture.W5gNoisePictureCompatibilityTest --tests org.graphiks.kanvas.surface.W5gConvergenceSurfacePixelTest
rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W5fColorFilterSurfacePixelTest --tests org.graphiks.kanvas.surface.W5fFilterOrderingSurfacePixelTest --tests org.graphiks.kanvas.surface.W5fGradientInterpolationSurfacePixelTest --tests org.graphiks.kanvas.surface.W5fImageFilterSurfacePixelTest --tests org.graphiks.kanvas.surface.W5fConvergenceSurfacePixelTest --tests org.graphiks.kanvas.picture.W5fPictureFilterInterpolationTest --tests org.graphiks.kanvas.surface.W5eDecodedImageSurfacePixelTest --tests org.graphiks.kanvas.surface.W5eImageFamiliesSurfacePixelTest --tests org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest --tests org.graphiks.kanvas.surface.W5eImageConvergenceSurfaceTest --tests org.graphiks.kanvas.surface.W5dGradientAddressingSurfacePixelTest --tests org.graphiks.kanvas.surface.W5cGradientSurfacePixelTest --tests org.graphiks.kanvas.surface.W5bBlendSurfacePixelTest --tests org.graphiks.kanvas.surface.W5aMaterialSurfacePixelTest --tests org.graphiks.kanvas.surface.W5gComposedMaterialSurfacePixelTest --tests org.graphiks.kanvas.picture.W5gComposedMaterialPictureTest --tests org.graphiks.kanvas.surface.W5gNoiseSurfacePixelTest --tests org.graphiks.kanvas.picture.W5gNoisePictureCompatibilityTest --tests org.graphiks.kanvas.surface.W5gConvergenceSurfacePixelTest --rerun-tasks --no-parallel --console=plain
rtk proxy ./gradlew :render-ir:compileKotlin
rtk proxy ./gradlew :gpu-plan:compileKotlin
rtk proxy ./gradlew :gpu-renderer:compileKotlin
rtk proxy ./gradlew :kanvas:compileKotlin
rtk proxy ./gradlew :kanvas:compileTestKotlin
rtk git diff --check
```

Five compile commands are separate invocations, not a combined claim. Fresh final public tests must cover the same source that is committed; later source changes invalidate that final-source claim and require the necessary affected covering, not endless arbitrary reruns. ROOT verifies staged/committed source against tested source using normal repository custody checks, without adding source/hash tests to the suite.

- [ ] **Step 4 — Finish the reviewed branch.** Commit the coherent public integration slice, then ROOT performs ONE whole-branch Sol review against the W5f base. Give every Critical/Important finding verbatim to one adapted Astra fix wave and return only those findings plus introduced breakage to ONE scoped Sol review. If the plan's numerical or public evidence gate remains unresolved, report it precisely; no W5g CLOSED claim. After acceptance ROOT updates only the three durable docs and opens the single stacked Draft PR. No merge and no parent update.

## Self-review checklist for ROOT and fresh Astra plan review

The SDD preflight checks each self-row and every pair that shares a file or interface:

| Review relation | Required coupling check |
| --- | --- |
| Task1 → Task1 | Scalar capture/DAG/final uniform graph+proof →29modes/public Rect/direct+stencil/replay; no claimed resource promotion. |
| Task2 → Task2 | Independent normalized segment/gradient oracle →context DAG/final shared stops→same proof/emitter/public families/domains. |
| Task3 → Task3 | Logical image reads→binding-owned original upload/frame map→typed inventory/native custody→two-image/mixed public pixels. |
| Task4 → Task4 | Tile/archive→pinned table→reviewed255loop→software work policy→both Noise public families. |
| Task5 → Task5 | Independent whole-frame oracle→causal complete-inventory budgets/refusal→native recovery, no reset. |
| Task1 → Task2 | Same capture/material/source/layout/proof/stage/oracle/classes; gradients extend scalar DAG, no replacement/historical regression. |
| Task1 → Task3 | Same root program/binding/graph/typed layout/oracle/classes; image resources preserve scalar operator/alpha/filter order. |
| Task1 → Task4 | Same V5 shape/proof/permit; Noise adds leaf graph/work facts, no new material system. |
| Task1 → Task5 | Scalar cases/owner contracts preserved in final mixed covering. |
| Task2 → Task3 | Same branch coordinates/shared stops/proof/native resource rows/oracle; image sampling uses same interval contexts, not image-origin equations. |
| Task2 → Task4 | Same normalized coordinates/stop storage/stage mapping; Noise slab coexists, no single-storage alias. |
| Task2 → Task5 | Gradient/domain/mutation/native cases retained in mixed frames. |
| Task3 → Task4 | Same logical physical-resource/authenticity/native inventory; Noise extends typed mapping without payload-retaining program. |
| Task3 → Task5 | Image/gradient mixed bindings/leases/native custody and public classes retained. |
| Task4 → Task5 | Dynamic seed/slab/work config agree; sharing bytes differs from contextual/draw work; full255/stitch/archive gates retained. |

Each relation is an independent review obligation. ROOT's preflight records the actual file/interface joins before dispatch; it does not require another tracked document.

- [ ] §§5.2–5.3: dst/src and effective alpha/filter/coordinate order are carried by the actual graph, including shared sibling contexts; child blend never becomes draw blend.
- [ ] §§5.4–5.5: metadata capture, full-frame checked inventory and exact owners precede every new bulk copy/preparation/packing/native action; existing V3 debt is identified, not expanded.
- [ ] §5.6: independent oracle and actual graph share only the published contract, not production implementation. 255-octave representation and rounding/tail derivation are resolved before Noise edits; valid refused domains stay explicit.
- [ ] §11.2: PRNG, seed range, table generation order, pinned zero normalization, U16 little-endian storage, q+.5, turbulence versus signed fractal, alpha premultiplication, zero octaves, stitching choice/wrap, full work charging and table ownership have concrete obligations.
- [ ] Compatibility: nullable bridge is real; old F32 malformed values cannot saturate or be bit-reinterpreted; Picture10 support is retained alongside8/9; SizeI32 is reused.
- [ ] Matrix: Blend, Perlin, Fractal each have Rect AND Path fill plus alpha/mutation/nontrivial final blend. H lanes are named; old promoted source lanes are not unintentionally rejected/promoted.
- [ ] Exact file map: every new symbol is defined in this plan; any further consumer identified by implementation is classified before edit. Source review, never an infrastructure test, verifies architectural uniqueness.
- [ ] Closure: W5f historical native failure/warnings/numerical limits remain accurately inherited; five implementation tasks grouped into three family deliveries and one bounded final review/fix cycle do not promise native/global ISO or W5h/W6 work.

## ROOT preflight and plan-review decisions

Fresh Astra review/scoped confirmation closes the original3Important and coordinate correctionR8. The monolithic worker subsequently returned genuine scalar RED plus a FULL131-line size escalation, no production. R9–11 settle missing exact layout/diagnostics, payload-free logical image transport and functional execution decomposition. Same-seat refinement confirmation reads the complete585-line refined plan and permits scalar1 resumption:0Critical/0Important/1nonblocking numbering Minor, corrected in the two live references above. The15self/pair rows are recorded in the own ignored ledger; no old/sibling scope is current progress. R1–8 use the original numbering: oldBlend1 is nowTasks1–3,oldNoise2 now4,oldConvergence3 now5; their requirements/costs remain, not waived.

| Ruling | Decision and reason | Cost if wrong / required safeguard |
| --- | --- | --- |
| R19 | Real V5 full ColorSourceProof on same logical sampler graph/context/actual device bounds, never a fabricated V3 identity-domain certificate. Factor only existing coordinate-expression schedule; preserve cumulative valid/reset/CTM/clamp order.45th owner ImageNumericAuthorityV1 only finite-texel predicate visibility private→internal, body/legacy arithmetic seal unchanged. ROOT full original authority, actual coordinate owner, complete common prove/issueComposed/substitution and sampler read before edit. | Coordinate/invalid-point divergence/counterfeit authorization/owner or legacy regression; exact original-context-upload-row-frame resolver, all reachable operation proofs, raw-texel predicate, real image/context/clamp/projective/history and Sol audit. Identity sampler projection is recipe not certificate; no new numerical policy/evaluator/box, ImageCoordinate/Execution owner read-only. |
| R18 | Shared decoded classification metadata extraction inside existing EffectiveMaterialPlanner; V5 retains original immutable Pixels and checked dimensions/formats/cursor without seal/upload copy before whole-frame checked permit. Existing ImageUploadPlanV1.seal unchanged only afterward. Actual V3 same order/debt preserved; ROOT complete planner/metadata/seal/Pixels/capture and actual overlay caller read before edit. | New prebudget copies/wrong origin-request-slot/late-sibling undercharge/old regression; exact same metadata→issued upload→proof/native receipt and complete planned/actual inventory seals, all arithmetic/leaf diagnostics retained, public mixed memory/refusal/history and Sol audit. No new owner/hierarchy/physical policy/canonical-only sharing;44 finite paths remain, Upload/ResourceSnapshot read-only. |
| R17 | Narrowly parameterize W5eImageTexelEvaluatorV1.addressDeclarations helper symbol, exact legacy default; actual sibling tile topologies need distinct authenticated slot-shaped names. Keep narrowed R10 graph-free TexelRead/SampledRegion facts; constructor/SamplingKind rewrite withdrawn before edits. ROOT full evaluator/numeric transport/recipes and actual colorV4/imageV3 callers read before authorization. | Helper collision/wrong address or historical regression; same axis/guards/decoder/kernel equations and typed proof/native resolver, public two-image/tile/mixed/history and Sol gate.44th finite Modify owner only function-symbol region, no dynamic value key/sentinel/new numerical rule/recipe or legacy-constructor rewrite. |
| R1 | New W5g branch at unchanged W5f final55e4992d5 in existing isolation; future Draft PR targets W5f#2400 only. User authorized next stacked lot, not parent mutation/merge. | Stack contamination; exact branch/status and individually classified paths before commits, no parent writes. |
| R2 | Blend Task1 independently proceeds after plan acceptance; Noise production waits for a reviewed safe255 phase/stitch derivation. Full valid255 semantics are binding. | Overflow/false closure or unnecessary Blend delay; no epsilon/lower ceiling/refusal-as-success, mandatory public255/stitch witnesses. |
| R3 | Accept software work default2^30 and verified Nothing? nullable bridge, reusing math SizeI32. Explicit implementation refinements, never physical capability facts. | Old call-site break or poor budget default; real four declarations/public render/Picture constructor cases, exact budget/equality transport review; no binary/generated-copy promise. |
| R4 | Genuine old8/9/10 Noise archive and causal public storage witnesses remain required; unavailable evidence stays an open gate, never static/unrelated-fixture closure. | Wire regression or hidden memory/ownership defect; authentic provenance and analytically derived public controls before corresponding acceptance. |
| R5 | One independent point-aware public-Shader interpreter with defaulted device point/CTM, interval coordinates/wrappers/images/stops, and Task2 raw Noise interval join/common external-filter sequence. Actual disconnected oracle could not produce mandated fixtures. | Oracle-only errors masquerading as RED or missed sampled/coordinate behavior; published equations only, bounded/disjoint expectations before capture and actual Native Render/Readback. |
| R6 | Add exact existing native source binding and aggregate-memory owners to Task1/2; same typed resource rows and dynamic DAG/binding owners through native reflection/bindings/lease/slab custody. Actual code has singular resource assumptions. | Missing/aliased/unaccounted resource or unsafe lifetime; SAME materializer/completion/rollback owner, historical layouts/H retained, no pool/dispatcher/harness/lifecycle redesign. |
| R7 | Both Task3 controls retain64 distinct source uniforms through the same per-draw Matrix discriminator; freeze complete non-table inventory before table-only274176-byte delta/budget. Actual canonical dedup confounds seed-only controls. | False causal refusal or hidden residency costs; both complete inventories, independent output and positive/refusal/recovery, no tuned budget or static-only pass. |
| R8 | Preserve existing local-coordinate normalization boundaries in the independent oracle: compose each uninterrupted branch-local segment in F64, invert/project once before clamp/end; clone pending segments across Blend. Actual MaterialCoordinatePlanV2 does not map a separate F32 inverse per edge. | Wrong rounding/transform oracle could mask or falsely report a coordinate defect; independent equations and normalized F32 map envelopes, no production helper imports or new flush at Blend. |
| R9 | Copy exact built-in §12 layout rows/tags/hash encoding and six composed diagnostic strings into the brief; reuse current budgets, no runtime admission. Worker identified missing supplied contracts before production. | Guessed ABI/diagnostic owner or key fork; same sealed rows through proof/emission/reflection/native, old first-leaf diagnostics retained. |
| R10 | Classify only ImageNumericOperationGraphV1 logical-read transport regions for Task3; code-shaped V5 refs resolve through binding/frame-owned exact image proof/emission. Actual TexelRead retains upload payload after rebase. | Value-retaining program, sampled wrong owner/slot or decoder divergence; no key-only workaround, exact legacy sampler paths and same graph/owner mapping. |
| R11 | Split too-large monolithic Blend unit into scalar1,contextual-gradient2,image3; Noise4 and convergence5 follow. Retain all29/full-family/native/shared-budget obligations and ONE final stacked PR. | Partial slice mislabeled family closure or extra integration rework; owned pending-family refusal, each complete public slice+Sol review, full Blend closure only after3, wholeW5g only after5/gates. |
| R12 | Classify ONLY W4eNativePayloadPlan.collectPathPayload.materialColor's exhaustive MaterialV5→W5g Unpromoted branch, before edit, after ROOT full local-function/caller inspection and concrete compiler error. | Unintended W4e admission or old diagnostic loss; rejection-only hunk, preserve all historical branches, public/H controls and Sol review, no flattened material/native payload policy change. |
| R13 | Classify five exact exhaustive H readers: GPUPreparedMaterialProgram.hasNonFiniteW5aBindings, prepared Text/Vertices tableSnapshotIdentity, W4b RRect validate/resolve and W4d stroke resolve; V5 rejection only, after focused ROOT function/caller checks. | H leak/common-stage refusal or old gradient/token regression; retain all old branches especially strokeV4/V2, no flattening/resource/lifecycle change, functional/history/H gates and Sol review. |
| R14 | SAME captured owner may have distinct local uniform context/header fields in one grouped logical block; all gradient refs explicitly resolve to ONE shared frame stop slab physical row at the first prefix gradient owner. Normative§7.4 requires ALL frame gradients share one buffer/offsetzero;§12maps field-local offsets. Worker asked before production. | Wrong offsets/shared samples/provenance or double/omitted storage; checked nonoverlap/prefix owner grouping, explicit context/range/original-owner→same row proof/emission/native/frame custody, existing immutable cursor/domain/recipe rules, complete byte charge once, no dynamic program payload, public context/domain/mutation/history and Sol review. |
| R15 | Correct literal mutable-local-matrix fixture to SAME mutable stops and ColorMatrixF32/filter shared across branches under immutable Matrix3x3F32. Actual final data class has nine val coefficients; assigning local.tx cannot compile. Coordinate/order witnesses remain separate. | Missing mutable capture path; actual API inspection, meaningful changed-stop/color-matrix/reversed expectations before capture and retained Surface/Picture original+decoded native pixels twice, Sol task review. No new mutable math type, private/reflection test or Shader/wire/API change. |
| R16 | Preserve exact historical W3/composite `<session>.gradient-stops` allocation contracts; all mixed-lane stop records use the authentic composite session identity. Keep both historical readers read-only. Link included complete allocation to same source partitions/frame/storage proof/slab; narrowly necessary immutable CPU provenance may travel through existing whitelisted packet/source joins, not a guessed/canonical label owner. | Inconsistent lowering or wrong/double/omitted slab charge; exact actual seals, typed session/packet/frame ownership, complete bytes/kind/category/extent/lifetime checks, range proofs and single-charge inventory, focused public controls/historical covering and Sol static review. No seal relaxation, allocation-label parsing, GPU ABI/numeric/budget/lifecycle or out-of-whitelist expansion. |

Execution status: Tasks1–3 functionally accepted after their sole Sol task reviews (Tasks1/3 SAME-seat fix1 and scoped closure; Task2 no correction),0Critical/Important/0newMinor. Task3 final529PASS0F/E/S on exact committed source remains distinct from FAILED/native224exit133 UNKNOWN. Bounded Blend family is delivered; Noise4 safe255/archive/storage and convergence5/final19/five separate compiles/whole Sol/ONE Draft remain open. ROOT owns durable progress; no parent mutation, nativeGREEN, Ready-to-merge or ISO claim.
