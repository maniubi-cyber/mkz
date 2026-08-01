"""Java 业务服务 HTTP 客户端。

通过 Spring Cloud 网关异步调用课程、促销、评论等微服务。
网关路由前缀（StripPrefix=1）：
    /cs/**  -> course-service   课程服务
    /prs/** -> promotion-service 优惠券服务
    /rs/**  -> remark-service    点赞评论服务
网关需要 JWT 鉴权，统一通过 Authorization 请求头传递。
经网关的响应会被 WrapperResponseBodyAdvice 包装为统一结构 R<T>：
    {"code": 200, "msg": "OK", "data": <T>, "requestId": "..."}
本客户端负责解包校验并将业务数据返回给上层。
"""
from __future__ import annotations

from typing import Any

import httpx

from app.config.settings import get_settings


class JavaClient:
    """异步 HTTP 客户端，封装对 Java 网关的调用与统一响应解包。"""

    # Java 网关统一响应 R<T> 的成功业务码（ErrorInfo.Code.SUCCESS = 200）
    _SUCCESS_CODE: int = 200

    def __init__(self) -> None:
        settings = get_settings()
        # base_url 指向网关地址，后续请求路径以 /cs、/prs、/rs 开头
        self._client: httpx.AsyncClient = httpx.AsyncClient(
            base_url=settings.java_gateway_url,
            timeout=15.0,
        )

    async def close(self) -> None:
        """关闭底层 HTTP 连接池，应在应用停机时调用。"""
        await self._client.aclose()

    async def _request(
        self,
        method: str,
        path: str,
        *,
        jwt_token: str,
        params: dict | None = None,
        json_body: dict | None = None,
    ) -> Any:
        """发起经网关的请求并解包统一响应 R<T>。

        :param method: HTTP 方法（GET/POST/PUT 等）
        :param path:   相对网关 base_url 的路径，如 /cs/courses/ranking
        :param jwt_token: 用户 JWT，写入 Authorization 请求头鉴权
        :param params:   URL 查询参数
        :param json_body: 请求体（JSON）
        :return: 解包后的业务数据 data；非统一包装结构原样返回
        """
        headers = {"Authorization": jwt_token}
        try:
            resp = await self._client.request(
                method, path, params=params, json=json_body, headers=headers
            )
        except httpx.TimeoutException as exc:
            raise RuntimeError(f"调用 Java 服务超时：{method} {path} - {exc}") from exc
        except httpx.RequestError as exc:
            raise RuntimeError(f"调用 Java 服务网络异常：{method} {path} - {exc}") from exc

        # 网关或服务端返回 4xx/5xx
        if resp.status_code >= 400:
            raise RuntimeError(
                f"Java 服务返回 HTTP 错误：{resp.status_code} {method} {path} - {resp.text}"
            )

        try:
            payload = resp.json()
        except ValueError as exc:
            raise RuntimeError(f"Java 服务响应解析失败：{method} {path} - {exc}") from exc

        return self._unwrap(payload, method=method, path=path)

    @staticmethod
    def _unwrap(payload: Any, *, method: str, path: str) -> Any:
        """解包统一响应 R<T> 并校验业务码。

        识别 {code, msg, data, requestId} 结构：成功返回 data，失败抛 RuntimeError。
        非该结构（如直连服务未包装）原样返回。
        """
        if isinstance(payload, dict) and "code" in payload and "data" in payload:
            code = payload.get("code")
            if code != JavaClient._SUCCESS_CODE:
                raise RuntimeError(
                    f"Java 业务失败：{method} {path} - code={code} msg={payload.get('msg')}"
                )
            return payload.get("data")
        return payload

    async def query_courses_by_name(self, name: str, jwt_token: str) -> list[dict]:
        """按名称查询课程列表。GET /cs/courses/simpleInfo/list

        简化为按名称查课程，name 作为查询参数传递。
        """
        data = await self._request(
            "GET",
            "/cs/courses/simpleInfo/list",
            jwt_token=jwt_token,
            params={"name": name},
        )
        if data is None:
            return []
        if not isinstance(data, list):
            raise RuntimeError("课程查询返回数据格式异常，期望列表")
        return data

    async def get_course_ranking(self, top_n: int, jwt_token: str) -> list[dict]:
        """获取课程排行榜。GET /cs/courses/ranking?topN={top_n}"""
        data = await self._request(
            "GET",
            "/cs/courses/ranking",
            jwt_token=jwt_token,
            params={"topN": top_n},
        )
        if data is None:
            return []
        if not isinstance(data, list):
            raise RuntimeError("课程排行榜返回数据格式异常，期望列表")
        return data

    async def receive_coupon(self, coupon_id: int, jwt_token: str) -> dict:
        """领取优惠券。POST /prs/user-coupons/{coupon_id}/receive

        Java 侧为 void 返回，解包后 data 为 None，这里归一化为成功信息。
        """
        data = await self._request(
            "POST",
            f"/prs/user-coupons/{coupon_id}/receive",
            jwt_token=jwt_token,
        )
        if isinstance(data, dict):
            return data
        return {"success": True, "coupon_id": coupon_id}

    async def like_liked(self, biz_id: int, jwt_token: str) -> dict:
        """点赞。POST /rs/likes，请求体 {bizId, liked}

        对应 remark-service 的 LikeRecordFormDTO（字段 bizId/bizType/liked）。
        """
        data = await self._request(
            "POST",
            "/rs/likes",
            jwt_token=jwt_token,
            json_body={"bizId": biz_id, "liked": True},
        )
        if isinstance(data, dict):
            return data
        return {"success": True, "biz_id": biz_id}

    async def course_updown(self, course_id: int, action: str, jwt_token: str) -> dict:
        """课程上架/下架。PUT /cs/courses/{course_id}/{action}

        :param action: "up" 上架 / "down" 下架
        """
        if action not in ("up", "down"):
            raise RuntimeError(f"非法的上下架动作：{action}，仅支持 up/down")
        data = await self._request(
            "PUT",
            f"/cs/courses/{course_id}/{action}",
            jwt_token=jwt_token,
        )
        if isinstance(data, dict):
            return data
        return {"success": True, "course_id": course_id, "action": action}

    async def handle_violation_comment(self, comment_id: int, action: str, jwt_token: str) -> dict:
        """处理违规评论。PUT /rs/comments/{comment_id}/{action}

        :param action: "hide" 隐藏 / "delete" 删除
        """
        if action not in ("hide", "delete"):
            raise RuntimeError(f"非法的评论处理动作：{action}，仅支持 hide/delete")
        data = await self._request(
            "PUT",
            f"/rs/comments/{comment_id}/{action}",
            jwt_token=jwt_token,
        )
        if isinstance(data, dict):
            return data
        return {"success": True, "comment_id": comment_id, "action": action}
