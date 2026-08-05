"""
Internal Service Authentication (HMAC-SHA256 Signed Requests)

Validates that incoming requests to AI endpoints come from the Java backend.

Signature scheme (must match Java `AiInternalTokenProvider`):
    payload = f"{METHOD}:{path}:{timestamp}:{nonce}"
    signature = base64(HMAC-SHA256(payload, AI_INTERNAL_SECRET))

Headers sent by Java backend:
    X-Internal-Timestamp   epoch milliseconds
    X-Internal-Nonce       random string (anti-replay)
    X-Internal-Signature   base64 HMAC-SHA256 over the payload

The check:
    1. If AI_INTERNAL_ENABLED is False → always pass (test environments).
    2. Timestamp must be within AI_INTERNAL_TOLERANCE_SECONDS of now.
    3. Computed signature must match (constant-time compare).
"""

from __future__ import annotations

import base64
import hashlib
import hmac
import time

from fastapi import HTTPException, Request

from app.core.config import settings

HEADER_TIMESTAMP = "X-Internal-Timestamp"
HEADER_NONCE = "X-Internal-Nonce"
HEADER_SIGNATURE = "X-Internal-Signature"


def _compute_signature(method: str, path: str, timestamp: str, nonce: str, secret: str) -> str:
    """Compute HMAC-SHA256 signature over the canonical payload."""
    payload = f"{method}:{path}:{timestamp}:{nonce}".encode("utf-8")
    digest = hmac.new(secret.encode("utf-8"), payload, hashlib.sha256).digest()
    return base64.b64encode(digest).decode("ascii")


def _safe_equal(a: str, b: str) -> bool:
    """Constant-time string comparison."""
    return hmac.compare_digest(a.encode("ascii"), b.encode("ascii"))


def verify_internal_signature(request: Request) -> None:
    """Validate the internal signature on the request; raise 401/403 on failure."""
    if not settings.AI_INTERNAL_ENABLED:
        return

    timestamp = request.headers.get(HEADER_TIMESTAMP)
    nonce = request.headers.get(HEADER_NONCE)
    signature = request.headers.get(HEADER_SIGNATURE)

    if not timestamp or not nonce or not signature:
        raise HTTPException(
            status_code=401,
            detail="Missing internal signature headers",
        )

    # 1) Timestamp freshness (tolerates clock skew between containers)
    try:
        ts = int(timestamp)
    except ValueError:
        raise HTTPException(status_code=401, detail="Invalid signature timestamp") from None

    tolerance = settings.AI_INTERNAL_TOLERANCE_SECONDS * 1000
    if abs(time.time() * 1000 - ts) > tolerance:
        raise HTTPException(status_code=401, detail="Signature timestamp expired")

    # 2) HMAC verification (method + path binding prevents cross-endpoint replay)
    method = request.method or "GET"
    path = request.url.path or "/"
    expected = _compute_signature(method, path, timestamp, nonce, settings.AI_INTERNAL_SECRET)
    if not _safe_equal(expected, signature):
        raise HTTPException(status_code=403, detail="Invalid internal signature")


def make_internal_headers(method: str = "GET", path: str = "/", secret: str | None = None) -> dict[str, str]:
    """Build signature headers (used by integration tests / local debugging)."""
    secret = secret or settings.AI_INTERNAL_SECRET
    timestamp = str(int(time.time() * 1000))
    nonce = base64.urlsafe_b64encode(__import__("uuid").uuid4().bytes).decode("ascii").rstrip("=")
    return {
        HEADER_TIMESTAMP: timestamp,
        HEADER_NONCE: nonce,
        HEADER_SIGNATURE: _compute_signature(method, path, timestamp, nonce, secret),
    }
