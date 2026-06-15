#!/usr/bin/env python3
import json
import re
import sys
from pathlib import Path
from typing import Any, Iterable


MANIFEST_PATH = "reports/pure-kotlin-text/boundary-contracts.json"
SOURCE_SPEC_PATH = ".upstream/specs/pure-kotlin-text/00-architecture-and-module-boundaries.md"

TOP_LEVEL_KEYS = [
    "schemaVersion",
    "manifestId",
    "sourceSpec",
    "artifactType",
    "supportClaim",
    "nonClaims",
    "validationCommand",
    "targetPackageRoots",
    "contractSymbols",
]

SOURCE_SPEC_KEYS = ["path", "section"]
PACKAGE_ROW_KEYS = ["id", "packageRoot", "ownerArea", "sourceRoots", "boundaryRole"]
CONTRACT_ROW_KEYS = [
    "id",
    "symbol",
    "qualifiedName",
    "kind",
    "packageRoot",
    "ownerFile",
    "ownerArea",
    "contractRole",
]

REQUIRED_PACKAGE_ROOTS = {
    "org.graphiks.kanvas.font",
    "org.graphiks.kanvas.font.scaler",
    "org.graphiks.kanvas.text.shaping",
    "org.graphiks.kanvas.text.paragraph",
    "org.graphiks.kanvas.glyph",
    "org.graphiks.kanvas.glyph.gpu",
    "org.graphiks.kanvas.gpu.renderer.commands",
    "org.graphiks.kanvas.gpu.renderer.text",
}

REQUIRED_CONTRACT_SYMBOLS = {
    "FontSourceID",
    "TypefaceID",
    "GlyphStrikeKey",
    "GPUGlyphRunDescriptor",
    "GPUTextArtifactID",
    "GPUTextArtifactGeneration",
    "GPUTextArtifactReference",
    "GPUTextRouteDiagnostics",
    "NormalizedDrawCommand.DrawTextRun",
    "GPUTextDiagnosticCodes",
}

PURE_KOTLIN_TEXT_SOURCE_ROOTS = [
    "font/core/src/main/kotlin",
    "font/sfnt/src/main/kotlin",
    "font/scaler/src/main/kotlin",
    "font/text/src/main/kotlin",
    "font/glyph/src/main/kotlin",
    "font/gpu-api/src/main/kotlin",
]

GPU_RENDERER_TEXT_SOURCE_ROOTS = [
    "gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/commands",
    "gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/text",
]

PURE_KOTLIN_FORBIDDEN_PREFIXES = [
    ("org.graphiks.kanvas.gpu.renderer", "gpu renderer"),
    ("org.skia", "Skia facade"),
    ("org.jetbrains.skia", "Skia binding"),
    ("org.jetbrains.skiko", "Skia binding"),
    ("skia", "Skia package"),
    ("java.awt", "AWT"),
    ("javax.swing", "Swing"),
    ("java.lang.foreign", "JNI/native wrapper"),
    ("kotlinx.cinterop", "native interop"),
    ("com.sun.jna", "JNA"),
    ("com.kenai.jffi", "JNI wrapper"),
    ("jnr.ffi", "JNA/JNI wrapper"),
    ("org.lwjgl.util.freetype", "FreeType"),
    ("org.freetype", "FreeType"),
    ("freetype", "FreeType"),
    ("org.lwjgl.util.harfbuzz", "HarfBuzz"),
    ("org.harfbuzz", "HarfBuzz"),
    ("harfbuzz", "HarfBuzz"),
    ("fontations", "Fontations"),
    ("org.fontations", "Fontations"),
    ("com.google.fontations", "Fontations"),
    ("org.robovm.apple.coretext", "CoreText"),
    ("coretext", "CoreText"),
    ("directwrite", "DirectWrite"),
    ("org.freedesktop.fontconfig", "fontconfig"),
    ("fontconfig", "fontconfig"),
    ("android.graphics", "platform font engine"),
    ("android.text", "platform shaper"),
    ("org.graphiks.kanvas.platform.text", "platform shaper"),
    ("org.graphiks.kanvas.native.text", "native text engine"),
    ("org.graphiks.kanvas.text.native", "native text engine"),
]

