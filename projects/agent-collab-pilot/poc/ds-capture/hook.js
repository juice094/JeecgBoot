// DS 会话捕获钩子（路线 A：页面内 fetch/XHR 包装）
// 注入方式：CDP Page.addScriptToEvaluateOnNewDocument
// 输出通道：console.log('__DSCAP__'+JSON) → 父层 CDP Runtime.consoleAPICalled 落 jsonl
(() => {
  if (window.__dsCapture) return; window.__dsCapture = true;
  const TAG = '__DSCAP__';
  const EMIT = (rec) => { try { console.log(TAG + JSON.stringify(rec)); } catch (e) {} };
  // 关注点：deepseek web API（广义匹配，PoC 期宽松，正式版收紧到 /api/v0/ 清单）
  const INTEREST = /(^|\.)chat\.deepseek\.com\/|\/api\/v0\//;
  const HIT = (url) => window.__DSCAP_WIDE === true || INTEREST.test(url);
  const MAXBODY = 200000; // 200KB/条，防巨型响应炸管道

  // --- fetch 包装 ---
  const of = window.fetch;
  window.fetch = async (...args) => {
    const req = args[0];
    const url = String((req && req.url) || req);
    const method = (args[1] && args[1].method) || (req && req.method) || 'GET';
    const res = await of(...args);
    try {
      if (HIT(url)) {
        const clone = res.clone();
        let body = null;
        try { body = (await clone.text()).slice(0, MAXBODY); } catch (e) {}
        EMIT({ k: 'fetch', url, method, status: res.status, ts: Date.now(), body });
      }
    } catch (e) {}
    return res;
  };

  // --- XHR 包装 ---
  const oo = XMLHttpRequest.prototype.open;
  const os = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function (method, url, ...rest) {
    this.__cap = { method, url: String(url) };
    return oo.call(this, method, url, ...rest);
  };
  XMLHttpRequest.prototype.send = function (body) {
    const cap = this.__cap;
    if (cap && HIT(cap.url)) {
      this.addEventListener('load', () => {
        let rb = null;
        try { rb = String(this.responseText || '').slice(0, MAXBODY); } catch (e) {}
        EMIT({ k: 'xhr', ...cap, status: this.status, ts: Date.now(), body: rb });
      });
    }
    return os.call(this, body);
  };

  EMIT({ k: 'boot', ts: Date.now(), href: location.href, ua: navigator.userAgent });
})();
