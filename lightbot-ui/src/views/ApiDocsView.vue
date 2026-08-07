<template>
  <div class="docs-shell">
    <header class="docs-topbar">
      <div class="docs-brand" @click="openHome">
        <img src="/lightbot-logo-single.png" alt="智元" class="docs-logo" />
        <span>智元 在线文档</span>
      </div>
      <div class="docs-topbar-right">
        <span class="docs-topbar-hint">API Key 开放能力</span>
      </div>
    </header>

    <div class="docs-body">
      <!-- 左侧：稳定能力导航 -->
      <aside class="docs-nav">
        <div class="docs-nav-title">开放能力</div>
        <nav class="docs-nav-scroll">
          <div v-for="group in groups" :key="group.id" class="docs-nav-group">
            <button
              type="button"
              class="docs-nav-group-btn"
              :class="{ active: activeGroupId === group.id && !activeApiId }"
              @click="selectGroup(group.id)"
            >
              <span>{{ group.title }}</span>
              <span v-if="group.type === 'apis'" class="docs-nav-count">{{ group.apis?.length || 0 }}</span>
            </button>
            <div v-if="group.type === 'apis' && activeGroupId === group.id" class="docs-nav-apis">
              <button
                v-for="api in group.apis"
                :key="api.id"
                type="button"
                class="docs-nav-api-btn"
                :class="{ active: activeApiId === api.id }"
                @click="selectApi(group.id, api.id)"
              >
                <span class="docs-nav-method" :data-method="api.method">{{ api.method }}</span>
                <span class="docs-nav-summary">{{ api.summary }}</span>
              </button>
            </div>
          </div>
        </nav>
      </aside>

      <!-- 右侧：可滚动详情 -->
      <main class="docs-content">
        <div class="docs-content-scroll">
          <!-- 指南页 -->
          <template v-if="activeGroup?.type === 'guide'">
            <div class="docs-panel">
              <h1>{{ activeGroup.title }}</h1>
              <p class="docs-lead">{{ activeGroup.desc }}</p>
              <div class="docs-guide" v-html="activeGroup.guideHtml" />
            </div>
          </template>

          <!-- 接口详情 -->
          <template v-else-if="activeApi">
            <div class="docs-panel">
              <div class="docs-api-head">
                <code class="docs-method" :data-method="activeApi.method">{{ activeApi.method }}</code>
                <code class="docs-path">{{ activeApi.path }}</code>
              </div>
              <h1>{{ activeApi.summary }}</h1>
              <p class="docs-lead">{{ activeApi.description }}</p>

              <div class="docs-tabs" role="tablist">
                <button
                  type="button"
                  role="tab"
                  class="docs-tab"
                  :class="{ active: contentTab === 'spec' }"
                  :aria-selected="contentTab === 'spec'"
                  @click="contentTab = 'spec'"
                >
                  接口说明
                </button>
                <button
                  type="button"
                  role="tab"
                  class="docs-tab"
                  :class="{ active: contentTab === 'test' }"
                  :aria-selected="contentTab === 'test'"
                  @click="contentTab = 'test'"
                >
                  在线测试
                </button>
              </div>

              <div v-show="contentTab === 'spec'" class="docs-tab-panel" role="tabpanel">
                <section class="docs-section">
                  <h2>入参</h2>
                  <div v-if="!activeApi.params?.length" class="docs-empty">无入参</div>
                  <table v-else class="docs-table">
                    <thead>
                      <tr>
                        <th>名称</th>
                        <th>位置</th>
                        <th>类型</th>
                        <th>必填</th>
                        <th>说明</th>
                        <th>示例</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="p in activeApi.params" :key="p.in + p.name">
                        <td><code>{{ p.name }}</code></td>
                        <td>{{ p.in || 'body' }}</td>
                        <td>{{ p.type }}</td>
                        <td>{{ p.required ? '是' : '否' }}</td>
                        <td>{{ p.desc }}</td>
                        <td>
                          <code v-if="p.example" class="docs-example">{{ p.example }}</code>
                          <span v-else class="docs-mute">—</span>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                  <div v-if="activeApi.bodyExample" class="docs-subblock">
                    <div class="docs-subtitle">请求示例</div>
                    <pre class="docs-code">{{ activeApi.bodyExample }}</pre>
                  </div>
                </section>

                <section class="docs-section">
                  <h2>返回值</h2>
                  <div v-if="!activeApi.responseFields?.length" class="docs-empty">暂无字段说明</div>
                  <table v-else class="docs-table">
                    <thead>
                      <tr>
                        <th>名称</th>
                        <th>类型</th>
                        <th>说明</th>
                        <th>示例</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="f in activeApi.responseFields" :key="f.name">
                        <td><code>{{ f.name }}</code></td>
                        <td>{{ f.type }}</td>
                        <td>{{ f.desc }}</td>
                        <td>
                          <code v-if="f.example" class="docs-example">{{ f.example }}</code>
                          <span v-else class="docs-mute">—</span>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                  <div v-if="activeApi.responseExample" class="docs-subblock">
                    <div class="docs-subtitle">响应示例</div>
                    <pre class="docs-code">{{ activeApi.responseExample }}</pre>
                  </div>
                </section>
              </div>

              <div v-show="contentTab === 'test'" class="docs-tab-panel" role="tabpanel">
                <template v-if="activeApi.testable === false">
                  <div class="docs-empty">该接口暂不支持页面内测试，请使用 curl / Postman。</div>
                </template>
                <template v-else>
                  <div class="docs-test">
                    <div class="docs-test-row">
                      <label>API Key</label>
                      <a-input
                        v-model:value="apiKey"
                        placeholder="lbkey_xxxx"
                        allow-clear
                        autocomplete="off"
                        name="lightbot-api-key"
                        @change="persistApiKey"
                      />
                    </div>
                    <div
                      v-for="p in pathParams"
                      :key="'path-' + p.name"
                      class="docs-test-row"
                    >
                      <label>路径 {{ p.name }}</label>
                      <a-input v-model:value="pathValues[p.name]" :placeholder="p.example || p.desc" />
                    </div>
                    <div
                      v-for="p in queryParams"
                      :key="'query-' + p.name"
                      class="docs-test-row"
                    >
                      <label>Query {{ p.name }}</label>
                      <a-input v-model:value="queryValues[p.name]" :placeholder="p.example || p.desc" />
                    </div>
                    <div
                      v-for="p in fileParams"
                      :key="'file-' + p.name + '-' + fileInputKey"
                      class="docs-test-row"
                    >
                      <label>文件 {{ p.name }}</label>
                      <div class="docs-file-field">
                        <input
                          type="file"
                          class="docs-file-input"
                          @change="onFileChange(p.name, $event)"
                        />
                        <span v-if="fileValues[p.name]" class="docs-file-name">{{ fileValues[p.name].name }}</span>
                        <span v-else class="docs-mute">未选择文件</span>
                      </div>
                    </div>
                    <div v-if="needsBody" class="docs-test-row docs-test-row--block">
                      <label>Body (JSON)</label>
                      <a-textarea v-model:value="bodyText" :rows="10" class="docs-test-body" />
                    </div>
                    <div class="docs-test-actions">
                      <button
                        type="button"
                        class="docs-btn-primary"
                        :disabled="testing"
                        @click="runTest"
                      >
                        {{ testing ? '请求中…' : '发送请求' }}
                      </button>
                      <button type="button" class="docs-btn-ghost" :disabled="testing" @click="resetTestForm">
                        重置示例
                      </button>
                    </div>
                    <div v-if="testMeta" class="docs-test-meta">{{ testMeta }}</div>
                    <pre v-if="testResult != null" class="docs-code docs-code--result">{{ testResult }}</pre>
                  </div>
                </template>
              </div>
            </div>
          </template>

          <!-- 能力组概览：未选具体 API -->
          <template v-else-if="activeGroup?.type === 'apis'">
            <div class="docs-panel">
              <h1>{{ activeGroup.title }}</h1>
              <p class="docs-lead">{{ activeGroup.desc }} · 请在左侧选择具体接口</p>
              <div class="docs-api-cards">
                <button
                  v-for="api in activeGroup.apis"
                  :key="api.id"
                  type="button"
                  class="docs-api-card"
                  @click="selectApi(activeGroup.id, api.id)"
                >
                  <code class="docs-method" :data-method="api.method">{{ api.method }}</code>
                  <div>
                    <div class="docs-api-card-title">{{ api.summary }}</div>
                    <code class="docs-api-card-path">{{ api.path }}</code>
                  </div>
                </button>
              </div>
            </div>
          </template>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { API_DOC_GROUPS } from '../constants/apiKeyDocs'

