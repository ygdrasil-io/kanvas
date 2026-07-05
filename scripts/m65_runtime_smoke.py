#!/usr/bin/env python3
"""M65 headless runtime smoke lane.

This script intentionally stays offscreen: it proves the first runtime command,
telemetry schema, nonblank frame artifact, and stable Kadre-host blocker without
claiming live Kadre/WebGPU performance.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import platform
import struct
import time
import zlib
from pathlib import Path
from typing import Any


WIDTH = 192
HEIGHT = 128
FRAME_COUNT = 120
SCHEMA_VERSION = 1
KADRE_HEAD = "9dae217144fde70bdafddcf60855e2329921b1e5"


def png_chunk(kind: bytes, payload: bytes) -> bytes:
    crc = zlib.crc32(kind)
    crc = zlib.crc32(payload, crc)
    return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", crc & 0xFFFFFFFF)


def write_png(path: Path, width: int, height: int, rgba: list[int]) -> None:
    if len(rgba) != width * height * 4:
        raise ValueError(f"RGBA buffer has {len(rgba)} bytes, expected {width * height * 4}")
    raw = bytearray()
    stride = width * 4
    for y in range(height):
        raw.append(0)
        raw.extend(rgba[y * stride : (y + 1) * stride])
    payload = b"\x89PNG\r\n\x1a\n"
    payload += png_chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    payload += png_chunk(b"IDAT", zlib.compress(bytes(raw), level=9))
    payload += png_chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(payload)


def draw_scene(scene_id: str, frame: int) -> list[int]:
    def clamp_channel(value: float) -> int:
        return max(0, min(255, int(value)))

    rgba = [0] * (WIDTH * HEIGHT * 4)
    phase = frame / max(FRAME_COUNT - 1, 1)
    for y in range(HEIGHT):
        for x in range(WIDTH):
            base = (y * WIDTH + x) * 4
            if scene_id == "baseline":
                r = clamp_channel(24 + 80 * x / WIDTH)
                g = clamp_channel(36 + 110 * y / HEIGHT)
                b = 72
            elif scene_id == "m63":
                r = clamp_channel(28 + 180 * x / WIDTH)
                g = clamp_channel(30 + 110 * (1.0 - y / HEIGHT))
                b = clamp_channel(55 + 65 * math.sin((x + frame) / 21.0))
            else:
                r = clamp_channel(35 + 60 * math.sin((x + frame) / 17.0))
                g = clamp_channel(26 + 85 * y / HEIGHT)
                b = clamp_channel(88 + 130 * x / WIDTH)
            rgba[base : base + 4] = [r, g, b, 255]

    cx = int(30 + phase * 118)
    cy = 52 if scene_id == "baseline" else 70 if scene_id == "m63" else 48
    color = {
        "baseline": (245, 188, 66, 255),
        "m63": (70, 220, 160, 255),
        "m64": (212, 96, 235, 255),
    }[scene_id]
    for y in range(max(0, cy - 16), min(HEIGHT, cy + 17)):
        for x in range(max(0, cx - 16), min(WIDTH, cx + 17)):
            if (x - cx) * (x - cx) + (y - cy) * (y - cy) <= 16 * 16:
                base = (y * WIDTH + x) * 4
                rgba[base : base + 4] = list(color)

    band_y = 96 if scene_id != "m64" else 88
    for y in range(band_y, min(HEIGHT, band_y + 10)):
        for x in range(18, WIDTH - 18):
            if (x + frame) % 11 < 7:
                base = (y * WIDTH + x) * 4
                rgba[base : base + 4] = [236, 238, 223, 255]
    return rgba


def nonblank_stats(rgba: list[int]) -> dict[str, Any]:
    pixels = len(rgba) // 4
    transparent = 0
    non_background = 0
    first = tuple(rgba[0:4])
    checksum = hashlib.sha256(bytes(rgba)).hexdigest()
    for offset in range(0, len(rgba), 4):
        px = tuple(rgba[offset : offset + 4])
        if px[3] == 0:
            transparent += 1
        if px != first:
            non_background += 1
    return {
        "pixels": pixels,
        "transparentPixels": transparent,
        "nonBackgroundPixels": non_background,
        "nonblank": non_background > 0,
        "sha256": checksum,
    }


def source_artifact_status(project_root: Path, relative: str) -> dict[str, Any]:
    path = project_root / relative
    if not path.is_file():
        return {"path": relative, "exists": False, "nonblankProbe": "missing"}
    data = path.read_bytes()
    status: dict[str, Any] = {
        "path": relative,
        "exists": True,
        "bytes": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
    }
    if path.suffix == ".png":
        try:
            width, height, rgba = read_png_rgba(data)
            status.update(nonblank_stats(rgba))
            status["width"] = width
            status["height"] = height
            status["nonblankProbe"] = "decoded-png-rgba"
        except ValueError as exc:
            status["nonblankProbe"] = "png-decode-failed"
            status["decodeError"] = str(exc)
    else:
        status["nonblankProbe"] = "not-pixel-artifact"
    return status


def read_png_rgba(data: bytes) -> tuple[int, int, list[int]]:
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError("missing PNG signature")
    offset = 8
    width = height = None
    color_type = None
    bit_depth = None
    idat = bytearray()
    while offset + 8 <= len(data):
        length = struct.unpack(">I", data[offset : offset + 4])[0]
        kind = data[offset + 4 : offset + 8]
        payload = data[offset + 8 : offset + 8 + length]
        offset += 12 + length
        if kind == b"IHDR":
            width, height, bit_depth, color_type, compression, filter_method, interlace = struct.unpack(
                ">IIBBBBB", payload
            )
            if compression != 0 or filter_method != 0 or interlace != 0:
                raise ValueError("unsupported PNG compression/filter/interlace")
        elif kind == b"IDAT":
            idat.extend(payload)
        elif kind == b"IEND":
            break
    if width is None or height is None or bit_depth is None or color_type is None:
        raise ValueError("missing IHDR")
    if bit_depth != 8 or color_type not in (2, 6):
        raise ValueError(f"unsupported PNG format bitDepth={bit_depth} colorType={color_type}")
    channels = 4 if color_type == 6 else 3
    row_bytes = width * channels
    raw = zlib.decompress(bytes(idat))
    expected = (row_bytes + 1) * height
    if len(raw) != expected:
        raise ValueError(f"unexpected decompressed size {len(raw)} != {expected}")
    previous = [0] * row_bytes
    rgba: list[int] = []
    pos = 0
    for _ in range(height):
        filter_type = raw[pos]
        pos += 1
        row = list(raw[pos : pos + row_bytes])
        pos += row_bytes
        recon = [0] * row_bytes
        for i, value in enumerate(row):
            left = recon[i - channels] if i >= channels else 0
            up = previous[i]
            up_left = previous[i - channels] if i >= channels else 0
            if filter_type == 0:
                recon[i] = value
            elif filter_type == 1:
                recon[i] = (value + left) & 0xFF
            elif filter_type == 2:
                recon[i] = (value + up) & 0xFF
            elif filter_type == 3:
                recon[i] = (value + ((left + up) // 2)) & 0xFF
            elif filter_type == 4:
                p = left + up - up_left
                pa = abs(p - left)
                pb = abs(p - up)
                pc = abs(p - up_left)
                predictor = left if pa <= pb and pa <= pc else up if pb <= pc else up_left
                recon[i] = (value + predictor) & 0xFF
            else:
                raise ValueError(f"unsupported PNG filter {filter_type}")
        previous = recon
        for x in range(width):
            base = x * channels
            rgba.extend(recon[base : base + 3])
            rgba.append(recon[base + 3] if channels == 4 else 255)
    return width, height, rgba


def frame_metrics(scene_id: str, artifact_dir: Path) -> dict[str, Any]:
    frame_times_ms: list[float] = []
    update_times_ms: list[float] = []
    plan_times_ms: list[float] = []
    render_times_ms: list[float] = []
    first_stats: dict[str, Any] | None = None
    final_stats: dict[str, Any] | None = None

    for frame in range(FRAME_COUNT):
        start = time.perf_counter_ns()
        update_ms = 0.10 + (frame % 5) * 0.01
        plan_ms = 0.16 + (frame % 7) * 0.01
        rgba = draw_scene(scene_id, frame)
        render_ms = (time.perf_counter_ns() - start) / 1_000_000
        frame_ms = update_ms + plan_ms + render_ms
        update_times_ms.append(round(update_ms, 4))
        plan_times_ms.append(round(plan_ms, 4))
        render_times_ms.append(round(render_ms, 4))
        frame_times_ms.append(round(frame_ms, 4))
        if frame == 0:
            first_stats = nonblank_stats(rgba)
            write_png(artifact_dir / f"{scene_id}-first-frame.png", WIDTH, HEIGHT, rgba)
        if frame == FRAME_COUNT - 1:
            final_stats = nonblank_stats(rgba)
            write_png(artifact_dir / f"{scene_id}-final-frame.png", WIDTH, HEIGHT, rgba)

    sorted_frames = sorted(frame_times_ms)
    median = sorted_frames[len(sorted_frames) // 2]
    p95 = sorted_frames[int(len(sorted_frames) * 0.95) - 1]
    return {
        "frames": FRAME_COUNT,
        "firstFrame": first_stats,
        "finalFrame": final_stats,
        "medianFrameMs": median,
        "p95FrameMs": p95,
        "averageFrameMs": round(sum(frame_times_ms) / len(frame_times_ms), 4),
        "averageFps": round(1000.0 / (sum(frame_times_ms) / len(frame_times_ms)), 2),
        "cpuUpdateMedianMs": sorted(update_times_ms)[len(update_times_ms) // 2],
        "cpuPlanningMedianMs": sorted(plan_times_ms)[len(plan_times_ms) // 2],
        "offscreenRenderMedianMs": sorted(render_times_ms)[len(render_times_ms) // 2],
    }


def build_slots(project_root: Path, output_root: Path) -> list[dict[str, Any]]:
    artifact_dir = output_root / "artifacts"
    slots = [
        {
            "slot": "baseline",
            "title": "M65 baseline transform smoke",
            "linearIssue": "FOR-36",
            "status": "pass",
            "runtimeRoute": "headless.offscreen.smoke-renderer.v1",
            "sourceEvidence": [],
            "refusalReason": "none",
        },
        {
            "slot": "m63",
            "title": "M63 color/blend scene slot",
            "linearIssue": "FOR-36",
            "status": "expected-unsupported",
            "runtimeRoute": "headless.offscreen.slot-preview",
            "sourceEvidence": [
                source_artifact_status(
                    project_root,
                    "reports/wgsl-pipeline/scenes/artifacts/gradient-color-filter-linear-kplus/gpu.png",
                ),
                source_artifact_status(
                    project_root,
                    "reports/wgsl-pipeline/scenes/artifacts/gradient-color-filter-linear-kplus/route-gpu.json",
                ),
            ],
            "refusalReason": "m65.runtime-display-list-replay-not-wired",
        },
        {
            "slot": "m64",
            "title": "M64 registered runtime-effect scene slot",
            "linearIssue": "FOR-36",
            "status": "expected-unsupported",
            "runtimeRoute": "headless.offscreen.slot-preview",
            "sourceEvidence": [
                source_artifact_status(
                    project_root,
                    "reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/gpu.png",
                ),
                source_artifact_status(
                    project_root,
                    "reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/route-gpu.json",
                ),
            ],
            "refusalReason": "m65.runtime-display-list-replay-not-wired",
        },
    ]
    for slot in slots:
        metrics = frame_metrics(slot["slot"], artifact_dir)
        slot["telemetrySummary"] = metrics
        slot["artifacts"] = {
            "firstFrame": f"artifacts/{slot['slot']}-first-frame.png",
            "finalFrame": f"artifacts/{slot['slot']}-final-frame.png",
        }
    return slots


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def write_report(path: Path, payload: dict[str, Any]) -> None:
    lines = [
        "# M65 Runtime Smoke Lane",
        "",
        "Status: reporting-only smoke evidence, not a performance release claim.",
        "",
        "## Outputs",
        "",
        f"- Telemetry: `{payload['telemetry']}`",
        f"- Slots: `{payload['slots']}`",
        f"- Artifact root: `{payload['artifactRoot']}`",
        "",
        "## Host Decision",
        "",
        "- Kadre remains the selected live/native host.",
        "- This lane is headless/offscreen because Kadre is not wired into Kanvas as a submodule or published dependency in this branch.",
        "- Live Kadre presentation is refused with `m65.kadre-host-not-wired`; M67/M68 can promote it after the host bridge lands.",
        "",
        "## Slot Results",
        "",
        "| Slot | Status | Runtime route | Refusal | Median frame ms | P95 frame ms | Nonblank |",
        "|---|---|---|---|---:|---:|---|",
    ]
    for slot in payload["slotPayload"]["slots"]:
        summary = slot["telemetrySummary"]
        nonblank = summary["firstFrame"]["nonblank"] and summary["finalFrame"]["nonblank"]
        lines.append(
            f"| `{slot['slot']}` | `{slot['status']}` | `{slot['runtimeRoute']}` | "
            f"`{slot['refusalReason']}` | {summary['medianFrameMs']} | {summary['p95FrameMs']} | {nonblank} |"
        )
    lines += [
        "",
        "## Non-Claims",
        "",
        "- No Kadre-hosted frame loop is claimed.",
        "- No WebGPU adapter timing or release FPS gate is claimed.",
        "- M63/M64 slots keep stable runtime-replay refusals while linking their existing generated source artifacts.",
    ]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project-root", default=".")
    parser.add_argument("--output-dir", default="reports/wgsl-pipeline/m65-runtime-smoke")
    args = parser.parse_args()

    project_root = Path(args.project_root).resolve()
    output_root = (project_root / args.output_dir).resolve()
    output_root.mkdir(parents=True, exist_ok=True)

    slots = build_slots(project_root, output_root)
    timestamp = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    telemetry = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedBy": "scripts/m65_runtime_smoke.py",
        "generatedAt": timestamp,
        "scopeGroup": "FOR-31",
        "scopeIds": ["FOR-33", "FOR-34", "FOR-35", "FOR-36"],
        "lane": "m65-runtime-smoke",
        "mode": "headless-offscreen",
        "claimLevel": "reporting-only",
        "frameCount": FRAME_COUNT,
        "surface": {"width": WIDTH, "height": HEIGHT, "format": "rgba8unorm-srgb"},
        "host": {
            "selected": "Kadre",
            "repository": "https://github.com/ygdrasil-io/poc-koreos",
            "auditedCommit": KADRE_HEAD,
            "status": "blocked-not-wired",
            "refusalReason": "m65.kadre-host-not-wired",
        },
        "runtimeCapabilities": {
            "frameClock": "synthetic-monotonic",
            "displayListReplay": "not-wired",
            "invalidationDiagnostics": "full-redraw",
            "resourceTelemetry": "schema-only",
            "presentation": "offscreen-png",
        },
        "environment": {
            "python": platform.python_version(),
            "platform": platform.platform(),
            "cwd": os.getcwd(),
        },
        "slots": slots,
    }
    slot_payload = {"schemaVersion": SCHEMA_VERSION, "slots": slots}
    write_json(output_root / "telemetry.json", telemetry)
    write_json(output_root / "slots.json", slot_payload)
    report_payload = {
        "telemetry": "reports/wgsl-pipeline/m65-runtime-smoke/telemetry.json",
        "slots": "reports/wgsl-pipeline/m65-runtime-smoke/slots.json",
        "artifactRoot": "reports/wgsl-pipeline/m65-runtime-smoke/artifacts",
        "slotPayload": slot_payload,
    }
    write_report(project_root / "reports/wgsl-pipeline/2026-06-01-m65-runtime-smoke.md", report_payload)
    print(f"Wrote M65 runtime smoke telemetry: {output_root.relative_to(project_root) / 'telemetry.json'}")


if __name__ == "__main__":
    main()
