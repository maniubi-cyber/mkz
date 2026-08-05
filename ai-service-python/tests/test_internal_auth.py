"""Tests for the internal HMAC signature auth (app.core.internal_auth)."""

from __future__ import annotations

import time

import pytest
from fastapi import HTTPException

from app.core.internal_auth import (
    HEADER_NONCE,
    HEADER_SIGNATURE,
    HEADER_TIMESTAMP,
    _compute_signature,
    make_internal_headers,
    verify_internal_signature,
)


class FakeRequest:
    """Minimal stand-in for starlette Request (only headers/url/method used)."""

    def __init__(self, method: str, path: str, headers: dict[str, str]):
        self.method = method
        self.url = type("U", (), {"path": path})()
        self.headers = headers


def test_missing_headers_are_rejected():
    req = FakeRequest("POST", "/ai/search", {})
    with pytest.raises(HTTPException) as exc:
        verify_internal_signature(req)
    assert exc.value.status_code == 401


def test_well_signed_request_passes():
    headers = make_internal_headers(method="POST", path="/ai/search")
    req = FakeRequest("POST", "/ai/search", headers)
    # 不应抛异常
    verify_internal_signature(req)


def test_wrong_signature_is_rejected():
    headers = make_internal_headers(method="POST", path="/ai/search")
    tampered = dict(headers)
    # 篡改 nonce → 签名不再匹配
    tampered[HEADER_NONCE] = "forged-nonce"
    req = FakeRequest("POST", "/ai/search", tampered)
    with pytest.raises(HTTPException) as exc:
        verify_internal_signature(req)
    assert exc.value.status_code == 403


def test_signature_is_bound_to_path():
    # 用 /ai/search 的签名访问 /ai/chat → 必须被拒（防跨端点重放）
    headers = make_internal_headers(method="POST", path="/ai/search")
    req = FakeRequest("POST", "/ai/chat", headers)
    with pytest.raises(HTTPException) as exc:
        verify_internal_signature(req)
    assert exc.value.status_code == 403


def test_signature_is_bound_to_method():
    headers = make_internal_headers(method="POST", path="/ai/search")
    req = FakeRequest("GET", "/ai/search", headers)
    with pytest.raises(HTTPException) as exc:
        verify_internal_signature(req)
    assert exc.value.status_code == 403


def test_expired_timestamp_is_rejected(monkeypatch):
    headers = make_internal_headers(method="POST", path="/ai/search")
    old_ts = str(int(time.time() * 1000) - 3600_000)  # 1 小时前
    headers[HEADER_TIMESTAMP] = old_ts
    req = FakeRequest("POST", "/ai/search", headers)
    with pytest.raises(HTTPException) as exc:
        verify_internal_signature(req)
    assert exc.value.status_code == 401


def test_disabled_auth_always_passes(monkeypatch):
    monkeypatch.setattr("app.core.internal_auth.settings.AI_INTERNAL_ENABLED", False)
    req = FakeRequest("POST", "/ai/search", {})
    verify_internal_signature(req)  # 不应抛异常


def test_compute_signature_is_deterministic():
    a = _compute_signature("GET", "/ai/models", "123", "abc", "secret")
    b = _compute_signature("GET", "/ai/models", "123", "abc", "secret")
    c = _compute_signature("GET", "/ai/models", "123", "abc", "other-secret")
    assert a == b
    assert a != c
