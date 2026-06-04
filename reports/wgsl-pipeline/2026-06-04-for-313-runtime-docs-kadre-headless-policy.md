# FOR-313 Runtime Docs Kadre Headless Policy Audit

Linear: `FOR-313`

Source memory:
`global/kanvas/ticket-drafts/draft-for-next-runtime-docs-kadre-headless-policy-audit-ticket`

Decision: `M90_RUNTIME_DOCS_KADRE_HEADLESS_POLICY_AUDIT_APPLIED`

## Result

FOR-313 audits the M90 / MEP-NEXT runtime documents and the Markdown generator
that owns them. Required checked-in validation is now documented as
`rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive`, while the direct Kadre runtime task remains only an
optional/provisioned evidence refresh.

No renderer, shader, Gradle dependency, Kadre native behavior, scene score,
fallback, readiness percentage, or telemetry count changed.

## Checked Documents

- `reports/wgsl-pipeline/2026-06-02-mep-next-runtime-interactive.md`: required `rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive`; optional direct refresh preserved.
- `reports/wgsl-pipeline/m90-runtime-interactive/pm-report.md`: required `rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive`; optional direct refresh preserved.
- `reports/wgsl-pipeline/2026-06-02-mep-next-closeout.md`: required `./gradlew --no-daemon validateMepNextRuntimeInteractive`; optional direct refresh preserved.

## Policy

- Required headless validator: `rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive`
- Optional direct Kadre refresh: `rtk ./gradlew --no-daemon :kadre-runtime:pipelineMepNextRuntimeInteractive`
- Optional refresh gate: `optional/provisioned-only`
- Provisioning accepted: `external/poc-koreos, org.graphiks.kadre`
- Generator: `kadre-runtime/src/main/kotlin/org/skia/kadre/runtime/MepNextRuntimeInteractive.kt`
- Evidence JSON: `reports/wgsl-pipeline/m90-runtime-interactive/evidence.json`

## Validation

- `rtk python3 scripts/validate_for313_runtime_docs_kadre_headless_policy.py`
- `rtk python3 -m json.tool reports/wgsl-pipeline/runtime-docs-kadre-headless-policy-for313.json`
- `rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk git diff --check origin/master...HEAD`
