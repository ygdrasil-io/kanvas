#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import Any


DEFAULT_OUTPUT_DIR = "reports/wgsl-pipeline/codec-provenance-matrix"
OUTPUT_JSON = "kan-047-codec-provenance-matrix.json"
OUTPUT_MARKDOWN = "kan-047-codec-provenance-matrix.md"

SUPPORTED_CODECS = "SUPPORTED_CODECS.md"
FIXTURE_PROVENANCE = "codec-real-image-tests/FIXTURES.md"
TARGET_RENDERER = ".upstream/target/skia-like-realtime-renderer-target.md"
SPEC_RENDERING_FEATURE = ".upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md"

KAN046_JSON = "reports/wgsl-pipeline/tile-modes-mipmap-boundary/kan-046-tile-modes-mipmap-boundary.json"
M79_JSON = "reports/wgsl-pipeline/m79-bitmap-replay/evidence.json"
KAN014_ROOT = "reports/wgsl-pipeline/scenes/artifacts/kan-014-bitmap-rect"
D54_ROOT = "reports/wgsl-pipeline/scenes/artifacts/d54-skia-gm-image"

STABLE_CODEC_REASONS = {
    "codec.decoder-unavailable",
    "codec.color-profile-unsupported",
    "codec.animated-frame-unsupported",
    "image.imagegm.surface-snapshot-drawimage-webgpu-artifacts-required",
}


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"KAN-047 codec provenance matrix validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def load_json(root: Path, relative_path: str) -> Any:
    path = root / relative_path
    require(path.is_file(), f"missing JSON file: {relative_path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {relative_path}: {exc}")


def require_text(root: Path, relative_path: str, snippets: list[str]) -> None:
    path = root / relative_path
    require(path.is_file(), f"missing source file: {relative_path}")
    text = path.read_text(encoding="utf-8")
    flattened = " ".join(text.split())
    for snippet in snippets:
        require(
            snippet in text or " ".join(snippet.split()) in flattened,
            f"{relative_path} missing required snippet: {snippet}",
        )


def row_by_id(rows: list[Any], row_id: str, source: str) -> dict[str, Any]:
    row = next((item for item in rows if isinstance(item, dict) and item.get("rowId") == row_id), None)
    require(isinstance(row, dict), f"{source} row missing: {row_id}")
    return row


def source_audit(root: Path) -> dict[str, Any]:
    require_text(
        root,
        SUPPORTED_CODECS,
        [
            "| PNG | Supported through `codec-png-kotlin`",
            "| JPEG | Supported through `codec-jpeg-kotlin`",
            "| GIF | Supported through `codec-gif-kotlin`",
            "| BMP | Supported through `codec-bmp-kotlin`",
            "| WBMP | Supported through `codec-wbmp-kotlin`",
            "| ICO / CUR | Supported through `codec-ico-kotlin`",
            "| WebP | Supported through `codec-webp-kotlin`",
            "| AVIF / JPEG XL / RAW / video | Not supported in the portable pure Kotlin runtime.",
            "must not silently fall back to AWT/ImageIO/JNI",
        ],
    )
    require_text(
        root,
        FIXTURE_PROVENANCE,
        [
            "## Fixture Index",
            "`codec-real-images/png/mandrill_64.png`",
            "`codec-real-images/jpeg/dog.jpg`",
            "`codec-real-images/gif/test640x479.gif`",
            "`codec-real-images/webp/vp8l_lossless_2x1.webp`",
            "`codec-real-images/ico/embedded_png.ico`",
        ],
    )
    require_text(
        root,
        TARGET_RENDERER,
        [
            "Font and codec work must use real dependencies or real implementations; do",
            "not add substitutes just to clear old backlog rows.",
        ],
    )
    require_text(
        root,
        SPEC_RENDERING_FEATURE,
        [
            "broad image, texture, codec, mipmap, perspective, or color-managed decode support",
        ],
    )
    require_text(
        root,
        "cpu-raster/src/main/kotlin/org/skia/tools/ToolUtils.kt",
        [
            "SkCodec.MakeFromData",
            "GetResourceAsImage",
        ],
    )
    require_text(
        root,
        "skia-integration-tests/src/main/kotlin/org/skia/tests/BitmapSubsetShaderGM.kt",
        [
            "images/color_wheel.png",
            "ToolUtils.GetResourceAsImage",
        ],
    )
    require_text(
        root,
        "skia-integration-tests/src/main/kotlin/org/skia/tests/AnimatedImageGM.kt",
        [
            "images/stoplight_h.webp",
            "images/flightAnim.gif",
            "STUB.ANIMATED_IMAGE",
        ],
    )
    require_text(root, "codec-extended/src/main/kotlin/org/skia/codec/SkAvifDecoder.kt", ["Always returns `null`"])
    require_text(root, "codec-extended/src/main/kotlin/org/skia/codec/SkJpegxlDecoder.kt", ["Always returns `null`"])
    require_text(root, "codec-extended/src/main/kotlin/org/skia/codec/SkRawDecoder.kt", ["Always returns `null`"])
    require_text(root, "codec-extended/src/main/kotlin/org/skia/codec/SkVideoDecoder.kt", ["STUB.FFMPEG"])
    return {
        "supportedCodecs": SUPPORTED_CODECS,
        "fixtureProvenance": FIXTURE_PROVENANCE,
        "targetRenderer": TARGET_RENDERER,
        "renderingFeatureSpec": SPEC_RENDERING_FEATURE,
    }


