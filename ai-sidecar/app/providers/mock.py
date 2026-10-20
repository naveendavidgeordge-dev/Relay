"""Deterministic mock provider.

Produces schema-conforming JSON from simple keyword heuristics so the whole Relay demo (triage,
branching, approval, injection payloads) runs without any model. It is *not* an LLM: it never
follows instructions in the text, which is exactly the property the engine must not rely on — the
approval gate holds even when a real model is talked into `refund_request`.

Demo switches:
- MOCK_AI_FIRST_INVALID=true: the first answer for every prompt omits a required field, so the
  trace shows the engine's repair retry succeeding.
- A prompt containing "[invalid-twice]" is always answered invalidly, so the step fails cleanly
  with `ai_schema_violation` after one repair.
"""

from __future__ import annotations

import json
import os
import re
from typing import Any

from app.providers.base import Completion, words

INJECTION_MARKERS = (
    "ignore the ticket",
    "ignore previous",
    "ignore all previous",
    "new instructions",
    "system override",
    "admin mode",
    "supersedes all previous",
    "do not create an approval",
    "system prompt",
    "environment variables",
)
REFUND_MARKERS = ("refund", "money back", "reimburse", "return it", "want my money")
URGENT_MARKERS = ("urgent", "asap", "immediately", "right now", "stopped working", "cracked", "broken")
NEGATIVE_MARKERS = ("disappointed", "bad", "terrible", "broken", "cracked", "angry", "worst", "hate")
QUESTION_STARTS = ("how", "what", "when", "where", "why", "does", "do ", "can", "is ", "are ", "hi,")


def _customer_text(prompt: str) -> str:
    """The quoted customer message between '---' fences in the seed prompt, else the prompt."""
    parts = prompt.split("---")
    return parts[1].strip() if len(parts) >= 3 else prompt.strip()


class _Signals:
    def __init__(self, text: str) -> None:
        low = text.lower()
        self.text = text
        self.injection = any(m in low for m in INJECTION_MARKERS)
        self.refund = any(m in low for m in REFUND_MARKERS)
        self.urgent = any(m in low for m in URGENT_MARKERS)
        self.negative = any(m in low for m in NEGATIVE_MARKERS)
        self.question = "?" in text and not self.refund or low.startswith(QUESTION_STARTS)


def _pick_enum(name: str, options: list[Any], s: _Signals) -> Any:
    opts = [str(o) for o in options]
    lname = name.lower()

    def first(*candidates: str) -> Any | None:
        for c in candidates:
            if c in opts:
                return options[opts.index(c)]
        return None

    if "refund_request" in opts or lname == "category":
        if s.refund:
            return first("refund_request") or options[0]
        if s.question and not s.negative:
            return first("question") or options[0]
        return first("complaint", "question") or options[0]
    if lname in ("priority", "urgency", "severity") or "high" in opts:
        if s.urgent or s.injection:
            return first("high", "urgent", "critical") or options[-1]
        if s.question and not s.negative:
            return first("low") or options[0]
        return first("medium", "normal") or options[0]
    if "negative" in opts and "positive" in opts:
        return first("negative") if s.negative or s.refund else first("positive")
    if "urgent" in opts:
        return first("urgent") if s.urgent else first("not_urgent", "normal", "low") or options[-1]
    return options[0]


def _summary(s: _Signals, max_len: int) -> str:
    if s.injection:
        text = (
            "Message contains embedded instructions addressed to the system; "
            "treated as untrusted data and not followed."
        )
    else:
        first_sentence = re.split(r"(?<=[.!?])\s+", s.text.strip())[0] if s.text.strip() else ""
        text = f"Customer: {first_sentence}" if first_sentence else "No message content."
    return text[:max_len]


def _value(name: str, schema: dict[str, Any], s: _Signals) -> Any:
    if "enum" in schema and schema["enum"]:
        return _pick_enum(name, list(schema["enum"]), s)
    if "const" in schema:
        return schema["const"]
    t = schema.get("type")
    if isinstance(t, list):
        t = next((x for x in t if x != "null"), "string")
    if t == "object":
        return _object(schema, s)
    if t == "array":
        item = schema.get("items", {"type": "string"})
        return [_value(name, item, s) for _ in range(int(schema.get("minItems", 0)))]
    if t == "boolean":
        return s.urgent if "urgent" in name.lower() else False
    if t in ("integer", "number"):
        lo = schema.get("minimum", 0)
        return int(lo) if t == "integer" else float(lo)
    max_len = int(schema.get("maxLength", 200))
    if any(k in name.lower() for k in ("summary", "reason", "explanation", "text")):
        out = _summary(s, max_len)
    else:
        out = "n/a"
    min_len = int(schema.get("minLength", 0))
    return out.ljust(min_len, ".")[: max(max_len, min_len)]


def _object(schema: dict[str, Any], s: _Signals) -> dict[str, Any]:
    props: dict[str, Any] = schema.get("properties", {})
    return {k: _value(k, v, s) for k, v in props.items()}


class MockProvider:
    name = "mock"

    def __init__(self) -> None:
        self.first_invalid = os.getenv("MOCK_AI_FIRST_INVALID", "false").lower() == "true"

    async def complete(
        self,
        system: str,
        user: str,
        schema: dict[str, Any],
        *,
        model: str | None,
        repair: bool,
    ) -> Completion:
        signals = _Signals(_customer_text(user))
        answer = _object(schema, signals) if schema.get("type", "object") == "object" else {}
        always_invalid = "[invalid-twice]" in user
        if always_invalid or (self.first_invalid and not repair):
            required = schema.get("required") or list(answer)
            if required:
                answer.pop(required[0], None)
            answer["note"] = "deliberately invalid (mock demo switch)"
        text = json.dumps(answer)
        return Completion(text, words(system) + words(user), words(text), model or "mock-heuristic-v1")
