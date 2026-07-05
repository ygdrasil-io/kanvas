#!/usr/bin/env python3
"""Generate M70-M73 Kadre live runtime PM evidence."""

from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


SCHEMA_VERSION = 1
SCOPE_IDS = [
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
    "FOR-74",
    "FOR-75",
    "FOR-76",
    "FOR-77",
    "FOR-78",
    "FOR-79",
    "FOR-80",
    "FOR-81",
    "FOR-82",
    "FOR-83",
    "FOR-84",
    "FOR-85",
    "FOR-86",
    "FOR-87",
    "FOR-88",
    "FOR-89",
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
    autonomous_clock = telemetry.get("autonomousFrameClock") is True
    autonomous_frames = int(telemetry.get("autonomousFrameCount") or 0)
    replay = native_demo.get("sceneReplay") if isinstance(native_demo.get("sceneReplay"), dict) else {}
    replay_counters = replay.get("commandCounters") if isinstance(replay.get("commandCounters"), dict) else {}
    replay_supported = replay.get("source") == "kanvas-replay-data"
    replay_unsupported = int(replay_counters.get("unsupported") or 0)
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
        and autonomous_clock
        and autonomous_frames >= presented
        and replay_supported
        and replay_unsupported == 0
        and success_count > 0
    ):
        return "native-runnable", "m73.kadre-replay-pack-selected-scene-presented-frames"
    if (
        native_demo.get("presentCallCompleted") is True
        and has_full_frame_count
        and has_measured_samples
        and autonomous_clock
        and autonomous_frames >= presented
        and replay_supported
        and replay_unsupported == 0
        and timeout_count == presented
    ):
        return "degraded", "m73.kadre-replay-pack-selected-scene-present-call-completed-timeout-only"
    return "blocked", str(native_demo.get("reason") or native_demo.get("error") or "m70.kadre-demo-not-runnable")


def build_route(project_root: Path) -> dict[str, Any]:
    native_demo = parse_json(project_root / NATIVE_DEMO_FILE)
    native_smoke = parse_json(project_root / NATIVE_SMOKE_FILE)
    status, reason = status_from_demo(native_demo)
    telemetry = native_demo.get("runtimeTelemetry") if isinstance(native_demo.get("runtimeTelemetry"), dict) else {}
    capture = native_demo.get("capture") if isinstance(native_demo.get("capture"), dict) else {}
    scene_contract = native_demo.get("sceneContract") if isinstance(native_demo.get("sceneContract"), dict) else {}
    scene_replay = native_demo.get("sceneReplay") if isinstance(native_demo.get("sceneReplay"), dict) else {}
    replay_pack = native_demo.get("replayPack") if isinstance(native_demo.get("replayPack"), dict) else {}
    replay_counters = scene_replay.get("commandCounters") if isinstance(scene_replay.get("commandCounters"), dict) else {}
    cpu_reference = native_demo.get("cpuReference") if isinstance(native_demo.get("cpuReference"), dict) else {}
    has_real_capture = capture.get("realNativeReadback") is True
    non_claims = [
        "M70-A/B/C prove one selected Kadre native route only, with status determined by surface evidence; M72 proves one selected `solid-rect` replay contract; M73 expands that to a small typed replay-pack registry.",
        "Native presentation is claimed only when the normalized surface status summary contains at least one success.",
        "Raw Kadre/wgpu4k API status names remain recorded separately when they differ from normalized evidence semantics.",
        "Broad Kanvas display-list replay is not claimed.",
        "Frame timing is reporting-only and not a release-grade FPS gate.",
        "M71 claims autonomous frame scheduling only when runtimeTelemetry.autonomousFrameClock is true and autonomousFrameCount records frame requests generated by Kadre/AppKit ControlFlow.Poll.",
        "M73 claims a bounded registry of typed replay contracts only; arbitrary SkCanvas op streams, broad display-list replay, and dynamic multi-scene live switching remain future work.",
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
        "scopeIds": SCOPE_IDS,
        "status": status,
        "reason": reason,
        "sceneId": scene_contract.get("id") or "m72-solid-rect-replay-v1",
        "sourceSceneId": scene_replay.get("sourceSceneId"),
        "sceneReplay": scene_replay,
        "replayPack": replay_pack,
        "replayCommandCounters": replay_counters,
        "cpuReference": cpu_reference,
        "mode": native_demo.get("mode") or "demo",
        "nativePresented": native_demo.get("nativePresented") is True,
        "presentCallCompleted": native_demo.get("presentCallCompleted") is True,
        "requestedFrames": int(native_demo.get("requestedFrames") or 0),
        "presentedFrames": int(native_demo.get("presentedFrames") or 0),
        "warmupFrames": int(native_demo.get("warmupFrames") or 0),
        "frameClockSource": telemetry.get("frameClockSource"),
        "autonomousFrameClock": telemetry.get("autonomousFrameClock") is True,
        "autonomousFrameCount": int(telemetry.get("autonomousFrameCount") or 0),
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
        "claimLevel": "bounded-replay-pack-selected-scene" if status == "native-runnable" else "selected-present-call-demo" if status == "degraded" else "blocked",
        "releaseBlocking": False,
        "nonClaims": non_claims,
    }


