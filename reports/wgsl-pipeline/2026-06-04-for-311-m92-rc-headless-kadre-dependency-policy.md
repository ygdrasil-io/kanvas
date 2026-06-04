# FOR-311 M92 RC Headless Kadre Dependency Policy

Linear: `FOR-311`

Source memory:
`global/kanvas/ticket-drafts/draft-for-next-m92-rc-headless-kadre-dependency-policy-guard-ticket`

Decision: `M92_RC_HEADLESS_KADRE_DEPENDENCY_POLICY_GUARD_APPLIED`

## Result

FOR-311 keeps M92 RC validation headless by making checked-in artifact
validators the required CI path and keeping direct Kadre runtime regeneration as
an optional/provisioned refresh.

No renderer, shader, runtime, Kadre source integration, Gradle substitution,
visual threshold, scene status, fallback, telemetry count, readiness score, or
native demo behavior changed.

## Required Headless Path

- Runtime evidence validator: `rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive`
- RC validator: `python3 scripts/validate_mep_rc_runtime.py .`
- PM bundle: `rtk ./gradlew --no-daemon pipelinePmBundle`
- Headless uses Kadre native submodule: `False`
- Validates checked-in artifacts: `True`

## Optional Kadre Refresh

- Command: `rtk ./gradlew --no-daemon :kadre-runtime:pipelineMepNextRuntimeInteractive`
- CI gate: `False`
- Precondition: git submodule update --init --recursive external/poc-koreos or provide local org.graphiks.kadre artifacts

## Native Demo Boundary

- Command: `rtk ./gradlew --no-daemon :kadre-runtime:runMepNextKadreNativeInteractive`
- Native window: `True`
- CI gate: `False`

## Classification Preserved

| Classification | Count |
|---|---:|
| `observed` | `5` |
| `observed-partial` | `2` |
| `derived` | `4` |
| `not-observable` | `4` |
| `expected-unsupported` | `2` |

Blockers preserved:

- `kadre-runtime.native-cache-counter-unavailable`
- `kadre-runtime.resource-lifetime-observation-partial`

Readiness delta: `0.00%`

Release-blocking performance gate: `False`

## Policy Cases

| Case | Decision | Allowed | Reason |
|---|---|---:|---|
| Current M92 required validation is allowed | `allowed-guarded` | True | M92 required validation stays headless and checked-in; Kadre refresh remains optional/provisioned. |
| Direct Kadre task as CI gate is forbidden | `forbidden` | False | Direct Kadre runtime refresh may resolve unpublished Kadre artifacts and must not be a CI gate. |
| Direct Kadre task before optional section is forbidden | `forbidden` | False | Direct Kadre runtime task must appear only in optional/provisioned sections. |
| Native demo as CI gate is forbidden | `forbidden` | False | Native window demo must remain opt-in and outside CI. |
| Unknown headless command is ambiguous | `ambiguous` | False | Headless evidence must name the checked-in MEP-NEXT runtime validator. |

## Validation

- `rtk python3 scripts/validate_for311_m92_rc_headless_kadre_dependency_policy.py`
- `rtk python3 scripts/validate_mep_rc_runtime.py .`
- `rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk python3 -m json.tool reports/wgsl-pipeline/m92-kadre-runtime-rc/m92-rc-headless-kadre-dependency-policy-for311.json`
- `rtk python3 -m json.tool reports/wgsl-pipeline/m92-kadre-runtime-rc/evidence.json`
- `rtk git diff --check origin/master...HEAD`
