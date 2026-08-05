/**
 * OT 核心原语 + 协作客户端状态机（纯函数，无 IO）
 * ----------------------------------------------------------------------------
 * 与 frontend/src/utils/ot.ts 及 frontend/src/composables/useCollabEditor.ts 逐行等价。
 *
 * 关键修复（useCollabEditor.receiveOperation 远端分支）：
 * 旧实现只 transform 本地 pending、却把【未变换的 incoming】直接应用到文本，导致并发编辑
 * （同位置插入 / 插入落入删除区间等）两端文本分叉。正确做法（ot.js 一致）是把 incoming
 * 依次与本地 pending transform，应用【变换后】的版本。回归见 ot-client-convergence.test.mjs。
 *
 * 被以下文件复用：
 *   - ws-collab-e2e.mjs        （端到端联调，需要 Node >= 22 全局 WebSocket）
 *   - ot-client-convergence.test.mjs  （离线收敛回归测试，node 直接跑）
 */

const clamp = (v, min, max) => Math.max(min, Math.min(v, max))

export function apply (text, op) {
  if (!op) return text
  if (op.type === 'insert') {
    const pos = clamp(op.position ?? 0, 0, text.length)
    const content = op.content ?? ''
    return text.slice(0, pos) + content + text.slice(pos)
  }
  if (op.type === 'delete') {
    const pos = clamp(op.position ?? 0, 0, text.length)
    const len = op.length ?? 0
    const end = clamp(pos + len, pos, text.length)
    return text.slice(0, pos) + text.slice(end)
  }
  return text // retain
}

const o1FirstAtSamePos = (o1, o2) => {
  const s1 = o1.siteId ?? 0
  const s2 = o2.siteId ?? 0
  if (s1 !== s2) return s1 < s2
  return (o1.content ?? '').localeCompare(o2.content ?? '') <= 0
}
const insertLen = (op) => (op.type === 'insert' ? (op.content ?? '').length : 0)
const isNoop = (op) => {
  if (op.type === 'retain') return true
  if (op.type === 'insert') return (op.content ?? '').length === 0
  return (op.length ?? 0) <= 0
}
const copy = (op) => ({ ...op })
function degradeToNoop (op) { op.type = 'retain'; op.content = ''; op.length = 0; op.retain = 0 }

export function transform (op1, op2) {
  const o1 = copy(op1)
  const o2 = copy(op2)
  if (isNoop(o1) || isNoop(o2)) return [o1, o2]
  if (o1.type === 'insert' && o2.type === 'insert') return transformInsertInsert(o1, o2)
  if (o1.type === 'insert' && o2.type === 'delete') return transformInsertDelete(o1, o2)
  if (o1.type === 'delete' && o2.type === 'insert') {
    const [r2, r1] = transformInsertDelete(o2, o1)
    return [r1, r2]
  }
  if (o1.type === 'delete' && o2.type === 'delete') return transformDeleteDelete(o1, o2)
  return [o1, o2]
}
function transformInsertInsert (o1, o2) {
  if (o1.position < o2.position) o2.position += insertLen(o1)
  else if (o1.position > o2.position) o1.position += insertLen(o2)
  else if (o1FirstAtSamePos(o1, o2)) o2.position += insertLen(o1)
  else o1.position += insertLen(o2)
  return [o1, o2]
}
function transformInsertDelete (o1, o2) {
  const insertPos = o1.position
  const il = insertLen(o1)
  const deleteStart = o2.position
  if (insertPos <= deleteStart) o2.position = deleteStart + il
  else if (insertPos >= o2.position + (o2.length ?? 0)) o1.position = insertPos - (o2.length ?? 0)
  else { degradeToNoop(o1); o2.length = (o2.length ?? 0) + il }
  return [o1, o2]
}
function transformDeleteDelete (o1, o2) {
  const start1 = o1.position, end1 = o1.position + (o1.length ?? 0), len1 = o1.length ?? 0
  const start2 = o2.position, end2 = o2.position + (o2.length ?? 0), len2 = o2.length ?? 0
  const overlap = Math.max(0, Math.min(end1, end2) - Math.max(start1, start2))
  let newLen1 = len1 - overlap
  if (newLen1 <= 0) degradeToNoop(o1)
  else {
    let ns = start1 >= start2 && start1 < end2 ? start2 : start1
    ns -= Math.max(0, Math.min(end2, ns) - start2)
    o1.position = Math.max(0, ns); o1.length = newLen1
  }
  let newLen2 = len2 - overlap
  if (newLen2 <= 0) degradeToNoop(o2)
  else {
    let ns = start2 >= start1 && start2 < end1 ? start1 : start2
    ns -= Math.max(0, Math.min(end1, ns) - start1)
    o2.position = Math.max(0, ns); o2.length = newLen2
  }
  return [o1, o2]
}

export function diff (oldText, newText) {
  if (oldText === newText) return []
  let p = 0
  const maxP = Math.min(oldText.length, newText.length)
  while (p < maxP && oldText[p] === newText[p]) p++
  let s = 0
  const maxS = Math.min(oldText.length - p, newText.length - p)
  while (s < maxS && oldText[oldText.length - 1 - s] === newText[newText.length - 1 - s]) s++
  const oldMid = oldText.length - p - s
  const newMid = newText.length - p - s
  const ops = []
  if (oldMid === 0) ops.push({ type: 'insert', position: p, content: newText.slice(p, p + newMid) })
  else if (newMid === 0) ops.push({ type: 'delete', position: p, length: oldMid })
  else { ops.push({ type: 'delete', position: p, length: oldMid }); ops.push({ type: 'insert', position: p, content: newText.slice(p, p + newMid) }) }
  return ops
}

/**
 * 客户端收到一条（非 sync/error）WS 消息后的状态转移。
 * 修正点：远端操作必须把 incoming 依次与本地 pending transform，应用 transformed 结果。
 *
 * @param state { text, pending:[{op,base}], revision, userId, receivedSelfAck, receivedRemoteOp, lastRemote }
 * @param msg   { type, operation:{type,position,content,length,retain}, userId, version }
 */
export function clientReceive (state, msg) {
  if (msg.type !== 'operation') return state
  const raw = msg.operation
  if (!raw) return state
  const incoming = {
    type: raw.type,
    position: raw.position ?? 0,
    content: raw.content,
    length: raw.length,
    retain: raw.retain,
    siteId: msg.userId
  }
  // 自身 ack：仅推进 revision、弹出队首 pending，不改动文本
  if (msg.userId === state.userId) {
    return {
      ...state,
      revision: msg.version ?? state.revision,
      pending: state.pending.slice(1),
      receivedSelfAck: true
    }
  }
  // 远端：incoming 依次与本地 pending transform，应用 transformed 后的版本
  let transformed = incoming
  const pending = state.pending.map((p) => {
    const [np, nt] = transform(p.op, transformed)
    transformed = nt
    return { ...p, op: np }
  })
  const text = apply(state.text, transformed)
  return {
    ...state,
    text,
    pending,
    revision: msg.version ?? state.revision,
    receivedRemoteOp: true,
    lastRemote: transformed
  }
}

/** 本地输入：返回要发送的操作序列，并就地推进客户端状态（与前端 handleLocalInput 一致） */
export function localEdit (state, newText) {
  const ops = diff(state.text, newText)
  let text = state.text
  const pending = state.pending.slice()
  for (const op of ops) {
    op.siteId = state.userId
    text = apply(text, op)
    pending.push({ op: { ...op }, base: state.revision })
  }
  return { state: { ...state, text, pending }, ops }
}