const STORAGE_KEY = 'lightbot_docs_api_key'

const groups = API_DOC_GROUPS
const activeGroupId = ref(groups[0]?.id || 'overview')
const activeApiId = ref(null)
/** @type {import('vue').Ref<'spec'|'test'>} */
const contentTab = ref('spec')

const apiKey = ref(localStorage.getItem(STORAGE_KEY) || '')
const pathValues = reactive({})
const queryValues = reactive({})
/** @type {Record<string, File>} */
const fileValues = reactive({})
const fileInputKey = ref(0)
const bodyText = ref('')
const testing = ref(false)
const testResult = ref(null)
const testMeta = ref('')

const activeGroup = computed(() => groups.find((g) => g.id === activeGroupId.value) || null)
const activeApi = computed(() => {
  if (!activeGroup.value || activeGroup.value.type !== 'apis' || !activeApiId.value) return null
  return activeGroup.value.apis?.find((a) => a.id === activeApiId.value) || null
})

const pathParams = computed(() => (activeApi.value?.params || []).filter((p) => p.in === 'path'))
const queryParams = computed(() => (activeApi.value?.params || []).filter((p) => p.in === 'query'))
const fileParams = computed(() =>
  (activeApi.value?.params || []).filter((p) => p.type === 'file')
)
const needsBody = computed(() => {
  const api = activeApi.value
  if (!api) return false
  // multipart 用文件字段；sse 仍走 JSON 请求体，仅响应按事件流解析
  if (api.contentType === 'multipart') return false
  if (api.contentType === 'json' || api.contentType === 'sse') return true
  return (api.params || []).some((p) => p.in === 'body' && p.type !== 'file')
})

