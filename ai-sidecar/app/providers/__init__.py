"""Provider registry selected by AI_PROVIDER (mock | gemini | groq | ollama | openai)."""

from __future__ import annotations

import os

from app.providers.base import Provider
from app.providers.http_providers import GeminiProvider, OllamaProvider, OpenAICompatibleProvider
from app.providers.mock import MockProvider


def get_provider(name: str | None = None) -> Provider:
    choice = (name or os.getenv("AI_PROVIDER", "mock")).strip().lower()
    if choice == "mock":
        return MockProvider()
    if choice == "gemini":
        return GeminiProvider()
    if choice == "groq":
        return OpenAICompatibleProvider(
            "groq", "https://api.groq.com/openai/v1", "GROQ_API_KEY", "llama-3.3-70b-versatile"
        )
    if choice == "ollama":
        return OllamaProvider()
    if choice == "openai":
        return OpenAICompatibleProvider(
            "openai",
            os.getenv("OPENAI_BASE_URL", "http://localhost:9001/v1"),
            "OPENAI_API_KEY",
            "alpha-small",
        )
    raise ValueError(f"unknown AI_PROVIDER '{choice}' (mock, gemini, groq, ollama, openai)")


__all__ = ["Provider", "get_provider"]
