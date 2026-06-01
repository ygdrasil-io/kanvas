#!/usr/bin/env python3
"""M67 performance tiering artifacts.

Consumes the M65 headless runtime smoke telemetry and emits candidate frame
gate plus family budget reports. The lane is intentionally conservative: M65
offscreen timings can become a `frame.headless-webgpu` candidate baseline, but
native Kadre timing remains reporting-only until M68 produces real evidence.
"""

from __future__ import annotations

import argparse
import json
import os
import platform
import subprocess
import time
from pathlib import Path
from typing import Any


SCHEMA_VERSION = 1
FRAME_LANE = "frame.headless-webgpu"
NATIVE_LANE = "frame.kadre-windowed"
WARMUP_FRAMES = 120
MEASURED_FRAMES = 300
TARGET_P50_MS = 16.7
WARNING_P95_MS = 33.3
MINIMUM_SAMPLE_COUNT = 120
DEFAULT_OUTPUT_DIR = "reports/wgsl-pipeline/performance/m67-performance-tiering"


def read_json(path: Path) -> dict[str, Any]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, dict):
        raise ValueError(f"{path} must contain a JSON object")
    return payload


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def git_commit(project_root: Path) -> str:
    try:
        return subprocess.check_output(
            ["git", "rev-parse", "--short=12", "HEAD"],
            cwd=project_root,
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip()
    except (OSError, subprocess.CalledProcessError):
        return "unknown"


def now_utc() -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())


def status_count(rows: list[dict[str, Any]], field: str = "status") -> dict[str, int]:
    counts: dict[str, int] = {}
    for row in rows:
        key = str(row.get(field, "unknown"))
        counts[key] = counts.get(key, 0) + 1
    return dict(sorted(counts.items()))


def host_summary(telemetry: dict[str, Any]) -> dict[str, Any]:
    env = telemetry.get("environment", {})
    if not isinstance(env, dict):
        env = {}
    host = telemetry.get("host", {})
    if not isinstance(host, dict):
        host = {}
    return {
        "host": platform.node() or "unknown",
        "osArchitecture": platform.platform(),
        "python": platform.python_version(),
        "jdk": "not-measured-by-m65-python-smoke",
        "backend": telemetry.get("mode", "unknown"),
        "adapter": "not-measured",
        "m65Cwd": env.get("cwd", "unknown"),
        "kadreStatus": host.get("status", "unknown"),
        "kadreRefusalReason": host.get("refusalReason", "unknown"),
    }


def decide_frame_status(
    median_ms: float,
    p95_ms: float,
    samples: int,
    fixture: str,
    host: dict[str, Any],
) -> tuple[str, str, list[str]]:
    if fixture == "negative-quarantine":
        return (
            "quarantine",
            "m67.negative-fixture.adapter-eligibility",
            ["adapter eligibility intentionally mismatched by negative fixture"],
        )
    if samples < MINIMUM_SAMPLE_COUNT:
        return ("quarantine", f"m67.sample-count-below-{MINIMUM_SAMPLE_COUNT}", [f"sampleCount<{MINIMUM_SAMPLE_COUNT}"])
    if host.get("adapter") == "not-measured":
        return (
            "pass",
            "m67.headless-offscreen-candidate-without-native-adapter",
            ["GPU adapter is not measured by the M65 Python smoke lane"],
        )
    if median_ms > TARGET_P50_MS or p95_ms > WARNING_P95_MS:
        return (
            "warn",
            "m67.frame-budget-warning-threshold",
            [f"medianMs={median_ms} target<={TARGET_P50_MS}", f"p95Ms={p95_ms} warning<={WARNING_P95_MS}"],
        )
    return ("pass", "m67.frame-budget-candidate-within-thresholds", [])