function selectGroup(groupId) {
  activeGroupId.value = groupId
  activeApiId.value = null
  contentTab.value = 'spec'
  clearTestOutput()
}

function selectApi(groupId, apiId) {
  activeGroupId.value = groupId
  activeApiId.value = apiId
  contentTab.value = 'spec'
  clearTestOutput()
}

function persistApiKey() {
  if (apiKey.value) localStorage.setItem(STORAGE_KEY, apiKey.value)
  else localStorage.removeItem(STORAGE_KEY)
}

function openHome() {
  window.open('/', '_blank')
}

function clearTestOutput() {
  testResult.value = null
  testMeta.value = ''
}

function resetTestForm() {
  const api = activeApi.value
  if (!api) return
  Object.keys(pathValues).forEach((k) => delete pathValues[k])
  Object.keys(queryValues).forEach((k) => delete queryValues[k])
  Object.keys(fileValues).forEach((k) => delete fileValues[k])
  fileInputKey.value += 1
  for (const p of pathParams.value) pathValues[p.name] = p.example || ''
  for (const p of queryParams.value) queryValues[p.name] = p.example || ''
  bodyText.value = api.bodyExample && api.bodyExample.startsWith('{') ? api.bodyExample : ''
  clearTestOutput()
}

function onFileChange(name, event) {
  const file = event?.target?.files?.[0]
  if (file) fileValues[name] = file
  else delete fileValues[name]
}

watch(activeApi, () => {
  resetTestForm()
}, { immediate: true })

function buildUrl(api) {
  let path = api.path
  for (const p of pathParams.value) {
    const val = String(pathValues[p.name] ?? '').trim()
    if (!val) throw new Error(`请填写路径参数：${p.name}`)
    path = path.replace(`{${p.name}}`, encodeURIComponent(val))
  }
  const qs = new URLSearchParams()
  for (const p of queryParams.value) {
    const val = String(queryValues[p.name] ?? '').trim()
    if (val) qs.set(p.name, val)
  }
  const q = qs.toString()
  return q ? `${path}?${q}` : path
}

