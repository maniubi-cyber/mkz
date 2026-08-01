"""知识库服务：文档上传分块、检索增强对话、文档删除。

组合 VectorStoreManager（向量检索）与 ChatOpenAI（LLM 生成）实现 RAG 流程：
上传 Markdown → 按 H2/H3 标题分块入库 → 检索 top3 相关分块 → 拼接上下文 → LLM 回答。
"""
import re

from langchain_openai import ChatOpenAI

from app.config.settings import get_settings
from app.rag.vector_store import VectorStoreManager

# prompt 模板：参考资料 + 问题，要求基于资料回答不编造
_PROMPT_TEMPLATE = (
    "[SYS_CONTEXT_BEGIN]\n{context}\n[SYS_CONTEXT_END]\n\n"
    "请基于上述参考资料回答问题：{question}\n"
    "若参考资料未覆盖，请如实说明，不要编造。"
)

# 无标题时的兜底分块大小（字符数）
_FALLBACK_CHUNK_SIZE = 500


class KnowledgeService:
    """知识库服务，组合向量检索与 LLM 实现检索增强对话。"""

    def __init__(self, vector_store: VectorStoreManager) -> None:
        self._vector_store = vector_store
        settings = get_settings()
        # 通过 OpenAI 兼容接口指向 Ollama / 其他本地推理服务
        self._llm = ChatOpenAI(
            base_url=settings.llm_base_url,
            api_key=settings.llm_api_key,
            model=settings.llm_model,
            temperature=settings.llm_stream_temperature,
            max_tokens=settings.llm_max_tokens,
            timeout=settings.llm_timeout_seconds,
            max_retries=settings.llm_max_retries,
        )

    def upload_markdown(self, user_id: str, doc_id: str, content: str) -> int:
        """上传 Markdown 文档：按 H2/H3 标题分块 → 入库。返回分块数。"""
        chunks = self._split_markdown(content)
        return self._vector_store.upsert_chunks(user_id, doc_id, chunks)

    def chat(self, user_id: str, session_id: str, question: str) -> tuple[str, list[dict]]:
        """知识库对话：检索 top3 相关分块 → 拼接 context → 调用 LLM 回答。

        返回 (answer, references)。references 是检索到的分块列表，
        每项形如 {title, content, score}。
        session_id 预留给后续接入多轮会话记忆，当前为无状态单轮检索。
        """
        references = self._vector_store.search_chunks(user_id, question, top_k=3)
        # 拼接上下文：编号 + 标题 + 内容
        if references:
            context_parts = [
                f"【{i + 1}】{ref['title']}\n{ref['content']}"
                for i, ref in enumerate(references)
            ]
            context = "\n\n".join(context_parts)
        else:
            context = "（未检索到相关参考资料）"
        prompt = _PROMPT_TEMPLATE.format(context=context, question=question)
        response = self._llm.invoke(prompt)
        answer = response.content if hasattr(response, "content") else str(response)
        return answer, references

    def delete_doc(self, user_id: str, doc_id: str) -> int:
        """删除文档及其向量，返回删除的分块数。"""
        return self._vector_store.delete_by_doc(user_id, doc_id)

    @staticmethod
    def _split_markdown(content: str) -> list[dict]:
        """Markdown 分块：按 ## 和 ### 标题切分。

        - 每块包含 title（标题文本）与 content（标题下正文）
        - 若无标题，按段落累积切分，每 500 字符一块；无段落分隔则按 500 字符硬切
        - 返回 [{title, content}]
        """
        if not content or not content.strip():
            return []
        pattern = re.compile(r"^(#{2,3})\s+(.+)$", re.MULTILINE)
        matches = list(pattern.finditer(content))
        chunks: list[dict] = []
        if matches:
            # 标题之前的非空内容作为"概述"首块
            first = content[: matches[0].start()].strip()
            if first:
                chunks.append({"title": "概述", "content": first})
            for i, m in enumerate(matches):
                title = m.group(2).strip()
                start = m.end()
                end = matches[i + 1].start() if i + 1 < len(matches) else len(content)
                body = content[start:end].strip()
                if body:
                    chunks.append({"title": title, "content": body})
        else:
            # 无标题：按段落累积，每 500 字符一块
            paragraphs = [p.strip() for p in content.split("\n\n") if p.strip()]
            if len(paragraphs) <= 1:
                # 无明显段落分隔，按字符硬切
                text = content.strip()
                for i in range(0, len(text), _FALLBACK_CHUNK_SIZE):
                    piece = text[i:i + _FALLBACK_CHUNK_SIZE].strip()
                    if piece:
                        chunks.append({"title": "段落", "content": piece})
            else:
                buffer = ""
                for para in paragraphs:
                    buffer = f"{buffer}\n\n{para}" if buffer else para
                    if len(buffer) >= _FALLBACK_CHUNK_SIZE:
                        chunks.append({"title": "段落", "content": buffer})
                        buffer = ""
                if buffer:
                    chunks.append({"title": "段落", "content": buffer})
        # 兜底：分块为空则整体作为一块
        if not chunks:
            chunks.append({"title": "全文", "content": content.strip()})
        return chunks
