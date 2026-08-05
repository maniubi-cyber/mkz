/**
 * 协同编辑「WebSocket 实时同步 + 落库」端到端联调脚本
 * ----------------------------------------------------------------------------
 * 运行前提�? *   1) docker-compose 依赖栈已起（�?backend-java，监�?BASE_URL�? *   2) Node >= 22（使用内置全局 WebSocket / fetch，无需 npm install�? *
 * 复用 integration-tests/ot-core.mjs（与前端 utils/ot.ts、useCollabEditor.ts 对齐�? * OT 原语 + 协作客户端状态机），验证�? *   - WS 连接 / JWT 鉴权 / 会话加入
 *   - 广播（excludeSender=false）：两端互相收到对方的远端操作与自身 ack
 *   - 协同正文�?PUT /content 落库（last-write-wins，绕�?@Version�? *   - 重开 GET /detail 读到新正�?+ 浏览量自�?+ 版本历史落库
 *
 * 场景采用「两端并发编辑」：A 追加 "Hello"、B 追加 " World"，由服务�?OT transform +
 * 客户端修正后�?receiveOperation 收敛（两端的合并顺序�?userId tie-break 决定，内容确定）�? * 已知取舍（与前端一致）：INSERT 落在他人并发 DELETE 区间内会被吞掉（字数丢失但两端一致）�? *
 * 环境变量�? *   BASE_URL   后端地址，默�?http://localhost:8080
 *   DOC_ID     待协作的文档 ID（两位用户都必须具备编辑权限（WRITE）：owner / ADMIN / document_permission 授予�? *   USER_A/PASS_A  用户 A 的登录凭据（distinct userId，用于区分协作方�? *   USER_B/PASS_B  用户 B 的登录凭�? *   TOKEN_A/TOKEN_B  可选，直接提供 JWT 跳过登录
 * 退出码�?=全部断言通过�?=存在失败或环境不可达�? */
import { strict as assert } from 'node:assert'
import { clientReceive, localEdit } from './ot-core.mjs'

