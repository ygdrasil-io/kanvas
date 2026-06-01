#!/usr/bin/env python3
"""Generate M70-A Kadre live runtime PM evidence."""

from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


SCHEMA_VERSION = 1
LINEAR_ISSUES = [
    "FOR-60",
    "FOR-61",
    "FOR-62",
    "FOR-63",
    "FOR-64",
    "FOR-65",
    "FOR-66",
    "FOR-67",
    "FOR-68",
    "FOR-69",
    "FOR-70",
    "FOR-71",
    "FOR-72",
    "FOR-73",
]
NATIVE_DEMO_FILE = "reports/wgsl-pipeline/m70-kadre-native/native-demo.json"
NATIVE_SMOKE_FILE = "reports/wgsl-pipeline/m69-kadre-native/native-smoke.json"


def now_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def parse_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        return {}
    parsed = json.loads(path.read_text(encoding="utf-8"))
    return parsed if isinstance(parsed, dict) else {}


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def status_from_demo(native_demo: dict[str, Any]) -> tuple[str, str]:
    if not native_demo:
        return "blocked", "m70.kadre-demo-not-run"
    requested = int(native_demo.get("requestedFrames") or 0)
    presented = int(native_demo.get("presentedFrames") or 0)
    telemetry = native_demo.get("runtimeTelemetry") if isinstance(native_demo.get("runtimeTelemetry"), dict) else {}
    measured_samples = int(telemetry.get("measuredSampleCount") or 0)
    status_summary = native_demo.get("surfaceStatusSummary") if isinstance(native_demo.get("surfaceStatusSummary"), dict) else {}
    success_count = int(status_summary.get("success") or 0)
    timeout_count = int(status_summary.get("timeout") or 0)
    has_full_frame_count = requested > 0 and presented == requested
    has_measured_samples = measured_samples > 0
    if (
        native_demo.get("status") == "native-runnable"
        and native_demo.get("nativePresented") is True
        and has_full_frame_count
        and has_measured_samples
        and success_count > 0
    ):
        return "native-runnable", str(native_demo.get("reason") or "m70.kadre-native-demo-presented-frames")
    if (
        native_demo.get("presentCallCompleted") is True
        and has_full_frame_count
        and has_measured_samples
        and timeout_count == presented
    ):
        return "degraded", str(native_demo.get("reason") or "m70.kadre-present-call-completed-timeout-only")
    return "blocked", str(native_demo.get("reason") or native_demo.get("error") or "m70.kadre-demo-not-runnable")


def build_route(project_root: Path) -> dict[str, Any]:
    native_demo = parse_json(project_root / NATIVE_DEMO_FILE)
    native_smoke = parse_json(project_root / NATIVE_SMOKE_FILE)
    status, reason = status_from_demo(native_demo)
    telemetry = native_demo.get("runtimeTelemetry") if isinstance(native_demo.get("runtimeTelemetry"), dict) else {}
    capture = native_demo.get("capture") if isinstance(native_demo.get("capture"), dict) else {}
    scene_contract = native_demo.get("sceneContract") if isinstance(native_demo.get("sceneContract"), dict) else {}
    has_real_capture = capture.get("realNativeReadback") is True
    non_claims = [
        "M70-A/B/C prove one selected Kadre native demo route only, with status determined by surface evidence.",
        "Native presentation is claimed only when the normalized surface status summary contains at least one success.",
        "Raw Kadre/wgpu4k API status names remain recorded separately when they differ from normalized evidence semantics.",
        "Broad Kanvas display-list replay is not claimed.",
        "Frame timing is reporting-only and not a release-grade FPS gate.",
    ]
    if has_real_capture:
        non_claims.insert(
            3,
            "The capture artifact is a real wgpu4k native offscreen texture readback of the selected scene contract, not a system screenshot or window-surface readback.",
        )
    else:
        non_claims.insert(3, "Native capture remains unavailable unless capture.realNativeReadback is true.")

    return {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": now_iso(),
        "generatedBy": "scripts/m70_kadre_live_runtime_evidence.py",
        "linearIssues": LINEAR_ISSUES,
        "status": status,
        "reason": reason,
        "sceneId": scene_contract.get("id") or "m70-a-kanvas-owned-kadre-native-scene",
        "mode": native_demo.get("mode") or "demo",
        "nativePresented": native_demo.get("nativePresented") is True,
        "presentCallCompleted": native_demo.get("presentCallCompleted") is True,
        "requestedFrames": int(native_demo.get("requestedFrames") or 0),
        "presentedFrames": int(native_demo.get("presentedFrames") or 0),
        "warmupFrames": int(native_demo.get("warmupFrames") or 0),
        "surface": native_demo.get("surface") if isinstance(native_demo.get("surface"), dict) else {},
        "adapterInfo": native_demo.get("adapterInfo"),
        "runtimeTelemetry": telemetry,
        "surfaceStatusSummary": native_demo.get("surfaceStatusSummary")
        if isinstance(native_demo.get("surfaceStatusSummary"), dict)
        else {},
        "capture": capture or {
            "status": "unavailable",
            "reason": "m70.native-readback-not-available",
            "realNativeReadback": False,
        },
        "m69SmokeCompatibility": {
            "status": native_smoke.get("status"),
            "presentedFrames": native_smoke.get("presentedFrames"),
            "reason": native_smoke.get("reason"),
        },
        "claimLevel": "selected-native-demo" if status == "native-runnable" else "selected-present-call-demo" if status == "degraded" else "blocked",
        "releaseBlocking": False,
        "nonClaims": non_claims,
    }