def slot_to_frame_row(
    slot: dict[str, Any],
    telemetry: dict[str, Any],
    fixture: str,
    project_root: Path,
) -> dict[str, Any]:
    summary = slot.get("telemetrySummary", {})
    if not isinstance(summary, dict):
        summary = {}
    host = host_summary(telemetry)
    median_ms = float(summary.get("medianFrameMs", 0.0))
    p95_ms = float(summary.get("p95FrameMs", 0.0))
    frames = int(summary.get("frames", 0))
    status, regression_label, quarantine_reasons = decide_frame_status(median_ms, p95_ms, frames, fixture, host)
    refusal_reason = str(slot.get("refusalReason", "none"))
    if fixture == "normal" and refusal_reason != "none":
        status = "warn"
        regression_label = refusal_reason
        quarantine_reasons = [
            "slot is timed by the synthetic/offscreen smoke lane but runtime display-list replay remains refused"
        ]
    if fixture == "negative-fail":
        status = "fail"
        regression_label = "m67.negative-fixture-threshold-fail"
        quarantine_reasons = [f"negative fixture forces median threshold below observed median {median_ms}"]

    nonblank = bool(summary.get("firstFrame", {}).get("nonblank")) and bool(summary.get("finalFrame", {}).get("nonblank"))
    return {
        "sceneId": f"p0-live-{slot.get('slot', 'unknown')}",
        "sourceSlot": slot.get("slot", "unknown"),
        "title": slot.get("title", ""),
        "lane": FRAME_LANE,
        "tier": "P0 frame" if slot.get("slot") == "baseline" else "P1 family",
        "gatePhase": "candidate",
        "releaseBlocking": False,
        "status": status,
        "payloadStatus": "measured" if frames > 0 else "missing",
        "claimLevel": "candidate" if status in {"pass", "warn", "candidate"} else status,
        "command": "rtk ./gradlew --no-daemon pipelineM67PerformanceTiering",
        "host": host,
        "backend": telemetry.get("mode", "unknown"),
        "adapter": host["adapter"],
        "sampleCount": frames,
        "requiredWarmupFrames": WARMUP_FRAMES,
        "targetMeasuredFrames": MEASURED_FRAMES,
        "medianMs": median_ms,
        "p95Ms": p95_ms,
        "worstFrameMs": p95_ms,
        "targetMedianMs": TARGET_P50_MS,
        "warningP95Ms": WARNING_P95_MS,
        "nonblank": nonblank,
        "baseline": {
            "name": "m65-headless-offscreen-baseline",
            "commit": git_commit(project_root),
            "source": "reports/wgsl-pipeline/m65-runtime-smoke/telemetry.json",
            "owner": "FOR-45/FOR-46",
        },
        "counters": {
            "frameCount": frames,
            "warmupFrameCount": 0,
            "pipelineCacheMisses": 0,
            "bindGroupChurn": 0,
            "textureUploadBytes": 0,
            "intermediateTextureBytes": 0,
            "glyphAtlasMisses": 0,
            "fallbackRefusalCount": 0 if refusal_reason == "none" else 1,
        },
        "regressionLabel": regression_label,
        "quarantineReasons": quarantine_reasons,
        "artifacts": slot.get("artifacts", {}),
        "nonClaims": [
            "This is not native Kadre present timing.",
            "This is not a release-blocking FPS gate.",
            "M65 synthetic/offscreen timing is measured only for the Python smoke lane.",
        ],
    }


