# Skia Fidelity Wave 2: UNPREMUL Image Preparation

## Status

Design approved in discussion; written specification pending user review.

## Scope

Wave 2 starts from commit `0e98d3198` on branch `codex/skia-fidelity-wave2`.
This wave closes the shared image-preparation cause represented by
`unsupported.image.alpha_interpretation` for decoded CPU RGBA/BGRA images whose
source alpha type is `UNPREMUL`.

The Wave 1 report remains immutable. Its 58 alpha rows are the input cohort,
not a claim that all 58 rows will pass after one change. Any downstream refusal
revealed after the common alpha gate is removed must receive its own stable
failure code and remain visible in the refreshed manifest.

## Baseline And Cohort

The Wave 1 classification contains 58 unique rows for this failure code. They
are all `referenceKind=skia-upstream` and all share the terminal diagnostic
`GPUPreparedSurfaceTerminalException` with the same alpha-interpretation
boundary. The family distribution is:

| Family | Rows |
| --- | ---: |
| IMAGE | 38 |
| COMPOSITE | 8 |
| CLIP | 6 |
| BLUR | 3 |
| GRADIENT | 2 |
| RUNTIME_EFFECT | 1 |

The historical report records a different render `sourceCommit` from the
Wave 2 branch base. Before using the report as current acceptance evidence,
Wave 2 must capture the branch commit, environment, runner commands, and a
fresh before/after evidence set. The historical report is a classification
reference only until that reconciliation is complete.

## Graphite And Dawn Comparison

The reference implementation confirms that alpha interpretation belongs at the
pixel transfer boundary rather than in a per-GM workaround or an unconditional
shader branch:

- `skia-main/src/gpu/graphite/task/UploadTask.cpp:131-152` builds the transfer
  function from source and destination `SkColorInfo`.
- `skia-main/src/core/SkColorSpaceXformSteps.cpp:137-140` enables unpremultiply
  only for a premultiplied source and enables premultiply only when the
  destination requires it.
- `skia-main/src/core/SkColorSpaceXformSteps.cpp:202-210` removes a redundant
  unpremultiply/premultiply pair when no nonlinear conversion is present.
- `skia-main/src/gpu/graphite/TextureUtils.cpp:118-159` handles renderable
  intermediate alpha types and restores the final image alpha metadata.

Graphite therefore preserves an `UNPREMUL` source when the upload destination is
also unpremultiplied. Kanvas has a narrower, explicit contract:

1. upload `RGBA8UnormSrgb` pixels as `StraightEncodedSrgb`;
2. sample them as straight encoded color;
3. convert to `LinearPremul` in the prepared-image shader path.

The existing Kanvas shader already implements step 3 in
`GPUPreparedImageShader.kt:247-268`. Graphite's broader renderable-alpha
machinery must not be copied into the artifact contract because it would add
unproven dynamic destination-alpha semantics.

## Proposed Architecture

Change one semantic layer only: source-pixel normalization in
`GPUPreparedImageArtifactFactory.prepare` in
`gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/images/PreparedImageContracts.kt`.

- Accept `AlphaType.PREMUL` and `AlphaType.UNPREMUL` for non-A8 decoded CPU
  images.
- Keep `AlphaType.UNKNOWN` as an explicit `ALPHA_INTERPRETATION` refusal.
- Keep the existing A8 contract: only `A8 + PREMUL` is accepted; A8 with
  `OPAQUE`, `UNPREMUL`, or `UNKNOWN` remains refused.
- Keep the existing authoritative opaque-alpha validation: an `OPAQUE` image
  with any non-255 alpha remains refused.
- Retain the existing RGBA/BGRA normalization and row-stride handling.
- Run the existing RGB recovery loop only when `alphaType == PREMUL`.
- For `UNPREMUL`, preserve normalized straight RGB bytes, including RGB values
  above the associated alpha, and preserve alpha unchanged.
- Keep the artifact's `StraightEncodedSrgb` upload encoding and upload
  interpretation unchanged.
- Do not change `GPUPreparedImageShader`, `GPUPreparedImageSource`, the
  prepared-image payload, or the generic artifact schema.
- Do not add a CPU fallback, silent refusal downgrade, or per-GM exception.

This prevents the current failure in two directions: it removes the common
refusal for a valid source alpha type and prevents a naïve acceptance change
from double-unpremultiplying already-straight bytes.

## Test-First Plan

Before changing production code, add a focused failing test to
`PreparedImageContractsTest`:

