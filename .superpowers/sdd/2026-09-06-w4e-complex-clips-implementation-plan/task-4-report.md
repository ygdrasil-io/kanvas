# W4e Task 4 report — RenderGraph clip/mask

Base reviewed: `d87daee70`.

## Implementation

- Added the linear RGBA8 coverage-mask format, accumulator/scratch/MSAA-scratch/D24S8 roles, and exact sample/resolve capability facts.
- Added immutable clip passes (`ClipMaskInitialize`, `ClipMaskProducer`, `ClipMaskFold`), clip strategy snapshots, and `ClippedPlanDraw` consumers.
- Validated ordered initializer → producer → fold ping-pong graphs: opaque initialization, finite target-domain bounds, separate read/write/sample attachments, AA4 resolve proof, matching D24S8, producer/fold dependencies, final consumer dependency, and lifetimes ending at each final consumer.
- Reused the mathematical clip and inverse snapshots only; no renderer, font, codec, GM, dashboard, or baseline code was changed.
- Preserved existing AA4 hard-edge behavior: a binary one-sample mask remains consumed by `BinaryMaskedPathDraw` for four-sample color coverage.

## TDD evidence

1. Added public contract tests for ordered linear folds and fold alias rejection, then ran the requested focused command RED. It failed because the clip-mask pass/format API did not exist.
2. Added the minimal graph resources, pass contracts, and validation; the same focused command passed.
3. Added the inverse-zero and AA4 producer contracts before their respective API/helper support. Each focused compile failed for the missing public contract and passed once support was supplied.

## Verification

```text
rtk ./gradlew :gpu-plan:test --tests '*RenderGraphContractTest*ClipMask*' --tests '*RenderGraphContractTest*Inverse*'
BUILD SUCCESSFUL — 4 focused clip/inverse contract tests passed.

rtk ./gradlew :gpu-plan:test
BUILD SUCCESSFUL.

rtk git diff --check
No output; clean.
```

## Fix round 3/5

- Recursively unwraps every `ClippedPlanDraw.source` and walks every nested clip strategy, so an inner `Mask` or `InverseMask` participates in resource discovery, writer/consumer dependencies, lifetimes, and stencil validation.
- W4c path validation now applies to the unwrapped `PathDraw`: valid direct paths remain valid through wrapper chains, while hidden stencil/general path contracts cannot bypass the legacy path rules.
- Requires clip stencil resources to match the graph extent as well as D24S8 role, format, usage, sample count, and non-aliasing color target.
- Added behavior-only tests for nested wrapper escapes, valid W4c wrappers, hard AA4 Mask and InverseMask consumers, one-sample binary source/four-sample broadcast, and mutation rejections for stencil role/format/sample/extent/alias, resource lifetime, producer-without-consumer, and writer dependency.

```text
rtk ./gradlew :gpu-plan:test --tests '*RenderGraphContractTest*nested clipped*' --tests '*RenderGraphContractTest*AA4*clip*'
BUILD SUCCESSFUL.

rtk ./gradlew :gpu-plan:test
BUILD SUCCESSFUL.

rtk git diff --check
No output; clean.
```

## Fix round 2/5

- Traversed nested `Scissor`/`Stencil` clip strategies for both resource discovery and mask-consumer validation. Clip stencil attachments now require the appropriate D24S8 role, usage, sample count, lifetime, and non-aliasing color target.
- Kept clip passes and their coverage resources compatible with the existing AA4 and legacy path/stencil contract inventories, instead of treating them as an alternative graph mode.
- Sealed inverse-mask identity with its domain and interior geometry/zero state, and sealed nested clip strategy structure canonically.
- Added a public complete graph contract: AA4 color, a binary one-sample hard-edge source, an ordered one-sample clip producer/fold accumulator, and nested scissor/stencil/mask consumer. Its binary source is explicitly reported as one-sample while coverage is broadcast over AA4 color samples.

```text
rtk ./gradlew :gpu-plan:test --tests '*RenderGraphContractTest*AA4*hard*' --tests '*RenderGraphContractTest*Clip*' --tests '*RenderGraphContractTest*Stencil*'
BUILD SUCCESSFUL.

rtk ./gradlew :gpu-plan:test
BUILD SUCCESSFUL.

rtk git diff --check
No output; clean.
```

## Scope check

Only the five requested `gpu-plan` production files and `RenderGraphContractTest.kt` were changed. Tests assert public graph/resource/pass behavior and validation outcomes only; they use no reflection, private state, source-shape checks, or call counts.

## Fix round 1/5

- Made clip-mask, explicit AA4, and legacy stencil validation composable instead of mutually exclusive.
- Added `ClippedBinaryMaskedPathDraw`, a typed `PathRenderDraw` wrapper that retains the one-sample binary mask and its four-sample broadcast while carrying ordered clip coverage. `RenderPass` still rejects multisample draws.
- Validated mask consumers even when no clip producer passes are present; producer graphs now require a final consumer. Stencil clip strategies now retain the depth-stencil resource reference.
- Extended the W4d canonical seal for the new typed path draw and added public behavior tests for binary AA4 coverage.

```text
rtk ./gradlew :gpu-plan:test
BUILD SUCCESSFUL.

rtk git diff --check
No output; clean.
```