def common_non_claims(extra: list[str] | None = None) -> list[str]:
    claims = [
        "no-broad-codec-support-claim",
        "no-arbitrary-texture-claim",
        "no-color-managed-decode-claim",
        "no-native-jni-bridge-claim",
        "no-animated-renderer-support-claim",
    ]
    if extra:
        claims.extend(extra)
    return claims


def merge_non_claims(*claim_lists: list[str]) -> list[str]:
    merged: list[str] = []
    for claims in claim_lists:
        for claim in claims:
            if claim not in merged:
                merged.append(claim)
    return merged


def kan014_fixture_row(root: Path) -> dict[str, Any]:
    route_cpu = load_json(root, f"{KAN014_ROOT}/route-cpu.json")
    route_gpu = load_json(root, f"{KAN014_ROOT}/route-webgpu.json")
    stats = load_json(root, f"{KAN014_ROOT}/stats.json")
    require(route_gpu.get("fixtureBacked") is True, "KAN-014 must remain fixture-backed")
    require(route_gpu.get("fallbackReason") == "none", "KAN-014 WebGPU fallback must remain none")
    return {
        "rowId": route_gpu.get("sceneId"),
        "pmCategory": "deterministic-fixture-scene",
        "status": route_gpu.get("status"),
        "format": "raw-rgba8888-fixture",
        "decoder": {
            "name": "none",
            "kind": "deterministic-fixture",
            "module": None,
        },
        "colorInfo": {
            "colorType": "RGBA_8888",
            "colorSpace": "sRGB",
            "alphaType": "unpremul",
            "policy": route_gpu.get("colorSpacePolicy"),
        },
        "origin": {
            "kind": "in-repo-deterministic-fixture",
            "fixtureId": route_gpu.get("fixtureId"),
            "fixtureSha256": route_gpu.get("fixtureSha256"),
            "sourceEvidence": f"{KAN014_ROOT}/route-webgpu.json",
        },
        "decodeResult": {
            "status": "fixture-no-codec-decode",
            "result": "not-applicable",
        },
        "supportClaim": route_gpu.get("supportScope"),
        "reasonCode": "none",
        "route": {
            "cpu": route_cpu.get("selectedRoute"),
            "gpu": route_gpu.get("selectedRoute"),
            "fallbackReason": route_gpu.get("fallbackReason"),
            "pipelineKey": route_gpu.get("pipelineKey"),
        },
        "stats": {
            "matchingPixels": stats.get("cpuComparison", {}).get("matchingPixels"),
            "gpuMatchingPixels": stats.get("webGpuComparison", {}).get("matchingPixels"),
            "globalThresholdChanged": stats.get("globalThresholdChanged"),
        },
        "artifacts": {
            "reference": f"{KAN014_ROOT}/reference.png",
            "cpu": f"{KAN014_ROOT}/cpu.png",
            "gpu": f"{KAN014_ROOT}/webgpu.png",
            "stats": f"{KAN014_ROOT}/stats.json",
            "routeCpu": f"{KAN014_ROOT}/route-cpu.json",
            "routeGpu": f"{KAN014_ROOT}/route-webgpu.json",
        },
        "nonClaims": merge_non_claims(list(route_gpu.get("nonClaims", [])), common_non_claims()),
    }


