"""Provider adapter contract."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Protocol


@dataclass(frozen=True)
class Completion:
    text: str
    tokens_prompt: int
    tokens_completion: int
    model: str | None


class ProviderError(Exception):
    """A provider call failed.

    `transient` errors (timeouts, 5xx, 429) map to HTTP 503/429 so the Java engine retries with
    backoff; permanent ones (bad credentials, bad request) map to 400 and fail the step.
    """

    def __init__(self, message: str, *, transient: bool, status: int | None = None) -> None:
        super().__init__(message)
        self.transient = transient
        self.status = status


class Provider(Protocol):
    name: str

    async def complete(
        self,
        system: str,
        user: str,
        schema: dict[str, Any],
        *,
        model: str | None,
        repair: bool,
    ) -> Completion: ...


def classify_status(status: int) -> ProviderError:
    if status == 429:
        return ProviderError("provider rate limited", transient=True, status=429)
    if status >= 500:
        return ProviderError(f"provider returned {status}", transient=True, status=status)
    return ProviderError(f"provider rejected the request ({status})", transient=False, status=status)


def words(text: str) -> int:
    """Rough token estimate when a provider does not report usage."""
    return max(1, len(text.split()))