```kotlin
@Test
fun `factory accepts unpremultiplied color and preserves straight encoded upload`() {
    val artifact = ready(
        input(
            alpha = AlphaType.UNPREMUL,
            bytes = byteArrayOf(40, 120, 210.toByte(), 160.toByte()),
        ),
    )

    assertContentEquals(
        byteArrayOf(40, 120, 210.toByte(), 160.toByte()),
        artifact.tightRgba8BytesForUpload(),
    )
    assertEquals(
        ArtifactColorUploadEncoding.StraightEncodedSrgb,
        artifact.colorUploadEncoding,
    )
    assertEquals(
        GPUColorInterpretation.StraightEncodedSrgb.value,
        artifact.colorUploadInterpretation,
    )
}
```

The test must fail before the production edit because the factory currently
returns `ALPHA_INTERPRETATION` for `UNPREMUL`. It also catches an implementation
that merely removes the refusal but applies the `PREMUL` recovery loop.

Update the existing tests without weakening assertions:

- `PreparedImageContractsTest`: replace the old UNPREMUL refusal assertion with
  acceptance and add BGRA, alpha-zero, and key/byte invariants where needed.
- `GPUPreparedImageSourceTest`: replace the caller-pixel refusal expectation
  with a ready result.
- `GPUPreparedImageRefusalCases.kt`: remove only the valid decoded CPU
  UNPREMUL case from the refusal matrix.
- `GPUPreparedImageRefusalMatrixTest.kt` and derived refusal-matrix tests:
  preserve coverage for `UNKNOWN`, invalid opaque data, and invalid A8 alpha
  combinations.

The existing PREMUL recovery test must remain unchanged and continue proving
`25,75,132,160 -> 40,120,210,160`.

## Evidence And Manifest

The Wave 2 evidence directory will be separate from the Wave 1 directory,
under `reports/upstream-rebaseline/`, and will contain:

- the sealed before and after manifests;
- the exact cohort identity list and failure-code counts;
- route diagnostics and minimal operation traces;
- CPU output, GPU output, Skia reference, diff image, and diff/stat payloads;
- environment and command manifests, including JDK, adapter/driver, display
  configuration, and repeat count;
- the old/new score payloads and threshold values;
- explicit residual refusals and their follow-up family mapping.

Generated scores and dashboard data must be produced by the existing runner /
reconciliation workflow, not edited by hand. The refreshed manifest must state
that:

- `unclassifiedFailures=0`;
- `assertionsWeakened=false`;
- `globalThresholdWeakened=false`;
- reference provenance is `skia-upstream` for each claimed comparable row;
- every supported-after claim has CPU, GPU, reference, diff/stat, and route
  evidence;
- rows still blocked by adjacent capabilities remain explicit refusals;
- no CPU fallback was used to turn a GPU refusal into a pass.

The required validation commands are the project tasks `:kanvas:test`,
`:gpu-renderer:test`, `:integration-tests:skia:test`, and
`:integration-tests:svg:test`, with `-Dkanvas.gm.includeBlocking=true` and
`-Pgm.includeBlocking=true` whenever blocking rows are included. Trace runs
may add `-Dkanvas.render.debugLevel=TRACE`. Native Kadre execution is optional
local evidence and is not a headless CI dependency.

## Sequencing And Escalation

The implementation order is:

1. Record branch commit and environment.
2. Add and run the failing UNPREMUL test.
3. Preserve the failing-test artifact as before evidence.
4. Change only the conditional alpha normalization in
   `PreparedImageContracts.kt`.
5. Run focused tests, then Kanvas and GPU module tests.
6. Rerun the 58-GM cohort and collect all evidence roles.
7. Reconcile the Wave 2 manifest and verify all non-weakening invariants.
8. Obtain independent review before integration.

If a residual failure exposes WGSL parser/IR/generator ambiguity, stop and
prepare minimized `wgsl4k` evidence instead of adding a hidden workaround. If
three targeted hypotheses fail for the same cohort, stop and re-evaluate the
architecture before continuing. Adjacent unsupported behavior remains a stable
refusal until its own semantic contract is proven.

## Definition Of Done

This cohort is complete only when:

- the focused pre-change test demonstrably fails and the post-change test
  passes;
- PREMUL behavior is unchanged and UNPREMUL bytes are preserved correctly;
- CPU and GPU module tests pass without weakened assertions;
- the rerun has no new unclassified failures;
- all claimed pixel improvements have CPU/GPU/reference/diff/stat/route proof;
- residual refusals are explicit and stable;
- no global or family threshold was relaxed;
- the Wave 2 manifest is generated by reconciliation and records the complete
  cohort result;
- an independent review confirms the ownership boundary and evidence;
- the branch is committed, pushed, and opened as a PR.
