"""Real model providers over HTTP (httpx, hard timeouts).

- gemini: Google Generative Language API (`x-goog-api-key` header, JSON response mime type)
- groq: OpenAI-compatible chat completions (`response_format: json_object`)
- ollama: local `/api/chat` with the JSON Schema passed as `format`
- openai: any OpenAI-compatible endpoint (incl. `scripts/mock_provider.py`)

API keys are read from the environment, sent only in headers, and never logged or returned.
"""

from __future__ import annotations

import json
import os
from typing import Any

import httpx

from app.providers.base import Completion, ProviderError, classify_status, words


def _timeout() -> httpx.Timeout:
    seconds = float(os.getenv("AI_TIMEOUT_S", "25"))
    return httpx.Timeout(seconds, connect=min(5.0, seconds))


async def _post(url: str, headers: dict[str, str], body: dict[str, Any]) -> dict[str, Any]:
    try:
        async with httpx.AsyncClient(timeout=_timeout()) as client:
            res = await client.post(url, headers=headers, json=body)
    except httpx.TimeoutException as e:
        raise ProviderError("provider timed out", transient=True) from e
    except httpx.HTTPError as e:
        raise ProviderError(f"provider unreachable: {type(e).__name__}", transient=True) from e
    if res.status_code >= 400:
        raise classify_status(res.status_code)
    data: dict[str, Any] = res.json()
    return data


class GeminiProvider:
    name = "gemini"

    async def complete(
        self, system: str, user: str, schema: dict[str, Any], *, model: str | None, repair: bool
    ) -> Completion:
        key = os.getenv("GEMINI_API_KEY", "")
        if not key:
            raise ProviderError("GEMINI_API_KEY is not set", transient=False)
        m = model or os.getenv("AI_MODEL") or "gemini-2.5-flash"
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{m}:generateContent"
        body = {
            "systemInstruction": {"parts": [{"text": system}]},
            "contents": [{"role": "user", "parts": [{"text": user}]}],
            "generationConfig": {"responseMimeType": "application/json", "temperature": 0},
        }
        data = await _post(url, {"x-goog-api-key": key}, body)
        try:
            text = "".join(p.get("text", "") for p in data["candidates"][0]["content"]["parts"])
        except (KeyError, IndexError) as e:
            raise ProviderError("provider returned no candidate", transient=True) from e
        usage = data.get("usageMetadata", {})
        return Completion(
            text,
            int(usage.get("promptTokenCount", words(system + user))),
            int(usage.get("candidatesTokenCount", words(text))),
            m,
        )


class OpenAICompatibleProvider:
    """Groq, OpenAI, or scripts/mock_provider.py."""

    def __init__(self, name: str, base_url: str, key_env: str, default_model: str) -> None:
        self.name = name
        self.base_url = base_url.rstrip("/")
        self.key_env = key_env
        self.default_model = default_model

    async def complete(
        self, system: str, user: str, schema: dict[str, Any], *, model: str | None, repair: bool
    ) -> Completion:
        key = os.getenv(self.key_env, "")
        if not key:
            raise ProviderError(f"{self.key_env} is not set", transient=False)
        m = model or os.getenv("AI_MODEL") or self.default_model
        body: dict[str, Any] = {
            "model": m,
            "temperature": 0,
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
        }
        if self.name == "groq":
            body["response_format"] = {"type": "json_object"}
        data = await _post(
            f"{self.base_url}/chat/completions", {"Authorization": f"Bearer {key}"}, body
        )
        try:
            text = data["choices"][0]["message"]["content"] or ""
        except (KeyError, IndexError) as e:
            raise ProviderError("provider returned no choice", transient=True) from e
        usage = data.get("usage", {})
        return Completion(
            text,
            int(usage.get("prompt_tokens", words(system + user))),
            int(usage.get("completion_tokens", words(text))),
            m,
        )


class OllamaProvider:
    name = "ollama"

    async def complete(
        self, system: str, user: str, schema: dict[str, Any], *, model: str | None, repair: bool
    ) -> Completion:
        base = os.getenv("OLLAMA_URL", "http://localhost:11434").rstrip("/")
        m = model or os.getenv("AI_MODEL") or "llama3.1"
        body = {
            "model": m,
            "stream": False,
            "format": schema,
            "options": {"temperature": 0},
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
        }
        data = await _post(f"{base}/api/chat", {}, body)
        text = str(data.get("message", {}).get("content", ""))
        return Completion(
            text,
            int(data.get("prompt_eval_count", words(system + user))),
            int(data.get("eval_count", words(text))),
            m,
        )


def describe(value: Any) -> str:
    """Compact JSON for logs (never used for secrets)."""
    return json.dumps(value)[:200]