def kan046_scene_rows(root: Path) -> list[dict[str, Any]]:
    payload = load_json(root, KAN046_JSON)
    rows = payload.get("samplingRows")
    require(isinstance(rows, list), "KAN-046 samplingRows missing")
    repeat = row_by_id(rows, "bitmap-shader-repeat-tile", "KAN-046")
    subset = row_by_id(rows, "bitmap-subset-local-matrix-repeat", "KAN-046")
    require(repeat.get("status") == "pass", "repeat tile row must remain pass")
    require(subset.get("status") == "pass", "subset local matrix row must remain pass")
    return [
        {
            "rowId": "bitmap-shader-repeat-tile",
            "pmCategory": "deterministic-fixture-scene",
            "status": "pass",
            "format": "raw-rgba8-programmatic-image",
            "decoder": {
                "name": "none",
                "kind": "deterministic-fixture",
                "module": None,
            },
            "colorInfo": {
                "colorType": "RGBA_8888",
                "colorSpace": "sRGB",
                "alphaType": "opaque",
                "policy": "in-test quadrant image, unmanaged sRGB byte oracle",
            },
            "origin": {
                "kind": "in-test-programmatic-image",
                "sourceEvidence": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/BitmapShaderPaintRectTest.kt#makeQuadrantImage",
            },
            "decodeResult": {
                "status": "fixture-no-codec-decode",
                "result": "not-applicable",
            },
            "supportClaim": "bounded-bitmap-sampling-only",
            "reasonCode": "none",
            "route": repeat.get("route"),
            "stats": repeat.get("stats"),
            "artifacts": repeat.get("artifacts"),
            "nonClaims": merge_non_claims(list(repeat.get("nonClaims", [])), common_non_claims(["no-codec-decode-claim"])),
        },
        {
            "rowId": "bitmap-subset-local-matrix-repeat",
            "pmCategory": "real-codec-decoded-scene-source",
            "status": "pass",
            "format": "PNG",
            "decoder": {
                "name": "png",
                "kind": "portable-codec",
                "module": "codec-png-kotlin",
                "dispatch": "ToolUtils.GetResourceAsImage -> SkCodec.MakeFromData -> SkCodec.getImage",
            },
            "colorInfo": {
                "colorType": "codec-natural RGBA_8888",
                "colorSpace": "codec reported color space, sampled as selected bitmap shader source",
                "alphaType": "codec natural alpha",
                "policy": "codec decode is source material only; KAN-046 support claim remains bitmap sampling, not broad codec or color-managed decode",
            },
            "origin": {
                "kind": "skia-resource-encoded-image",
                "resource": "images/color_wheel.png",
                "sourceEvidence": "skia-integration-tests/src/main/kotlin/org/skia/tests/BitmapSubsetShaderGM.kt",
            },
            "decodeResult": {
                "status": "kSuccess",
                "result": "ToolUtils.GetResourceAsImage returns decoded SkImage before subset copy",
            },
            "supportClaim": "bounded-bitmap-sampling-only",
            "reasonCode": "none",
            "route": subset.get("route"),
            "stats": subset.get("stats"),
            "artifacts": subset.get("artifacts"),
            "nonClaims": merge_non_claims(list(subset.get("nonClaims", [])), common_non_claims()),
        },
    ]