GPU_RENDERER_FORBIDDEN_PREFIXES = [
    ("org.skia", "Skia facade"),
    ("org.jetbrains.skia", "Skia binding"),
    ("org.jetbrains.skiko", "Skia binding"),
    ("skia", "Skia package"),
    ("org.graphiks.kanvas.font.sfnt", "font parser"),
    ("org.graphiks.kanvas.font.scaler", "font scaler"),
    ("org.graphiks.kanvas.text.shaping", "text shaping"),
    ("org.graphiks.kanvas.text.paragraph", "paragraph layout"),
    ("java.awt", "AWT"),
    ("javax.swing", "Swing"),
    ("java.lang.foreign", "JNI/native wrapper"),
    ("kotlinx.cinterop", "native interop"),
    ("com.sun.jna", "JNA"),
    ("com.kenai.jffi", "JNI wrapper"),
    ("jnr.ffi", "JNA/JNI wrapper"),
    ("org.lwjgl.util.freetype", "FreeType"),
    ("org.freetype", "FreeType"),
    ("freetype", "FreeType"),
    ("org.lwjgl.util.harfbuzz", "HarfBuzz"),
    ("org.harfbuzz", "HarfBuzz"),
    ("harfbuzz", "HarfBuzz"),
    ("fontations", "Fontations"),
    ("org.fontations", "Fontations"),
    ("com.google.fontations", "Fontations"),
    ("org.robovm.apple.coretext", "CoreText"),
    ("coretext", "CoreText"),
    ("directwrite", "DirectWrite"),
    ("org.freedesktop.fontconfig", "fontconfig"),
    ("fontconfig", "fontconfig"),
    ("android.graphics", "platform font engine"),
    ("android.text", "platform shaper"),
    ("org.graphiks.kanvas.platform.text", "platform shaper"),
    ("org.graphiks.kanvas.native.text", "native text engine"),
    ("org.graphiks.kanvas.text.native", "native text engine"),
]

FORBIDDEN_SUPPORT_CLAIM_PATTERNS = [
    re.compile(r"\btarget-supported\b", re.IGNORECASE),
    re.compile(r"\bcurrent-supported\b", re.IGNORECASE),
    re.compile(r"\bcomplete\s+support\b", re.IGNORECASE),
    re.compile(r"\bsupport\s+complete\b", re.IGNORECASE),
    re.compile(r"\bsupport\s+claim\b", re.IGNORECASE),
    re.compile(r"\bGPU\s+text\s+support\b", re.IGNORECASE),
    re.compile(r"\brendering\s+support\b", re.IGNORECASE),
]

FONT_ARCHITECTURE_SKIA_API_LEAK = "font.architecture.skia-api-leak"
FONT_ARCHITECTURE_GPU_BACKEDGE = "font.architecture.gpu-backedge"
FONT_ARCHITECTURE_GPU_FONT_DEPENDENCY = "font.architecture.gpu-font-dependency"
FONT_ARCHITECTURE_NATIVE_FONT_DEPENDENCY = "font.architecture.native-font-dependency"
FONT_ARCHITECTURE_FORBIDDEN_IMPORT = "font.architecture.forbidden-import"

SKIA_FORBIDDEN_REASONS = {"Skia facade", "Skia binding", "Skia package"}
GPU_RENDERER_TEXT_FONT_REASONS = {"font parser", "font scaler", "text shaping", "paragraph layout"}
NATIVE_FONT_FORBIDDEN_REASONS = {
    "AWT",
    "Swing",
    "JNI/native wrapper",
    "native interop",
    "JNA",
    "JNI wrapper",
    "JNA/JNI wrapper",
    "FreeType",
    "HarfBuzz",
    "Fontations",
    "CoreText",
    "DirectWrite",
    "fontconfig",
    "platform font engine",
    "platform shaper",
    "native text engine",
}


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"pure Kotlin text boundary contract validation failed: {message}")


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


def load_manifest(root: Path) -> dict[str, Any]:
    manifest = load_json(root, MANIFEST_PATH)
    require(isinstance(manifest, dict), "manifest root must be an object")
    return manifest


def require_string(value: Any, label: str) -> str:
    require(isinstance(value, str) and value.strip() == value and value, f"{label} must be a non-empty trimmed string")
    return value


def require_string_list(value: Any, label: str, *, allow_empty: bool = False) -> list[str]:
    require(isinstance(value, list), f"{label} must be a list")
    if not allow_empty:
        require(value, f"{label} must be non-empty")
    for index, item in enumerate(value):
        require_string(item, f"{label}[{index}]")
    return value


def require_keys(payload: dict[str, Any], expected: list[str], label: str) -> None:
    require(list(payload.keys()) == expected, f"{label} keys must be stable and ordered: {expected}")


def _resolve_under_root(root: Path, relative_path: str, label: str) -> Path:
    require_string(relative_path, label)
    require(not Path(relative_path).is_absolute(), f"{label} must be relative: {relative_path}")
    resolved_root = root.resolve()
    resolved_path = (resolved_root / relative_path).resolve()
    require(
        resolved_path == resolved_root or resolved_root in resolved_path.parents,
        f"{label} must stay under project root: {relative_path}",
    )
    return resolved_path