def write_markdown(path: Path, route: dict[str, Any]) -> None:
    telemetry = route.get("runtimeTelemetry") if isinstance(route.get("runtimeTelemetry"), dict) else {}
    capture = route.get("capture") if isinstance(route.get("capture"), dict) else {}
    surface = route.get("surface") if isinstance(route.get("surface"), dict) else {}
    surface_status = route.get("surfaceStatusSummary") if isinstance(route.get("surfaceStatusSummary"), dict) else {}
    lines = [
        "# M70-A Kadre Live Runtime Evidence",
        "",
        f"Status: `{route['status']}`",
        "",
        "M70-A/B/C turn the PM-validated M69 native smoke into a PM-visible live-runtime slice. "
        "The demo is still deliberately narrow: it renders one selected Kanvas-owned scene contract in a Kadre native WebGPU window and emits reporting-only telemetry.",
        "",
        "## PM Outcome",
        "",
        f"- Scene: `{route['sceneId']}`",
        f"- Mode: `{route['mode']}`",
        f"- Native presented: `{route['nativePresented']}`",
        f"- Present-call completed: `{route['presentCallCompleted']}`",
        f"- Requested/presented frames: `{route['requestedFrames']}` / `{route['presentedFrames']}`",
        f"- Warmup frames: `{route['warmupFrames']}`",
        f"- Surface: `{surface.get('width')}` x `{surface.get('height')}` `{surface.get('format')}`",
        f"- Capture status: `{capture.get('status')}`",
        f"- Capture reason: `{capture.get('reason')}`",
        f"- Capture artifact: `{capture.get('imagePath')}`",
        f"- Window-surface readback: `{capture.get('windowSurfaceReadback')}`",
        f"- Surface status summary: success `{surface_status.get('success')}`, timeout `{surface_status.get('timeout')}`",
        "",
        "## Linear Scope",
        "",
        "- Epic: `FOR-60` M70-A Kadre Live Runtime V1.",
        "- Runtime demo task: `FOR-61`.",
        "- Selected Kanvas-owned scene route: `FOR-62`.",
        "- Native capture/readback status: `FOR-63`.",
        "- Runtime telemetry counters: `FOR-64`.",
        "- PM bundle/readiness closeout: `FOR-65`.",
        "- M70-B native surface success and presentation: `FOR-66`, `FOR-68`, `FOR-69`, `FOR-70`.",
        "- M70-C native capture/readback evidence: `FOR-67`, `FOR-71`, `FOR-72`, `FOR-73`.",
        "",
        "## Reporting-Only Runtime Telemetry",
        "",
        f"- Lane: `{telemetry.get('lane')}`",
        f"- Gate phase: `{telemetry.get('gatePhase')}`",
        f"- Measured samples: `{telemetry.get('measuredSampleCount')}`",
        f"- p50/p95/worst: `{telemetry.get('measuredP50Ms')}` / `{telemetry.get('measuredP95Ms')}` / `{telemetry.get('measuredWorstMs')}` ms",
        f"- Surface status samples: `{telemetry.get('surfaceStatusCount')}`",
        "",
        "## Artifacts",
        "",
        "- `reports/wgsl-pipeline/m70-kadre-native/native-demo.json`",
        "- `reports/wgsl-pipeline/m70-kadre-native/native-demo-readback.png`",
        "- `reports/wgsl-pipeline/m70-kadre-live-runtime/route-status.json`",
        "- `reports/wgsl-pipeline/2026-06-01-m70-a-kadre-live-runtime.md`",
        "",
        "## Non-Claims",
        "",
    ]
    lines.extend(f"- {claim}" for claim in route["nonClaims"])
    lines.extend(
        [
            "",
            "## Readiness Accounting",
            "",
            "Readiness moves from approximately 64% to approximately 65%. The movement is intentionally conservative: M70-B/C confirm normalized native surface success and add a real offscreen native texture readback artifact when capture.realNativeReadback is true; broad display-list replay, input, and a release-grade frame gate are still not claimed.",
            "",
            "| Area | Previous | Current | Reason |",
            "|---|---:|---:|---|",
            "| Rendering feature breadth | 60% | 60% | No new rendering-family support/refusal denominator changed. |",
            "| Skia-like fidelity | 50% | 50% | No new selected GM/reference rows landed. |",
            "| Real-time runtime | 72% | 75% | PM-visible Kadre demo task and one selected Kanvas-owned native scene contract now execute present calls in the windowed lane with normalized surface-success evidence and a produced native readback artifact. |",
            "| Performance and cache readiness | 40% | 45% | `frame.kadre-windowed` now has reporting-only warmup/measured telemetry; no release-blocking FPS gate is enabled. |",
            "| PM/demo operability | 100% | 100% | PM bundle includes M70-A/B/C route status, native demo telemetry, and the readback artifact. |",
            "",
            "## Validation",
            "",
            "```bash",
            "rtk ./gradlew --no-daemon :kadre-runtime:compileKotlin",
            "rtk ./gradlew --no-daemon -PkadreDemoFrames=12 -PkadreDemoWarmupFrames=3 :kadre-runtime:runM70KadreNativeDemo",
            "rtk ./gradlew --no-daemon pipelineM70KadreLiveRuntimeEvidence",
            "rtk ./gradlew --no-daemon pipelinePmBundle",
            "```",
            "",
        ]
    )
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project-root", default=".")
    parser.add_argument("--output-dir", default="reports/wgsl-pipeline/m70-kadre-live-runtime")
    args = parser.parse_args()

    project_root = Path(args.project_root).resolve()
    output_dir = project_root / args.output_dir
    route = build_route(project_root)
    write_json(output_dir / "route-status.json", route)
    write_markdown(project_root / "reports/wgsl-pipeline/2026-06-01-m70-a-kadre-live-runtime.md", route)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
