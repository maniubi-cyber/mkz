"""ReAct Agent：基于 ReAct 循环实现"思考→调用工具→观察结果→再决策"。

手动实现 ReAct 循环（而非使用 LangChain AgentExecutor），以便完全控制
工具执行流程，集成 ToolExecutor 的三级风险分流与 Human-in-the-Loop 审批。

非流式 chat() 返回完整回答；流式 stream() 以 SSE 事件逐 token 产出
思考、工具调用、观察与最终回答，前端可实时渲染 Agent 的推理过程。
"""
from __future__ import annotations

import json
import logging
import re
from typing import AsyncIterator, Optional

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage
from langchain_openai import ChatOpenAI

from app.agent.tool_executor import ToolExecutor
from app.config.settings import Settings, get_settings
from app.guard.injection import contains_injection, sanitize_input
from app.memory.chat_memory import SlidingWindowMemory
from app.tools.base import ToolResult
from app.utils.prompts import REACT_SYSTEM_PROMPT, REACT_TEMPLATE

logger = logging.getLogger(__name__)

# ReAct 输出解析正则
# Action: 工具名 + Action Input: JSON 参数
_ACTION_RE = re.compile(
    r"Action:\s*(\w+)\s*\n+\s*Action Input:\s*(\{.*\})",
    re.DOTALL,
)
# Final Answer: 最终回答
_FINAL_ANSWER_RE = re.compile(r"Final Answer:\s*(.*)", re.DOTALL)
# Thought: 思考过程（到下一个 Action/Final Answer 之前）
_THOUGHT_RE = re.compile(
    r"Thought:\s*(.*?)(?=\n(?:Action|Final Answer):|$)",
    re.DOTALL,
)

# 消息类型 -> 展示角色名
_ROLE_LABELS = {"human": "用户", "ai": "小天", "system": "系统"}