def require_existing_path(root: Path, relative_path: str, label: str) -> Path:
    resolved_path = _resolve_under_root(root, relative_path, label)
    require(resolved_path.is_file(), f"{label} does not exist: {relative_path}")
    return resolved_path


def require_existing_dir(root: Path, relative_path: str, label: str) -> Path:
    resolved_path = _resolve_under_root(root, relative_path, label)
    require(resolved_path.is_dir(), f"{label} does not exist: {relative_path}")
    return resolved_path


def walk_strings(value: Any, path: tuple[Any, ...] = ()) -> Iterable[tuple[tuple[Any, ...], str]]:
    if isinstance(value, str):
        yield path, value
    elif isinstance(value, list):
        for index, item in enumerate(value):
            yield from walk_strings(item, (*path, index))
    elif isinstance(value, dict):
        for key, item in value.items():
            yield from walk_strings(item, (*path, key))


def support_claim_scan_allows_path(path: tuple[Any, ...]) -> bool:
    return bool(path and path[0] in {"supportClaim", "nonClaims"})


def _hidden_char(char: str) -> str:
    return "\n" if char == "\n" else " "


def kotlin_code_without_comments_and_strings(text: str) -> str:
    result: list[str] = []
    index = 0
    length = len(text)
    while index < length:
        if text.startswith("//", index):
            while index < length and text[index] != "\n":
                result.append(" ")
                index += 1
            continue

        if text.startswith("/*", index):
            result.extend("  ")
            index += 2
            block_depth = 1
            while index < length:
                if text.startswith("/*", index):
                    result.extend("  ")
                    index += 2
                    block_depth += 1
                    continue
                if text.startswith("*/", index):
                    result.extend("  ")
                    index += 2
                    block_depth -= 1
                    if block_depth == 0:
                        break
                    continue
                result.append(_hidden_char(text[index]))
                index += 1
            continue

        if text.startswith('"""', index):
            result.extend("   ")
            index += 3
            while index < length:
                if text.startswith('"""', index):
                    result.extend("   ")
                    index += 3
                    break
                result.append(_hidden_char(text[index]))
                index += 1
            continue

        char = text[index]
        if char == '"':
            result.append(" ")
            index += 1
            escaped = False
            while index < length:
                current = text[index]
                result.append(_hidden_char(current))
                index += 1
                if current == "\n":
                    escaped = False
                    continue
                if escaped:
                    escaped = False
                    continue
                if current == "\\":
                    escaped = True
                    continue
                if current == '"':
                    break
            continue

        result.append(char)
        index += 1

    return "".join(result)


def validate_no_support_claim_text(manifest: dict[str, Any]) -> None:
    require(
        manifest["supportClaim"] == "audit-coordination-artifact-no-support-claim",
        "manifest must be explicit that it is not a support claim",
    )
    non_claims = require_string_list(manifest["nonClaims"], "nonClaims")
    require("not-a-support-claim" in non_claims, "nonClaims must include not-a-support-claim")
    require("no-rendering-support-claim" in non_claims, "nonClaims must include no-rendering-support-claim")
    require("no-GPU-text-route-promotion" in non_claims, "nonClaims must include no-GPU-text-route-promotion")

    for path, text in walk_strings(manifest):
        if support_claim_scan_allows_path(path):
            continue
        for pattern in FORBIDDEN_SUPPORT_CLAIM_PATTERNS:
            if pattern.search(text):
                fail(f"support claim wording is forbidden at {'.'.join(map(str, path))}: {text}")


def validate_package_row(root: Path, row: Any, index: int) -> dict[str, Any]:
    require(isinstance(row, dict), f"targetPackageRoots[{index}] must be an object")
    require_keys(row, PACKAGE_ROW_KEYS, f"targetPackageRoots[{index}]")
    package_id = require_string(row["id"], f"targetPackageRoots[{index}].id")
    require_string(row["packageRoot"], f"{package_id}.packageRoot")
    require_string(row["ownerArea"], f"{package_id}.ownerArea")
    source_roots = require_string_list(row["sourceRoots"], f"{package_id}.sourceRoots")
    for source_index, source_root in enumerate(source_roots):
        require_existing_dir(root, source_root, f"{package_id}.sourceRoots[{source_index}]")
    require_string(row["boundaryRole"], f"{package_id}.boundaryRole")
    return row