if (typeof globalThis.WebSocket === 'undefined') {
  console.error('�?当前 Node 版本缺少全局 WebSocket（需 Node >= 22），或请 `npm i ws` 后改�?import�?)
  process.exit(1)
}

// =====================================================================
// 最小协作客户端 —�?复用 ot-core �?clientReceive / localEdit
// =====================================================================
function wsBaseFromHttp (httpUrl) {
  const u = new URL(httpUrl)
  return `${u.protocol === 'https:' ? 'wss:' : 'ws:'}//${u.host}`
}

class CollabClient {
  constructor ({ docId, token, userId, username, baseUrl }) {
    this.docId = docId
    this.token = token
    this.userId = userId
    this.username = username
    this.wsUrl = `${wsBaseFromHttp(baseUrl)}/ws/doc/${docId}?token=${encodeURIComponent(token)}&docId=${docId}`
    this.state = { text: '', pending: [], revision: 0, userId, receivedSelfAck: false, receivedRemoteOp: false }
    this.connected = false
    this.lastError = null
    this._ws = null
    this._onConverge = null
  }

  connect () {
    return new Promise((resolve, reject) => {
      try { this._ws = new WebSocket(this.wsUrl) } catch (e) { return reject(e) }
      this._ws.onopen = () => {
        this.connected = true
        this.send({ type: 'sync' })
        resolve()
      }
      this._ws.onmessage = (ev) => {
        try {
          const msg = JSON.parse(ev.data)
          if (msg.type === 'operation') this.state = clientReceive(this.state, msg)
          else if (msg.type === 'sync_response') this.state.revision = Math.max(this.state.revision, msg.version ?? this.state.revision)
          else if (msg.type === 'error') this.lastError = msg.error
          this._maybeResolveConverge()
        } catch { /* 忽略无法解析的消�?*/ }
      }
      this._ws.onerror = (e) => { this.lastError = e.message || 'ws error' }
      this._ws.onclose = () => { this.connected = false }
    })
  }

  send (obj) {
    if (this._ws && this._ws.readyState === WebSocket.OPEN) this._ws.send(JSON.stringify(obj))
  }

  localEdit (newText) {
    const { state, ops } = localEdit(this.state, newText)
    this.state = state
    for (const op of ops) {
      this.send({
        type: 'operation',
        version: this.state.revision,
        operation: {
          type: op.type,
          position: op.position,
          ...(op.type === 'insert' ? { content: op.content } : {}),
          ...(op.type === 'delete' ? { length: op.length } : {}),
          ...(op.type === 'retain' ? { retain: op.retain } : {})
        }
      })
    }
  }

  /** 等待某个谓词成立（用于等待自�?ack / 对方广播�?*/
  waitUntil (pred, timeoutMs = 8000) {
    return new Promise((resolve) => {
      const deadline = Date.now() + timeoutMs
      const tick = () => {
        if (pred(this)) { this._onConverge = null; return resolve(true) }
        if (Date.now() >= deadline) { this._onConverge = null; return resolve(false) }
        setTimeout(tick, 50)
      }
      this._onConverge = tick
      tick()
    })
  }

  _maybeResolveConverge () { this._onConverge && this._onConverge() }
  disconnect () { try { this._ws && this._ws.close() } catch { /* noop */ } }
}

// =====================================================================
// REST 助手
// =====================================================================
async function rest (baseUrl, method, path, { token, body } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers['Authorization'] = `Bearer ${token}`
  const res = await fetch(`${baseUrl}${path}`, { method, headers, body: body ? JSON.stringify(body) : undefined })
  const json = await res.json().catch(() => null)
  return { status: res.status, json }
}
async function login (baseUrl, username, password) {
  const { status, json } = await rest(baseUrl, 'POST', '/api/auth/login', { body: { username, password } })
  if (status !== 200 || !json || !json.data || !json.data.accessToken) {
    throw new Error(`登录失败 [${username}]: HTTP ${status} ${JSON.stringify(json)}`)
  }
  return { token: json.data.accessToken, user: json.data.user }
}

// =====================================================================
// 主流�?// =====================================================================
const results = []
function check (name, cond, detail = '') {
  results.push({ name, ok: !!cond, detail })
  console.log(`${cond ? '�?PASS' : '�?FAIL'}  ${name}${detail ? '  �?' + detail : ''}`)
}
function finish () {
  const failed = results.filter((r) => !r.ok)
  console.log(`\n==== 联调结果�?{results.length - failed.length}/${results.length} 通过 ====`)
  process.exit(failed.length ? 1 : 0)
}

async function main () {
  const BASE_URL = process.env.BASE_URL || 'http://localhost:8080'
  const DOC_ID = process.env.DOC_ID
  if (!DOC_ID) throw new Error('缺少必填环境变量 DOC_ID（文�?ID，两位用户均需具备该文档的编辑权限）')

  let tokA = process.env.TOKEN_A
  let tokB = process.env.TOKEN_B
  let userA, userB
  if (!tokA || !tokB) {
    if (!process.env.USER_A || !process.env.USER_B) throw new Error('需提供 TOKEN_A/TOKEN_B，或 USER_A/PASS_A �?USER_B/PASS_B 用于登录')
    try { userA = await login(BASE_URL, process.env.USER_A, process.env.PASS_A); tokA = userA.token } catch (e) { throw new Error(`用户A登录失败�?{e.message}`) }
    try { userB = await login(BASE_URL, process.env.USER_B, process.env.PASS_B); tokB = userB.token } catch (e) { throw new Error(`用户B登录失败�?{e.message}`) }
  }
  const userIdA = userA?.user?.id ?? Number(process.env.USER_ID_A || 0)
  const userIdB = userB?.user?.id ?? Number(process.env.USER_ID_B || 0)
  console.log(`用户A userId=${userIdA}, 用户B userId=${userIdB}, 文档 docId=${DOC_ID}`)

  const before = await rest(BASE_URL, 'GET', `/api/documents/${DOC_ID}/detail`, { token: tokA })
  check('GET /detail 可读（用户A有权查看文档�?, before.status === 200 && before.json?.data, `HTTP ${before.status}`)
  if (before.status !== 200) return finish()
  const initContent = before.json.data.content ?? ''
  const initVersion = before.json.data.version ?? 0
  const initViewCount = before.json.data.viewCount ?? 0
  console.log(`初始正文="${initContent}"  version=${initVersion}  viewCount=${initViewCount}`)

  const clientA = new CollabClient({ docId: DOC_ID, token: tokA, userId: userIdA, username: 'A', baseUrl: BASE_URL })
  const clientB = new CollabClient({ docId: DOC_ID, token: tokB, userId: userIdB, username: 'B', baseUrl: BASE_URL })
  await Promise.all([clientA.connect(), clientB.connect()])
  check('两位用户 WS 均连接成�?, clientA.connected && clientB.connected)

  // 并发协作：两端几乎同时本地编辑并发送（目标文本 = 各自当前文本 + 追加内容），
  // 由服务器 OT transform + 客户端修正后�?receiveOperation 保证收敛�?  clientA.localEdit((clientA.state.text || '') + 'Hello')
  clientB.localEdit((clientB.state.text || '') + ' World')
  const settled = await Promise.all([
    clientA.waitUntil((c) => c.state.revision >= 2 && c.state.receivedRemoteOp),
    clientB.waitUntil((c) => c.state.revision >= 2 && c.state.receivedRemoteOp),
  ])
  check('A 收到自身 ack �?B 的远端操�?, clientA.state.receivedSelfAck && clientA.state.receivedRemoteOp, `rev=${clientA.state.revision}`)
  check('B 收到自身 ack �?A 的远端操�?, clientB.state.receivedSelfAck && clientB.state.receivedRemoteOp, `rev=${clientB.state.revision}`)
  check('广播生效（excludeSender=false�?, clientA.state.receivedRemoteOp && clientB.state.receivedRemoteOp)

  const chars = (s) => s.split('').sort().join('')
  const converged = clientA.state.text === clientB.state.text && chars(clientA.state.text) === chars('Hello World')
  check('协同收敛：两端文本一致且内容正确（顺序由 userId tie-break 决定�?, converged, `A="${clientA.state.text}" B="${clientB.state.text}"`)

  // 落库
  const saveRes = await rest(BASE_URL, 'PUT', `/api/documents/${DOC_ID}/content`, { token: tokA, body: { content: clientA.state.text, baseRevision: initVersion } })
  check('PUT /content 保存成功', saveRes.status === 200, `HTTP ${saveRes.status}`)

  // 重开详情
  const after = await rest(BASE_URL, 'GET', `/api/documents/${DOC_ID}/detail`, { token: tokA })
  check('重开详情读到落库正文', after.json?.data?.content === clientA.state.text, `content="${after.json?.data?.content}"`)
  const viewDelta = (after.json?.data?.viewCount ?? 0) - initViewCount
  check('浏览量自增（HINCRBY�?, viewDelta >= 1, `ΔviewCount=${viewDelta}`)
  const hist = after.json?.data?.versionHistory || []
  check('版本历史落库（协同编辑保存）', hist.some((h) => (h.editSummary || '').includes('协同编辑')), `history=${hist.length} 条`)

  clientA.disconnect(); clientB.disconnect()
  finish()
}

main().catch((e) => {
  console.error('�?联调脚本异常终止�?, e.message)
  console.error('提示：确�?docker-compose 已起、BASE_URL 可达、DOC_ID 正确且两用户都具备该文档的编辑权限�?)
  process.exit(1)
})