class ReactAgent:
    """基于 ReAct 推理范式的对话智能体。"""

    def __init__(
        self,
        memory: SlidingWindowMemory,
        tool_executor: ToolExecutor,
        settings: Optional[Settings] = None,
    ) -> None:
        self._settings = settings or get_settings()
        self._memory = memory
        self._tool_executor = tool_executor
        # ReAct 最大迭代次数，防止无限循环
        self._max_iterations = 5
        # 主 LLM（非流式）：用于 ReAct 推理与工具决策
        self._llm = ChatOpenAI(
            model=self._settings.llm_model,
            base_url=self._settings.llm_base_url,
            api_key=self._settings.llm_api_key,
            temperature=self._settings.llm_temperature,
            max_tokens=self._settings.llm_max_tokens,
            timeout=self._settings.llm_timeout_seconds,
            max_retries=self._settings.llm_max_retries,
        )
        # 流式 LLM：用于最终回答的逐 token 产出
        self._stream_llm = ChatOpenAI(
            model=self._settings.llm_model,
            base_url=self._settings.llm_base_url,
            api_key=self._settings.llm_api_key,
            temperature=self._settings.llm_stream_temperature,
            max_tokens=self._settings.llm_max_tokens,
            timeout=self._settings.llm_timeout_seconds,
            max_retries=self._settings.llm_max_retries,
            streaming=True,
        )

    async def chat(
        self,
        session_id: str,
        user_id: str,
        message: str,
        jwt_token: str,
    ) -> str:
        """非流式对话：ReAct 循环推理 + 工具调用 + 记忆持久化。"""
        # 1. 输入清洗与注入拦截
        message = sanitize_input(message)
        is_injection, pattern = contains_injection(message)
        if is_injection:
            logger.warning("用户 %s 输入检测到提示词注入: %s", user_id, pattern)
            final_answer = "抱歉，您的输入包含不安全的指令，我无法处理。"
            self._memory.add_message(session_id, user_id, HumanMessage(content=message))
            self._memory.add_message(session_id, user_id, AIMessage(content=final_answer))
            return final_answer

        # 2. 尝试从 checkpoint 恢复记忆（断线恢复）
        self._memory.restore_from_checkpoint(session_id, user_id)

        # 3. 加载上下文消息
        history_messages = self._memory.get_messages(session_id, user_id)

        # 4. ReAct 循环推理
        final_answer = await self._react_loop(
            session_id, user_id, message, jwt_token, history_messages
        )

        # 5. 持久化本轮对话
        self._memory.add_message(session_id, user_id, HumanMessage(content=message))
        self._memory.add_message(session_id, user_id, AIMessage(content=final_answer))

        return final_answer

    async def _react_loop(
        self,
        session_id: str,
        user_id: str,
        question: str,
        jwt_token: str,
        history_messages: list[BaseMessage],
    ) -> str:
        """执行 ReAct 循环：思考→行动→观察→再决策。"""
        summary, history = self._split_context(history_messages)
        # 累积的推理过程（Thought/Action/Observation），拼入后续 prompt
        scratchpad = ""

        for _iteration in range(self._max_iterations):
            prompt = REACT_TEMPLATE.format(
                system_prompt=REACT_SYSTEM_PROMPT,
                summary=summary,
                history=history,
                question=question,
            )
            if scratchpad:
                prompt += "\n" + scratchpad

            # 调用 LLM（同步阻塞调用）
            llm_output = self._extract_text(self._llm.invoke(prompt))

            # 解析 LLM 输出
            thought = self._parse_thought(llm_output)
            action, action_input, json_failed = self._parse_action(llm_output)
            final_answer = self._parse_final_answer(llm_output)

            # Action Input 不是合法 JSON：携带格式纠正提示词让 LLM 重新生成
            if action is not None and json_failed:
                scratchpad += (
                    f"\nThought: {thought}\n"
                    f"Action: {action}\n"
                    f"Action Input: [解析失败，非合法JSON]\n"
                    f"Observation: 上一次 Action Input 不是合法的 JSON 格式，"
                    f"请重新输出合法的 JSON 参数（例如 {{\"key\": \"value\"}}）。\n"
                )
                continue

            # 有工具调用则执行工具
            if action is not None:
                tool_result = await self._tool_executor.execute(
                    tool_name=action,
                    tool_args=action_input,
                    user_id=user_id,
                    session_id=session_id,
                    jwt_token=jwt_token,
                )
                # 高风险工具触发审批：告知用户并结束本轮
                if tool_result.requires_approval:
                    return (
                        f"该操作需要人工审批，已提交审批单"
                        f"（审批ID：{tool_result.approval_id}）。"
                        "审批通过后将执行操作，请耐心等待。"
                    )
                # 拼入观察结果，继续循环
                observation = self._format_observation(tool_result)
                scratchpad += (
                    f"\nThought: {thought}\n"
                    f"Action: {action}\n"
                    f"Action Input: {json.dumps(action_input, ensure_ascii=False)}\n"
                    f"Observation: {observation}\n"
                )
                continue

            # 无工具调用：若含 Final Answer 则返回，否则整段当作回答
            if final_answer is not None:
                return final_answer.strip()
            return llm_output.strip()

        # 达到最大迭代次数仍未得到最终回答，强制要求 LLM 总结
        prompt = REACT_TEMPLATE.format(
            system_prompt=REACT_SYSTEM_PROMPT,
            summary=summary,
            history=history,
            question=question,
        )
        prompt += "\n" + scratchpad
        prompt += "\n请根据以上信息直接给出最终回答。\nFinal Answer:"
        return self._extract_text(self._llm.invoke(prompt)).strip()

    async def stream(
        self,
        session_id: str,
        user_id: str,
        message: str,
        jwt_token: str,
    ) -> AsyncIterator[str]:
        """流式对话，yield SSE 事件字符串。

        事件类型：
        - thought     思考过程
        - action      工具调用（含 input）
        - observation 工具结果
        - answer      最终回答（分块）
        - approval    审批请求
        - [DONE]      结束
        """
        # 1. 输入清洗与注入拦截
        message = sanitize_input(message)
        is_injection, pattern = contains_injection(message)
        if is_injection:
            logger.warning("用户 %s 输入检测到提示词注入: %s", user_id, pattern)
            reject = "抱歉，您的输入包含不安全的指令，我无法处理。"
            yield self._sse_event("answer", content=reject)
            yield "data: [DONE]"
            self._memory.add_message(session_id, user_id, HumanMessage(content=message))
            self._memory.add_message(session_id, user_id, AIMessage(content=reject))
            return

        # 2. 恢复记忆 + 加载上下文
        self._memory.restore_from_checkpoint(session_id, user_id)
        history_messages = self._memory.get_messages(session_id, user_id)
        summary, history = self._split_context(history_messages)

        # 3. ReAct 循环
        scratchpad = ""
        approval_msg: Optional[str] = None

        for _iteration in range(self._max_iterations):
            prompt = REACT_TEMPLATE.format(
                system_prompt=REACT_SYSTEM_PROMPT,
                summary=summary,
                history=history,
                question=message,
            )
            if scratchpad:
                prompt += "\n" + scratchpad

            llm_output = self._extract_text(self._llm.invoke(prompt))
            thought = self._parse_thought(llm_output)
            action, action_input, json_failed = self._parse_action(llm_output)
            final_answer = self._parse_final_answer(llm_output)

            # 推送思考过程
            if thought:
                yield self._sse_event("thought", content=thought)

            # Action Input 不是合法 JSON：携带格式纠正提示词让 LLM 重新生成
            if action is not None and json_failed:
                scratchpad += (
                    f"\nThought: {thought}\n"
                    f"Action: {action}\n"
                    f"Action Input: [解析失败，非合法JSON]\n"
                    f"Observation: 上一次 Action Input 不是合法的 JSON 格式，"
                    f"请重新输出合法的 JSON 参数（例如 {{\"key\": \"value\"}}）。\n"
                )
                continue

            # 有工具调用则执行
            if action is not None:
                yield self._sse_event("action", content=action, input=action_input)
                tool_result = await self._tool_executor.execute(
                    tool_name=action,
                    tool_args=action_input,
                    user_id=user_id,
                    session_id=session_id,
                    jwt_token=jwt_token,
                )
                observation = self._format_observation(tool_result)
                yield self._sse_event("observation", content=observation)

                # 高风险工具触发审批
                if tool_result.requires_approval:
                    yield self._sse_event(
                        "approval",
                        approval_id=tool_result.approval_id,
                        content=f"操作已提交审批，审批ID={tool_result.approval_id}",
                    )
                    approval_msg = (
                        f"该操作需要人工审批，已提交审批单"
                        f"（审批ID：{tool_result.approval_id}）。"
                        "审批通过后将执行操作，请耐心等待。"
                    )
                    break

                # 拼入观察，继续循环
                scratchpad += (
                    f"\nThought: {thought}\n"
                    f"Action: {action}\n"
                    f"Action Input: {json.dumps(action_input, ensure_ascii=False)}\n"
                    f"Observation: {observation}\n"
                )
                continue

            # 无工具调用：可进入最终回答阶段
            break

        # 4. 生成最终回答
        if approval_msg is not None:
            # 审批场景：一次性推送提示
            yield self._sse_event("answer", content=approval_msg)
            answer_text = approval_msg
        else:
            # 用流式 LLM 逐 token 生成最终回答
            final_prompt = self._build_final_answer_prompt(
                summary, history, message, scratchpad
            )
            answer_text = ""
            for chunk in self._stream_llm.stream(final_prompt):
                token = self._extract_text(chunk)
                if token:
                    answer_text += token
                    yield self._sse_event("answer", content=token)
            if not answer_text:
                answer_text = "抱歉，我暂时无法回答您的问题。"
                yield self._sse_event("answer", content=answer_text)

        yield "data: [DONE]"

        # 5. 持久化本轮对话
        self._memory.add_message(session_id, user_id, HumanMessage(content=message))
        self._memory.add_message(session_id, user_id, AIMessage(content=answer_text))

    # ===== 上下文与解析辅助方法 =====

    @staticmethod
    def _split_context(messages: list[BaseMessage]) -> tuple[str, str]:
        """将记忆消息拆分为语义摘要与历史对话文本。

        SystemMessage 视为摘要，其余按角色格式化为历史对话。
        """
        summary = ""
        history_lines: list[str] = []
        for msg in messages:
            if msg.type == "system":
                summary = str(msg.content)
            else:
                role = _ROLE_LABELS.get(msg.type, msg.type)
                history_lines.append(f"{role}: {msg.content}")
        history = "\n".join(history_lines) if history_lines else "（无）"
        return summary, history

    @staticmethod
    def _format_observation(result: ToolResult) -> str:
        """将工具结果格式化为 Observation 文本。"""
        if result.success:
            return json.dumps(result.data, ensure_ascii=False, default=str)
        return f"执行失败：{result.error_message}"

    @staticmethod
    def _parse_thought(text: str) -> str:
        """解析 Thought 字段。"""
        match = _THOUGHT_RE.search(text)
        return match.group(1).strip() if match else ""

    @staticmethod
    def _parse_action(text: str) -> tuple[Optional[str], dict, bool]:
        """解析 Action 与 Action Input 字段，返回 (工具名, 参数字典, 是否JSON解析失败)。

        json_failed=True 供上层携带格式纠正提示词重试，而非静默吞掉。
        """
        match = _ACTION_RE.search(text)
        if not match:
            return None, {}, False
        action = match.group(1).strip()
        try:
            action_input = json.loads(match.group(2).strip())
            if not isinstance(action_input, dict):
                return action, {}, True
        except json.JSONDecodeError:
            return action, {}, True
        return action, action_input, False

    @staticmethod
    def _parse_final_answer(text: str) -> Optional[str]:
        """解析 Final Answer 字段，无则返回 None。"""
        match = _FINAL_ANSWER_RE.search(text)
        return match.group(1).strip() if match else None

    @staticmethod
    def _extract_text(message: BaseMessage) -> str:
        """从 LLM 响应中提取纯文本内容。"""
        content = message.content
        return content if isinstance(content, str) else str(content)

    @staticmethod
    def _build_final_answer_prompt(
        summary: str,
        history: str,
        question: str,
        scratchpad: str,
    ) -> str:
        """构建最终回答的流式生成 prompt。"""
        parts: list[str] = [REACT_SYSTEM_PROMPT]
        if summary:
            parts.append(f"历史摘要：\n{summary}")
        parts.append(f"最近对话：\n{history}")
        parts.append(f"问题：{question}")
        if scratchpad:
            parts.append(f"推理与工具观察：\n{scratchpad}")
        parts.append("请直接向用户给出最终回答（不要使用 Thought/Action/Final Answer 格式）：")
        return "\n\n".join(parts)

    @staticmethod
    def _sse_event(event_type: str, content: str = "", **extra) -> str:
        """构造 SSE 事件字符串。"""
        payload: dict = {"type": event_type, "content": content}
        payload.update(extra)
        return f"data: {json.dumps(payload, ensure_ascii=False)}"