def symbol_leaf(symbol: str) -> str:
    return symbol.rsplit(".", 1)[-1]


DECLARATION_KEYWORD_PATTERN = r"(?:value\s+class|data\s+class|enum\s+class|sealed\s+interface|class|object|interface|typealias)"


def declaration_pattern(name: str) -> re.Pattern[str]:
    return re.compile(rf"\b{DECLARATION_KEYWORD_PATTERN}\s+{re.escape(name)}\b")


def find_declaration(source: str, name: str, start: int, end: int) -> re.Match[str] | None:
    return declaration_pattern(name).search(source, start, end)


def body_bounds(source: str, declaration_end: int, symbol: str) -> tuple[int, int]:
    open_brace = source.find("{", declaration_end)
    require(open_brace >= 0, f"nested symbol parent has no body: {symbol}")
    depth = 0
    for index in range(open_brace, len(source)):
        char = source[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return open_brace + 1, index
    fail(f"nested symbol parent body is unterminated: {symbol}")


def validate_symbol_declared(source: str, symbol: str, owner_file: str) -> None:
    parts = symbol.split(".")
    search_start = 0
    search_end = len(source)
    parent_path: list[str] = []

    for index, part in enumerate(parts):
        match = find_declaration(source, part, search_start, search_end)
        current_symbol = ".".join([*parent_path, part])
        require(
            match is not None,
            f"{symbol}.ownerFile does not declare nested symbol {current_symbol}: {owner_file}",
        )
        if index < len(parts) - 1:
            parent_path.append(part)
            search_start, search_end = body_bounds(source, match.end(), current_symbol)


def validate_owner_file_contains_symbol(root: Path, row: dict[str, Any]) -> None:
    owner_file = row["ownerFile"]
    symbol = row["symbol"]
    source = kotlin_code_without_comments_and_strings(
        require_existing_path(root, owner_file, f"{symbol}.ownerFile").read_text(encoding="utf-8")
    )
    validate_symbol_declared(source, symbol, owner_file)


def validate_contract_row(root: Path, row: Any, index: int) -> dict[str, Any]:
    require(isinstance(row, dict), f"contractSymbols[{index}] must be an object")
    require_keys(row, CONTRACT_ROW_KEYS, f"contractSymbols[{index}]")
    symbol_id = require_string(row["id"], f"contractSymbols[{index}].id")
    symbol = require_string(row["symbol"], f"{symbol_id}.symbol")
    require_string(row["qualifiedName"], f"{symbol_id}.qualifiedName")
    require_string(row["kind"], f"{symbol_id}.kind")
    require_string(row["packageRoot"], f"{symbol_id}.packageRoot")
    require_string(row["ownerArea"], f"{symbol_id}.ownerArea")
    require_string(row["contractRole"], f"{symbol_id}.contractRole")
    validate_owner_file_contains_symbol(root, row)
    return row


def validate_manifest(root: Path, manifest: dict[str, Any]) -> None:
    root = root.resolve()
    require_keys(manifest, TOP_LEVEL_KEYS, "manifest")
    require(manifest["schemaVersion"] == 1, "schemaVersion must be 1")
    require(manifest["manifestId"] == "pure-kotlin-text-boundary-contracts", "manifestId changed")

    source_spec = manifest["sourceSpec"]
    require(isinstance(source_spec, dict), "sourceSpec must be an object")
    require_keys(source_spec, SOURCE_SPEC_KEYS, "sourceSpec")
    require(source_spec["path"] == SOURCE_SPEC_PATH, "sourceSpec.path must point to spec 00")
    require_existing_path(root, source_spec["path"], "sourceSpec.path")
    require(source_spec["section"] == "Architecture And Module Boundaries", "sourceSpec.section changed")

    require(
        manifest["artifactType"] == "audit-coordination-boundary-manifest",
        "artifactType must identify this as an audit/coordination artifact",
    )
    require(
        manifest["validationCommand"] == "rtk python3 scripts/validate_pure_kotlin_text_boundary_contracts.py",
        "validationCommand changed",
    )
    validate_no_support_claim_text(manifest)

    package_rows = manifest["targetPackageRoots"]
    require(isinstance(package_rows, list) and package_rows, "targetPackageRoots must be a non-empty list")
    package_rows = [validate_package_row(root, row, index) for index, row in enumerate(package_rows)]
    package_ids = [row["id"] for row in package_rows]
    require(package_ids == sorted(package_ids), "targetPackageRoots ids must be sorted")
    require(len(package_ids) == len(set(package_ids)), "targetPackageRoots ids must be unique")
    package_roots = {row["packageRoot"] for row in package_rows}
    require(
        REQUIRED_PACKAGE_ROOTS.issubset(package_roots),
        f"missing required package roots: {sorted(REQUIRED_PACKAGE_ROOTS - package_roots)}",
    )

    contract_rows = manifest["contractSymbols"]
    require(isinstance(contract_rows, list) and contract_rows, "contractSymbols must be a non-empty list")
    contract_rows = [validate_contract_row(root, row, index) for index, row in enumerate(contract_rows)]
    symbol_ids = [row["id"] for row in contract_rows]
    require(symbol_ids == sorted(symbol_ids), "contractSymbols ids must be sorted")
    require(len(symbol_ids) == len(set(symbol_ids)), "contractSymbols ids must be unique")
    symbols = {row["symbol"] for row in contract_rows}
    require(
        REQUIRED_CONTRACT_SYMBOLS.issubset(symbols),
        f"missing required contract symbols: {sorted(REQUIRED_CONTRACT_SYMBOLS - symbols)}",
    )

    validate_import_boundaries(root)


def import_or_package_target(line: str) -> tuple[str, str] | None:
    stripped = kotlin_code_without_comments_and_strings(line).strip()
    if stripped.startswith("package "):
        target = stripped[len("package ") :].strip()
        return "package", target
    if stripped.startswith("import "):
        target = stripped[len("import ") :].strip()
        target = target.split("//", 1)[0].strip()
        target = re.split(r"\s+as\s+", target, maxsplit=1)[0].strip()
        return "import", target
    return None


def target_matches_prefix(target: str, prefix: str) -> bool:
    return target == prefix or target.startswith(f"{prefix}.") or target.startswith(f"{prefix}.*")


def boundary_diagnostic_code(label: str, prefix: str, reason: str) -> str:
    if reason in SKIA_FORBIDDEN_REASONS:
        return FONT_ARCHITECTURE_SKIA_API_LEAK
    if label == "pure Kotlin text boundary" and prefix == "org.graphiks.kanvas.gpu.renderer":
        return FONT_ARCHITECTURE_GPU_BACKEDGE
    if label == "GPU renderer text boundary" and reason in GPU_RENDERER_TEXT_FONT_REASONS:
        return FONT_ARCHITECTURE_GPU_FONT_DEPENDENCY
    if reason in NATIVE_FONT_FORBIDDEN_REASONS:
        return FONT_ARCHITECTURE_NATIVE_FONT_DEPENDENCY
    return FONT_ARCHITECTURE_FORBIDDEN_IMPORT


def scan_kotlin_import_targets(root: Path, relative_root: str) -> Iterable[tuple[Path, int, str, str]]:
    source_root = root / relative_root
    if not source_root.is_dir():
        return
    for source_file in sorted(source_root.rglob("*.kt")):
        source = kotlin_code_without_comments_and_strings(source_file.read_text(encoding="utf-8"))
        for line_number, line in enumerate(source.splitlines(), start=1):
            declaration = import_or_package_target(line)
            if declaration is not None:
                kind, target = declaration
                yield source_file, line_number, kind, target


def validate_targets_against_prefixes(
    root: Path,
    source_roots: list[str],
    forbidden_prefixes: list[tuple[str, str]],
    label: str,
) -> None:
    root = root.resolve()
    for relative_root in source_roots:
        for source_file, line_number, kind, target in scan_kotlin_import_targets(root, relative_root):
            for prefix, reason in forbidden_prefixes:
                if target_matches_prefix(target, prefix):
                    relative_file = source_file.relative_to(root)
                    code = boundary_diagnostic_code(label, prefix, reason)
                    fail(f"{code}: {label} import violation in {relative_file}:{line_number}: {kind} {target} ({reason})")


def validate_import_boundaries(root: Path) -> None:
    validate_targets_against_prefixes(
        root,
        PURE_KOTLIN_TEXT_SOURCE_ROOTS,
        PURE_KOTLIN_FORBIDDEN_PREFIXES,
        "pure Kotlin text boundary",
    )
    validate_targets_against_prefixes(
        root,
        GPU_RENDERER_TEXT_SOURCE_ROOTS,
        GPU_RENDERER_FORBIDDEN_PREFIXES,
        "GPU renderer text boundary",
    )


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()
    try:
        manifest = load_manifest(root)
        validate_manifest(root, manifest)
    except ValidationError as exc:
        print(str(exc), file=sys.stderr)
        return 1

    print(
        "Pure Kotlin text boundary contract validation passed: "
        f"{len(manifest['targetPackageRoots'])} package roots, "
        f"{len(manifest['contractSymbols'])} contract symbols."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