def family_budget_rows(telemetry: dict[str, Any], frame_rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    slots = telemetry.get("slots", [])
    if not isinstance(slots, list):
        slots = []
    slot_by_name = {slot.get("slot"): slot for slot in slots if isinstance(slot, dict)}
    frame_by_slot = {row["sourceSlot"]: row for row in frame_rows}

    def measured_family(
        family: str,
        tier: str,
        source_slot: str,
        capability: str,
        scope: str,
    ) -> dict[str, Any]:
        row = frame_by_slot[source_slot]
        slot = slot_by_name.get(source_slot, {})
        refusal = slot.get("refusalReason", "none") if isinstance(slot, dict) else "none"
        status = "candidate" if row["payloadStatus"] == "measured" and row["status"] != "quarantine" else row["status"]
        if refusal != "none":
            status = "reporting-only"
        return {
            "family": family,
            "tier": tier,
            "capability": capability,
            "scope": scope,
            "status": status,
            "measurementType": "live frame benchmark",
            "measured": row["payloadStatus"] == "measured",
            "lane": FRAME_LANE,
            "source": "reports/wgsl-pipeline/m65-runtime-smoke/telemetry.json",
            "sampleCount": row["sampleCount"],
            "medianMs": row["medianMs"],
            "p95Ms": row["p95Ms"],
            "threshold": {"targetMedianMs": TARGET_P50_MS, "warningP95Ms": WARNING_P95_MS},
            "releaseBlocking": False,
            "reason": "M67 candidate from M65 headless/offscreen smoke telemetry."
            if refusal == "none"
            else f"Runtime replay remains refused with `{refusal}`; family is visible but not a support/performance pass.",
        }

    def reporting_only_family(
        family: str,
        tier: str,
        capability: str,
        scope: str,
        measurement_type: str,
        lane: str,
        source: str,
        reason: str,
    ) -> dict[str, Any]:
        return {
            "family": family,
            "tier": tier,
            "capability": capability,
            "scope": scope,
            "status": "reporting-only",
            "measurementType": measurement_type,
            "measured": False,
            "lane": lane,
            "source": source,
            "releaseBlocking": False,
            "reason": reason,
        }

    return [
        measured_family("core paint/blend", "P0 pipeline", "baseline", "animated transform + SrcOver-like smoke", "baseline smoke scene"),
        reporting_only_family(
            "runtime effect",
            "P1 family",
            "registered runtime-effect slot preview",
            "source evidence linked; display-list replay not wired",
            "live frame benchmark",
            FRAME_LANE,
            "reports/wgsl-pipeline/m65-runtime-smoke/telemetry.json",
            "M65 times a synthetic preview slot, but runtime display-list replay is refused; do not count as runtime-effect performance support.",
        ),
        reporting_only_family(
            "path/clip",
            "P1 family",
            "Path AA/stroke/clip budget",
            "not exercised by the M65 smoke scene",
            "GPU/cache row benchmark",
            "family.path-clip",
            "reports/wgsl-pipeline/scenes/generated/results.json",
            "No isolated M67 Path/clip timing payload exists yet; do not count the baseline transform smoke as a Path/clip measurement.",
        ),
        reporting_only_family(
            "image/bitmap",
            "P1 family",
            "bitmap sampling and upload budget",
            "not exercised by the M65 smoke scene",
            "GPU/cache row benchmark",
            "family.image-bitmap",
            "reports/wgsl-pipeline/scenes/generated/results.json",
            "No isolated M67 bitmap sampling or upload timing payload exists yet.",
        ),
        reporting_only_family(
            "image-filter DAG",
            "P1 family",
            "intermediate texture budget",
            "M61/M66 rows only; not exercised by M65 smoke",
            "image-filter intermediate texture benchmark",
            "family.image-filter-dag",
            "reports/wgsl-pipeline/scenes/generated/results.json",
            "No M67 measured intermediate texture payload exists yet; do not count as measured.",
        ),
        reporting_only_family(
            "text/glyph atlas",
            "P1 family",
            "glyph atlas upload/miss budget",
            "not exercised by the M65 smoke scene",
            "glyph atlas upload benchmark",
            "family.text-glyph-atlas",
            "reports/wgsl-pipeline/scenes/generated/results.json",
            "M65 does not render text, so glyph atlas misses remain schema/planning evidence only.",
        ),
        reporting_only_family(
            "native frame loop",
            "P0 frame",
            "Kadre present timing",
            "M68 native windowed demo",
            "live frame benchmark",
            NATIVE_LANE,
            "external/poc-koreos/",
            "Native Kadre timing remains outside M67 unless a real Kadre-hosted frame loop is measured.",
        ),
    ]


def markdown_frame_gate(payload: dict[str, Any]) -> str:
    lines = [
        "# M67 Frame Gate Candidate",
        "",
        "Status: candidate/reporting-only performance evidence, not release-blocking.",
        "",
        f"Generated by `{payload['generatedBy']}`.",
        f"Source telemetry: `{payload['sourceTelemetry']}`.",
        f"Fixture: `{payload['fixture']}`.",
        "",
        "## Counters",
        "",
        "| Counter | Value |",
        "|---|---:|",
    ]
    for key, value in payload["counters"].items():
        formatted = json.dumps(value, sort_keys=True) if isinstance(value, dict) else value
        lines.append(f"| {key} | {formatted} |")
    lines += [
        "",
        "## Frame Rows",
        "",
        "| Scene | Tier | Status | Samples | Median ms | P95 ms | Nonblank | Regression label |",
        "|---|---|---|---:|---:|---:|---|---|",
    ]
    for row in payload["rows"]:
        lines.append(
            f"| `{row['sceneId']}` | {row['tier']} | `{row['status']}` | {row['sampleCount']} | "
            f"{row['medianMs']} | {row['p95Ms']} | {row['nonblank']} | `{row['regressionLabel']}` |"
        )
    lines += [
        "",
        "## Policy",
        "",
        f"- `p50 <= {TARGET_P50_MS} ms` target.",
        f"- `p95 <= {WARNING_P95_MS} ms` warning threshold.",
        "- Release blocking is disabled for M67 until baseline variance, adapter eligibility, negative fixture behavior, and quarantine ownership are accepted.",
        "- Native Kadre window timing is not claimed by this lane.",
    ]
    return "\n".join(lines) + "\n"


def markdown_family_budgets(payload: dict[str, Any]) -> str:
    lines = [
        "# M67 Family Performance Budgets",
        "",
        "Status: mixed candidate/reporting-only budget inventory.",
        "",
        "## Budgets",
        "",
        "| Family | Tier | Status | Measured | Lane | Metric | Reason |",
        "|---|---|---|---|---|---|---|",
    ]
    for row in payload["families"]:
        metric = (
            f"{row.get('medianMs', 'n/a')} / {row.get('p95Ms', 'n/a')} ms"
            if row.get("measured") else "not measured"
        )
        lines.append(
            f"| {row['family']} | {row['tier']} | `{row['status']}` | {row['measured']} | "
            f"`{row['lane']}` | {metric} | {row['reason']} |"
        )
    lines += [
        "",
        "## Non-Claims",
        "",
        "- Reporting-only rows are not counted as measured performance evidence.",
        "- The native frame loop remains `frame.kadre-windowed` reporting-only for M67.",
        "- Family budgets define what will become measurable; they do not promote unsupported rendering features.",
    ]
    return "\n".join(lines) + "\n"


def markdown_baseline(payload: dict[str, Any]) -> str:
    lines = [
        "# M67 Performance Baseline",
        "",
        "Baseline source: `reports/wgsl-pipeline/m65-runtime-smoke/telemetry.json`.",
        "",
        "| Scene | Lane | Samples | Median ms | P95 ms | Baseline |",
        "|---|---|---:|---:|---:|---|",
    ]
    for row in payload["rows"]:
        lines.append(
            f"| `{row['sceneId']}` | `{row['lane']}` | {row['sampleCount']} | "
            f"{row['medianMs']} | {row['p95Ms']} | `{row['baseline']['name']}` |"
        )
    lines += [
        "",
        "This baseline is useful for trend visibility. It is not a native Kadre FPS baseline.",
    ]
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project-root", default=".")
    parser.add_argument("--telemetry", default="reports/wgsl-pipeline/m65-runtime-smoke/telemetry.json")
    parser.add_argument("--output-dir", default=DEFAULT_OUTPUT_DIR)
    parser.add_argument(
        "--fixture",
        choices=["normal", "negative-quarantine", "negative-fail"],
        default="normal",
        help="Deterministic fixture mode used to prove gate failure/quarantine paths.",
    )
    args = parser.parse_args()

    project_root = Path(args.project_root).resolve()
    telemetry_path = project_root / args.telemetry
    output_dir = project_root / args.output_dir
    telemetry = read_json(telemetry_path)
    slots = telemetry.get("slots", [])
    if not isinstance(slots, list):
        raise ValueError("M65 telemetry must contain slots[]")

    rows = [
        slot_to_frame_row(slot, telemetry, args.fixture, project_root)
        for slot in slots
        if isinstance(slot, dict)
    ]
    family_rows = family_budget_rows(telemetry, rows)
    timestamp = now_utc()
    generated_by = "scripts/m67_performance_tiering.py"

    baseline = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedBy": generated_by,
        "generatedAt": timestamp,
        "sourceTelemetry": args.telemetry,
        "fixture": args.fixture,
        "commit": git_commit(project_root),
        "rows": rows,
        "counters": {
            "rows": len(rows),
            "measuredRows": sum(1 for row in rows if row["payloadStatus"] == "measured"),
        },
    }
    gate = {
        **baseline,
        "mode": "m67-frame-gate-candidate",
        "releaseBlocking": False,
        "policy": {
            "gatePhase": "candidate",
            "minimumSampleCount": MINIMUM_SAMPLE_COUNT,
            "warmupFramesTarget": WARMUP_FRAMES,
            "measuredFramesTarget": MEASURED_FRAMES,
            "targetMedianMs": TARGET_P50_MS,
            "warningP95Ms": WARNING_P95_MS,
            "nativeKadreTiming": "out-of-scope-for-m67",
        },
        "counters": {
            "rows": len(rows),
            "measuredRows": sum(1 for row in rows if row["payloadStatus"] == "measured"),
            "passRows": sum(1 for row in rows if row["status"] == "pass"),
            "warnRows": sum(1 for row in rows if row["status"] == "warn"),
            "failRows": sum(1 for row in rows if row["status"] == "fail"),
            "quarantineRows": sum(1 for row in rows if row["status"] == "quarantine"),
            "status": status_count(rows),
        },
    }
    family = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedBy": generated_by,
        "generatedAt": timestamp,
        "sourceTelemetry": args.telemetry,
        "fixture": args.fixture,
        "families": family_rows,
        "counters": {
            "families": len(family_rows),
            "measuredFamilies": sum(1 for row in family_rows if row.get("measured") is True),
            "reportingOnlyFamilies": sum(1 for row in family_rows if row.get("status") == "reporting-only"),
            "status": status_count(family_rows),
        },
    }
    negative = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedBy": generated_by,
        "generatedAt": timestamp,
        "fixture": args.fixture,
        "expectedStatus": "quarantine" if args.fixture == "negative-quarantine" else "fail" if args.fixture == "negative-fail" else "not-run",
        "rows": rows,
        "deterministic": True,
        "opaqueFailure": False,
        "reason": "Negative fixture mutates only in-memory gate decision inputs; checked-in baseline data is not changed.",
    }

    write_json(output_dir / "m67-frame-baseline.json", baseline)
    write_json(output_dir / "m67-frame-gate-candidate.json", gate)
    write_json(output_dir / "m67-family-budgets.json", family)
    write_text(output_dir / "m67-frame-baseline.md", markdown_baseline(baseline))
    write_text(output_dir / "m67-frame-gate-candidate.md", markdown_frame_gate(gate))
    write_text(output_dir / "m67-family-budgets.md", markdown_family_budgets(family))

    if args.fixture != "normal":
        write_json(output_dir / "m67-negative-fixture.json", negative)
        write_text(
            output_dir / "m67-negative-fixture.md",
            "# M67 Negative Fixture\n\n"
            f"Fixture: `{args.fixture}`.\n\n"
            f"Expected status: `{negative['expectedStatus']}`.\n\n"
            "The fixture is deterministic and does not mutate checked-in baseline data.\n",
        )

    print(f"Wrote M67 performance tiering artifacts: {output_dir.relative_to(project_root)}")


if __name__ == "__main__":
    main()
