/** 新建业务页默认 H5 模板（iframe srcdoc）
 * 仅作结构样例，不含具体业务字段词典；标签文案可按业务自行改写。
 * 宿主会静默注入桥接，成功 fetch 后自动回传对话。
 */
export const DEFAULT_BUSINESS_PAGE_HTML = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>业务办理</title>
  <style>
    * { box-sizing: border-box; }
    body { margin: 0; font-family: system-ui, sans-serif; padding: 12px; color: #111; }
    label { display: block; font-size: 13px; margin: 10px 0 4px; }
    input, select, textarea {
      width: 100%; padding: 8px 10px; border: 1px solid #d4d4d8;
      border-radius: 8px; font-size: 14px;
    }
    .actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }
    button {
      padding: 8px 14px; border-radius: 8px; border: 1px solid #d4d4d8;
      background: #fff; cursor: pointer;
    }
    button.primary { background: #171717; color: #fff; border-color: #171717; }
    button:disabled { opacity: 0.55; cursor: not-allowed; }
    .hint { font-size: 12px; color: #71717a; margin: 0 0 8px; }
    .error {
      display: none; margin-top: 10px; padding: 8px 10px; border-radius: 8px;
      background: #fef2f2; color: #b91c1c; font-size: 12px; line-height: 1.45;
    }
    .error.show { display: block; }
  </style>
</head>
<body>
  <p class="hint" id="hint">请填写信息后提交。请按业务需要改字段名与校验；办结摘要会采集下方 label 文案。</p>
  <!-- 标签请与控件关联（包裹或 for=id）；摘要展示 label 原文，平台不会翻译 name/id -->
  <label for="field1">示例字段一
    <input id="field1" name="field1" autocomplete="off" />
  </label>
  <label for="field2">示例字段二
    <input id="field2" name="field2" autocomplete="off" />
  </label>
  <div class="error" id="error"></div>
  <div class="actions">
    <button type="button" id="btnCancel">取消</button>
    <button type="button" class="primary" id="btnSubmit">提交</button>
  </div>
  <script>
    // 改成你们的业务办理接口；留空则走宿主提供的本地演示路径
    const API_URL = '';

    let ctx = { props: {}, options: {}, pageType: '' };
    const elHint = document.getElementById('hint');
    const elError = document.getElementById('error');
    const elField1 = document.getElementById('field1');
    const elField2 = document.getElementById('field2');
    const btnSubmit = document.getElementById('btnSubmit');
    const btnCancel = document.getElementById('btnCancel');

    function showError(msg) {
      elError.textContent = msg || '办理失败';
      elError.classList.add('show');
    }

    function clearError() {
      elError.textContent = '';
      elError.classList.remove('show');
    }

    function setBusy(busy) {
      btnSubmit.disabled = busy;
      btnCancel.disabled = busy;
      btnSubmit.textContent = busy ? '办理中…' : '提交';
    }

    function readForm() {
      return {
        field1: (elField1.value || '').trim(),
        field2: (elField2.value || '').trim()
      };
    }

    function validate(form) {
      if (!form.field1) return '请填写示例字段一';
      if (!form.field2) return '请填写示例字段二';
      return '';
    }

    function applyInit(payload) {
      ctx = payload || ctx;
      if (ctx.options && ctx.options.hint) elHint.textContent = ctx.options.hint;
      const p = ctx.props || {};
      if (p.field1 != null) elField1.value = p.field1;
      if (p.field2 != null) elField2.value = p.field2;
    }

    if (window.__LIGHTBOT_BP_INIT__) applyInit(window.__LIGHTBOT_BP_INIT__);
    window.addEventListener('message', (e) => {
      const data = e.data;
      if (!data || data.source !== 'lightbot-business-page' || data.type !== 'init') return;
      applyInit(data.payload || ctx);
    });

    btnSubmit.onclick = async () => {
      clearError();
      const form = readForm();
      const err = validate(form);
      if (err) {
        showError(err);
        return;
      }
      setBusy(true);
      try {
        const url = API_URL || '/__lightbot_bp_demo__';
        const res = await fetch(url, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(form)
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
          throw new Error(data.message || data.error || ('办理失败（HTTP ' + res.status + '）'));
        }
      } catch (e) {
        showError(e && e.message ? e.message : '办理失败，请稍后重试');
        setBusy(false);
      }
    };
  </script>
</body>
</html>
`