def d54_surface_snapshot_row(root: Path) -> dict[str, Any]:
    route_cpu = load_json(root, f"{D54_ROOT}/route-cpu.json")
    route_gpu = load_json(root, f"{D54_ROOT}/route-gpu.json")
    stats = load_json(root, f"{D54_ROOT}/stats.json")
    require(route_gpu.get("status") == "expected-unsupported", "D54 WebGPU route must remain expected-unsupported")
    require(route_gpu.get("supportClaim") is False, "D54 must not claim WebGPU support")
    return {
        "rowId": route_gpu.get("sceneId"),
        "pmCategory": "deterministic-surface-snapshot-reference",
        "status": route_gpu.get("status"),
        "format": "surface-snapshot-with-png-reference-artifact",
        "decoder": {
            "name": "none",
            "kind": "surface-snapshot",
            "module": None,
        },
        "colorInfo": {
            "colorType": "SkSurface.MakeRaster N32",
            "colorSpace": "default sRGB raster surface",
            "alphaType": "premul",
            "policy": "reference PNG is comparison evidence, not scene codec support",
        },
        "origin": {
            "kind": "generated-skia-gm-surface-snapshot",
            "referencePath": route_cpu.get("referencePath"),
            "sourceEvidence": "skia-integration-tests/src/main/kotlin/org/skia/tests/ImageGM.kt",
        },
        "decodeResult": {
            "status": "generated-surface-no-codec-decode",
            "result": "GM creates raster surface snapshots; PNG reference is evidence-only",
        },
        "supportClaim": False,
        "reasonCode": route_gpu.get("fallbackReason"),
        "route": {
            "cpu": route_cpu.get("selectedRoute"),
            "gpu": route_gpu.get("selectedRoute"),
            "fallbackReason": route_gpu.get("fallbackReason"),
            "pipelineKey": route_gpu.get("pipelineKey"),
        },
        "stats": {
            "cpuSimilarity": stats.get("cpuSimilarity"),
            "gpuSimilarity": stats.get("gpuSimilarity"),
            "globalThresholdChanged": stats.get("globalThresholdChanged"),
            "broadImageDecodeClaim": stats.get("broadImageDecodeClaim"),
        },
        "artifacts": {
            "reference": f"{D54_ROOT}/skia.png",
            "cpu": f"{D54_ROOT}/cpu.png",
            "gpu": f"{D54_ROOT}/gpu.png",
            "stats": f"{D54_ROOT}/stats.json",
            "routeCpu": f"{D54_ROOT}/route-cpu.json",
            "routeGpu": f"{D54_ROOT}/route-gpu.json",
        },
        "nonClaims": common_non_claims(["no-codec-decode-claim", "no-webgpu-imagegm-support-claim"]),
    }


def animated_scene_rows() -> list[dict[str, Any]]:
    return [
        {
            "rowId": "animated-image-gm-stoplight-webp",
            "pmCategory": "animated-image-scene-dependency-gated",
            "status": "dependency-gated",
            "format": "WebP",
            "decoder": {
                "name": "webp",
                "kind": "portable-codec-plus-missing-scene-pipeline",
                "module": "codec-webp-kotlin",
            },
            "colorInfo": {
                "colorType": "RGBA_8888 for supported WebP frame encodings",
                "colorSpace": "sRGB output; VP8X ICC parsed when parseable",
                "alphaType": "codec frame alpha when supported",
                "policy": "SkAnimatedImage scene pipeline is disabled; no animated renderer support",
            },
            "origin": {
                "kind": "skia-resource-encoded-animation",
                "resource": "images/stoplight_h.webp",
                "sourceEvidence": "skia-integration-tests/src/main/kotlin/org/skia/tests/AnimatedImageGM.kt",
            },
            "decodeResult": {
                "status": "scene-pipeline-disabled",
                "result": "AnimatedImage WebGPU/CrossBackend tests are @Disabled(STUB.ANIMATED_IMAGE)",
            },
            "supportClaim": False,
            "reasonCode": "codec.animated-frame-unsupported",
            "nonClaims": common_non_claims(["no-scene-pass-from-animated-codec-metadata"]),
        },
        {
            "rowId": "animated-image-gm-flight-gif",
            "pmCategory": "animated-image-scene-dependency-gated",
            "status": "dependency-gated",
            "format": "GIF",
            "decoder": {
                "name": "gif",
                "kind": "portable-codec-plus-missing-scene-pipeline",
                "module": "codec-gif-kotlin",
            },
            "colorInfo": {
                "colorType": "RGBA_8888 indexed-frame output",
                "colorSpace": "sRGB output",
                "alphaType": "transparent color index when present",
                "policy": "SkAnimatedImage scene pipeline is disabled; no animated renderer support",
            },
            "origin": {
                "kind": "skia-resource-encoded-animation",
                "resource": "images/flightAnim.gif",
                "sourceEvidence": "skia-integration-tests/src/main/kotlin/org/skia/tests/AnimatedImageGM.kt",
            },
            "decodeResult": {
                "status": "scene-pipeline-disabled",
                "result": "AnimatedImage WebGPU/CrossBackend tests are @Disabled(STUB.ANIMATED_IMAGE)",
            },
            "supportClaim": False,
            "reasonCode": "codec.animated-frame-unsupported",
            "nonClaims": common_non_claims(["no-scene-pass-from-animated-codec-metadata"]),
        },
    ]