async function runTest() {
  const api = activeApi.value
  if (!api) return
  const key = apiKey.value.trim()
  if (!key) {
    message.warning('请先填写 API Key')
    return
  }
  if (!key.startsWith('lbkey_')) {
    message.warning('API Key 格式应为 lbkey_ 开头')
    return
  }

  for (const p of queryParams.value) {
    if (p.required && !String(queryValues[p.name] ?? '').trim()) {
      message.warning(`请填写 Query 参数：${p.name}`)
      return
    }
  }

  let url
  try {
    url = buildUrl(api)
  } catch (e) {
    message.warning(e.message || '参数不完整')
    return
  }

  let body
  if (needsBody.value) {
    const raw = bodyText.value.trim()
    if (!raw) {
      message.warning('请填写 JSON Body')
      return
    }
    try {
      body = JSON.parse(raw)
    } catch {
      message.warning('Body 不是合法 JSON')
      return
    }
  }

  if (api.contentType === 'multipart') {
    for (const p of fileParams.value) {
      if (p.required !== false && !fileValues[p.name]) {
        message.warning(`请选择文件：${p.name}`)
        return
      }
    }
  }

  testing.value = true
  clearTestOutput()
  const started = performance.now()
  try {
    if (api.contentType === 'sse') {
      await runSseTest(url, api.method, body, key, started)
    } else if (api.contentType === 'multipart') {
      await runMultipartTest(url, api.method, key, started)
    } else {
      await runJsonTest(url, api.method, body, key, started)
    }
  } catch (e) {
    testMeta.value = `失败 · ${Math.round(performance.now() - started)}ms`
    testResult.value = e?.message || String(e)
  } finally {
    testing.value = false
  }
}

async function runJsonTest(url, method, body, key, started) {
  const res = await fetch(url, {
    method,
    headers: {
      Authorization: `Bearer ${key}`,
      ...(body != null ? { 'Content-Type': 'application/json' } : {}),
    },
    body: body != null ? JSON.stringify(body) : undefined,
  })
  const text = await res.text()
  let pretty = text
  try {
    pretty = JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    // keep raw
  }
  testMeta.value = `HTTP ${res.status} · ${Math.round(performance.now() - started)}ms`
  testResult.value = pretty || `（空响应体，HTTP ${res.status}）`
}

/** multipart/form-data：浏览器自动带 boundary，勿手动设 Content-Type */
async function runMultipartTest(url, method, key, started) {
  const form = new FormData()
  for (const p of fileParams.value) {
    const file = fileValues[p.name]
    if (file) form.append(p.name, file, file.name)
  }
  const res = await fetch(url, {
    method,
    headers: {
      Authorization: `Bearer ${key}`,
    },
    body: form,
  })
  const text = await res.text()
  let pretty = text
  try {
    pretty = text ? JSON.stringify(JSON.parse(text), null, 2) : ''
  } catch {
    // keep raw
  }
  testMeta.value = `HTTP ${res.status} · ${Math.round(performance.now() - started)}ms`
  testResult.value = pretty || text || `（空响应体，HTTP ${res.status}）`
}

async function runSseTest(url, method, body, key, started) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 45000)
  try {
    const res = await fetch(url, {
      method,
      headers: {
        Authorization: `Bearer ${key}`,
        // 同时接受 JSON，便于流式接口在启动前返回业务错误（Result）
        Accept: 'text/event-stream, application/json',
        ...(body != null ? { 'Content-Type': 'application/json' } : {}),
      },
      body: body != null ? JSON.stringify(body) : undefined,
      signal: controller.signal,
    })
    if (!res.ok || !res.body) {
      const text = await res.text()
      testMeta.value = `HTTP ${res.status} · ${Math.round(performance.now() - started)}ms`
      let pretty = text
      try {
        pretty = text ? JSON.stringify(JSON.parse(text), null, 2) : ''
      } catch {
        // SSE error 事件等非纯 JSON，保留原文
      }
      testResult.value = pretty || text || `（空响应体，HTTP ${res.status}）`
      return
    }
    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let acc = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      acc += decoder.decode(value, { stream: true })
      // 截断过长预览，避免页面卡顿
      if (acc.length > 12000) {
        acc = `${acc.slice(0, 12000)}\n…(已截断)`
        controller.abort()
        break
      }
    }
    testMeta.value = `SSE · HTTP ${res.status} · ${Math.round(performance.now() - started)}ms`
    testResult.value = acc || '(空响应)'
  } finally {
    clearTimeout(timer)
  }
}
</script>

