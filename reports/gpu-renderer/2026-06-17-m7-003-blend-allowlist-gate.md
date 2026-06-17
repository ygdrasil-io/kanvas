# GPU Renderer M7-003 Blend Allowlist Gate

Date: 2026-06-17
Branch: `codex/kgpu-m7-003-blend-allowlist`
Ticket: `KGPU-M7-003`

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M7-003 | `done` | Added `GPUBlendAllowlistPlanner`, blend plan kind contracts, fixed-function attachment blend state dumps, pipeline-key hashes, destination-read strategy references, terminal refusal diagnostics, and `BlendAllowlistGateTest`. | Independent review `019ed58b-6d88-7af2-a38f-56ec7b547ee0` accepted the contract/refusal evidence with no remaining P0/P1/P2 findings. Adapter-backed native blend execution, destination-read texture sampling, framebuffer fetch, input attachments, all blend modes, and product activation remain unpromoted. |

## Evidence

- The `gpu-renderer.blend-allowlist` row emits `routeKind=GPUNative`,
  `classification=TargetNative`, `promoted=false`,
  `productActivation=false`, and `materialized=false` for fixed-function
  allowlisted modes.
- `Src`, `SrcOver`, and `DstOver` produce deterministic fixed-function
  attachment blend state dumps and pipeline-key hashes only for the accepted
  premultiplied alpha plan.
- Accepted fixed-function dumps include `GPUAlphaPlan` facts, and
  unaccepted alpha/premul plans refuse with
  `unsupported.blend.alpha_plan_unaccepted`.
- `Multiply` and `Screen` are classified as `ShaderBlendWithDstRead` and
  refuse without an accepted destination-read strategy.
- A non-product destination-read strategy plan is cited in the blend dump but
  the blend result still exposes `RefuseDiagnostic` / `Refuse` as its
  destination-read strategy and action.
- Cited destination-read plans must match the blend command id, copy bounds,
  generation, and target format. Destination-read strategy evidence now carries
  a `targetFormat=` source fact so both target-copy and existing-intermediate
  plans are checked; mismatches refuse with
  `unsupported.blend.destination_read_plan_mismatch`.
- `Custom` remains a terminal unsupported-mode refusal.
- Active attachment sampling refuses before strategy lookup with
  `unsupported.destination_read.active_attachment_sampled`.
- The non-claim line stays explicit:
  `nativeAdvancedBlend=false`, `shaderBlend=false`, `framebufferFetch=false`,
  `inputAttachment=false`, `destinationReadTexture=false`, and
  `productActivation=false`.

## Validations

```bash
rtk env GRADLE_USER_HOME=/Users/chaos/.codex/gradle-cache ./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :gpu-renderer:test --tests '*BlendAllowlistGateTest'
rtk env GRADLE_USER_HOME=/Users/chaos/.codex/gradle-cache ./gradlew --no-daemon --rerun-tasks -Dkotlin.compiler.execution.strategy=in-process :gpu-renderer:check
rtk env GRADLE_USER_HOME=/Users/chaos/.codex/gradle-cache ./gradlew --no-daemon --rerun-tasks -Dkotlin.compiler.execution.strategy=in-process :gpu-raster:test --tests '*Runtime*' --tests '*Blend*' --tests '*Color*'
rtk git diff --check
rtk awk '/^status: / {count[$2]++} END {for (s in count) print s, count[s]}' .upstream/specs/gpu-renderer/tickets/M*-*/KGPU-*.md
```

The targeted test first failed in RED state because the blend allowlist planner
and request/result contracts did not exist. After implementation, the targeted
test, full `:gpu-renderer:check`, and the `gpu-raster` Runtime/Blend/Color
lane passed. `rtk git diff --check` passed with no whitespace findings.

Current status count after moving KGPU-M7-003 to `done`:

```text
blocked 9
review 2
done 35
```

## Review

- Initial independent review `019ed579-496a-72d3-8aa8-b3be44a1ca0c` found P2
  issues around alpha/premul facts, executable destination-read actions on
  refused plans, and incomplete mode coverage.
- Follow-up review `019ed582-ae1f-7032-8cfe-d899c37af33b` found a P2 gap for
  `SampleExistingIntermediate` target-format matching.
- Final review `019ed58b-6d88-7af2-a38f-56ec7b547ee0` found no remaining
  P0/P1/P2 issues after the fixes below.
- fixed-function support is limited to modes representable by WebGPU
  attachment blend state.
- destination-read blend modes refuse without creating an implicit
  product route through target-copy/intermediate evidence.
- refused plans do not expose executable destination-read actions or raw
  destination-read strategy plans.
- cited destination-read strategy evidence matches the blend request command,
  copy bounds, generation, and target format for both target-copy and
  existing-intermediate plans.
- fixed-function blends are guarded by explicit alpha/premul facts.
- active attachment sampling, framebuffer fetch, input attachments, and
  CPU readback remain non-claims.
- pipeline-key and blend-state dumps exclude per-draw payload and
  backend resource handles.

## Non-Claims

- No product route activation.
- No adapter-backed native blend execution evidence.
- No destination-read texture sampling route.
- No framebuffer fetch or input attachment support.
- No shader blend product route.
- No all-blend-mode support.
- No CPU-rendered blend fallback.
