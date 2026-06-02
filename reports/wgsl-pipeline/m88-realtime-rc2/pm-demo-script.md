# M88 PM Demo Script

1. Open the PM bundle dashboard from `build/reports/wgsl-pipeline-pm-bundle/dashboard/index.html`.
2. Filter to pass rows to show current selected rendering support.
3. Filter to expected-unsupported rows to show stable refusal policy.
4. Explain shader wording: WGSL is the implementation target; SkSL only names Skia API compatibility that is not dynamically compiled by Kanvas.
5. Open `runtime/m87-runtime-effect-live-editing/evidence.md` to show selected SimpleRT live-editing evidence backed by a registered descriptor and reflected WGSL layout.
6. Open `runtime/m84-native-frame-timing/evidence.md` and explain that native timing is candidate/reporting-only.
7. Open `runtime/m85-resource-lifetime-cache/evidence.md` and explain that cache/resource evidence is a selected deterministic ledger, not broad observed runtime telemetry.
8. Use `release/m88-realtime-rc2/release-notes.md` in the PM bundle, or `reports/wgsl-pipeline/m88-realtime-rc2/release-notes.md` in the repository, for the final RC2 summary and next decision point.

Native Kadre window demos are local opt-in checks. If they are used during the
demo, initialize the unpublished Kadre source dependency first:

```bash
git submodule update --init --recursive external/poc-koreos
```
