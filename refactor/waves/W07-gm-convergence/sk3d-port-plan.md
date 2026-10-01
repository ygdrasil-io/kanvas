# W7 Sk3d Faithful Port Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Execute the original Skia sk3d_simple scene and measure its true residual parity gap.
**Architecture:** Correct only the existing GM port using math Matrix4x4F32 and planar asM33; retain direct drawing and genuine Picture Rect+CTM playback.
**Tech Stack:** Kotlin/JVM, public Surface/Picture, native WebGPU, frozen Skia corpus.
**Spec:** `refactor/waves/W07-gm-convergence/sk3d-port-design.md`

## Global Constraints

- Références, seuils, domaine LINEAR, exclusions et timeout 30 s/GM restent inchangés.
- Fonts, codecs/décodage externe et jpg-color-cube restent exclus.
- Aucune relaxation de proof, epsilon, cap ou oracle existant. Pas de fallback CPU, de routage par nom de GM ni de merge.
- Pas de tests d'infrastructure, mocks, forwarding ou inspection de source ; tests publics natifs seulement.
- Géométrie dans math avec nomenclature I/F32/64 ; réutiliser les matrices existantes.
- Workdir `/Users/chaos/.codex/worktrees/w7-gm-diagnostic/kanvas`, branche `codex/w7-sk3d-port`, base `b31185372f65c1597edc5fc7d2f783f528f88e09`. RTK pour tout shell, apply_patch absolu, aucun helper chez les workers.
- Terra implémente, Sol relit ; Astra réservé à un blocage architectural réel. Draft empilée sur #2426, publication par contrôleur.

## Review Focus

1. Ordre CTM×translation, et non translation×CTM : témoins de silhouette.
2. Degrés/radians et rotation Y vs Z : coordonnées device indépendantes.
3. Alpha 136/255 vs 0,5 et Picture omise : couleur intérieure indépendante.
4. AA implicite et bord à x=40,504985 : pixels voisins hard, sans oracle ajusté.
5. Picture réelle et état restauré : audit du port, pixels extérieurs et repeat natif ; aucune substitution de chemin pour contourner une capacité manquante.

### Task 1: Correct the port and qualify its pixels

**Files:**
- Modify `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/composite/Sk3dSimpleGm.kt`.
- Create `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/Sk3dSimpleSurfacePixelTest.kt`.
- Update `refactor/waves/W07-gm-convergence/status.md`, `pilotage.md`, `refactor/README.md`; add measured `sk3d-port-<sha>.json` alongside the prior lot's snapshot.

**Interfaces:** Existing SkiaGm.draw/GmCanvas, PictureRecorder and math matrices. No new renderer/API contract. Read the spec for exact Skia parameters and order; update the port's source link to the pinned revision. Direct GmCanvas and recorder Canvas retain their existing distinct adapters.

- [ ] Write three public native tests named `perspectiveFootprintMatchesIndependentCamera`, `pictureOverlayUsesSkiaAlpha`, `hardEdgeHasNoPartialCoverage`. Record the actual GM onto a white public Surface, using its existing composition config; no snapshot-op/source-text assertions. The expected coordinates/colors are set before creating Surface.
  - Footprint: inside `(60,100)` and `(150,100)` are `(182±1,0,193±1,255)`; outside `(20,150)`, `(220,150)`, `(0,0)`, `(299,299)` are exact white. Use the spec's independent camera derivation, not a production matrix helper for expectations.
  - Alpha: interior `(150,100)` has the same fixed color band; show before GPU that these bands exclude half-alpha `(188,0,188)` and omitted Picture `(255,0,0)`.
  - Hard edge: `(40,100)` exact white, `(41,100)` interior color. The left edge x=40.5049845 is just beyond the first pixel centre; document the independent margin. Do not change expected bands after observing GPU.
  - Every test requires clean Render/Readback, dispatch >0, no refusal/diagnostic/skip and second clean byte-identical render. Follow existing native Surface cleanup conventions.
- [ ] Run only this class to causal RED before editing the port. Archive test identities, exact failure and wrapper/child exit; setup/compiler failure is not RED.
- [ ] Implement the spec's faithful camera, colors and AA flags, preserving real recorder Rect+CTM and drawing order. Run focused GREEN. If the scene refuses, report its precise source/capability path and archive before changing renderer or replacing geometry; controller then decides the next architecture task.
- [ ] Re-run this class plus `AlphaGradientsSurfacePixelTest` and `HardstopGradientSurfacePixelTest`. Account all identities, XML failures/errors/skips/stderr and exits. Run one bounded240 `:kanvas:test` attempt after final edits; list reached failures by identity and preserve timeout/unreached as incomplete, not green. No per-edit global reruns.
- [ ] Commit only port/tests. Measure frozen631/443 at that exact full SHA with existing `measureSkiaParity` and strict `summarize-parity.mjs`, 30s/GM, partitions0–607/607–608/608–631. Use fresh run dirs; keep live handles until terminal, never restart on observation timeout. Compare18invariants and every prior outcome/hash/metric against `picture-3398dc3.json`. Only sk3d_simple should change; inspect its actual/reference/diff even if score rises. Report gains/losses and fidelity separately.
- [ ] Update the existing durable docs and add the current snapshot without falsifying baseline history. Mark review pending. Self-review, commit docs, and write full report with literal commands, RED/GREEN evidence, all commits, corpus comparison and remaining limitations. Controller owns independent Sol task review and publication.

## Validation execution

Archive root `/private/tmp/kanvas-w7-sk3d.WUms9p` (fresh per invocation).
Reuse existing read-only runner `/private/tmp/kanvas-w7-aa-blend.rFtTrn/bounded-run.rb`
and `review-evidence.init.gradle`, reading them before use. Gradle flags:
`--offline --no-daemon --no-build-cache --no-parallel --console=plain`.
Public test task `:integration-tests:skia:test --tests org.graphiks.kanvas.skia.Sk3dSimpleSurfacePixelTest`.
Global baseline `/private/tmp/kanvas-w7-aa-blend.rFtTrn/layer-preservation-full-final-1`
has751END,710SUCCESS/40FAILURE/1SKIPPED,timeout124/143; inherited red is not green.
The user delegates the bounded design/execution choice; no new approval loop.
Plan self-review: one vertical task, no shared-task dependency; all five review
focus items have public witnesses or the explicit real-Picture audit.
