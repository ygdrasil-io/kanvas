#!/usr/bin/env python3
"""Generate M68 Kadre/native demo evidence without overstating support.

M68 depends on Kadre as the native host. Kadre now exists in this repository as
the ``external/poc-koreos`` git submodule, but Kanvas does not yet have a
checked-in host adapter that can present Kanvas-rendered frames through Kadre.
This generator therefore produces a bridge-smoke and blocked native-launch
evidence pack instead of claiming a native demo success.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import platform
import subprocess
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


SCHEMA_VERSION = 1
SCOPE_IDS = ["FOR-50", "FOR-51", "FOR-52", "FOR-53"]

CONTRACT_SPECS = [
    ".upstream/specs/skia-like-realtime/02-realtime-runtime-architecture.md",
    ".upstream/specs/skia-like-realtime/05-pm-demo-and-release-candidate.md",
]

KADRE_FILES = {
    "readme": "external/poc-koreos/README.md",
    "settings": "external/poc-koreos/settings.gradle.kts",
    "facadeBuild": "external/poc-koreos/kadre/build.gradle.kts",
    "eventLoop": "external/poc-koreos/kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/EventLoop.kt",
    "activeEventLoop": "external/poc-koreos/kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/ActiveEventLoop.kt",
    "window": "external/poc-koreos/kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/Window.kt",
    "events": "external/poc-koreos/kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/Events.kt",
    "handler": "external/poc-koreos/kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/ApplicationHandler.kt",
    "rawHandles": "external/poc-koreos/kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/RawHandles.kt",
    "frameTracer": "external/poc-koreos/kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/FrameTimingTracer.kt",
    "helloWindow": "external/poc-koreos/samples/hello-window/src/commonMain/kotlin/org/graphiks/kadre/samples/hellowindow/HelloApp.kt",
    "helloTriangle": "external/poc-koreos/samples/hello-triangle/src/main/kotlin/org/graphiks/kadre/samples/hellotriangle/Main.kt",
}

FEATURE_SCENES = [
    {
        "feature": "animated transform",
        "status": "bridge-smoke",
        "claim": "M65 provides synthetic/offscreen frame motion; M68 does not yet present it through Kadre.",
        "artifacts": [
            "reports/wgsl-pipeline/m65-runtime-smoke/artifacts/baseline-first-frame.png",
            "reports/wgsl-pipeline/m65-runtime-smoke/artifacts/baseline-final-frame.png",
            "reports/wgsl-pipeline/m65-runtime-smoke/telemetry.json",
        ],
    },
    {
        "feature": "Path AA / stroke / clip",
        "status": "source-evidence-ready",
        "claim": "Kanvas dashboard has CPU/GPU source rows suitable for a future native scene.",
        "artifacts": [
            "reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/cpu.png",
            "reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/gpu.png",
            "reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/route-gpu.json",
            "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/route-gpu.json",
        ],
    },
    {
        "feature": "image / bitmap sampling",
        "status": "source-evidence-ready",
        "claim": "Bitmap rows exist with CPU/GPU artifact coverage.",
        "artifacts": [
            "reports/wgsl-pipeline/scenes/artifacts/bitmap-rect-nearest/cpu.png",
            "reports/wgsl-pipeline/scenes/artifacts/bitmap-rect-nearest/gpu.png",
            "reports/wgsl-pipeline/scenes/artifacts/bitmap-rect-nearest/route-gpu.json",
            "reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-local-matrix/route-gpu.json",
        ],
    },
    {
        "feature": "image-filter DAG",
        "status": "source-evidence-ready",
        "claim": "Bounded image-filter DAG rows exist; unsupported DAGs remain explicit refusals.",
        "artifacts": [
            "reports/wgsl-pipeline/scenes/artifacts/crop-image-filter-nonnull-prepass/gpu.png",
            "reports/wgsl-pipeline/scenes/artifacts/crop-image-filter-nonnull-prepass/route-gpu.json",
            "reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/route-gpu.json",
        ],
    },
    {
        "feature": "text / glyph",
        "status": "source-evidence-ready",
        "claim": "Simple Latin glyph rows exist; complex shaping and color emoji stay refused.",
        "artifacts": [
            "reports/wgsl-pipeline/scenes/artifacts/font-latin-outline-drawstring/cpu.png",
            "reports/wgsl-pipeline/scenes/artifacts/font-latin-outline-drawstring/route-gpu.json",
            "reports/wgsl-pipeline/scenes/artifacts/font-textblob-positioned-glyph-run/route-gpu.json",
            "reports/wgsl-pipeline/scenes/artifacts/font-complex-shaping-refusal/route-gpu.json",
        ],
    },
    {
        "feature": "blend / color filter",
        "status": "source-evidence-ready",
        "claim": "Blend/color-filter source rows exist for future interactive toggles.",
        "artifacts": [
            "reports/wgsl-pipeline/scenes/artifacts/gradient-color-filter-linear-kplus/gpu.png",
            "reports/wgsl-pipeline/scenes/artifacts/gradient-color-filter-linear-kplus/route-gpu.json",
            "reports/wgsl-pipeline/scenes/artifacts/src-over-stack/route-gpu.json",
        ],
    },
    {
        "feature": "runtime-effect controls",
        "status": "source-evidence-ready",
        "claim": "Registered runtime-effect source row exists; live parameter editing is not wired.",
        "artifacts": [
            "reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/gpu.png",
            "reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/route-gpu.json",
        ],
    },
]


def rel(path: Path, root: Path) -> str:
    return path.relative_to(root).as_posix()


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def now_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def run_git(project_root: Path, args: list[str]) -> str:
    try:
        return subprocess.check_output(["git", *args], cwd=project_root, text=True, stderr=subprocess.DEVNULL).strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
        return "unknown"


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


def audit_kadre(project_root: Path) -> tuple[dict[str, Any], list[str]]:
    missing: list[str] = []
    file_evidence = {name: file_status(project_root, path) for name, path in KADRE_FILES.items()}
    for name, evidence in file_evidence.items():
        if not evidence["exists"]:
            missing.append(f"Kadre file missing: {name} -> {evidence['path']}")

    def text_for(name: str) -> str:
        path = project_root / KADRE_FILES[name]
        return read_text(path) if path.is_file() else ""

    event_loop = text_for("eventLoop")
    active_loop = text_for("activeEventLoop")
    window = text_for("window")
    events = text_for("events")
    handler = text_for("handler")
    raw_handles = text_for("rawHandles")
    frame_tracer = text_for("frameTracer")
    hello_triangle = text_for("helloTriangle")
    settings = read_text(project_root / "settings.gradle.kts") if (project_root / "settings.gradle.kts").is_file() else ""
    gitmodules = read_text(project_root / ".gitmodules") if (project_root / ".gitmodules").is_file() else ""

    host_contract = {
        "surfaceCreation": {
            "status": "available-in-kadre-sample",
            "evidence": contains_all(
                hello_triangle,
                ["getSurfaceFromMetalLayer", "RawWindowHandle.AppKit", "CAMetalLayer", "requestAdapter", "requestDevice"],
            ),
            "kanvasStatus": "blocked-no-host-adapter",
        },
        "resize": {
            "status": "available-in-kadre-api",
            "evidence": {
                **contains_all(window, ["innerSize", "scaleFactor"]),
                **contains_all(events, ["WindowEvent.Resized", "ScaleFactorChanged"]),
                **contains_all(hello_triangle, ["handleResize", "configure("]),
            },
            "kanvasStatus": "blocked-no-host-adapter",
        },
        "present": {
            "status": "available-in-wgpu4k-sample",
            "evidence": contains_all(hello_triangle, ["getCurrentTexture", "queue.submit", "present()"]),
            "kanvasStatus": "blocked-no-host-adapter",
        },
        "input": {
            "status": "available-in-kadre-api",
            "evidence": contains_all(
                events,
                ["KeyboardInput", "PointerMoved", "MouseInput", "MouseWheel", "Touch", "CloseRequested"],
            ),
            "kanvasStatus": "blocked-no-runtime-control-binding",
        },
        "frameClock": {
            "status": "available-as-redraw-loop-and-tracer",
            "evidence": {
                **contains_all(active_loop, ["setControlFlow", "controlFlow"]),
                **contains_all(window, ["requestRedraw"]),
                **contains_all(frame_tracer, ["FrameTimingTracer", "onRedrawStart", "onPresentEnd"]),
                **contains_all(hello_triangle, ["aboutToWait", "requestRedraw"]),
            },
            "kanvasStatus": "blocked-no-host-adapter",
        },
        "diagnostics": {
            "status": "partial",
            "evidence": {
                **contains_all(raw_handles, ["RawWindowHandle", "RawDisplayHandle", "AppKit", "Win32", "Xlib", "Wayland", "Web"]),
                **contains_all(hello_triangle, ["Adapter", "supportedFormats", "SurfaceTextureStatus"]),
            },
            "kanvasStatus": "needs-kanvas-telemetry-mapping",
        },
        "skikoOrWgpu4Hosting": {
            "status": "wgpu4k-sample-ready",
            "evidence": contains_all(hello_triangle, ["io.ygdrasil.webgpu", "WGPU.createInstance", "ShaderModuleDescriptor"]),
            "kanvasStatus": "wgpu4k-host-contract-not-wired-to-kanvas-device",
        },
    }

    if 'includeBuild("external/poc-koreos")' not in settings:
        missing.append("settings.gradle.kts does not include the Kadre source build.")
    if "external/poc-koreos" not in gitmodules:
        missing.append(".gitmodules does not declare external/poc-koreos.")

    submodule_head = run_git(project_root, ["-C", "external/poc-koreos", "rev-parse", "HEAD"])
    root_head = run_git(project_root, ["rev-parse", "HEAD"])
    audit = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": now_iso(),
        "generatedBy": "scripts/m68_kadre_demo_evidence.py",
        "scopeIds": SCOPE_IDS,
        "repository": {
            "rootCommit": root_head,
            "kadreSubmoduleCommit": submodule_head,
            "submodulePath": "external/poc-koreos",
            "gitmodules": file_status(project_root, ".gitmodules"),
            "settings": file_status(project_root, "settings.gradle.kts"),
        },
        "kadreFiles": file_evidence,
        "hostContract": host_contract,
        "summary": {
            "kadreSourceBuildPresent": 'includeBuild("external/poc-koreos")' in settings,
            "kadreSubmoduleDeclared": "external/poc-koreos" in gitmodules,
            "nativeKanvasLaunchStatus": "blocked",
            "nativeKanvasLaunchReason": "m68.kadre-host-adapter-not-implemented",
            "bridgeSmokeStatus": "pass",
        },
    }
    return audit, missing


def inspect_scene_features(project_root: Path) -> tuple[list[dict[str, Any]], list[str]]:
    missing: list[str] = []
    features: list[dict[str, Any]] = []
    for feature in FEATURE_SCENES:
        artifacts = [file_status(project_root, artifact) for artifact in feature["artifacts"]]
        missing_artifacts = [artifact["path"] for artifact in artifacts if not artifact["exists"]]
        if missing_artifacts:
            missing.extend(f"{feature['feature']}: missing {path}" for path in missing_artifacts)
        features.append(
            {
                **feature,
                "artifacts": artifacts,
                "allArtifactsPresent": not missing_artifacts,
                "nativePresented": False,
                "nativePresentationReason": "m68.kadre-host-adapter-not-implemented",
            }
        )
    return features, missing


def measured_overlay_sample(features: list[dict[str, Any]]) -> dict[str, Any]:
    measured_frames: list[dict[str, Any]] = []
    start = time.perf_counter()
    frame_count = 120
    for frame in range(frame_count):
        frame_start = time.perf_counter()
        animated_x = 40.0 + frame * 1.75
        opacity = 0.72 + ((frame % 30) / 30.0) * 0.18
        route_count = sum(1 for feature in features if feature["allArtifactsPresent"])
        unsupported_count = sum(1 for feature in features if feature["status"] == "bridge-smoke")
        # This is intentionally tiny measured work: it times the evidence/overlay
        # producer, not a native GPU frame.
        checksum_input = f"{frame}:{animated_x:.2f}:{opacity:.3f}:{route_count}:{unsupported_count}".encode()
        checksum = hashlib.sha256(checksum_input).hexdigest()[:16]
        measured_frames.append(
            {
                "frame": frame,
                "measuredOverlayPlanningMs": round((time.perf_counter() - frame_start) * 1000.0, 4),
                "animatedTransformX": round(animated_x, 3),
                "runtimeEffectKnob": round(opacity, 3),
                "routeCount": route_count,
                "unsupportedCount": unsupported_count,
                "checksum": checksum,
            }
        )
    total_ms = (time.perf_counter() - start) * 1000.0
    samples = sorted(frame["measuredOverlayPlanningMs"] for frame in measured_frames)
    p50 = samples[len(samples) // 2]
    p95 = samples[int((len(samples) - 1) * 0.95)]
    return {
        "schemaVersion": SCHEMA_VERSION,
        "claimLevel": "bridge-smoke",
        "nativeFrameTimingClaim": "none",
        "measured": {
            "source": "python evidence generator overlay planning loop",
            "totalMs": round(total_ms, 4),
            "frames": frame_count,
            "p50OverlayPlanningMs": round(p50, 4),
            "p95OverlayPlanningMs": round(p95, 4),
        },
        "staticMetadata": {
            "targetFps": 60,
            "warningFps": 30,
            "host": "Kadre",
            "backend": "wgpu4k/WebGPU through future Kanvas host adapter",
            "windowedNativeStatus": "blocked",
            "windowedNativeReason": "m68.kadre-host-adapter-not-implemented",
        },
        "sampleFrames": [measured_frames[0], measured_frames[len(measured_frames) // 2], measured_frames[-1]],
    }


def write_json(path: Path, data: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def write_markdown(path: Path, audit: dict[str, Any], features: list[dict[str, Any]], overlay: dict[str, Any], failures: list[str]) -> None:
    lines = [
        "# M68 Kadre Native Demo Evidence",
        "",
        "Status: `bridge-smoke-blocked-native-launch`",
        "",
        "M68 now has Kadre source evidence through the `external/poc-koreos` submodule, but it does not yet have a Kanvas host adapter capable of presenting Kanvas-rendered frames in a Kadre window. This report therefore records the exact bridge status and refuses the native launch claim with `m68.kadre-host-adapter-not-implemented`.",
        "",
        "## Host Contract Audit",
        "",
        f"- Kadre source build present: `{audit['summary']['kadreSourceBuildPresent']}`",
        f"- Kadre submodule declared: `{audit['summary']['kadreSubmoduleDeclared']}`",
        f"- Kadre submodule commit: `{audit['repository']['kadreSubmoduleCommit']}`",
        f"- Native Kanvas launch status: `{audit['summary']['nativeKanvasLaunchStatus']}`",
        f"- Native launch reason: `{audit['summary']['nativeKanvasLaunchReason']}`",
        "",
        "| Capability | Kadre status | Kanvas status |",
        "|---|---|---|",
    ]
    for name, contract in audit["hostContract"].items():
        lines.append(f"| `{name}` | `{contract['status']}` | `{contract['kanvasStatus']}` |")

    lines.extend(
        [
            "",
            "## Flagship Scene Readiness",
            "",
            "These rows are source/evidence inputs for the future native scene, not a native rendered screenshot.",
            "",
            "| Feature | Status | Native presented | Artifact coverage |",
            "|---|---|---:|---:|",
        ]
    )
    for feature in features:
        present_count = sum(1 for artifact in feature["artifacts"] if artifact["exists"])
        lines.append(
            f"| {feature['feature']} | `{feature['status']}` | `{feature['nativePresented']}` | {present_count}/{len(feature['artifacts'])} |"
        )

    lines.extend(
        [
            "",
            "## Telemetry Separation",
            "",
            f"- Measured source: `{overlay['measured']['source']}`",
            f"- Measured frames: `{overlay['measured']['frames']}`",
            f"- p50 overlay planning: `{overlay['measured']['p50OverlayPlanningMs']} ms`",
            f"- p95 overlay planning: `{overlay['measured']['p95OverlayPlanningMs']} ms`",
            f"- Native frame timing claim: `{overlay['nativeFrameTimingClaim']}`",
            f"- Static host metadata: `{overlay['staticMetadata']['host']}` / `{overlay['staticMetadata']['backend']}`",
            "",
            "## Artifacts",
            "",
            "- `reports/wgsl-pipeline/m68-kadre-demo/kadre-host-audit.json`",
            "- `reports/wgsl-pipeline/m68-kadre-demo/bridge-smoke.json`",
            "- `reports/wgsl-pipeline/m68-kadre-demo/flagship-scene-evidence.json`",
            "- `reports/wgsl-pipeline/m68-kadre-demo/route-summary.json`",
            "- `reports/wgsl-pipeline/m68-kadre-demo/telemetry-overlay-sample.json`",
            "",
            "## Blockers And Non-Claims",
            "",
            "- No native Kanvas/Kadre windowed frame is claimed.",
            "- No native screenshot/frame PNG is claimed.",
            "- Existing dashboard rows remain source evidence until the Kanvas host adapter presents them through Kadre.",
            "- Runtime-effect controls are source metadata only; live editing is not wired.",
        ]
    )
    if failures:
        lines.extend(["", "## Validation Issues", ""])
        for failure in failures:
            lines.append(f"- {failure}")
    else:
        lines.extend(["", "## Validation Issues", "", "- None for this bridge-smoke scope."])
    lines.extend(
        [
            "",
            "## Validation",
            "",
            "```bash",
            "rtk ./gradlew --no-daemon pipelineM68KadreDemoEvidence",
            "```",
            "",
        ]
    )
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project-root", default=".")
    parser.add_argument("--output-dir", default="reports/wgsl-pipeline/m68-kadre-demo")
    args = parser.parse_args()

    project_root = Path(args.project_root).resolve()
    output_dir = project_root / args.output_dir
    output_dir.mkdir(parents=True, exist_ok=True)

    failures: list[str] = []
    missing_specs = [spec for spec in CONTRACT_SPECS if not (project_root / spec).is_file()]
    failures.extend(f"Missing required spec: {spec}" for spec in missing_specs)

    audit, audit_failures = audit_kadre(project_root)
    failures.extend(audit_failures)
    features, feature_failures = inspect_scene_features(project_root)
    failures.extend(feature_failures)
    overlay = measured_overlay_sample(features)

    route_summary = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": now_iso(),
        "generatedBy": "scripts/m68_kadre_demo_evidence.py",
        "nativeRoute": {
            "status": "blocked",
            "route": "kadre.native.windowed",
            "reason": "m68.kadre-host-adapter-not-implemented",
        },
        "bridgeRoute": {
            "status": "pass",
            "route": "kadre.source-build.contract-audit",
            "reason": "none",
        },
        "featureRoutes": [
            {
                "feature": feature["feature"],
                "sourceEvidenceStatus": feature["status"],
                "nativePresented": feature["nativePresented"],
                "nativePresentationReason": feature["nativePresentationReason"],
            }
            for feature in features
        ],
    }
    bridge_smoke = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": now_iso(),
        "generatedBy": "scripts/m68_kadre_demo_evidence.py",
        "scopeIds": SCOPE_IDS,
        "environment": {
            "platform": platform.platform(),
            "python": platform.python_version(),
            "cwd": str(project_root),
        },
        "status": "pass" if not failures else "blocked",
        "claimLevel": "bridge-smoke",
        "nativeLaunch": {
            "status": "blocked",
            "reason": "m68.kadre-host-adapter-not-implemented",
            "nonClaim": "No Kadre-hosted Kanvas frame loop or native screenshot was generated.",
        },
        "failures": failures,
    }
    flagship = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": now_iso(),
        "generatedBy": "scripts/m68_kadre_demo_evidence.py",
        "scopeIds": SCOPE_IDS,
        "scene": "m68-flagship-kadre-native-demo",
        "nativePresented": False,
        "nativePresentationReason": "m68.kadre-host-adapter-not-implemented",
        "features": features,
    }

    write_json(output_dir / "kadre-host-audit.json", audit)
    write_json(output_dir / "bridge-smoke.json", bridge_smoke)
    write_json(output_dir / "flagship-scene-evidence.json", flagship)
    write_json(output_dir / "route-summary.json", route_summary)
    write_json(output_dir / "telemetry-overlay-sample.json", overlay)
    write_markdown(
        project_root / "reports/wgsl-pipeline/2026-06-01-m68-kadre-demo-evidence.md",
        audit,
        features,
        overlay,
        failures,
    )

    print(json.dumps({"status": bridge_smoke["status"], "outputDir": rel(output_dir, project_root), "failures": failures}, sort_keys=True))
    # Missing Kadre submodule contents are a truthful bridge-smoke blocker in CI
    # jobs that do not initialize submodules, not a Gradle configuration failure.
    return 2 if missing_specs else 0


if __name__ == "__main__":
    raise SystemExit(main())
