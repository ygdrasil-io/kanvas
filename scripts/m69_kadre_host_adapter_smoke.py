#!/usr/bin/env python3
"""Generate M69 Kanvas/Kadre host adapter smoke evidence.

M69 narrows the previous M68 blocker into an executable route decision. It can
consume host-local native Kadre evidence when present, but the native smoke is a
bounded standalone WGSL present loop, not broad Kanvas display-list replay.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import platform
import subprocess
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


SCHEMA_VERSION = 1
SCOPE_IDS = ["FOR-56", "FOR-57", "FOR-58", "FOR-59"]

REQUIRED_SPECS = [
    ".upstream/specs/skia-like-realtime/02-realtime-runtime-architecture.md",
    ".upstream/specs/skia-like-realtime/05-pm-demo-and-release-candidate.md",
]

KADRE_CORE_FILES = {
    "eventLoop": "external/poc-koreos/kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/EventLoop.kt",
    "activeEventLoop": "external/poc-koreos/kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/ActiveEventLoop.kt",
    "applicationHandler": "external/poc-koreos/kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/ApplicationHandler.kt",
    "window": "external/poc-koreos/kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/Window.kt",
    "windowAttributes": "external/poc-koreos/kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/WindowAttributes.kt",
    "events": "external/poc-koreos/kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/Events.kt",
    "rawHandles": "external/poc-koreos/kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/RawHandles.kt",
    "frameTimingTracer": "external/poc-koreos/kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/FrameTimingTracer.kt",
}

KADRE_SAMPLE_FILES = {
    "helloTriangle": "external/poc-koreos/samples/hello-triangle/src/main/kotlin/org/graphiks/kadre/samples/hellotriangle/Main.kt",
    "helloWindow": "external/poc-koreos/samples/hello-window/src/commonMain/kotlin/org/graphiks/kadre/samples/hellowindow/HelloApp.kt",
}

M65_FILES = {
    "telemetry": "reports/wgsl-pipeline/m65-runtime-smoke/telemetry.json",
    "slots": "reports/wgsl-pipeline/m65-runtime-smoke/slots.json",
    "baselineFirstFrame": "reports/wgsl-pipeline/m65-runtime-smoke/artifacts/baseline-first-frame.png",
    "baselineFinalFrame": "reports/wgsl-pipeline/m65-runtime-smoke/artifacts/baseline-final-frame.png",
}

NATIVE_SMOKE_FILE = "reports/wgsl-pipeline/m69-kadre-native/native-smoke.json"

SCENE_SOURCE_ARTIFACTS = [
    {
        "feature": "animated transform",
        "requirement": "M69 first scene animation proof",
        "artifacts": [
            "reports/wgsl-pipeline/m65-runtime-smoke/artifacts/baseline-first-frame.png",
            "reports/wgsl-pipeline/m65-runtime-smoke/artifacts/baseline-final-frame.png",
            "reports/wgsl-pipeline/m65-runtime-smoke/telemetry.json",
        ],
    },
    {
        "feature": "gradient or bitmap",
        "requirement": "M69 first scene paint/image proof",
        "artifacts": [
            "reports/wgsl-pipeline/scenes/artifacts/bitmap-rect-nearest/gpu.png",
            "reports/wgsl-pipeline/scenes/artifacts/bitmap-rect-nearest/route-gpu.json",
            "reports/wgsl-pipeline/scenes/artifacts/gradient-color-filter-linear-kplus/gpu.png",
            "reports/wgsl-pipeline/scenes/artifacts/gradient-color-filter-linear-kplus/route-gpu.json",
        ],
    },
    {
        "feature": "simple shape/path",
        "requirement": "M69 first scene geometry proof",
        "artifacts": [
            "reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/cpu.png",
            "reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/gpu.png",
            "reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/route-gpu.json",
        ],
    },
]


def now_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.is_file() else ""


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def file_status(project_root: Path, relative: str) -> dict[str, Any]:
    path = project_root / relative
    status: dict[str, Any] = {
        "path": relative,
        "exists": path.is_file(),
    }
    if path.is_file():
        status["bytes"] = path.stat().st_size
        status["sha256"] = sha256(path)
    return status


def contains_all(text: str, needles: list[str]) -> dict[str, bool]:
    return {needle: needle in text for needle in needles}


def contains_tokens(prefix: str, text: str, needles: list[str]) -> dict[str, bool]:
    return {f"{prefix}.{needle}": needle in text for needle in needles}


def run_git(project_root: Path, args: list[str]) -> str:
    try:
        return subprocess.check_output(["git", *args], cwd=project_root, text=True, stderr=subprocess.DEVNULL).strip()
    except (FileNotFoundError, subprocess.CalledProcessError):
        return "unknown"


def parse_json_file(path: Path) -> dict[str, Any]:
    if not path.is_file():
        return {}
    parsed = json.loads(path.read_text(encoding="utf-8"))
    return parsed if isinstance(parsed, dict) else {}


def build_contract(project_root: Path) -> tuple[dict[str, Any], list[str]]:
    failures: list[str] = []
    audit_warnings: list[str] = []
    settings = read_text(project_root / "settings.gradle.kts")
    gitmodules = read_text(project_root / ".gitmodules")
    core_files = {name: file_status(project_root, path) for name, path in KADRE_CORE_FILES.items()}
    sample_files = {name: file_status(project_root, path) for name, path in KADRE_SAMPLE_FILES.items()}

    for name, status in {**core_files, **sample_files}.items():
        if not status["exists"]:
            audit_warnings.append(f"Missing Kadre evidence file: {name} -> {status['path']}")

    if 'includeBuild("external/poc-koreos")' not in settings:
        failures.append("settings.gradle.kts does not include the Kadre source build.")
    if "external/poc-koreos" not in gitmodules:
        failures.append(".gitmodules does not declare external/poc-koreos.")

    event_loop = read_text(project_root / KADRE_CORE_FILES["eventLoop"])
    active_event_loop = read_text(project_root / KADRE_CORE_FILES["activeEventLoop"])
    handler = read_text(project_root / KADRE_CORE_FILES["applicationHandler"])
    window = read_text(project_root / KADRE_CORE_FILES["window"])
    window_attrs = read_text(project_root / KADRE_CORE_FILES["windowAttributes"])
    events = read_text(project_root / KADRE_CORE_FILES["events"])
    raw_handles = read_text(project_root / KADRE_CORE_FILES["rawHandles"])
    frame_tracer = read_text(project_root / KADRE_CORE_FILES["frameTimingTracer"])
    hello_triangle = read_text(project_root / KADRE_SAMPLE_FILES["helloTriangle"])
    hello_window = read_text(project_root / KADRE_SAMPLE_FILES["helloWindow"])

    capabilities = {
        "surfaceCreation": {
            "kadreEvidenceStatus": "available-in-window-api-and-wgpu4k-sample",
            "requiredBehavior": "Create a WebGPU/wgpu4-compatible drawable surface with adapter/device metadata.",
            "sourceEvidence": {
                **contains_tokens("ApplicationHandler", handler, ["canCreateSurfaces", "ActiveEventLoop"]),
                **contains_tokens("ActiveEventLoop", active_event_loop, ["createWindow"]),
                **contains_tokens("WindowAttributes", window_attrs, ["WindowAttributes"]),
                **contains_tokens("HelloTriangle", hello_triangle, ["getSurfaceFromMetalLayer", "requestAdapter", "requestDevice"]),
            },
            "kanvasAdapterStatus": "route-contract-defined",
            "nativePresented": False,
        },
        "resize": {
            "kadreEvidenceStatus": "available-in-window-and-event-api",
            "requiredBehavior": "Report physical size, scale factor, and resize events before the next frame plan.",
            "sourceEvidence": {
                **contains_tokens("Window", window, ["innerSize", "scaleFactor"]),
                **contains_tokens("Events", events, ["WindowEvent.Resized", "ScaleFactorChanged"]),
                **contains_tokens("HelloTriangle", hello_triangle, ["handleResize", "configure("]),
            },
            "kanvasAdapterStatus": "route-contract-defined",
            "nativePresented": False,
        },
        "present": {
            "kadreEvidenceStatus": "available-in-wgpu4k-sample",
            "requiredBehavior": "Present a completed Kanvas frame and expose present timing proxy where available.",
            "sourceEvidence": contains_tokens("HelloTriangle", hello_triangle, ["getCurrentTexture", "queue.submit", "present()"]),
            "kanvasAdapterStatus": "blocked-native-present-not-wired",
            "nativePresented": False,
        },
        "input": {
            "kadreEvidenceStatus": "available-in-event-api",
            "requiredBehavior": "Forward pointer, keyboard, and close events to the runtime update phase.",
            "sourceEvidence": contains_all(
                events,
                ["KeyboardInput", "PointerMoved", "MouseInput", "MouseWheel", "Touch", "CloseRequested"],
            ),
            "kanvasAdapterStatus": "route-contract-defined",
            "nativePresented": False,
        },
        "frameClock": {
            "kadreEvidenceStatus": "available-as-redraw-loop-and-frame-tracer",
            "requiredBehavior": "Provide vsync-like callback or monotonic timer suitable for smoke tests.",
            "sourceEvidence": {
                **contains_tokens("EventLoop", event_loop, ["runApp"]),
                **contains_tokens("ActiveEventLoop", active_event_loop, ["controlFlow", "setControlFlow"]),
                **contains_tokens("Window", window, ["requestRedraw"]),
                **contains_tokens("FrameTimingTracer", frame_tracer, ["FrameTimingTracer", "onRedrawStart", "onPresentEnd"]),
                **contains_tokens("HelloWindow", hello_window, ["requestRedraw"]),
            },
            "kanvasAdapterStatus": "route-contract-defined",
            "nativePresented": False,
        },
        "diagnostics": {
            "kadreEvidenceStatus": "partial-host-diagnostics-available",
            "requiredBehavior": "Expose host, backend, adapter, surface format, and failure reason in telemetry.",
            "sourceEvidence": {
                **contains_tokens("RawHandles", raw_handles, ["RawWindowHandle", "RawDisplayHandle"]),
                **contains_tokens("HelloTriangle", hello_triangle, ["Adapter", "supportedFormats", "SurfaceTextureStatus"]),
            },
            "kanvasAdapterStatus": "route-contract-defined",
            "nativePresented": False,
        },
        "export": {
            "kadreEvidenceStatus": "not-a-kadre-core-feature",
            "requiredBehavior": "Export current frame evidence to dashboard-style artifacts.",
            "sourceEvidence": {
                "m65-offscreen-png-artifacts": (project_root / M65_FILES["baselineFirstFrame"]).is_file()
                    and (project_root / M65_FILES["baselineFinalFrame"]).is_file(),
            },
            "kanvasAdapterStatus": "headless-export-ready-native-export-pending",
            "nativePresented": False,
        },
        "nativePresentation": {
            "kadreEvidenceStatus": "host-capable-via-sample",
            "requiredBehavior": "Present a bounded WebGPU frame in a Kadre native window and keep Kanvas display-list replay as a separate claim.",
            "sourceEvidence": contains_tokens("HelloTriangle", hello_triangle, ["present()", "SurfaceTextureStatus.success"]),
            "kanvasAdapterStatus": "blocked-native-kanvas-frame-loop-not-implemented",
            "nativePresented": False,
        },
    }

    optional_evidence = {
        "frameClock": {"HelloWindow.requestRedraw"},
    }
    for name, capability in capabilities.items():
        source_evidence = capability["sourceEvidence"]
        optional_keys = optional_evidence.get(name, set())
        required_ready = all(value is True for key, value in source_evidence.items() if key not in optional_keys)
        all_ready = all(value is True for value in source_evidence.values())
        capability["requiredEvidenceReady"] = required_ready
        capability["evidenceReadiness"] = "ready" if all_ready else "ready-with-optional-gap" if required_ready else "partial"
        if name == "nativePresentation":
            capability["evidenceReadiness"] = "blocked-native-present"

    contract = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": now_iso(),
        "generatedBy": "scripts/m69_kadre_host_adapter_smoke.py",
        "scopeIds": SCOPE_IDS,
        "repository": {
            "rootCommit": run_git(project_root, ["rev-parse", "HEAD"]),
            "kadreSubmoduleCommit": run_git(project_root, ["-C", "external/poc-koreos", "rev-parse", "HEAD"]),
            "kadreSubmodulePath": "external/poc-koreos",
            "settingsIncludeBuildPresent": 'includeBuild("external/poc-koreos")' in settings,
            "gitmodulesEntryPresent": "external/poc-koreos" in gitmodules,
        },
        "specReferences": REQUIRED_SPECS,
        "kadreCoreFiles": core_files,
        "kadreSampleFiles": sample_files,
        "auditWarnings": audit_warnings,
        "capabilities": capabilities,
        "nonClaims": [
            "No native Kadre screenshot or present timing is claimed by M69.",
            "M69 proves the route contract and headless bridge only unless a future native artifact is produced.",
        ],
    }
    return contract, failures


def build_scene_route(project_root: Path, m65: dict[str, Any], native_smoke: dict[str, Any]) -> tuple[dict[str, Any], list[str]]:
    failures: list[str] = []
    feature_rows: list[dict[str, Any]] = []
    for feature in SCENE_SOURCE_ARTIFACTS:
        artifacts = [file_status(project_root, artifact) for artifact in feature["artifacts"]]
        missing = [artifact["path"] for artifact in artifacts if not artifact["exists"]]
        if missing:
            failures.extend(f"{feature['feature']}: missing {path}" for path in missing)
        feature_rows.append(
            {
                "feature": feature["feature"],
                "requirement": feature["requirement"],
                "status": "source-evidence-ready" if not missing else "blocked-missing-source-evidence",
                "nativePresented": False,
                "nativePresentationReason": "m69.feature-specific-native-replay-not-claimed",
                "artifacts": artifacts,
            }
        )

    m65_slots = m65.get("slots", [])
    bridge_slots = []
    if isinstance(m65_slots, list):
        for slot in m65_slots:
            if isinstance(slot, dict):
                summary = slot.get("telemetrySummary", {})
                bridge_slots.append(
                    {
                        "slot": slot.get("slot"),
                        "title": slot.get("title"),
                        "status": slot.get("status"),
                        "runtimeRoute": slot.get("runtimeRoute"),
                        "firstFrameNonblank": isinstance(summary, dict)
                        and bool(summary.get("firstFrame", {}).get("nonblank")),
                        "finalFrameNonblank": isinstance(summary, dict)
                        and bool(summary.get("finalFrame", {}).get("nonblank")),
                        "medianFrameMs": summary.get("medianFrameMs") if isinstance(summary, dict) else None,
                        "p95FrameMs": summary.get("p95FrameMs") if isinstance(summary, dict) else None,
                    }
                )

    native_presented = native_smoke.get("nativePresented") is True and native_smoke.get("status") == "native-runnable"
    scene = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": now_iso(),
        "generatedBy": "scripts/m69_kadre_host_adapter_smoke.py",
        "scopeIds": SCOPE_IDS,
        "sceneId": "m69-first-kanvas-kadre-host-adapter-scene",
        "claimLevel": "native-runnable" if native_presented else "headless-bridge",
        "nativePresented": native_presented,
        "nativePresentationReason": "m69.standalone-wgsl-present-loop" if native_presented else "m69.native-kadre-present-not-run",
        "requestedContent": [
            "animated transform",
            "gradient or bitmap",
            "simple shape/path",
        ],
        "sourceFeatures": feature_rows,
        "headlessPixelProof": {
            "source": "reports/wgsl-pipeline/m65-runtime-smoke/telemetry.json",
            "mode": m65.get("mode"),
            "frameCount": m65.get("frameCount"),
            "surface": m65.get("surface"),
            "slots": bridge_slots,
        },
        "nativeStandaloneSmoke": native_smoke if native_presented else {},
    }
    return scene, failures


def decide_route(
    project_root: Path,
    contract: dict[str, Any],
    m65: dict[str, Any],
    native_smoke: dict[str, Any],
    failures: list[str],
) -> dict[str, Any]:
    required_m65 = [file_status(project_root, relative) for relative in M65_FILES.values()]
    missing_m65 = [status["path"] for status in required_m65 if not status["exists"]]
    if missing_m65:
        failures.extend(f"Missing M65 bridge evidence: {path}" for path in missing_m65)

    capabilities = contract["capabilities"]
    source_audit_ready = all(
        bool(capability.get("requiredEvidenceReady"))
        for name, capability in capabilities.items()
        if name != "nativePresentation"
    )
    native_presentation_source_ready = bool(capabilities["nativePresentation"].get("requiredEvidenceReady"))
    m65_nonblank = False
    slots = m65.get("slots", [])
    if isinstance(slots, list):
        m65_nonblank = any(
            isinstance(slot, dict)
            and isinstance(slot.get("telemetrySummary"), dict)
            and bool(slot["telemetrySummary"].get("firstFrame", {}).get("nonblank"))
            and bool(slot["telemetrySummary"].get("finalFrame", {}).get("nonblank"))
            for slot in slots
        )

    native_presented = native_smoke.get("nativePresented") is True and native_smoke.get("status") == "native-runnable"
    native_reason = str(native_smoke.get("reason") or "m69.native-kadre-present-not-run")

    if failures:
        route_status = "blocked"
        reason = "m69.required-evidence-missing"
    elif native_presented:
        route_status = "native-runnable"
        reason = native_reason
    elif source_audit_ready and m65_nonblank:
        route_status = "headless-bridge"
        reason = "m69.kadre-contract-ready-m65-headless-pixel-proof"
    else:
        route_status = "blocked"
        reason = "m69.kadre-contract-or-headless-pixel-proof-incomplete"

    return {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": now_iso(),
        "generatedBy": "scripts/m69_kadre_host_adapter_smoke.py",
        "scopeIds": SCOPE_IDS,
        "status": route_status,
        "reason": reason,
        "nativePresented": native_presented,
        "nativePresentationReason": "none" if native_presented else native_reason,
        "routes": {
            "nativeWindowed": {
                "route": native_smoke.get("route") or "kadre.native.windowed",
                "status": "pass" if native_presented else "blocked",
                "reason": "none" if native_presented else native_reason,
                "nativePresented": native_presented,
                "presentedFrames": int(native_smoke.get("presentedFrames") or 0),
                "surface": native_smoke.get("surface") if isinstance(native_smoke.get("surface"), dict) else {},
                "claim": "bounded-standalone-wgsl-present-loop" if native_presented else "none",
            },
            "headlessBridge": {
                "route": "m65.headless.offscreen.pixel-proof-plus-kadre-contract",
                "status": "pass" if route_status in {"headless-bridge", "native-runnable"} else "blocked",
                "reason": "none" if route_status in {"headless-bridge", "native-runnable"} else reason,
                "nativePresented": False,
            },
        },
        "evidence": {
            "sourceAuditReady": source_audit_ready,
            "nativePresentationSourceReady": native_presentation_source_ready,
            "m65NonblankPixelProof": m65_nonblank,
            "nativeSmoke": native_smoke,
            "nativeClaim": "bounded standalone WGSL present loop; Kanvas display-list replay is not claimed",
            "requiredM65Files": required_m65,
            "failures": failures,
        },
    }


def build_telemetry(route: dict[str, Any], scene: dict[str, Any], m65: dict[str, Any]) -> dict[str, Any]:
    slots = scene["headlessPixelProof"]["slots"]
    measured_slots = [slot for slot in slots if isinstance(slot.get("medianFrameMs"), (int, float))]
    median_values = [slot["medianFrameMs"] for slot in measured_slots]
    p95_values = [slot["p95FrameMs"] for slot in measured_slots if isinstance(slot.get("p95FrameMs"), (int, float))]
    native_route = route["routes"]["nativeWindowed"]
    native_smoke = route["evidence"].get("nativeSmoke")
    native_timing = (native_smoke.get("frameTiming") if isinstance(native_smoke, dict) else {}) or {}
    return {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": now_iso(),
        "generatedBy": "scripts/m69_kadre_host_adapter_smoke.py",
        "scopeIds": SCOPE_IDS,
        "claimLevel": route["status"],
        "nativePresented": bool(route["nativePresented"]),
        "nativeFrameTimingClaim": "present-call-duration-only" if route["nativePresented"] else "none",
        "frameClock": {
            "native": "kadre-redraw-loop" if route["nativePresented"] else "blocked",
            "headless": "m65.synthetic-monotonic",
            "targetFrames": m65.get("frameCount"),
        },
        "presentation": {
            "native": {
                "status": native_route["status"],
                "reason": native_route["reason"],
                "presentedFrames": native_route.get("presentedFrames", 0),
                "firstFrameMs": native_timing.get("firstFrameMs"),
                "averageFrameMs": native_timing.get("averageFrameMs"),
            },
            "headlessBridge": {
                "status": "pass" if route["status"] in {"headless-bridge", "native-runnable"} else "blocked",
                "pixelProof": "M65 first/final frame nonblank telemetry and PNG artifacts",
            },
        },
        "headlessTimingSummary": {
            "source": "reports/wgsl-pipeline/m65-runtime-smoke/telemetry.json",
            "slotCount": len(slots),
            "medianFrameMsMin": round(min(median_values), 4) if median_values else None,
            "medianFrameMsMax": round(max(median_values), 4) if median_values else None,
            "p95FrameMsMax": round(max(p95_values), 4) if p95_values else None,
            "notNativeTiming": True,
        },
        "routeCounters": {
            "nativeRoutes": 1,
            "nativeRunnableRoutes": 1 if route["status"] == "native-runnable" else 0,
            "headlessBridgeRoutes": 1 if route["status"] in {"headless-bridge", "native-runnable"} else 0,
            "blockedRoutes": 0 if route["status"] == "native-runnable" else 1,
        },
        "environment": {
            "platform": platform.platform(),
            "python": platform.python_version(),
        },
    }


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def write_markdown(path: Path, contract: dict[str, Any], route: dict[str, Any], scene: dict[str, Any], telemetry: dict[str, Any]) -> None:
    native_smoke = route["evidence"].get("nativeSmoke")
    native_surface = (native_smoke.get("surface") if isinstance(native_smoke, dict) else {}) or {}
    lines = [
        "# M69 Kanvas/Kadre Host Adapter Smoke",
        "",
        f"Status: `{route['status']}`",
        "",
        "M69 replaces the M68 generic native-launch blocker with a concrete route decision. Kadre source APIs are audited from `external/poc-koreos`, Kanvas headless/offscreen pixels are reused from M65 as bridge evidence, and the native lane now runs a bounded standalone Kadre/AppKit/Metal WebGPU present loop when host-local evidence is available.",
        "",
        "## Route Decision",
        "",
        f"- Route status: `{route['status']}`",
        f"- Reason: `{route['reason']}`",
        f"- Native presented: `{route['nativePresented']}`",
        f"- Native presentation reason: `{route['nativePresentationReason']}`",
        f"- Native presented frames: `{route['routes']['nativeWindowed'].get('presentedFrames', 0)}`",
        f"- Native claim: `{route['evidence']['nativeClaim']}`",
        f"- Headless pixel proof: `{route['evidence']['m65NonblankPixelProof']}`",
        f"- Kadre source audit ready: `{route['evidence']['sourceAuditReady']}`",
        f"- Native presentation source ready: `{route['evidence']['nativePresentationSourceReady']}`",
        "",
        "## Host Contract",
        "",
        "| Capability | Kadre evidence | Evidence readiness | Kanvas adapter status | Native presented |",
        "|---|---|---|---|---:|",
    ]
    for name, capability in contract["capabilities"].items():
        lines.append(
            f"| `{name}` | `{capability['kadreEvidenceStatus']}` | `{capability['evidenceReadiness']}` | `{capability['kanvasAdapterStatus']}` | `{capability['nativePresented']}` |"
        )

    lines.extend(
        [
            "",
            "## First Scene Route",
            "",
            f"- Scene id: `{scene['sceneId']}`",
            f"- Claim level: `{scene['claimLevel']}`",
            f"- Native presented: `{scene['nativePresented']}`",
            f"- Native presentation reason: `{scene['nativePresentationReason']}`",
            f"- Source headless frame count: `{scene['headlessPixelProof']['frameCount']}`",
            "",
            "| Feature | Status | Artifact coverage |",
            "|---|---|---:|",
        ]
    )
    for feature in scene["sourceFeatures"]:
        present_count = sum(1 for artifact in feature["artifacts"] if artifact["exists"])
        lines.append(f"| {feature['feature']} | `{feature['status']}` | {present_count}/{len(feature['artifacts'])} |")

    lines.extend(
        [
            "",
            "## Telemetry",
            "",
            f"- Native frame timing claim: `{telemetry['nativeFrameTimingClaim']}`",
            f"- Native surface: `{native_surface.get('width')}` x `{native_surface.get('height')}` `{native_surface.get('format')}`",
            f"- Native first/average present duration: `{telemetry['presentation']['native'].get('firstFrameMs')}` / `{telemetry['presentation']['native'].get('averageFrameMs')}` ms",
            f"- Headless slot count: `{telemetry['headlessTimingSummary']['slotCount']}`",
            f"- Headless median frame min/max: `{telemetry['headlessTimingSummary']['medianFrameMsMin']}` / `{telemetry['headlessTimingSummary']['medianFrameMsMax']}` ms",
            f"- Headless p95 max: `{telemetry['headlessTimingSummary']['p95FrameMsMax']}` ms",
            "",
            "## Artifacts",
            "",
            "- `reports/wgsl-pipeline/m69-kadre-host-adapter/contract.json`",
            "- `reports/wgsl-pipeline/m69-kadre-host-adapter/route-status.json`",
            "- `reports/wgsl-pipeline/m69-kadre-host-adapter/scene-route.json`",
            "- `reports/wgsl-pipeline/m69-kadre-host-adapter/telemetry.json`",
            "- `reports/wgsl-pipeline/m69-kadre-host-adapter/bridge-smoke.json`",
            "- `reports/wgsl-pipeline/m69-kadre-native/native-smoke.json`",
            "",
            "## Non-Claims",
            "",
            "- No native screenshot or frame PNG is claimed yet.",
            "- Native timing is present-call duration only, not a release-grade FPS claim.",
            "- The native smoke presents standalone generated WGSL, not Kanvas display-list replay.",
            "- Input-driven interaction and broad Kanvas display-list replay remain outside M69.",
            "",
            "## Validation",
            "",
            "```bash",
            "rtk ./gradlew --no-daemon :kadre-runtime:runM69KadreNativeSmoke",
            "rtk ./gradlew --no-daemon pipelineM69KadreHostAdapterSmoke",
            "```",
            "",
        ]
    )
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project-root", default=".")
    parser.add_argument("--output-dir", default="reports/wgsl-pipeline/m69-kadre-host-adapter")
    args = parser.parse_args()

    project_root = Path(args.project_root).resolve()
    output_dir = project_root / args.output_dir
    failures = [f"Missing required spec: {spec}" for spec in REQUIRED_SPECS if not (project_root / spec).is_file()]

    m65 = parse_json_file(project_root / M65_FILES["telemetry"])
    native_smoke = parse_json_file(project_root / NATIVE_SMOKE_FILE)
    contract, contract_failures = build_contract(project_root)
    failures.extend(contract_failures)
    native_presented = native_smoke.get("nativePresented") is True and native_smoke.get("status") == "native-runnable"
    if native_presented:
        capabilities = contract.get("capabilities", {})
        if isinstance(capabilities, dict):
            for name in ("surfaceCreation", "present", "nativePresentation", "diagnostics"):
                capability = capabilities.get(name)
                if isinstance(capability, dict):
                    capability["kanvasAdapterStatus"] = "native-kadre-standalone-present-loop-implemented"
                    capability["nativePresented"] = True
                    capability["evidenceReadiness"] = "native-runnable"
        contract["nonClaims"] = [
            "No native screenshot or frame PNG is claimed by M69.",
            "Native timing is present-call duration only; release-grade FPS, Kanvas display-list replay, and input-driven interaction remain future work.",
        ]
    scene, scene_failures = build_scene_route(project_root, m65, native_smoke)
    failures.extend(scene_failures)
    route = decide_route(project_root, contract, m65, native_smoke, failures)
    telemetry = build_telemetry(route, scene, m65)
    bridge_smoke = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": now_iso(),
        "generatedBy": "scripts/m69_kadre_host_adapter_smoke.py",
        "scopeIds": SCOPE_IDS,
        "environment": {
            "platform": platform.platform(),
            "python": platform.python_version(),
            "cwd": str(project_root),
        },
        "status": "pass" if route["status"] in {"headless-bridge", "native-runnable"} else "blocked",
        "routeStatus": route["status"],
        "nativePresented": route["nativePresented"],
        "failures": failures,
    }

    write_json(output_dir / "contract.json", contract)
    write_json(output_dir / "route-status.json", route)
    write_json(output_dir / "scene-route.json", scene)
    write_json(output_dir / "telemetry.json", telemetry)
    write_json(output_dir / "bridge-smoke.json", bridge_smoke)
    write_markdown(
        project_root / "reports/wgsl-pipeline/2026-06-01-m69-kadre-host-adapter-smoke.md",
        contract,
        route,
        scene,
        telemetry,
    )

    if route["status"] == "blocked":
        print(f"M69 Kadre host adapter smoke blocked: {route['reason']}")
        return 1
    print(f"M69 Kadre host adapter smoke generated: {route['status']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
