"""Wire models and JSON Schema helpers.

The Java engine is the authority on schema validation (it validates against the schema in the
run's snapshot). The sidecar validates too, but only to report errors early and to help providers
produce conforming output; its verdict is advisory.
"""

from __future__ import annotations

import json
import re
from typing import Any

from jsonschema import Draft7Validator
from jsonschema.exceptions import SchemaError
from pydantic import BaseModel, Field


class ClassifyRequest(BaseModel):
    prompt: str = Field(min_length=1, max_length=50_000)
    output_schema: dict[str, Any]
    repair_feedback: str | None = Field(default=None, max_length=10_000)
    metadata: dict[str, Any] | None = None
    model: str | None = None


class Validation(BaseModel):
    valid: bool
    errors: list[str]


class ClassifyResponse(BaseModel):
    output: dict[str, Any] | None
    raw_text: str
    tokens_prompt: int
    tokens_completion: int
    provider: str
    model: str | None
    validation: Validation


def check_schema(schema: dict[str, Any]) -> None:
    """Raise SchemaError if the schema itself is invalid."""
    Draft7Validator.check_schema(schema)


def validate(output: Any, schema: dict[str, Any]) -> list[str]:
    """Return human-readable validation errors (empty when valid)."""
    if output is None:
        return ["$: answer is not valid JSON"]
    try:
        validator = Draft7Validator(schema)
    except SchemaError as e:  # pragma: no cover - checked at request time
        return [f"invalid schema: {e.message}"]
    errors = sorted(validator.iter_errors(output), key=lambda e: list(e.absolute_path))
    out = []
    for e in errors:
        path = "$" + "".join(f".{p}" if isinstance(p, str) else f"[{p}]" for p in e.absolute_path)
        out.append(f"{path}: {e.message}")
    return out


_FENCE = re.compile(r"^```(?:json)?\s*\n(.*)\n```\s*$", re.DOTALL)


def parse_json_object(text: str) -> dict[str, Any] | None:
    """Parse a JSON object from model text, tolerating a Markdown code fence."""
    if not text:
        return None
    s = text.strip()
    m = _FENCE.match(s)
    if m:
        s = m.group(1).strip()
    try:
        value = json.loads(s)
    except json.JSONDecodeError:
        return None
    return value if isinstance(value, dict) else None
