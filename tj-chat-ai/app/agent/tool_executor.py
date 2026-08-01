"""工具执行器：三级风险分流 + 注入拦截 + JSON 解析重试 + 工具异常降级。

编排层（ReactAgent）通过 ToolExecutor 统一调用工具，由执行器根据工具
风险等级决定执行策略：
    LOW    只读操作，直接执行，对结果做非空校验防止 LLM 编造
    MEDIUM 写操作，执行前用 Redis SET NX EX 做幂等校验，避免重复提交
    HIGH   敏感操作，触发 Human-in-the-Loop 审批，审批通过后才执行

同时在输入侧做提示词注入拦截，在执行侧做 JSON 解析重试与异常降级，
保证工具调用链路的安全性与健壮性。
"""
from __future__ import annotations

import hashlib
import json
import logging
from typing import Optional

import redis

from app.agent.approval import ApprovalService, ApprovalStatus
from app.config.settings import Settings, get_settings
from app.guard.injection import contains_injection
from app.tools.base import ToolRegistry, ToolResult, ToolRiskLevel

logger = logging.getLogger(__name__)


class ToolExecutor:
    """工具执行器：统一封装工具调用的安全策略与异常处理。"""

    # MEDIUM 级工具幂等锁 TTL（秒），5 分钟内相同参数视为重复操作
    _IDEMPOTENT_TTL = 300

    def __init__(
        self,
        registry: ToolRegistry,
        approval_service: ApprovalService,
        settings: Optional[Settings] = None,
    ) -> None:
        self._registry = registry
        self._approval_service = approval_service
        self._settings = settings or get_settings()
        # Redis 用于 MEDIUM 级工具的幂等校验（SET NX EX）
        self._redis = redis.Redis(
            host=self._settings.redis_host,
            port=self._settings.redis_port,
            password=self._settings.redis_password,
            db=self._settings.redis_db,
            decode_responses=True,
        )

    async def execute(
        self,
        tool_name: str,
        tool_args: dict,
        user_id: str,
        session_id: str,
        jwt_token: str,
    ) -> ToolResult:
        """工具执行入口，三级风险分流。"""
        # 1. 输入侧注入拦截：检查 tool_args 序列化后是否含注入模式
        args_text = json.dumps(tool_args, ensure_ascii=False)
        is_injection, pattern = contains_injection(args_text)
        if is_injection:
            logger.warning(
                "用户 %s 调用工具 %s 时检测到提示词注入: %s",
                user_id,
                tool_name,
                pattern,
            )
            return ToolResult(
                success=False,
                error_message="检测到提示词注入，已拦截该工具调用。",
            )

        # 2. 查询工具风险等级（未注册工具在此抛 KeyError，由上层捕获）
        _func, level = self._registry.get(tool_name)

        # 3. 按风险等级分流执行
        if level == ToolRiskLevel.LOW:
            return await self._execute_low_risk(
                tool_name, tool_args, user_id, session_id, jwt_token
            )
        if level == ToolRiskLevel.MEDIUM:
            return await self._execute_medium_risk(
                tool_name, tool_args, user_id, session_id, jwt_token
            )
        return await self._execute_high_risk(
            tool_name, tool_args, user_id, session_id, jwt_token
        )

    async def _execute_low_risk(
        self,
        tool_name: str,
        tool_args: dict,
        user_id: str,
        session_id: str,
        jwt_token: str,
    ) -> ToolResult:
        """低风险（只读）：直接执行 + 结果非空校验防编造。"""
        result = await self._execute_with_retry(tool_name, tool_args, jwt_token)
        if result.success:
            self._validate_result(tool_name, result)
        return result

    async def _execute_medium_risk(
        self,
        tool_name: str,
        tool_args: dict,
        user_id: str,
        session_id: str,
        jwt_token: str,
    ) -> ToolResult:
        """中风险（写操作）：执行前用 Redis SET NX EX 校验幂等。

        相同用户 + 相同工具 + 相同参数在 TTL 内视为重复操作，直接拒绝，
        避免用户重复领取优惠券、重复点赞等。
        """
        idempotent_key = self._idempotent_key(user_id, tool_name, tool_args)
        # SET NX EX：仅当 key 不存在时设置，返回 True 表示首次操作
        acquired = self._redis.set(
            idempotent_key, "1", nx=True, ex=self._IDEMPOTENT_TTL
        )
        if not acquired:
            logger.info(
                "用户 %s 重复调用中风险工具 %s，已被幂等校验拦截",
                user_id,
                tool_name,
            )
            return ToolResult(
                success=False,
                error_message="操作过于频繁，请勿重复提交相同操作。",
            )
        return await self._execute_with_retry(tool_name, tool_args, jwt_token)

    async def _execute_high_risk(
        self,
        tool_name: str,
        tool_args: dict,
        user_id: str,
        session_id: str,
        jwt_token: str,
    ) -> ToolResult:
        """高风险（敏感操作）：触发 Human-in-the-Loop 审批。

        不在此阻塞等待，由上层（ReactAgent）决定是否轮询；同时也提供
        wait_and_execute_high_risk 方法供需要同步等待的场景使用。
        """
        description = f"工具 {tool_name} 参数 {tool_args} 需要人工审批"
        approval_id = self._approval_service.create_approval(
            user_id=user_id,
            session_id=session_id,
            tool_name=tool_name,
            tool_args=tool_args,
            description=description,
        )
        logger.info(
            "用户 %s 调用高风险工具 %s，已创建审批单 %s",
            user_id,
            tool_name,
            approval_id,
        )
        return ToolResult(
            success=False,
            requires_approval=True,
            approval_id=approval_id,
            approval_status=ToolResult.PENDING,
            error_message="等待人工审批",
        )

    async def wait_and_execute_high_risk(
        self,
        tool_name: str,
        tool_args: dict,
        user_id: str,
        session_id: str,
        jwt_token: str,
        approval_id: str,
    ) -> ToolResult:
        """等待审批通过后执行高级工具（同步阻塞场景）。

        审批通过则执行工具；拒绝或超时则返回对应的失败结果。
        """
        status = self._approval_service.wait_for_approval(approval_id)
        if status == ApprovalStatus.APPROVED:
            return await self._execute_with_retry(tool_name, tool_args, jwt_token)
        if status == ApprovalStatus.REJECTED:
            return ToolResult(
                success=False,
                error_message="审批已拒绝",
                approval_id=approval_id,
                approval_status=ToolResult.REJECTED,
            )
        # PENDING 超时
        return ToolResult(
            success=False,
            error_message="审批超时",
            approval_id=approval_id,
            approval_status=ToolResult.PENDING,
        )

    async def _execute_with_retry(
        self,
        tool_name: str,
        tool_args: dict,
        jwt_token: str,
        retry_count: int = 2,
    ) -> ToolResult:
        """执行工具，工具异常不中断循环，错误信息作为结果回传由 LLM 决策。

        - 调用 registry.invoke，jwt_token 与 tool_args 均以 kwargs 传入，
          java_client 由注册表自动注入。
        - JSON 解析失败（工具返回非合法 JSON）：不重试，直接降级回传，
          由 LLM 自行决定降级回复或切换工具。
        - 其他异常（如接口超时）：重试 retry_count 次，重试耗尽后降级回传。
        """
        last_error = ""
        for attempt in range(retry_count + 1):
            try:
                result = await self._registry.invoke(
                    tool_name, jwt_token=jwt_token, **tool_args
                )
                return result
            except json.JSONDecodeError as exc:
                # 工具返回非合法 JSON：不重试，直接降级回传由 LLM 决策
                logger.warning("工具 %s 返回非合法JSON: %s", tool_name, exc)
                return ToolResult(
                    success=False,
                    error_message=f"工具返回的数据不是合法JSON格式: {exc}。你可以尝试其他方式或换一种方式回答。",
                )
            except Exception as exc:
                # 其他异常（如接口超时）重试，重试耗尽后降级回传
                last_error = str(exc)
                logger.warning(
                    "工具 %s 第 %d 次执行异常: %s",
                    tool_name,
                    attempt + 1,
                    exc,
                )
                if attempt < retry_count:
                    continue
        return ToolResult(
            success=False,
            error_message=f"工具执行异常（重试{retry_count}次后仍失败）: {last_error}。你可以尝试其他方式或换一种方式回答。",
        )

    def _validate_result(self, tool_name: str, result: ToolResult) -> None:
        """校验低风险工具返回结果，防止编造不存在的信息。

        - 课程查询 / 排行榜：返回列表非空校验
        - 若数据为空，将 success 置为 False 并在 error_message 标注
          "未查询到相关数据"，引导 LLM 如实告知用户而非编造。
        """
        if tool_name not in ("query_course", "get_ranking"):
            return
        data = result.data
        if data is None or (isinstance(data, list) and len(data) == 0):
            result.success = False
            result.error_message = "未查询到相关数据"

    @staticmethod
    def _idempotent_key(user_id: str, tool_name: str, tool_args: dict) -> str:
        """构造幂等校验 key：用户 + 工具 + 参数指纹（MD5）。

        使用 MD5 对排序后的参数生成稳定指纹，保证同一参数跨进程、
        跨重启得到相同的 key（Python 内置 hash() 跨进程不稳定）。
        """
        args_fingerprint = hashlib.md5(
            json.dumps(tool_args, sort_keys=True, ensure_ascii=False).encode("utf-8")
        ).hexdigest()
        return f"idempotent:{user_id}:{tool_name}:{args_fingerprint}"