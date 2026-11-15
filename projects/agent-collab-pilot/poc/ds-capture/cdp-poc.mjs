// cdp-poc.mjs — 路线 A 机制验证：无头 Chrome + CDP 注入 hook → jsonl
// 用法: node cdp-poc.mjs [URL] [DURATION_SEC]
// 零第三方依赖：Node 22+ 原生 WebSocket
import { spawn } from 'node:child_process';
import fs from 'node:fs';

const URL_ = process.argv[2] || 'https://chat.deepseek.com/';
const DUR = Number(process.argv[3] || 75);
const OUT = new URL('./capture-' + Date.now() + '.jsonl', import.meta.url).pathname;
const HOOK = fs.readFileSync(new URL('./hook.js', import.meta.url), 'utf8');
const PROFILE = '/tmp/dscap-poc-profile-' + Date.now();

const chrome = spawn('google-chrome', [
  '--headless=new', '--no-sandbox', '--disable-gpu',
  '--remote-debugging-port=0', `--user-data-dir=${PROFILE}`,
  '--disable-extensions', '--no-first-run', '--window-size=1280,900',
  'about:blank',
], { stdio: ['ignore', 'pipe', 'pipe'] });

const wsUrl = await new Promise((res, rej) => {
  let buf = '';
  chrome.stderr.on('data', (d) => {
    buf += d;
    const m = buf.match(/DevTools listening on (ws:\/\/\S+)/);
    if (m) res(m[1]);
  });
  chrome.on('exit', (c) => rej(new Error('chrome exited ' + c + ' :: ' + buf.slice(-300))));
  setTimeout(() => rej(new Error('ws url timeout :: ' + buf.slice(-300))), 20000);
});

const ws = new WebSocket(wsUrl);
await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej; });

let msgId = 0;
const pending = new Map();
const send = (method, params = {}, sessionId) => new Promise((res, rej) => {
  const id = ++msgId;
  pending.set(id, { res, rej });
  ws.send(JSON.stringify({ id, method, params, sessionId }));
});
ws.onmessage = (ev) => {
  const m = JSON.parse(ev.data);
  if (m.id && pending.has(m.id)) {
    const { res, rej } = pending.get(m.id); pending.delete(m.id);
    m.error ? rej(new Error(JSON.stringify(m.error))) : res(m.result);
  } else if (m.method === 'Runtime.consoleAPICalled' && m.sessionId) {
    for (const arg of m.params.args || []) {
      const v = arg.value;
      if (typeof v === 'string' && v.startsWith('__DSCAP__')) {
        fs.appendFileSync(OUT, v.slice(9) + '\n');
        const rec = JSON.parse(v.slice(9));
        console.log('[CAP]', rec.k, rec.method || '', (rec.url || rec.href || '').slice(0, 90), rec.status ?? '');
      }
    }
  }
};

const { targetId } = await send('Target.createTarget', { url: 'about:blank' });
const { sessionId } = await send('Target.attachToTarget', { targetId, flatten: true });
await send('Runtime.enable', {}, sessionId);
await send('Page.enable', {}, sessionId);
await send('Page.addScriptToEvaluateOnNewDocument', { source: 'window.__DSCAP_WIDE=true;' }, sessionId);
await send('Page.addScriptToEvaluateOnNewDocument', { source: HOOK }, sessionId);
await send('Page.navigate', { url: URL_ }, sessionId);

console.log('[POC] navigating', URL_, '| capture window', DUR + 's | out', OUT);
await new Promise(r => setTimeout(r, 9000)); // 等 SPA 启动
const DRV = `(() => {
  const E=(m)=>console.log('__DSCAP__'+JSON.stringify({k:'drv',m,ts:Date.now()}));
  E('readyState='+document.readyState+' url='+location.href);
  const ta=document.querySelector('textarea');
  E('textarea='+(ta?'yes':'no'));
  if(ta){ const st=Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype,'value').set; st.call(ta,'ping'); ta.dispatchEvent(new Event('input',{bubbles:true})); }
  const btns=[...document.querySelectorAll('button')].map(b=>(b.getAttribute('aria-label')||b.title||String(b.className).slice(0,30))).slice(0,12);
  E('buttons='+JSON.stringify(btns));
  if(ta){ ta.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true})); }
  return 'drv-done'; })()`;
try { const r = await send('Runtime.evaluate', { expression: DRV, returnByValue: true }, sessionId); console.log('[POC] driver:', r.result?.value); }
catch (e) { console.log('[POC] driver err:', e.message); }
setTimeout(async () => {
  try { await send('Browser.close'); } catch {}
  try { chrome.kill('SIGKILL'); } catch {}
  const n = fs.existsSync(OUT) ? fs.readFileSync(OUT, 'utf8').trim().split('\n').filter(Boolean).length : 0;
  console.log('[POC] done. records:', n, '→', OUT);
  // 进程替换式清理由调用方负责 profile 目录（禁 rm -rf 军规：保留供查验）
  console.log('[POC] profile kept at', PROFILE);
  process.exit(0);
}, DUR * 1000);