<style scoped>
.docs-shell {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--color-canvas-soft, #f5f7fb);
  overflow: hidden;
}
.docs-topbar {
  flex-shrink: 0;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: var(--color-canvas);
  border-bottom: 1px solid var(--color-hairline);
}
.docs-brand {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-ink);
  cursor: pointer;
}
.docs-logo {
  width: 22px;
  height: 22px;
  object-fit: contain;
}
.docs-topbar-hint {
  font-size: 12px;
  color: var(--color-mute);
}
.docs-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
}
.docs-nav {
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--color-canvas);
  border-right: 1px solid var(--color-hairline);
}
.docs-nav-title {
  flex-shrink: 0;
  padding: 16px 16px 10px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-mute);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}
.docs-nav-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 10px 20px;
}
.docs-nav-group {
  margin-bottom: 4px;
}
.docs-nav-group-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  border: none;
  background: transparent;
  text-align: left;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 550;
  color: var(--color-ink);
  cursor: pointer;
}
.docs-nav-group-btn:hover {
  background: var(--color-canvas-soft-2, #f4f4f5);
}
.docs-nav-group-btn.active {
  background: rgba(0, 112, 243, 0.08);
  color: var(--color-link);
}
.docs-nav-count {
  font-size: 11px;
  color: var(--color-mute);
  background: var(--color-canvas-soft-2);
  border-radius: 999px;
  padding: 0 7px;
  line-height: 18px;
}
.docs-nav-apis {
  padding: 2px 0 8px 8px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.docs-nav-api-btn {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  border: none;
  background: transparent;
  text-align: left;
  padding: 7px 10px;
  border-radius: 6px;
  cursor: pointer;
  color: var(--color-body);
}
.docs-nav-api-btn:hover {
  background: var(--color-canvas-soft-2, #f4f4f5);
}
.docs-nav-api-btn.active {
  background: rgba(0, 112, 243, 0.1);
  color: var(--color-link);
}
.docs-nav-method {
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 700;
  min-width: 42px;
  text-align: center;
  padding: 1px 4px;
  border-radius: 3px;
  background: var(--color-canvas-soft-2);
}
.docs-nav-summary {
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.docs-content {
  min-height: 0;
  min-width: 0;
}
.docs-content-scroll {
  height: 100%;
  overflow-y: auto;
  padding: 16px 20px 24px;
}
.docs-panel {
  width: 100%;
  max-width: none;
  margin: 0;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 12px;
  padding: 20px 24px 28px;
}
.docs-panel h1 {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 650;
  color: var(--color-ink);
}
.docs-lead {
  margin: 0 0 20px;
  font-size: 14px;
  color: var(--color-mute);
  line-height: 1.6;
}
.docs-tabs {
  display: flex;
  gap: 0;
  margin: 0 0 20px;
  border-bottom: 1px solid var(--color-hairline);
}
.docs-tab {
  position: relative;
  border: none;
  background: transparent;
  padding: 10px 16px;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-mute);
  cursor: pointer;
}
.docs-tab:hover {
  color: var(--color-ink);
}
.docs-tab.active {
  color: var(--color-link);
}
.docs-tab.active::after {
  content: '';
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: -1px;
  height: 2px;
  background: var(--color-link);
  border-radius: 2px 2px 0 0;
}
.docs-tab-panel {
  min-height: 120px;
}
.docs-tab-panel .docs-section:first-child {
  margin-top: 0;
}
.docs-api-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.docs-method {
  font-size: 12px;
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 4px;
  background: var(--color-canvas-soft-2);
}
.docs-method[data-method='GET'],
.docs-nav-method[data-method='GET'] {
  color: #08979c;
  background: rgba(8, 151, 156, 0.12);
}
.docs-method[data-method='POST'],
.docs-nav-method[data-method='POST'] {
  color: #389e0d;
  background: rgba(56, 158, 13, 0.12);
}
.docs-method[data-method='PUT'],
.docs-nav-method[data-method='PUT'] {
  color: #d48806;
  background: rgba(212, 136, 6, 0.12);
}
.docs-method[data-method='DELETE'],
.docs-nav-method[data-method='DELETE'] {
  color: #cf1322;
  background: rgba(207, 19, 34, 0.12);
}
.docs-path {
  font-size: 14px;
  color: var(--color-ink);
  word-break: break-all;
}
.docs-section {
  margin-top: 28px;
}
.docs-section h2 {
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-ink);
}
.docs-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.docs-table th,
.docs-table td {
  border: 1px solid var(--color-hairline);
  padding: 8px 10px;
  text-align: left;
  vertical-align: top;
}
.docs-table th {
  background: var(--color-canvas-soft-2);
  color: var(--color-mute);
  font-weight: 550;
}
.docs-table code {
  font-size: 12px;
}
.docs-example {
  display: inline-block;
  max-width: 100%;
  word-break: break-all;
  white-space: pre-wrap;
  font-size: 12px;
  color: var(--color-ink);
  background: var(--color-canvas-soft-2);
  padding: 2px 6px;
  border-radius: 4px;
}
.docs-mute {
  color: var(--color-mute);
}
.docs-subblock {
  margin-top: 12px;
}
.docs-subtitle {
  font-size: 12px;
  color: var(--color-mute);
  margin-bottom: 6px;
}
.docs-code {
  margin: 0;
  padding: 12px 14px;
  background: var(--color-canvas-soft-2);
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.55;
  color: var(--color-ink);
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'SFMono-Regular', Consolas, monospace;
  max-height: 420px;
  overflow: auto;
}
.docs-code--result {
  margin-top: 12px;
  max-height: 480px;
}
.docs-empty {
  font-size: 13px;
  color: var(--color-mute);
  padding: 12px;
  background: var(--color-canvas-soft-2);
  border-radius: 8px;
}
.docs-guide :deep(h3) {
  margin: 20px 0 8px;
  font-size: 15px;
  color: var(--color-ink);
}
.docs-guide :deep(p),
.docs-guide :deep(li) {
  font-size: 13px;
  color: var(--color-body);
  line-height: 1.7;
}
.docs-guide :deep(pre) {
  margin: 8px 0 12px;
  padding: 12px 14px;
  background: var(--color-canvas-soft-2);
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  font-size: 12px;
  overflow: auto;
}
.docs-guide :deep(code) {
  font-size: 12px;
  background: var(--color-canvas-soft-2);
  padding: 1px 6px;
  border-radius: 4px;
}
.docs-api-cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.docs-api-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  width: 100%;
  text-align: left;
  border: 1px solid var(--color-hairline);
  background: var(--color-canvas);
  border-radius: 10px;
  padding: 14px 16px;
  cursor: pointer;
}
.docs-api-card:hover {
  border-color: var(--color-link);
}
.docs-api-card-title {
  font-size: 14px;
  font-weight: 550;
  color: var(--color-ink);
  margin-bottom: 4px;
}
.docs-api-card-path {
  font-size: 12px;
  color: var(--color-mute);
}
.docs-test {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.docs-test-row {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
}
.docs-test-row--block {
  align-items: start;
}
.docs-test-row label {
  font-size: 13px;
  color: var(--color-mute);
}
.docs-file-field {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.docs-file-input {
  font-size: 13px;
  max-width: 100%;
}
.docs-file-name {
  font-size: 12px;
  color: var(--color-ink);
  word-break: break-all;
}
.docs-test-body {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
}
.docs-test-actions {
  display: flex;
  gap: 8px;
  padding-left: 132px;
}
.docs-btn-primary,
.docs-btn-ghost {
  height: 34px;
  padding: 0 16px;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
}
.docs-btn-primary {
  border: none;
  background: var(--color-primary, #111);
  color: #fff;
}
.docs-btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.docs-btn-ghost {
  border: 1px solid var(--color-hairline);
  background: transparent;
  color: var(--color-ink);
}
.docs-test-meta {
  padding-left: 132px;
  font-size: 12px;
  color: var(--color-mute);
}
@media (max-width: 900px) {
  .docs-body {
    grid-template-columns: 1fr;
  }
  .docs-nav {
    max-height: 240px;
    border-right: none;
    border-bottom: 1px solid var(--color-hairline);
  }
  .docs-test-row {
    grid-template-columns: 1fr;
  }
  .docs-test-actions,
  .docs-test-meta {
    padding-left: 0;
  }
}
</style>
