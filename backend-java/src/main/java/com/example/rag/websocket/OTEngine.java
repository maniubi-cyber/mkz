"""
OT (Operational Transformation) Engine

Implements the classic OT algorithm for collaborative text editing.
Supports three primitive operations:
  - INSERT(position, text) — insert text at position
  - DELETE(position, length) — delete length characters at position
  - RETAIN(count) — keep count characters unchanged

The transform function determines how an operation should be adjusted
when another concurrent operation has already been applied.

See: "A Theory of Collaborative Editing" — Ellis & Gibbs, 1989
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from typing import Literal

logger = logging.getLogger(__name__)

# Operation types
OpType = Literal["insert", "delete", "retain"]


@dataclass
class Operation:
    """
    An atomic edit operation in OT protocol.

    Attributes:
        type:  Operation type — "insert" | "delete" | "retain"
        pos:   Position in the document where the operation applies (0-indexed)
        text:  Inserted text (only for INSERT operations)
        length: Number of characters to delete (only for DELETE operations)
        count:  Number of characters to retain (only for RETAIN operations)
    """
    type: OpType
    pos: int = 0
    text: str = ""
    length: int = 0
    count: int = 0

    def __post_init__(self):
        if self.type == "insert" and not self.text:
            raise ValueError("INSERT operation requires non-empty text")
        if self.type == "delete" and self.length <= 0:
            raise ValueError("DELETE operation requires positive length")
        if self.type == "retain" and self.count <= 0:
            raise ValueError("RETAIN operation requires positive count")

    def __repr__(self) -> str:
        if self.type == "insert":
            return f"OP(I, {self.pos}, '{self.text}')"
        elif self.type == "delete":
            return f"OP(D, {self.pos}, {self.length})"
        return f"OP(R, {self.pos}, {self.count})"


# ============================================================
# OT Transform — Core Algorithm
# ============================================================

class OTTransform:
    """
    Operational Transformation engine.

    Given two concurrent operations op1 and op2, transform(op1, op2) returns
    a version of op1 that has been adjusted so that applying op2 first and
    then the transformed op1 produces the same final document state as
    applying op1 first and then op2.

    The key invariant:
        apply(apply(doc, op1), op2) == apply(apply(doc, op2), transform(op1, op2))
    """

    @staticmethod
    def transform(
        op1: Operation, op2: Operation, prefer: Literal["op1", "op2"] = "op1",
    ) -> tuple[Operation, Operation]:
        """
        Transform two concurrent operations against each other.

        Args:
            op1:    First operation (from client A)
            op2:    Second operation (from client B)
            prefer: Which operation's edit takes priority on conflict.
                    "op1" means op1's edit is preserved; "op2" means op2's edit.

        Returns:
            (op1', op2') — the transformed operations
        """
        # Build position-relative versions
        o1 = OTTransform._copy(op1)
        o2 = OTTransform._copy(op2)

        # Case 1: Operations on disjoint ranges — no conflict
        if o1.type == "insert" and o2.type == "insert":
            return OTTransform._transform_insert_insert(o1, o2, prefer)
        elif o1.type == "insert" and o2.type == "delete":
            return OTTransform._transform_insert_delete(o1, o2)
        elif o1.type == "delete" and o2.type == "insert":
            return OTTransform._transform_delete_insert(o1, o2)
        elif o1.type == "delete" and o2.type == "delete":
            return OTTransform._transform_delete_delete(o1, o2, prefer)
        elif o1.type == "retain":
            return (o1, o2)  # Retain is identity; no change needed
        elif o2.type == "retain":
            return (o1, o2)  # Retain is identity; no change needed
        else:
            raise ValueError(f"Unsupported operation types: {o1.type}, {o2.type}")

    # ---- Case: INSERT vs INSERT ----

    @staticmethod
    def _transform_insert_insert(
        o1: Operation, o2: Operation, prefer: str,
    ) -> tuple[Operation, Operation]:
        """
        Both operations insert text.
        - If o1 inserts before o2's position: o1 stays, o2 shifts right.
        - If o2 inserts before o1's position: o2 stays, o1 shifts right.
        - If they target the same position: prefer wins (o1 inserts first).
        """
        if o1.pos < o2.pos:
            # o1 is before o2 → o1 unchanged, o2 shifts right by o1's text length
            o2.pos += len(o1.text)
        elif o1.pos > o2.pos:
            # o2 is before o1 → o2 unchanged, o1 shifts right by o2's text length
            o1.pos += len(o2.text)
        else:
            # Same position — prefer wins
            if prefer == "op1":
                o2.pos += len(o1.text)
            else:
                o1.pos += len(o2.text)

        return (o1, o2)

    # ---- Case: INSERT vs DELETE ----

    @staticmethod
    def _transform_insert_delete(
        o1: Operation, o2: Operation,
    ) -> tuple[Operation, Operation]:
        """
        o1 inserts, o2 deletes.
        - If o1 inserts before o2's range: o1 unchanged.
        - If o1 inserts inside o2's range: o1 shifts to after the deleted range.
        - If o1 inserts after o2's range: o1 shifts left by deleted length.
        """
        delete_end = o2.pos + o2.length

        if o1.pos < o2.pos:
            # Insert before delete range — no change
            pass
        elif o1.pos >= delete_end:
            # Insert after delete range — shift left
            o1.pos -= o2.length
        else:
            # Insert inside delete range — shift to end of delete
            o1.pos = delete_end

        return (o1, o2)

    @staticmethod
    def _transform_delete_insert(
        o1: Operation, o2: Operation,
    ) -> tuple[Operation, Operation]:
        """
        o1 deletes, o2 inserts.
        - If o2 inserts before o1's range: o2 unchanged.
        - If o2 inserts inside o1's range: o2 shifts to after the deleted range.
        - If o2 inserts after o1's range: o2 shifts left by deleted length.
        """
        delete_end = o1.pos + o1.length

        if o2.pos < o1.pos:
            # Insert before delete range — no change
            pass
        elif o2.pos >= delete_end:
            # Insert after delete range — shift left
            o2.pos -= o1.length
        else:
            # Insert inside delete range — shift to end of delete
            o2.pos = delete_end

        return (o1, o2)

    # ---- Case: DELETE vs DELETE ----

    @staticmethod
    def _transform_delete_delete(
        o1: Operation, o2: Operation, prefer: str,
    ) -> tuple[Operation, Operation]:
        """
        Both operations delete.
        - If disjoint: both unchanged.
        - If overlapping: intersect is deleted once; non-overlapping parts stay.
        """
        delete1_start = o1.pos
        delete1_end = o1.pos + o1.length
        delete2_start = o2.pos
        delete2_end = o2.pos + o2.length

        # Case 1: Disjoint
        if delete1_end <= delete2_start or delete2_end <= delete1_start:
            if o2.pos >= delete1_end:
                o2.pos -= o1.length
            if o1.pos >= delete2_end:
                o1.pos -= o2.length
            return (o1, o2)

        # Case 2: One contains the other, or partial overlap
        # Determine intersection
        intersect_start = max(delete1_start, delete2_start)
        intersect_end = min(delete1_end, delete2_end)
        intersect_len = intersect_end - intersect_start

        # o1's effective delete range: original minus intersection
        if delete1_start < intersect_start:
            # o1 has content before intersection — adjust position
            o1.pos = delete1_start
            o1.length = intersect_start - delete1_start
        else:
            # o1 is entirely within or overlapping intersection
            o1.length -= intersect_len
            if o1.length <= 0:
                # o1 is completely consumed by intersection
                o1.type = "retain"
                o1.count = 0
                o1.text = ""
                return (o1, o2)

        # o2's effective delete range: original minus intersection
        if delete2_start < intersect_start:
            o2.pos = delete2_start
            o2.length = intersect_start - delete2_start
        else:
            o2.length -= intersect_len
            if o2.length <= 0:
                o2.type = "retain"
                o2.count = 0
                o2.text = ""
                return (o1, o2)

        # Adjust positions for remaining parts
        if prefer == "op1":
            if o2.pos >= delete1_end:
                o2.pos -= o1.length
        else:
            if o1.pos >= delete2_end:
                o1.pos -= o2.length

        return (o1, o2)

    # ---- Utility ----

    @staticmethod
    def _copy(op: Operation) -> Operation:
        """Deep copy an operation."""
        return Operation(
            type=op.type,
            pos=op.pos,
            text=op.text,
            length=op.length,
            count=op.count,
        )


# ============================================================
# OT Document Editor — High-level API
# ============================================================

class OTEditor:
    """
    High-level OT editor that manages a document's version and applies
    transformed operations from multiple clients.

    Usage::

        editor = OTEditor(initial_text="Hello world")

        # Client A edits
        op_a = Operation(type="insert", pos=5, text=" beautiful")
        editor.apply_operation(op_a, client_id="A")

        # Client B concurrently edits
        op_b = Operation(type="delete", pos=6, length=5)
        transformed_op_b = editor.transform_op(op_b, op_a)
        editor.apply_operation(transformed_op_b, client_id="B")
    """

    def __init__(self, initial_text: str = ""):
        self._text = initial_text
        self._version = 0
        self._pending_ops: dict[str, Operation] = {}  # client_id -> last op

    @property
    def text(self) -> str:
        return self._text

    @property
    def version(self) -> int:
        return self._version

    def apply_operation(self, op: Operation) -> str:
        """
        Apply a single operation to the document text.

        Returns:
            The updated document text.
        """
        if op.type == "insert":
            pos = min(op.pos, len(self._text))
            self._text = self._text[:pos] + op.text + self._text[pos:]
        elif op.type == "delete":
            pos = min(op.pos, len(self._text))
            end = min(pos + op.length, len(self._text))
            self._text = self._text[:pos] + self._text[end:]
        # retain is a no-op

        self._version += 1
        logger.debug(
            "Applied op: %s, version=%d, text_len=%d", op, self._version, len(self._text)
        )
        return self._text

    def transform_op(
        self, op: Operation, concurrent_op: Operation,
    ) -> Operation:
        """
        Transform an operation against a concurrent operation.

        Args:
            op:             The operation to transform.
            concurrent_op:  The operation that was applied first.

        Returns:
            The transformed operation, ready to apply.
        """
        transformed_op, _ = OTTransform.transform(op, concurrent_op)
        logger.debug(
            "Transformed %s against %s → %s", op, concurrent_op, transformed_op
        )
        return transformed_op

    def transform_ops(
        self, op1: Operation, op2: Operation,
    ) -> tuple[Operation, Operation]:
        """
        Transform two concurrent operations against each other.

        Returns:
            (op1_transformed, op2_transformed)
        """
        return OTTransform.transform(op1, op2)

    def ops_to_text(self, ops: list[Operation]) -> str:
        """
        Apply a sequence of operations to reconstruct document text.
        Useful for replaying from a base version.
        """
        text = self._text
        for op in ops:
            text = self._apply_single_op(text, op)
        return text

    @staticmethod
    def _apply_single_op(text: str, op: Operation) -> str:
        """Apply a single operation to text (stateless)."""
        if op.type == "insert":
            pos = min(op.pos, len(text))
            return text[:pos] + op.text + text[pos:]
        elif op.type == "delete":
            pos = min(op.pos, len(text))
            end = min(pos + op.length, len(text))
            return text[:pos] + text[end:]
        return text


# ============================================================
# Operation Helpers
# ============================================================

def insert_at(text: str, pos: int, content: str) -> tuple[str, Operation]:
    """Create and apply an INSERT operation, returning (new_text, op)."""
    op = Operation(type="insert", pos=pos, text=content)
    new_text = OTEditor._apply_single_op(text, op)
    return new_text, op


def delete_at(text: str, pos: int, length: int) -> tuple[str, Operation]:
    """Create and apply a DELETE operation, returning (new_text, op)."""
    op = Operation(type="delete", pos=pos, length=length)
    new_text = OTEditor._apply_single_op(text, op)
    return new_text, op


def retain(count: int) -> Operation:
    """Create a RETAIN operation."""
    return Operation(type="retain", count=count)


# ============================================================
# Module-level convenience
# ============================================================

def transform(
    op1: Operation, op2: Operation, prefer: str = "op1",
) -> tuple[Operation, Operation]:
    """Convenience: transform two operations."""
    return OTTransform.transform(op1, op2, prefer)
