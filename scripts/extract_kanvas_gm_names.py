#!/usr/bin/env python3
"""Shared Kotlin GM name extraction for Kanvas scripts."""

from __future__ import annotations

from dataclasses import dataclass
import re
from pathlib import Path


@dataclass(frozen=True)
class KanvasGmInventory:
    logical_names: set[str]
    reference_name_aliases: dict[str, str]


def extract_subclass_names(text: str, parent_class: str) -> set[str]:
    names = set()
    for match in re.finditer(
        r'(?:class|object)\s+\w+\s*(?:\([^)]*\))?\s*:\s*'
        + re.escape(parent_class)
        + r'\s*\(\s*"([^"]+)"',
        text,
    ):
        names.add(match.group(1))
    return names


def split_top_level_args(value: str) -> list[str]:
    parts: list[str] = []
    current: list[str] = []
    depth = 0
    for char in value:
        if char == "," and depth == 0:
            part = "".join(current).strip()
            if part:
                parts.append(part)
            current = []
            continue
        if char in "([{":
            depth += 1
        elif char in ")]}":
            depth = max(0, depth - 1)
        current.append(char)
    part = "".join(current).strip()
    if part:
        parts.append(part)
    return parts


def find_matching_brace(text: str, open_brace_index: int) -> int | None:
    depth = 0
    for index in range(open_brace_index, len(text)):
        char = text[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return index
    return None


def parse_constructor_param_names(signature: str) -> list[str]:
    if not signature:
        return []
    result = []
    for part in split_top_level_args(signature):
        match = re.search(r"\b(?:val|var)\s+(\w+)\s*:", part)
        if match:
            result.append(match.group(1))
            continue
        match = re.search(r"(\w+)\s*:", part)
        if match:
            result.append(match.group(1))
    return result


def parse_simple_value(expression: str) -> str | bool | None:
    expression = expression.strip()
    if expression == "true":
        return True
    if expression == "false":
        return False
    if re.fullmatch(r"[\w.]+", expression):
        return expression
    string_match = re.fullmatch(r'"([^"]*)"', expression)
    if string_match:
        return string_match.group(1)
    return None


def interpolate_template(template: str, values: dict[str, object]) -> str:
    rendered = template
    for key, value in values.items():
        rendered = rendered.replace(f"${{{key}}}", str(value))
        rendered = rendered.replace(f"${key}", str(value))
    return rendered


def extract_block_getter_variant_names(text: str) -> set[str]:
    names = set()
    for class_match in re.finditer(
        r"(?:open\s+)?class\s+(\w+)\s*\((.*?)\)\s*:\s*[\w.<>(),\s]+\{",
        text,
        re.DOTALL,
    ):
        class_name = class_match.group(1)
        constructor_signature = class_match.group(2)
        body_start = class_match.end() - 1
        body_end = find_matching_brace(text, body_start)
        if body_end is None:
            continue
        class_body = text[body_start + 1:body_end]

        getter_match = re.search(
            r"override\s+val\s+name\s*:\s*String\s+get\s*\(\s*\)\s*\{",
            class_body,
        )
        if getter_match is None:
            continue
        getter_start = getter_match.end() - 1
        getter_end = find_matching_brace(class_body, getter_start)
        if getter_end is None:
            continue
        getter_body = class_body[getter_start + 1:getter_end]

        when_match = re.search(
            r"val\s+(\w+)\s*=\s*when\s*\(\s*(\w+)\s*\)\s*\{",
            getter_body,
        )
        if when_match is None:
            continue
        descriptor_name = when_match.group(1)
        selector_name = when_match.group(2)
        when_start = when_match.end() - 1
        when_end = find_matching_brace(getter_body, when_start)
        if when_end is None:
            continue
        when_body = getter_body[when_start + 1:when_end]

        selector_map = {
            case_match.group(1): case_match.group(2)
            for case_match in re.finditer(r"([\w.]+)\s*->\s*\"([^\"]+)\"", when_body)
        }
        if not selector_map:
            continue

        return_match = re.search(
            r'return\s+if\s*\(\s*(\w+)\s*\)\s*"([^"]*?\$\w+[^"]*)"\s*else\s*"([^"]*?\$\w+[^"]*)"',
            getter_body,
            re.DOTALL,
        )
        if return_match is None:
            continue
        condition_name = return_match.group(1)
        true_template = return_match.group(2)
        false_template = return_match.group(3)

        constructor_params = parse_constructor_param_names(constructor_signature)
        if selector_name not in constructor_params or condition_name not in constructor_params:
            continue

        for subclass_match in re.finditer(
            r"(?:class|object)\s+\w+\s*(?:\([^)]*\))?\s*:\s*"
            + re.escape(class_name)
            + r"\s*\(([^)]*)\)",
            text,
        ):
            raw_args = split_top_level_args(subclass_match.group(1))
            if len(raw_args) != len(constructor_params):
                continue

            resolved_args = {
                param_name: parse_simple_value(raw_value)
                for param_name, raw_value in zip(constructor_params, raw_args)
            }
            if any(value is None for value in resolved_args.values()):
                continue

            descriptor_value = selector_map.get(resolved_args[selector_name])
            hq_value = resolved_args[condition_name]
            if descriptor_value is None or not isinstance(hq_value, bool):
                continue

            template = true_template if hq_value else false_template
            names.add(interpolate_template(template, {descriptor_name: descriptor_value}))

    return names


def extract_logical_gm_names(text: str) -> set[str]:
    names = set()
    for match in re.finditer(r'override\s+val\s+name\s*=\s*"([^"]+)"', text):
        names.add(match.group(1))

    for match in re.finditer(
        r'override\s+val\s+name(?:\s*:\s*String)?\s*=\s*if\s*\([^)]*\)\s*"([^"]+)"\s*else\s*"([^"]+)"',
        text,
    ):
        names.add(match.group(1))
        names.add(match.group(2))

    for match in re.finditer(
        r'override\s+val\s+name\s*:\s*String\s+get\s*\(\s*\)\s*=\s*if\s*\([^)]*\)\s*"([^"]+)"\s*else\s*"([^"]+)"',
        text,
    ):
        names.add(match.group(1))
        names.add(match.group(2))

    template_match = re.search(
        r'override\s+val\s+name\s*:\s*String\s+get\s*\(\s*\)\s*=\s*"([^"]*)\$',
        text,
    )
    if template_match:
        class_match = re.search(
            r'(?:class|object)\s+(\w+)\s*(?:\([^)]*\))?\s*[:\{]',
            text,
        )
        if class_match:
            names.update(extract_subclass_names(text, class_match.group(1)))

    for match in re.finditer(
        r'class\s+(\w+)\s*\([^)]*override\s+val\s+name\s*:\s*String',
        text,
    ):
        names.update(extract_subclass_names(text, match.group(1)))

    for match in re.finditer(r'gmName\s*=\s*"([^"]+)"', text):
        names.add(match.group(1))

    for match in re.finditer(r'variantName\s*=\s*"([^"]+)"', text):
        names.add(match.group(1))

    for match in re.finditer(r'return\s+"([^"]+)"', text):
        names.add(match.group(1))

    for match in re.finditer(
        r'override\s+val\s+name\s*:\s*String\s+get\s*\(\s*\)\s*=\s*"([^"]+)"',
        text,
    ):
        names.add(match.group(1))

    for match in re.finditer(r'return "([^"]+)"', text):
        names.add(match.group(1))

    for match in re.finditer(r'name\s*=\s*"([^"]+)"', text):
        start = match.start()
        prefix = text[max(0, start - 40):start]
        if "override val" not in prefix and "kanvas.skia.gm" not in prefix:
            names.add(match.group(1))

    for match in re.finditer(r':\s*\w+\s*\(\s*"([a-z][a-z0-9_]+)"', text):
        names.add(match.group(1))

    names.update(extract_block_getter_variant_names(text))
    return names


def iter_class_or_object_bodies(text: str) -> list[str]:
    bodies: list[str] = []
    for match in re.finditer(r"\b(?:class|object)\s+\w+\b", text):
        body_start = text.find("{", match.end())
        if body_start == -1:
            continue
        body_end = find_matching_brace(text, body_start)
        if body_end is None:
            continue
        bodies.append(text[body_start + 1:body_end])
    return bodies


def extract_literal_override(body: str, property_name: str) -> str | None:
    match = re.search(
        rf'override\s+val\s+{re.escape(property_name)}(?:\s*:\s*String)?\s*=\s*"([^"]+)"',
        body,
    )
    if match is None:
        return None
    return match.group(1)


def extract_reference_name_aliases(text: str) -> dict[str, str]:
    aliases: dict[str, str] = {}
    for body in iter_class_or_object_bodies(text):
        logical_name = extract_literal_override(body, "name")
        reference_name = extract_literal_override(body, "referenceName")
        if logical_name is None or reference_name is None:
            continue
        aliases[reference_name] = logical_name
    return aliases


def extract_kanvas_gm_inventory(gm_dir: Path) -> KanvasGmInventory:
    logical_names = set()
    reference_name_aliases: dict[str, str] = {}

    for kt_file in sorted(gm_dir.rglob("*Gm.kt")):
        text = kt_file.read_text(encoding="utf-8")
        logical_names.update(extract_logical_gm_names(text))
        reference_name_aliases.update(extract_reference_name_aliases(text))

    return KanvasGmInventory(
        logical_names=logical_names,
        reference_name_aliases=reference_name_aliases,
    )


def extract_kanvas_gm_names(gm_dir: Path) -> set[str]:
    return extract_kanvas_gm_inventory(gm_dir).logical_names
