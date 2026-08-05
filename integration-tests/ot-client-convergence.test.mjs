/**
 * 离线 OT 客户端收敛回归测试（无需后端 / Docker，node 直接跑）
 * ----------------------------------------------------------------------------
 * 模拟「真实服务器」：收到客户端操作 → 若 clientVersion<serverVersion 则与历史 transform
 * → 递增版本 → 广播给两端（含发送者自身 ack）。两端用 ot-core 的 clientReceive 推进状态。
 *
 * 验证：
 *   ✅ 有序协作、同位置并发插入(siteA<siteB)、插入落入并发删除区间、并发重叠删除 —— 必须收敛
 *   ⚠️ 同位置并发插入且 siteId 顺序与服务器到达顺序相反 —— 单区间模型 TP2 缺口，当前会分叉，
 *      记录为已知问题（不计入失败），根治需升级为操作序列模型。
 *
 * 运行：node integration-tests/ot-client-convergence.test.mjs
 * 退出码：0=必须收敛的场景全部收敛，1=出现意外分叉。
 */
import { transform, clientReceive, localEdit } from './ot-core.mjs'

const copyOp = (o) => ({ ...o })

function makeServer () { return { ver: 0, history: [] } }
// 注意：服务器收到的是【客户端当前 pending 操作】（客户端发出前已对远端操作做过 transform），
// 而非最初本地构造的 op。这是与真实协议一致的关键。
function serverDeliver (server, clients, senderIdx) {
  const pending = clients[senderIdx].state.pending
  if (!pending.length) return // 无待发操作（如已 self-ack 清空）
  const op = pending[pending.length - 1].op
  const clientVer = clients[senderIdx].state.revision
  let o = copyOp(op)
  if (clientVer < server.ver) {
    for (const h of server.history) if (h.ver > clientVer && h.ver <= server.ver) o = transform(o, h.op)[0]
  }
  server.ver++
  const msg = { type: 'operation', operation: o, userId: clients[senderIdx].state.userId, version: server.ver }
  server.history.push({ ver: server.ver, op: copyOp(o) })
  for (const c of clients) c.state = clientReceive(c.state, msg)
}

function mkClient (userId, text) {
  return { state: { text, pending: [], revision: 0, userId, receivedSelfAck: false, receivedRemoteOp: false } }
}

// 两个客户端各自做本地编辑（并发：在收到对方广播前都已本地应用），再按 order 提交给服务器
function runConcurrent (siteA, siteB, initA, initB, aNew, bNew, order) {
  const a = mkClient(siteA, initA)
  const b = mkClient(siteB, initB)
  const clients = [a, b]
  const rA = localEdit(a.state, aNew); a.state = rA.state
  const rB = localEdit(b.state, bNew); b.state = rB.state
  const server = makeServer()
  for (const idx of order) serverDeliver(server, clients, idx)
  return { a: a.state.text, b: b.state.text }
}

// 有序：A 先编辑并提交，B 在收到 A 之后（文本已变为 "Hello"）再在末尾追加
function runSequential () {
  const a = mkClient(1, '')
  const b = mkClient(2, '')
  const clients = [a, b]
  const rA = localEdit(a.state, 'Hello'); a.state = rA.state
  const server = makeServer()
  serverDeliver(server, clients, 0) // A 提交
  const rB = localEdit(b.state, 'Hello World'); b.state = rB.state // B 此时 text="Hello"
  serverDeliver(server, clients, 1) // B 提交
  return { a: a.state.text, b: b.state.text }
}

let failed = 0
function check (name, cond, detail = '') {
  if (cond) console.log(`✅ PASS  ${name}${detail ? '  → ' + detail : ''}`)
  else { console.log(`❌ FAIL  ${name}${detail ? '  → ' + detail : ''}`); failed++ }
}
function warn (name, detail) {
  console.log(`⚠️  KNOWN GAP  ${name}${detail ? '  → ' + detail : ''}`)
}

// 1) 有序协作
{
  const { a, b } = runSequential()
  check('有序协作（A 先编辑，B 末尾追加）两端收敛', a === b && a === 'Hello World', `A="${a}" B="${b}"`)
}
// 2) 同位置并发插入，siteA<siteB（与后端 TP1 测试一致的顺序）
{
  const { a, b } = runConcurrent(1, 2, '', '', 'Hello', ' World', [0, 1])
  check('并发同位置插入(siteA<siteB)收敛', a === b, `A="${a}" B="${b}"`)
}
// 3) 同位置并发插入，siteId 顺序与服务器到达顺序相反（之前误判为 TP2 缺口，
//    实则客户端未对 incoming 做 transform 所致；修正后两端一致收敛）
{
  const { a, b } = runConcurrent(2, 1, '', '', 'Hello', ' World', [0, 1])
  check('并发同位置插入(siteA>siteB)收敛', a === b, `A="${a}" B="${b}"`)
}
// 4) 插入落入他人并发删除区间（单区间模型退化为吞掉插入）
{
  const { a, b } = runConcurrent(1, 2, 'Hello World', 'Hello World', 'Hello ', 'Hello WoXXrld', [0, 1])
  check('插入落入并发删除区间：两端一致（吞掉插入）', a === b, `A="${a}" B="${b}"`)
}
// 5) 并发重叠删除
{
  const { a, b } = runConcurrent(1, 2, 'Hello World', 'Hello World', 'Hell', 'Hello W', [0, 1])
  check('并发重叠删除两端收敛', a === b, `A="${a}" B="${b}"`)
}

console.log(`\n==== OT 客户端收敛回归：${failed === 0 ? '全部必须收敛的场景通过' : failed + ' 项意外分叉'} ====`)
process.exit(failed ? 1 : 0)
