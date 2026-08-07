/**
 * 内嵌 HTML（srcdoc）宿主桥接：业务页无需感知 LightBot 协议。
 * - 拦截成功的 fetch / XHR（默认识别 POST/PUT/PATCH）
 * - 识别取消按钮点击
 * - 演示路径 /__lightbot_bp_demo__ 由桥接返回模拟成功响应
 * - 仍兼容显式 LightBot.submit / postMessage
 */

export const BUSINESS_PAGE_MSG_SOURCE = 'lightbot-business-page'
export const BUSINESS_PAGE_DEMO_PATH = '/__lightbot_bp_demo__'

/**
 * @param {Record<string, any>} [options] payload.options 中的捕获/身份配置
 * @param {Record<string, any>|null} [initPayload] 含 callerContext / identityHeaders
 * @returns {object}
 */
export function resolveCaptureConfig(options = {}, initPayload = null) {
  const opts = options && typeof options === 'object' ? options : {}
  const init = initPayload && typeof initPayload === 'object' ? initPayload : {}
  const methods = Array.isArray(opts.captureMethods) && opts.captureMethods.length
    ? opts.captureMethods.map((m) => String(m).toUpperCase())
    : ['POST', 'PUT', 'PATCH']
  const identityHeaders = (init.identityHeaders && typeof init.identityHeaders === 'object')
    ? init.identityHeaders
    : {}
  return {
    autoCapture: opts.autoCapture !== false,
    captureMethods: methods,
    captureUrlIncludes: asStringArray(opts.captureUrlIncludes),
    captureUrlExcludes: asStringArray(opts.captureUrlExcludes),
    demoPath: BUSINESS_PAGE_DEMO_PATH,
    // 身份透传：默认证出站 Header（仅 pageHtml 桥接可强制）
    injectIdentityHeaders: opts.injectIdentityHeaders !== false,
    contextHeaderUrlIncludes: asStringArray(opts.contextHeaderUrlIncludes),
    callerContext: init.callerContext ?? null,
    identityHeaders,
  }
}

function asStringArray(v) {
  if (!Array.isArray(v)) return []
  return v.map((x) => String(x || '').trim()).filter(Boolean)
}

/**
 * 将桥接脚本注入到业务 HTML 头部（仅内嵌 srcdoc 使用）。
 * @param {string} html
 * @param {Record<string, any>} [options]
 * @param {Record<string, any>|null} [initPayload] 预填数据，写入 window.__LIGHTBOT_BP_INIT__
 * @returns {string}
 */
export function injectBusinessPageBridge(html, options, initPayload = null) {
  const raw = String(html || '')
  if (!raw.trim()) return raw
  if (raw.includes('data-lightbot-bp-bridge')) return raw
  let safeInit = null
  try {
    safeInit = initPayload == null ? null : JSON.parse(JSON.stringify(initPayload))
  } catch {
    safeInit = null
  }
  const cfg = resolveCaptureConfig(options, safeInit)
  // 转义 < 防止 JSON 中的 </script> 提前闭合标签
  const initJson = safeInit
    ? JSON.stringify(safeInit).replace(/</g, '\\u003c')
    : ''
  const initTag = initJson
    ? `<script data-lightbot-bp-init="1">window.__LIGHTBOT_BP_INIT__=${initJson};<\/script>`
    : ''
  const script = `${initTag}${buildBridgeScript(cfg)}`
  if (/<head[^>]*>/i.test(raw)) {
    return raw.replace(/<head[^>]*>/i, (m) => `${m}${script}`)
  }
  if (/<html[^>]*>/i.test(raw)) {
    return raw.replace(/<html[^>]*>/i, (m) => `${m}${script}`)
  }
  return `${script}${raw}`
}

/**
 * @param {object} cfg
 * @returns {string}
 */