def codec_format_rows() -> list[dict[str, Any]]:
    supported = [
        ("PNG", "png", "codec-png-kotlin", "kSuccess-for-covered-real-fixtures", "iCCP best effort; sRGB/gAMA/cHRM structurally validated; 16 bpc PNG decodes to RGBA_F16Norm where covered"),
        ("JPEG", "jpeg", "codec-jpeg-kotlin", "kSuccess-for-covered-real-fixtures", "APP2 ICC parsed when complete; EXIF orientation 1 through 8 parsed and applied"),
        ("GIF", "gif", "codec-gif-kotlin", "kSuccess-for-covered-real-fixtures", "indexed GIF frames and tested frame metadata decode; not a renderer animated-image support claim"),
        ("BMP", "bmp", "codec-bmp-kotlin", "kSuccess-for-covered-real-fixtures", "V4/V5 ICC accepted but ignored; output uses sRGB"),
        ("WBMP", "wbmp", "codec-wbmp-kotlin", "kSuccess-for-covered-real-fixtures", "type-0 monochrome WBMP output as opaque black/white RGBA"),
        ("ICO / CUR", "ico", "codec-ico-kotlin", "delegates-to-selected-payload-decoder", "container selects largest entry and delegates PNG/BMP payload color/origin behavior"),
        ("WebP", "webp", "codec-webp-kotlin", "kSuccess-or-kUnimplemented-by-covered-encoding", "VP8L lossless and selected VP8 paths covered; unsupported frame encodings return kUnimplemented"),
    ]
    rows: list[dict[str, Any]] = [
        {
            "format": fmt,
            "status": "supported",
            "decoder": {
                "name": name,
                "kind": "portable-codec",
                "module": module,
            },
            "colorInfo": color,
            "origin": "codec-real-image-tests/FIXTURES.md plus format-specific unit tests",
            "decodeResult": result,
            "reasonCode": "none",
        }
        for fmt, name, module, result, color in supported
    ]
    rows.extend(
        [
            {
                "format": "AVIF",
                "status": "dependency-gated",
                "decoder": {"name": "avif", "kind": "stub", "module": "codec-extended"},
                "colorInfo": "out of scope until real AVIF dependency lands",
                "origin": "codec-extended/src/main/kotlin/org/skia/codec/SkAvifDecoder.kt",
                "decodeResult": "stub-returns-null",
                "reasonCode": "codec.decoder-unavailable",
            },
            {
                "format": "JPEG XL",
                "status": "dependency-gated",
                "decoder": {"name": "jpegxl", "kind": "stub", "module": "codec-extended"},
                "colorInfo": "out of scope until real JPEG XL dependency lands",
                "origin": "codec-extended/src/main/kotlin/org/skia/codec/SkJpegxlDecoder.kt",
                "decodeResult": "stub-returns-null",
                "reasonCode": "codec.decoder-unavailable",
            },
            {
                "format": "RAW",
                "status": "dependency-gated",
                "decoder": {"name": "raw", "kind": "stub", "module": "codec-extended"},
                "colorInfo": "out of scope until real RAW dependency lands",
                "origin": "codec-extended/src/main/kotlin/org/skia/codec/SkRawDecoder.kt",
                "decodeResult": "stub-returns-null",
                "reasonCode": "codec.decoder-unavailable",
            },
            {
                "format": "video",
                "status": "dependency-gated",
                "decoder": {"name": "ffmpeg-video", "kind": "stub", "module": "codec-extended"},
                "colorInfo": "out of scope until FFmpeg-backed dependency lands outside portable runtime",
                "origin": "codec-extended/src/main/kotlin/org/skia/codec/SkVideoDecoder.kt",
                "decodeResult": "throws-STUB.FFMPEG",
                "reasonCode": "codec.decoder-unavailable",
            },
        ]
    )
    return rows


def build_scene_rows(root: Path) -> list[dict[str, Any]]:
    return [
        kan014_fixture_row(root),
        *kan046_scene_rows(root),
        d54_surface_snapshot_row(root),
        *animated_scene_rows(),
    ]


def is_fixture_like(row: dict[str, Any]) -> bool:
    origin = row.get("origin") if isinstance(row.get("origin"), dict) else {}
    origin_kind = origin.get("kind")
    return origin_kind in {
        "in-repo-deterministic-fixture",
        "in-test-programmatic-image",
        "generated-skia-gm-surface-snapshot",
    }


