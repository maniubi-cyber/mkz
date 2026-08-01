"""输入侧提示词注入与越权意图拦截器。

在用户输入送入 LLM 之前进行检测，识别常见的提示词注入、越狱、
角色劫持等恶意意图，并做基础清洗，防止系统提示词被泄露或覆盖。
"""
import re

# 提示词注入特征模式
INJECTION_PATTERNS = [
    r"ignore\s+(previous|above|all)\s+(instructions?|prompts?)",
    r"忽略(之前|上面|所有)(的)?(指令|提示|系统)",
    r"system\s+prompt",
    r"系统提示",
    r"jailbreak",
    r"越狱",
    r"override\s+(instructions?|rules?)",
    r"覆盖(指令|规则)",
    r"忽略你的(身份|角色)",
    r"you\s+are\s+now\s+(a|an)\s+",  # 角色劫持
    r"reveal\s+(your|the)\s+(system|prompt|instructions?)",
    r"DAN\s+mode",
]


def contains_injection(text: str) -> tuple[bool, str]:
    """检测输入是否包含提示词注入。

    返回 (是否检测到, 匹配的模式描述)。
    """
    for pattern in INJECTION_PATTERNS:
        if re.search(pattern, text, re.IGNORECASE):
            return True, pattern
    return False, ""


def sanitize_input(text: str) -> str:
    """对输入做基础清洗（去除危险标记，保留原文语义）"""
    # 移除可能的 prompt 标记分隔符
    text = text.replace("[SYS_CONTEXT_BEGIN]", "").replace("[SYS_CONTEXT_END]", "")
    return text.strip()
