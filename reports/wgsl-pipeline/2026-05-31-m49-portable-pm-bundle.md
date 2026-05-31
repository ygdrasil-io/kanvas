# M49 Portable PM Artifact Bundle

Date: 2026-05-31
Linear: GRA-290
Parent epic: GRA-287
Depends on: GRA-289

## Scope

GRA-290 adds a reproducible PM bundle task for the WGSL scene dashboard.

Command:

```bash
rtk ./gradlew --no-daemon pipelinePmBundle
```

Output:

```text
build/reports/wgsl-pipeline-pm-bundle/
```

## Bundle Contents

| Path | Purpose |
|---|---|
| `build/reports/wgsl-pipeline-pm-bundle/dashboard/index.html` | PM dashboard entrypoint. |
| `build/reports/wgsl-pipeline-pm-bundle/dashboard/data/scenes.json` | Merged static/generated scene JSON. |
| `build/reports/wgsl-pipeline-pm-bundle/dashboard/artifacts/` | Referenced images, diffs, route JSON, stats JSON, and performance payloads used by dashboard rows. |
| `build/reports/wgsl-pipeline-pm-bundle/reports/` | Checked-in report references used by row evidence links. |
| `build/reports/wgsl-pipeline-pm-bundle/reports/wgsl-pipeline/scenes/generated/results.json` | Source generated result manifest. |
| `build/reports/wgsl-pipeline-pm-bundle/generated/data/generated-scenes.json` | Materialized generated scene export JSON. |
| `build/reports/wgsl-pipeline-pm-bundle/gate/` | M49-B dashboard gate reports. |
| `build/reports/wgsl-pipeline-pm-bundle/manifest.json` | Commit, command, timestamp, counters, expected-unsupported rows, adapter-backed rows, limitations, and unavailable references. |
| `build/reports/wgsl-pipeline-pm-bundle/README.md` | Local open instructions. |

## Manifest Summary

The generated manifest includes:

| Field | Value |
|---|---|
| Generation command | `rtk ./gradlew --no-daemon pipelinePmBundle` |
| Serve command | `python3 -m http.server 8765 --bind 127.0.0.1 --directory build/reports/wgsl-pipeline-pm-bundle/dashboard` |
| Total rows | 23 |
| `pass` | 18 |
| `expected-unsupported` | 5 |
| Adapter-backed rows | 2 |
| Generated evidence rows | 21 |
| Static evidence rows | 2 |
| Unavailable references | 0 |

## Local Serve Test

The local serve path was tested with:

```bash
python3 -m http.server 8765 --bind 127.0.0.1 --directory build/reports/wgsl-pipeline-pm-bundle/dashboard
curl -fsS http://127.0.0.1:8765/index.html
curl -fsS http://127.0.0.1:8765/data/scenes.json
```

Both `index.html` and `data/scenes.json` were served successfully.

## Non-Claims

- The bundle is a static PM review artifact; it does not execute GPU captures.
- Expected-unsupported rows remain planning evidence, not support claims.
- Performance warnings remain non-blocking until M49-E defines promotion rules.
- Text, glyph masks, font, emoji, codec, arbitrary SkSL, arbitrary image-filter DAG, and broad Path AA support remain outside this bundle's claims.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk ./gradlew --no-daemon pipelinePmBundle
```