def existing_artifact_paths(root: Path, rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    audit = []
    for row in rows:
        artifacts = row.get("artifacts")
        if not isinstance(artifacts, dict):
            continue
        for key, value in artifacts.items():
            if isinstance(value, str):
                audit.append({"rowId": row.get("rowId"), "key": key, "path": value, "exists": (root / value).is_file()})
    return audit


def claim_guard(evidence: dict[str, Any], root: Path) -> dict[str, list[str]]:
    scene_rows = [row for row in evidence.get("sceneRows", []) if isinstance(row, dict)]
    format_rows = [row for row in evidence.get("codecFormatRows", []) if isinstance(row, dict)]
    artifact_audit = existing_artifact_paths(root, scene_rows)
    guard: dict[str, list[str]] = {
        "sceneRowsMissingProvenance": [],
        "stubCodecPassRows": [],
        "fixtureRowsClaimingCodecDecode": [],
        "unsupportedSceneRowsMissingReason": [],
        "dependencyGatedFormatRowsMissingReason": [],
        "formatRowsWithStubSuccess": [],
        "artifactPathsMissing": [],
        "hiddenCodecDecodeClaims": [],
    }
    for row in scene_rows:
        required = ["rowId", "status", "format", "decoder", "colorInfo", "origin", "decodeResult"]
        if any(row.get(key) in (None, "", {}) for key in required):
            guard["sceneRowsMissingProvenance"].append(str(row.get("rowId") or "<unknown>"))
        decoder = row.get("decoder") if isinstance(row.get("decoder"), dict) else {}
        decode = row.get("decodeResult") if isinstance(row.get("decodeResult"), dict) else {}
        if row.get("status") == "pass" and decoder.get("kind") == "stub":
            guard["stubCodecPassRows"].append(str(row.get("rowId")))
        if is_fixture_like(row) and (
            decoder.get("kind") not in {"deterministic-fixture", "surface-snapshot"}
            or decode.get("status") == "kSuccess"
        ):
            guard["fixtureRowsClaimingCodecDecode"].append(str(row.get("rowId")))
        if row.get("status") in {"dependency-gated", "expected-unsupported"}:
            reason = row.get("reasonCode")
            if not isinstance(reason, str) or reason not in STABLE_CODEC_REASONS:
                guard["unsupportedSceneRowsMissingReason"].append(str(row.get("rowId")))
        non_claims = row.get("nonClaims") if isinstance(row.get("nonClaims"), list) else []
        if is_fixture_like(row) and "no-codec-decode-claim" not in non_claims:
            guard["hiddenCodecDecodeClaims"].append(str(row.get("rowId")))
    for row in format_rows:
        if row.get("status") == "dependency-gated":
            reason = row.get("reasonCode")
            if not isinstance(reason, str) or reason not in STABLE_CODEC_REASONS:
                guard["dependencyGatedFormatRowsMissingReason"].append(str(row.get("format")))
        decoder = row.get("decoder") if isinstance(row.get("decoder"), dict) else {}
        if decoder.get("kind") == "stub" and row.get("decodeResult") in {"kSuccess", "kSuccess-for-covered-real-fixtures"}:
            guard["formatRowsWithStubSuccess"].append(str(row.get("format")))
    guard["artifactPathsMissing"] = [
        f"{item['rowId']}:{item['key']}:{item['path']}"
        for item in artifact_audit
        if item["exists"] is False
    ]
    return guard


def summarize(scene_rows: list[dict[str, Any]], format_rows: list[dict[str, Any]], guard: dict[str, list[str]]) -> dict[str, int]:
    return {
        "sceneRows": len(scene_rows),
        "deterministicFixtureSceneRows": sum(1 for row in scene_rows if is_fixture_like(row)),
        "realCodecDecodeSceneRows": sum(1 for row in scene_rows if row.get("decoder", {}).get("kind") == "portable-codec"),
        "dependencyGatedSceneRows": sum(1 for row in scene_rows if row.get("status") == "dependency-gated"),
        "portableCodecDecodeFormats": sum(1 for row in format_rows if row.get("status") == "supported"),
        "dependencyGatedFormatRows": sum(1 for row in format_rows if row.get("status") == "dependency-gated"),
        "stubCodecPassRows": len(guard["stubCodecPassRows"]),
        "fixtureRowsClaimingCodecDecode": len(guard["fixtureRowsClaimingCodecDecode"]),
        "sceneRowsMissingProvenance": len(guard["sceneRowsMissingProvenance"]),
        "unsupportedRowsMissingReason": len(guard["unsupportedSceneRowsMissingReason"])
        + len(guard["dependencyGatedFormatRowsMissingReason"]),
    }


def build_evidence(root: Path) -> dict[str, Any]:
    sources = source_audit(root)
    scene_rows = build_scene_rows(root)
    format_rows = codec_format_rows()
    evidence: dict[str, Any] = {
        "schemaVersion": 1,
        "ticket": "KAN-047",
        "packId": "kan-047-codec-provenance-matrix",
        "status": "pass",
        "closureDecision": "codec-provenance-matrix",
        "claimLevel": "pm-codec-provenance-evidence",
        "rendererChanged": False,
        "codecRuntimeChanged": False,
        "thresholdsWeakened": False,
        "nativeJniBridgeClaim": False,
        "animatedRendererSupportClaim": False,
        "sceneRows": scene_rows,
        "codecFormatRows": format_rows,
        "sourceAudit": sources,
        "artifactAudit": existing_artifact_paths(root, scene_rows),
        "requiredValidation": [
            "validateKan047CodecProvenanceMatrix",
            "checkCodecImageComplete",
            "pipelineConformance",
            "pipelinePmBundle",
        ],
        "nonClaims": [
            "KAN-047 does not add renderer, shader, threshold, codec runtime, JNI, ImageIO/AWT, or animated scene support.",
            "Fixture-backed bitmap scene passes remain renderer/sampling evidence, not broad codec decode support.",
            "Real codec decode evidence stays tied to the documented pure Kotlin codec matrix and real-image fixture corpus.",
            "Extended AVIF, JPEG XL, RAW, and video surfaces remain dependency-gated stubs with codec.decoder-unavailable.",
        ],
    }
    guard = claim_guard(evidence, root)
    evidence["claimGuard"] = guard
    evidence["summary"] = summarize(scene_rows, format_rows, guard)
    validate_evidence(evidence, root)
    return evidence


def validate_evidence(evidence: dict[str, Any], root: Path) -> None:
    require(evidence.get("ticket") == "KAN-047", "ticket id changed")
    require(evidence.get("rendererChanged") is False, "rendererChanged must remain false")
    require(evidence.get("codecRuntimeChanged") is False, "codecRuntimeChanged must remain false")
    require(evidence.get("thresholdsWeakened") is False, "thresholds must not be weakened")
    require(evidence.get("nativeJniBridgeClaim") is False, "native JNI bridge claim is not allowed")
    require(evidence.get("animatedRendererSupportClaim") is False, "animated renderer support claim is not allowed")
    scene_rows = evidence.get("sceneRows")
    format_rows = evidence.get("codecFormatRows")
    require(isinstance(scene_rows, list) and scene_rows, "sceneRows missing")
    require(isinstance(format_rows, list) and format_rows, "codecFormatRows missing")
    guard = claim_guard(evidence, root)
    if guard["sceneRowsMissingProvenance"]:
        fail(f"scene rows missing provenance: {guard['sceneRowsMissingProvenance']}")
    if guard["stubCodecPassRows"]:
        fail(f"stub codec pass rows: {guard['stubCodecPassRows']}")
    if guard["fixtureRowsClaimingCodecDecode"]:
        fail(f"fixture rows claiming codec decode: {guard['fixtureRowsClaimingCodecDecode']}")
    if guard["unsupportedSceneRowsMissingReason"]:
        fail(f"unsupported scene rows missing reason: {guard['unsupportedSceneRowsMissingReason']}")
    if guard["dependencyGatedFormatRowsMissingReason"]:
        fail(f"dependency-gated format rows missing reason: {guard['dependencyGatedFormatRowsMissingReason']}")
    if guard["formatRowsWithStubSuccess"]:
        fail(f"stub format rows claiming success: {guard['formatRowsWithStubSuccess']}")
    if guard["artifactPathsMissing"]:
        fail(f"artifact paths missing: {guard['artifactPathsMissing']}")
    if guard["hiddenCodecDecodeClaims"]:
        fail(f"hidden codec decode claims: {guard['hiddenCodecDecodeClaims']}")
    summary = summarize(scene_rows, format_rows, guard)
    expected = evidence.get("summary")
    if isinstance(expected, dict):
        for key, value in summary.items():
            require(expected.get(key) == value, f"summary mismatch for {key}: expected {value}, found {expected.get(key)}")


def markdown_table(headers: list[str], rows: list[list[Any]]) -> str:
    out = [
        "| " + " | ".join(headers) + " |",
        "| " + " | ".join("---" for _ in headers) + " |",
    ]
    for row in rows:
        out.append("| " + " | ".join(str(cell) for cell in row) + " |")
    return "\n".join(out)


def render_markdown(evidence: dict[str, Any]) -> str:
    scene_rows = evidence["sceneRows"]
    format_rows = evidence["codecFormatRows"]
    summary = evidence["summary"]
    scene_table = markdown_table(
        ["Scene", "Status", "Format", "Decoder", "Origin", "Decode result", "Reason"],
        [
            [
                row["rowId"],
                row["status"],
                row["format"],
                row["decoder"].get("module") or row["decoder"].get("name"),
                row["origin"].get("resource") or row["origin"].get("kind"),
                row["decodeResult"].get("status"),
                row.get("reasonCode", "none"),
            ]
            for row in scene_rows
        ],
    )
    format_table = markdown_table(
        ["Format", "Status", "Decoder", "Decode result", "Reason"],
        [
            [
                row["format"],
                row["status"],
                row["decoder"].get("module"),
                row["decodeResult"],
                row.get("reasonCode", "none"),
            ]
            for row in format_rows
        ],
    )
    return f"""# KAN-047 Codec Provenance Matrix

Status: `{evidence["status"]}`

KAN-047 records codec provenance without changing renderer code, codec runtime,
thresholds, JNI, ImageIO/AWT usage, or animated scene support.

## Summary

| Metric | Value |
| --- | ---: |
| Scene rows | `{summary["sceneRows"]}` |
| Deterministic fixture/surface rows | `{summary["deterministicFixtureSceneRows"]}` |
| Real codec decode scene rows | `{summary["realCodecDecodeSceneRows"]}` |
| Dependency-gated scene rows | `{summary["dependencyGatedSceneRows"]}` |
| Portable codec decode formats | `{summary["portableCodecDecodeFormats"]}` |
| Dependency-gated format rows | `{summary["dependencyGatedFormatRows"]}` |
| Stub codec pass rows | `{summary["stubCodecPassRows"]}` |
| Fixture rows claiming codec decode | `{summary["fixtureRowsClaimingCodecDecode"]}` |
| Scene rows missing provenance | `{summary["sceneRowsMissingProvenance"]}` |

## Scene Provenance

{scene_table}

## Codec Formats

{format_table}

## Claim Guards

- No stub codec renders a scene pass.
- Deterministic fixtures stay distinct from real `SkCodec` decode.
- `bitmap-subset-local-matrix-repeat` cites `codec-png-kotlin`, but its support
  claim remains bounded bitmap sampling, not broad codec or color-managed decode.
- Animated WebP/GIF scene rows remain dependency-gated via
  `codec.animated-frame-unsupported`.
- AVIF, JPEG XL, RAW, and video remain dependency-gated via
  `codec.decoder-unavailable`.

## Non-Claims

{chr(10).join(f"- {item}" for item in evidence["nonClaims"])}
"""


def write_outputs(root: Path, output_dir: Path) -> dict[str, Any]:
    evidence = build_evidence(root)
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / OUTPUT_JSON).write_text(json.dumps(evidence, indent=2, sort_keys=False) + "\n", encoding="utf-8")
    (output_dir / OUTPUT_MARKDOWN).write_text(render_markdown(evidence), encoding="utf-8")
    return evidence


def main(argv: list[str]) -> int:
    root = Path(argv[1]).resolve() if len(argv) > 1 else Path.cwd().resolve()
    output_dir = Path(argv[2]).resolve() if len(argv) > 2 else root / DEFAULT_OUTPUT_DIR
    try:
        evidence = write_outputs(root, output_dir)
    except ValidationError as exc:
        print(exc, file=sys.stderr)
        return 1
    print(
        f"KAN-047 codec provenance matrix PASS: "
        f"{evidence['summary']['sceneRows']} scene rows, "
        f"{evidence['summary']['portableCodecDecodeFormats']} portable codec formats, "
        f"{evidence['summary']['dependencyGatedFormatRows']} dependency-gated formats."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