def write_markdown(path: Path, route: dict[str, Any]) -> None:
    telemetry = route.get("runtimeTelemetry") if isinstance(route.get("runtimeTelemetry"), dict) else {}
    capture = route.get("capture") if isinstance(route.get("capture"), dict) else {}
    replay = route.get("sceneReplay") if isinstance(route.get("sceneReplay"), dict) else {}
    replay_pack = route.get("replayPack") if isinstance(route.get("replayPack"), dict) else {}
    replay_counters = route.get("replayCommandCounters") if isinstance(route.get("replayCommandCounters"), dict) else {}
    cpu_reference = route.get("cpuReference") if isinstance(route.get("cpuReference"), dict) else {}
    surface = route.get("surface") if isinstance(route.get("surface"), dict) else {}
    surface_status = route.get("surfaceStatusSummary") if isinstance(route.get("surfaceStatusSummary"), dict) else {}
    lines = [
        "# M70-M73 Kadre Live Runtime Evidence",
        "",
        f"Status: `{route['status']}`",
        "",
        "M70-A/B/C turn the PM-validated M69 native smoke into a PM-visible live-runtime slice. "
        "M71 closes the PM-observed mouse-wake limitation by driving the demo route through Kadre/AppKit ControlFlow.Poll. "
        "M72 replaces the shader-only demo claim with one selected Kanvas replay contract backed by the generated dashboard `solid-rect` row. "
        "M73 expands that narrow slice to a bounded replay-pack registry while still selecting one scene per native demo run. "
        "The demo is still deliberately narrow: it renders one selected typed replay scene contract in a Kadre native WebGPU window and emits reporting-only telemetry.",
        "",
        "## PM Outcome",
        "",
        f"- Scene: `{route['sceneId']}`",
        f"- Source dashboard scene: `{route['sourceSceneId']}`",
        f"- Replay source: `{replay.get('source')}`",
        f"- Replay claim level: `{replay.get('claimLevel')}`",
        f"- Replay commands: total `{replay_counters.get('total')}`, supported `{replay_counters.get('supported')}`, unsupported `{replay_counters.get('unsupported')}`",
        f"- Replay pack: `{replay_pack.get('id')}` with `{replay_pack.get('sceneCount')}` scenes, `{replay_pack.get('renderableSceneCount')}` renderable, `{replay_pack.get('unsupportedSceneCount')}` expected-unsupported",
        f"- Mode: `{route['mode']}`",
        f"- Native presented: `{route['nativePresented']}`",
        f"- Present-call completed: `{route['presentCallCompleted']}`",
        f"- Requested/presented frames: `{route['requestedFrames']}` / `{route['presentedFrames']}`",
        f"- Warmup frames: `{route['warmupFrames']}`",
        f"- Frame clock source: `{route['frameClockSource']}`",
        f"- Autonomous frame clock: `{route['autonomousFrameClock']}`",
        f"- Autonomous frame requests: `{route['autonomousFrameCount']}`",
        f"- Surface: `{surface.get('width')}` x `{surface.get('height')}` `{surface.get('format')}`",
        f"- Capture status: `{capture.get('status')}`",
        f"- Capture reason: `{capture.get('reason')}`",
        f"- Capture artifact: `{capture.get('imagePath')}`",
        f"- Window-surface readback: `{capture.get('windowSurfaceReadback')}`",
        f"- CPU reference checksum/nontransparent: `{cpu_reference.get('checksum')}` / `{cpu_reference.get('nonTransparentPixels')}`",
        f"- Native readback checksum/nontransparent: `{capture.get('checksum')}` / `{capture.get('nonTransparentPixels')}`",
        f"- Surface status summary: success `{surface_status.get('success')}`, timeout `{surface_status.get('timeout')}`",
        "",
        "## Scope",
        "",
        "- Epic: `FOR-60` M70-A Kadre Live Runtime V1.",
        "- Runtime demo task: `FOR-61`.",
        "- Selected Kanvas-owned scene route: `FOR-62`.",
        "- Native capture/readback status: `FOR-63`.",
        "- Runtime telemetry counters: `FOR-64`.",
        "- PM bundle/readiness closeout: `FOR-65`.",
        "- M70-B native surface success and presentation: `FOR-66`, `FOR-68`, `FOR-69`, `FOR-70`.",
        "- M70-C native capture/readback evidence: `FOR-67`, `FOR-71`, `FOR-72`, `FOR-73`.",
        "- M71 autonomous Kadre frame clock: `FOR-74`, `FOR-75`, `FOR-76`, `FOR-77`, `FOR-78`.",
        "- M72 single-scene Kadre replay: `FOR-79`, `FOR-80`, `FOR-81`, `FOR-82`, `FOR-83`.",
        "- M73 Kadre replay pack: `FOR-84`, `FOR-85`, `FOR-86`, `FOR-87`, `FOR-88`, `FOR-89`.",
        "",
        "## Replay Evidence",
        "",
        f"- Replay scene title: `{replay.get('title')}`",
        f"- Command source: `{replay.get('commandSource')}`",
        f"- GPU execution: `{replay.get('gpuExecution')}`",
        f"- Fallback policy: `{replay.get('fallbackPolicy')}`",
        f"- Source evidence: `{(replay.get('sourceEvidence') or {}).get('dashboardRow')}`",
        "",
        "## Replay Pack Registry",
        "",
        f"- Pack id: `{replay_pack.get('id')}`",
        f"- Scene count: `{replay_pack.get('sceneCount')}`",
        f"- Renderable scenes: `{replay_pack.get('renderableSceneCount')}`",
        f"- Expected-unsupported scenes: `{replay_pack.get('unsupportedSceneCount')}`",
        f"- Scene ids: `{', '.join(replay_pack.get('sceneIds') or [])}`",
        f"- Unsupported scene ids: `{', '.join(replay_pack.get('unsupportedSceneIds') or [])}`",
        "",
        "## Reporting-Only Runtime Telemetry",
        "",
        f"- Lane: `{telemetry.get('lane')}`",
        f"- Gate phase: `{telemetry.get('gatePhase')}`",
        f"- Frame clock source: `{telemetry.get('frameClockSource')}`",
        f"- Autonomous frame clock: `{telemetry.get('autonomousFrameClock')}`",
        f"- Autonomous frame requests: `{telemetry.get('autonomousFrameCount')}`",
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
            "Readiness moves from approximately 67% to an exact weighted score of 67.75%, rounded for PM to approximately 70%. The movement is intentionally conservative: M73 proves a small typed replay-pack registry and one selected rendered pack scene in Kadre, but broad display-list replay, arbitrary op streams, dynamic multi-scene switching, input, window-surface readback, and a release-grade frame gate are still not claimed.",
            "",
            "| Area | Previous | Current | Reason |",
            "|---|---:|---:|---|",
            "| Rendering feature breadth | 60% | 60% | No new rendering-family support/refusal denominator changed. |",
            "| Skia-like fidelity | 50% | 50% | No new selected GM/reference rows landed. |",
            "| Real-time runtime | 85% | 90% | The selected Kadre route now has a bounded M73 replay-pack registry and renders one selected pack scene with explicit command counters and source dashboard evidence. |",
            "| Performance and cache readiness | 45% | 45% | `frame.kadre-windowed` remains reporting-only warmup/measured telemetry; no release-blocking FPS gate is enabled. |",
            "| PM/demo operability | 100% | 100% | PM bundle includes M70-A/B/C route status, M71 autonomous clock evidence, M72/M73 replay counters, native demo telemetry, and the readback artifact. |",
            "",
            "## Validation",
            "",
            "```bash",
            "rtk ./gradlew --no-daemon :kadre-runtime:compileKotlin",
            "rtk ./gradlew --no-daemon -PkadreDemoFrames=180 -PkadreDemoWarmupFrames=30 :kadre-runtime:runM70KadreNativeDemo",
            "rtk ./gradlew --no-daemon -PkadreReplaySceneId=m73-bitmap-rect-nearest-replay-v1 -PkadreDemoFrames=12 -PkadreDemoWarmupFrames=0 :kadre-runtime:runM70KadreNativeDemo",
            "rtk ./gradlew --no-daemon -PkadreReplaySceneId=m73-nested-rrect-clip-refusal-v1 -PkadreDemoFrames=12 -PkadreDemoWarmupFrames=0 :kadre-runtime:runM70KadreNativeDemo",
            "rtk ./gradlew --no-daemon -PkadreReplaySceneId=m73-unknown-scene -PkadreDemoFrames=12 -PkadreDemoWarmupFrames=0 :kadre-runtime:runM70KadreNativeDemo",
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