export function buildBridgeScript(cfg) {
  const json = JSON.stringify(cfg)
  // 注意：以下为注入到 iframe 的 IIFE，勿使用外层模板未转义的反引号嵌套问题
  return `<script data-lightbot-bp-bridge="1">(function(){
  if (window.__LIGHTBOT_BP_BRIDGE__) return;
  window.__LIGHTBOT_BP_BRIDGE__ = true;
  var SOURCE = ${JSON.stringify(BUSINESS_PAGE_MSG_SOURCE)};
  var CFG = ${json};
  var done = false;

  function notify(type, extra) {
    try {
      var msg = { source: SOURCE, type: type };
      if (extra && typeof extra === 'object') {
        for (var k in extra) {
          if (Object.prototype.hasOwnProperty.call(extra, k)) msg[k] = extra[k];
        }
      }
      parent.postMessage(msg, '*');
    } catch (e) {}
  }

  function markDone() {
    done = true;
  }

  function summarize(responseData, requestBody) {
    if (responseData && typeof responseData === 'object' && !Array.isArray(responseData)) {
      return responseData;
    }
    if (requestBody && typeof requestBody === 'object' && !Array.isArray(requestBody)) {
      return requestBody;
    }
    if (responseData != null && responseData !== '') {
      return { result: responseData };
    }
    return requestBody != null ? { result: requestBody } : {};
  }

  function parseBody(body) {
    if (body == null || body === '') return null;
    if (typeof body === 'string') {
      try { return JSON.parse(body); } catch (e) { return { raw: body }; }
    }
    if (typeof URLSearchParams !== 'undefined' && body instanceof URLSearchParams) {
      var o = {};
      body.forEach(function(v, k) { o[k] = v; });
      return o;
    }
    return null;
  }

  function resolveUrl(input) {
    try {
      if (typeof input === 'string') return input;
      if (input && typeof input.url === 'string') return input.url;
    } catch (e) {}
    return '';
  }

  function resolveMethod(input, init, fallback) {
    var m = (init && init.method) || fallback || 'GET';
    try {
      if ((!init || !init.method) && input && typeof input.method === 'string') m = input.method;
    } catch (e) {}
    return String(m || 'GET').toUpperCase();
  }

  function urlMatched(url) {
    var u = String(url || '');
    var includes = CFG.captureUrlIncludes || [];
    var excludes = CFG.captureUrlExcludes || [];
    for (var i = 0; i < excludes.length; i++) {
      if (excludes[i] && u.indexOf(excludes[i]) !== -1) return false;
    }
    if (!includes.length) return true;
    for (var j = 0; j < includes.length; j++) {
      if (includes[j] && u.indexOf(includes[j]) !== -1) return true;
    }
    return false;
  }

  function methodMatched(method) {
    var list = CFG.captureMethods || ['POST', 'PUT', 'PATCH'];
    return list.indexOf(String(method || '').toUpperCase()) !== -1;
  }

  function shouldCapture(method, url, ok) {
    if (!CFG.autoCapture || done || !ok) return false;
    if (!methodMatched(method)) return false;
    return urlMatched(url);
  }

  function isSameOrigin(url) {
    try {
      if (!url || !/^https?:\\/\\//i.test(url)) return true;
      var a = document.createElement('a');
      a.href = url;
      return a.origin === window.location.origin;
    } catch (e) { return false; }
  }

  /**
   * 出站身份 Header 作用域：
   * - 有 includes → 仅匹配
   * - 跳过静态资源
   * - 同源 / 相对路径 / 含 /api/ → 注入
   * - 跨源（如 localhost→127.0.0.1）仅对写请求注入，避免污染 CDN GET
   */
  function shouldInjectIdentity(url, method) {
    if (!CFG.injectIdentityHeaders) return false;
    var idh = CFG.identityHeaders || {};
    var hasHeader = false;
    for (var hk in idh) {
      if (Object.prototype.hasOwnProperty.call(idh, hk) && idh[hk] != null && idh[hk] !== '') {
        hasHeader = true;
        break;
      }
    }
    if (!hasHeader) return false;
    var u = String(url || '');
    var includes = CFG.contextHeaderUrlIncludes || [];
    if (!includes.length) includes = CFG.captureUrlIncludes || [];
    var excludes = CFG.captureUrlExcludes || [];
    for (var i = 0; i < excludes.length; i++) {
      if (excludes[i] && u.indexOf(excludes[i]) !== -1) return false;
    }
    if (includes.length) {
      for (var j = 0; j < includes.length; j++) {
        if (includes[j] && u.indexOf(includes[j]) !== -1) return true;
      }
      return false;
    }
    if (/\\.(css|js|mjs|png|jpe?g|gif|svg|webp|ico|woff2?|ttf|map)(\\?|$)/i.test(u)) return false;
    if (u.indexOf('/api/') !== -1) return true;
    var m = String(method || 'GET').toUpperCase();
    var mutating = m === 'POST' || m === 'PUT' || m === 'PATCH' || m === 'DELETE';
    // localhost 与 127.0.0.1 不同源；业务提交多为跨源写请求，仍需注身份
    if (/^https?:\\/\\//i.test(u) && !isSameOrigin(u)) return mutating;
    return true;
  }

  function applyIdentityToHeaders(headersLike) {
    var idh = CFG.identityHeaders || {};
    var headers = headersLike;
    try {
      if (typeof Headers !== 'undefined' && !(headers instanceof Headers)) {
        headers = new Headers(headers || {});
      } else if (!headers) {
        headers = typeof Headers !== 'undefined' ? new Headers() : {};
      }
    } catch (e) {
      headers = headers || {};
    }
    for (var k in idh) {
      if (!Object.prototype.hasOwnProperty.call(idh, k)) continue;
      var v = idh[k];
      if (v == null || v === '') continue;
      try {
        if (headers && typeof headers.has === 'function') {
          if (!headers.has(k)) headers.set(k, String(v));
        } else if (headers && headers[k] == null) {
          headers[k] = String(v);
        }
      } catch (e2) {}
    }
    return headers;
  }

  function syncIdentityFromInit(init) {
    if (!init || typeof init !== 'object') return;
    if (Object.prototype.hasOwnProperty.call(init, 'callerContext')) {
      CFG.callerContext = init.callerContext;
    }
    if (init.identityHeaders && typeof init.identityHeaders === 'object') {
      CFG.identityHeaders = init.identityHeaders;
    }
    if (init.options && typeof init.options === 'object') {
      if (Object.prototype.hasOwnProperty.call(init.options, 'injectIdentityHeaders')) {
        CFG.injectIdentityHeaders = init.options.injectIdentityHeaders !== false;
      }
      if (Array.isArray(init.options.contextHeaderUrlIncludes)) {
        CFG.contextHeaderUrlIncludes = init.options.contextHeaderUrlIncludes.map(function(x) {
          return String(x || '').trim();
        }).filter(Boolean);
      }
    }
  }

  function emitSubmit(values, extra) {
    if (done) return;
    markDone();
    var payload = { values: values || {} };
    if (extra && typeof extra === 'object') payload.extra = extra;
    notify('submit', payload);
  }

  /** 提取节点可见文案（去掉内部控件，避免把 input value 拼进标签） */
  function extractLabelText(node) {
    if (!node) return '';
    try {
      var clone = node.cloneNode(true);
      var nested = clone.querySelectorAll('input,select,textarea,button,svg');
      for (var n = 0; n < nested.length; n++) {
        if (nested[n].parentNode) nested[n].parentNode.removeChild(nested[n]);
      }
      return String(clone.textContent || '').replace(/\\s+/g, ' ').trim();
    } catch (e) {
      return String(node.textContent || '').replace(/\\s+/g, ' ').trim();
    }
  }

  function escapeCssAttr(v) {
    return String(v || '').replace(/"/g, '\\\\"');
  }

  function isLabelish(node) {
    if (!node || !node.tagName) return false;
    var tag = String(node.tagName).toLowerCase();
    if (tag === 'label') return true;
    var cls = ' ' + String(node.className || '') + ' ';
    if (/(\\blabel\\b|form-item-label|field-label|form-label)/i.test(cls)) return true;
    return false;
  }

  function pickLabelNodeFrom(container) {
    if (!container) return null;
    if (isLabelish(container)) return container.tagName && String(container.tagName).toLowerCase() === 'label'
      ? container
      : (container.querySelector && container.querySelector('label')) || container;
    try {
      var lab = container.querySelector && container.querySelector('label');
      if (lab) return lab;
      var byCls = container.querySelector
        && container.querySelector('.label, .field-label, .form-label, .form-item-label, [class*="label"]');
      if (byCls && extractLabelText(byCls)) return byCls;
    } catch (e) {}
    return null;
  }

  /**
   * 解析单个控件的展示名。
   * 报名等 AI 页常见「label 列 + control 列」分栏，需向上找表单项容器，不能只看紧邻兄弟。
   */
  function resolveLabelForControl(el) {
    if (!el) return '';
    var text = '';
    // 1) label[for]
    if (el.id) {
      try {
        text = extractLabelText(document.querySelector('label[for="' + escapeCssAttr(el.id) + '"]'));
      } catch (e1) {}
    }
    // 2) 包在 label 内
    if (!text && el.closest) {
      try { text = extractLabelText(el.closest('label')); } catch (e2) {}
    }
    // 3) aria-labelledby
    if (!text) {
      var labelledBy = el.getAttribute && el.getAttribute('aria-labelledby');
      if (labelledBy) {
        try {
          var ref = document.getElementById(labelledBy.split(/\\s+/)[0]);
          text = extractLabelText(ref);
        } catch (e3) {}
      }
    }
    // 4) 向上最多 5 层：前一个兄弟 / 同层 label 列（Ant/表单 item 分栏）
    if (!text) {
      var node = el;
      for (var depth = 0; depth < 5 && node; depth++) {
        var prev = node.previousElementSibling;
        while (prev) {
          var fromPrev = pickLabelNodeFrom(prev);
          if (fromPrev) {
            text = extractLabelText(fromPrev);
            if (text) break;
          }
          if ((prev.tagName || '').toLowerCase() === 'br') {
            prev = prev.previousElementSibling;
            continue;
          }
          break;
        }
        if (text) break;
        var parent = node.parentElement;
        if (!parent) break;
        try {
          var kids = parent.children || [];
          var labelKids = [];
          for (var ki = 0; ki < kids.length; ki++) {
            if (kids[ki] !== el && !kids[ki].contains(el) && pickLabelNodeFrom(kids[ki])) {
              labelKids.push(pickLabelNodeFrom(kids[ki]));
            }
          }
          // 去重
          var uniq = [];
          for (var ui = 0; ui < labelKids.length; ui++) {
            if (uniq.indexOf(labelKids[ui]) < 0) uniq.push(labelKids[ui]);
          }
          if (uniq.length === 1) {
            text = extractLabelText(uniq[0]);
            if (text) break;
          }
        } catch (e4) {}
        node = parent;
      }
    }
    // 5) data-label / aria-label
    if (!text) {
      text = String(el.getAttribute('data-label') || el.getAttribute('aria-label') || '').trim();
    }
    return text;
  }

  function listFormControls() {
    var list = [];
    try {
      var nodes = document.querySelectorAll('input, select, textarea');
      for (var i = 0; i < nodes.length; i++) {
        var el = nodes[i];
        var typ = String(el.type || '').toLowerCase();
        if (typ === 'hidden' || typ === 'submit' || typ === 'button' || typ === 'reset' || typ === 'image') continue;
        if (el.disabled) continue;
        list.push(el);
      }
    } catch (e) {}
    return list;
  }

  /**
   * 从页面 DOM 采集字段展示名。
   * 禁止平台硬编码中英词典——采页面 label 原文。
   * 报名页常见坑：JSON key（name/phone）与 DOM name/id 对不上，或 label 在分栏容器里；
   * 故增加「按 DOM 顺序与提交字段顺序对齐」兜底。
   */
  function collectFieldLabels(keys) {
    var labels = {};
    if (!keys || !keys.length) return labels;
    var controls = listFormControls();

    for (var i = 0; i < keys.length; i++) {
      var key = keys[i];
      if (!key) continue;
      var el = null;
      var safe = escapeCssAttr(key);
      try {
        el = document.getElementById(key)
          || document.querySelector('[name="' + safe + '"]')
          || document.querySelector('[data-field="' + safe + '"]');
      } catch (e) { el = null; }
      // 再按 controls 的 name/id 匹配（含动态生成的控件）
      if (!el) {
        for (var ci = 0; ci < controls.length; ci++) {
          var c = controls[ci];
          if (c.name === key || c.id === key || c.getAttribute('data-field') === key) {
            el = c;
            break;
          }
        }
      }
      if (!el) continue;
      var text = resolveLabelForControl(el);
      if (text) labels[key] = text;
    }

    // 兜底：仍有缺失且可见控件数与提交字段数一致时，按 DOM 顺序对齐（AI 报名页常见）
    var missing = [];
    for (var mi = 0; mi < keys.length; mi++) {
      if (!labels[keys[mi]]) missing.push(keys[mi]);
    }
    if (missing.length && controls.length === keys.length) {
      for (var zi = 0; zi < keys.length; zi++) {
        if (labels[keys[zi]]) continue;
        var zText = resolveLabelForControl(controls[zi]);
        if (zText) labels[keys[zi]] = zText;
      }
    }
    return labels;
  }

  function withFieldLabels(formValues, extra) {
    var base = (extra && typeof extra === 'object') ? extra : {};
    if (!base.fieldLabels || typeof base.fieldLabels !== 'object') {
      base.fieldLabels = collectFieldLabels(Object.keys(formValues || {}));
    }
    return base;
  }

  /** 优先回传表单字段；接口响应放 extra，避免宿主把演示 JSON 整段当摘要展示 */
  function emitCaptured(responseData, requestBody) {
    var formValues = (requestBody && typeof requestBody === 'object' && !Array.isArray(requestBody))
      ? requestBody
      : summarize(responseData, requestBody);
    var extra = withFieldLabels(formValues, {});
    if (responseData && typeof responseData === 'object' && !Array.isArray(responseData)) {
      extra.response = responseData;
    }
    emitSubmit(formValues, extra);
  }

  function emitCancel() {
    if (done) return;
    markDone();
    notify('cancel', {});
  }

  window.LightBot = window.LightBot || {
    // 显式 submit 同样采集 DOM 标签，避免摘要只剩英文 field key
    submit: function(values, extra) {
      var formValues = values || {};
      emitSubmit(formValues, withFieldLabels(formValues, extra));
    },
    cancel: function() { emitCancel(); },
    resize: function() {
      try {
        notify('resize', { height: (document.body && document.body.scrollHeight || 320) + 24 });
      } catch (e) {}
    },
    /** 平台断言的调用方身份（只读快照） */
    getCallerContext: function() {
      try {
        if (window.__LIGHTBOT_BP_INIT__ && Object.prototype.hasOwnProperty.call(window.__LIGHTBOT_BP_INIT__, 'callerContext')) {
          return window.__LIGHTBOT_BP_INIT__.callerContext;
        }
      } catch (e) {}
      return CFG.callerContext || null;
    },
    /** 已渲染的出站身份 Header 字典 */
    getIdentityHeaders: function() {
      try {
        if (window.__LIGHTBOT_BP_INIT__ && window.__LIGHTBOT_BP_INIT__.identityHeaders) {
          return window.__LIGHTBOT_BP_INIT__.identityHeaders;
        }
      } catch (e) {}
      return CFG.identityHeaders || {};
    }
  };

  // 演示接口：业务页可 fetch 此路径，无需真实后端
  var demoPath = CFG.demoPath || ${JSON.stringify(BUSINESS_PAGE_DEMO_PATH)};

  var rawFetch = window.fetch ? window.fetch.bind(window) : null;
  if (rawFetch) {
    window.fetch = function(input, init) {
      var url = resolveUrl(input);
      var method = resolveMethod(input, init, 'GET');
      var nextInit = init ? Object.assign({}, init) : {};
      var reqBody = parseBody(nextInit.body);

      if (shouldInjectIdentity(url, method)) {
        nextInit.headers = applyIdentityToHeaders(nextInit.headers);
      }

      if (url.indexOf(demoPath) !== -1) {
        var demoPayload = Object.assign({ status: 'accepted', message: '演示受理成功' }, reqBody || {});
        var demoRes = new Response(JSON.stringify(demoPayload), {
          status: 200,
          headers: { 'Content-Type': 'application/json' }
        });
        if (shouldCapture(method || 'POST', url, true)) {
          emitCaptured(demoPayload, reqBody);
        }
        return Promise.resolve(demoRes);
      }

      return rawFetch(input, nextInit).then(function(res) {
        if (shouldCapture(method, url, !!(res && res.ok))) {
          var cloned = res.clone();
          cloned.json().then(function(data) {
            emitCaptured(data, reqBody);
          }).catch(function() {
            cloned.text().then(function(text) {
              var parsed = parseBody(text);
              emitCaptured(parsed, reqBody);
            }).catch(function() {
              emitCaptured(null, reqBody);
            });
          });
        }
        return res;
      });
    };
  }

  // XHR 拦截
  if (window.XMLHttpRequest) {
    var XHR = window.XMLHttpRequest;
    var open = XHR.prototype.open;
    var send = XHR.prototype.send;
    var setRequestHeader = XHR.prototype.setRequestHeader;
    XHR.prototype.open = function(method, url) {
      this.__lbMethod = String(method || 'GET').toUpperCase();
      this.__lbUrl = String(url || '');
      this.__lbHeaders = {};
      return open.apply(this, arguments);
    };
    XHR.prototype.setRequestHeader = function(name, value) {
      this.__lbHeaders = this.__lbHeaders || {};
      this.__lbHeaders[String(name || '').toLowerCase()] = true;
      return setRequestHeader.apply(this, arguments);
    };
    XHR.prototype.send = function(body) {
      var xhr = this;
      var reqBody = parseBody(body);
      if (shouldInjectIdentity(xhr.__lbUrl, xhr.__lbMethod)) {
        var idh = CFG.identityHeaders || {};
        for (var hk in idh) {
          if (!Object.prototype.hasOwnProperty.call(idh, hk)) continue;
          var hv = idh[hk];
          if (hv == null || hv === '') continue;
          if (xhr.__lbHeaders && xhr.__lbHeaders[String(hk).toLowerCase()]) continue;
          try { setRequestHeader.call(xhr, hk, String(hv)); } catch (e) {}
        }
      }
      if (String(xhr.__lbUrl || '').indexOf(demoPath) !== -1) {
        var demoPayload = Object.assign({ status: 'accepted', message: '演示受理成功' }, reqBody || {});
        setTimeout(function() {
          try {
            Object.defineProperty(xhr, 'status', { configurable: true, get: function() { return 200; } });
            Object.defineProperty(xhr, 'responseText', { configurable: true, get: function() { return JSON.stringify(demoPayload); } });
            Object.defineProperty(xhr, 'readyState', { configurable: true, get: function() { return 4; } });
          } catch (e) {}
          if (shouldCapture(xhr.__lbMethod || 'POST', xhr.__lbUrl, true)) {
            emitCaptured(demoPayload, reqBody);
          }
          if (typeof xhr.onreadystatechange === 'function') xhr.onreadystatechange();
          if (typeof xhr.onload === 'function') xhr.onload();
        }, 30);
        return;
      }
      xhr.addEventListener('load', function() {
        var ok = xhr.status >= 200 && xhr.status < 300;
        if (!shouldCapture(xhr.__lbMethod, xhr.__lbUrl, ok)) return;
        var data = null;
        try { data = JSON.parse(xhr.responseText); } catch (e) { data = parseBody(xhr.responseText); }
        emitCaptured(data, reqBody);
      });
      return send.apply(this, arguments);
    };
  }

  function looksLikeCancel(el) {
    if (!el || el.disabled) return false;
    var tag = (el.tagName || '').toLowerCase();
    if (tag !== 'button' && tag !== 'a' && !(tag === 'input' && /button|submit|reset/i.test(el.type || ''))) {
      return false;
    }
    if (el.getAttribute && el.getAttribute('data-lightbot-cancel') != null) return true;
    if ((el.type || '').toLowerCase() === 'reset') return true;
    var id = String(el.id || '');
    var cls = String(el.className || '');
    var text = String(el.textContent || el.value || '').replace(/\\s+/g, '');
    if (/cancel/i.test(id) || /cancel/i.test(cls)) return true;
    if (text === '取消' || /^cancel$/i.test(text)) return true;
    return false;
  }

  document.addEventListener('click', function(ev) {
    if (done) return;
    var t = ev.target;
    while (t && t !== document) {
      if (looksLikeCancel(t)) {
        ev.preventDefault();
        emitCancel();
        return;
      }
      t = t.parentNode;
    }
  }, true);

  /**
   * 原生 form submit（冒泡阶段）：
   * - 报名页等会先自行 validate + preventDefault + fetch → 由 fetch 拦截回传（含 fieldLabels）
   * - 切勿在捕获阶段抢先 emit：会绕过页内校验，且旧逻辑未带 fieldLabels（摘要只剩英文 key）
   * - 仅当页面未 preventDefault（无自写 JS）时，才兜底序列化 FormData 并采集 label
   */
  document.addEventListener('submit', function(ev) {
    if (done || !CFG.autoCapture) return;
    var form = ev.target;
    if (!form || !form.tagName || form.tagName.toLowerCase() !== 'form') return;
    var action = String(form.getAttribute('action') || '');
    // 有外部 action 时交给页面/浏览器；仅接管空 action 的本地表单
    if (action && !/^#|^javascript:/i.test(action)) return;
    // 页面已处理（校验失败或即将 fetch）→ 不抢发
    if (ev.defaultPrevented) return;
    ev.preventDefault();
    var fd = new FormData(form);
    var values = {};
    fd.forEach(function(v, k) { values[k] = v; });
    emitSubmit(values, withFieldLabels(values, {}));
  }, false);

  // 禁止 html/body 撑满 iframe 视口，否则 scrollHeight 会跟着 iframe 变高，形成空白反馈环
  try {
    var resetStyle = document.createElement('style');
    resetStyle.setAttribute('data-lightbot-bp-resize', '1');
    resetStyle.textContent = 'html,body{height:auto!important;min-height:0!important;max-height:none!important;overflow:hidden!important;}';
    (document.head || document.documentElement).appendChild(resetStyle);
  } catch (e) {}

  var lastEmittedHeight = 0;
  var resizeTimer = null;

  /** 按内容实际底部量高，避免使用会等于 iframe 视口的 scrollHeight */
  function measureHeight() {
    try {
      var body = document.body;
      if (!body) return 0;
      var bottom = 0;
      var nodes = body.children;
      for (var i = 0; i < nodes.length; i++) {
        var el = nodes[i];
        if (!el || el.getAttribute && el.getAttribute('data-lightbot-bp-resize')) continue;
        var r = el.getBoundingClientRect();
        if (r && r.bottom > bottom) bottom = r.bottom;
      }
      // 无子节点时退回 body 内容高度（仍避免 documentElement.scrollHeight）
      if (bottom <= 0) {
        bottom = body.scrollHeight || body.offsetHeight || 0;
      } else {
        // getBoundingClientRect 相对视口；加上页面滚动偏移
        bottom += (window.pageYOffset || document.documentElement.scrollTop || 0);
      }
      var pad = 0;
      try {
        var cs = window.getComputedStyle(body);
        pad = (parseFloat(cs.paddingBottom) || 0) + (parseFloat(cs.marginBottom) || 0);
      } catch (e2) {}
      return Math.ceil(bottom + pad + 8);
    } catch (e) {
      return 0;
    }
  }

  function emitResize() {
    if (done) return;
    var h = measureHeight();
    if (h <= 0) return;
    // 变化过小不重复上报，防止抖高
    if (Math.abs(h - lastEmittedHeight) < 2) return;
    lastEmittedHeight = h;
    notify('resize', { height: h });
  }

  function scheduleResize() {
    if (resizeTimer) clearTimeout(resizeTimer);
    resizeTimer = setTimeout(emitResize, 16);
  }

  // 脚本在 head 注入时 body 可能尚未就绪；用 MutationObserver 跟随内容，不用 ResizeObserver(body)
  // （ResizeObserver 会在父页改 iframe 高度时误触发，把空白越撑越大）
  function startAutoResize() {
    emitResize();
    if (typeof MutationObserver !== 'undefined' && document.body) {
      try {
        var mo = new MutationObserver(function() { scheduleResize(); });
        mo.observe(document.body, {
          childList: true,
          subtree: true,
          attributes: true,
          characterData: true
        });
      } catch (e) {}
    }
    window.addEventListener('load', scheduleResize);
    window.addEventListener('resize', scheduleResize);
    setTimeout(emitResize, 50);
    setTimeout(emitResize, 200);
    setTimeout(emitResize, 500);
  }

  /** 将宿主注入的 init 再派发一次 message，兼容只监听 postMessage 的旧页面 */
  function replayInitFromWindow() {
    try {
      if (!window.__LIGHTBOT_BP_INIT__) return;
      var init = window.__LIGHTBOT_BP_INIT__;
      syncIdentityFromInit(init);
      // 历史已提交：锁定交互，避免刷新后再次捕获
      if (init && init.options && init.options.submitted) {
        markDone();
        try {
          var buttons = document.querySelectorAll('button, input[type=submit], input[type=button]');
          for (var bi = 0; bi < buttons.length; bi++) buttons[bi].disabled = true;
        } catch (e0) {}
      }
      window.dispatchEvent(new MessageEvent('message', {
        data: { source: SOURCE, type: 'init', payload: init }
      }));
    } catch (e) {}
  }

  notify('ready', {});
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function() {
      replayInitFromWindow();
      startAutoResize();
    });
  } else {
    replayInitFromWindow();
    startAutoResize();
  }
  window.addEventListener('message', function(e) {
    if (e.data && e.data.source === SOURCE && e.data.type === 'init') {
      try {
        syncIdentityFromInit(e.data.payload);
        if (e.data.payload) window.__LIGHTBOT_BP_INIT__ = e.data.payload;
      } catch (e1) {}
      setTimeout(emitResize, 30);
      setTimeout(emitResize, 120);
    }
  });
})();<\/script>`
}
