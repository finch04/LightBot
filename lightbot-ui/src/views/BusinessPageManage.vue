<template>
  <div class="page">
    <div class="page-intro">
      业务页使用<strong>内嵌 HTML</strong>登记。宿主会静默拦截成功的业务请求并回传对话，页面无需编写对话框协议。
    </div>
    <a-spin :spinning="loading" style="min-height: 300px; display: block;">
      <div class="provider-grid">
        <EntityCard
          v-for="p in filteredList"
          :key="p.id || p.pageType"
          type="template"
          :name="p.displayName || p.pageType"
          @click="openDialog(p)"
        >
          <template #icon>
            <AppstoreOutlined />
            <span class="status-dot" :class="p.enabled === 1 ? 'status-active' : 'status-disabled'" />
          </template>
          <template #actions>
            <a-dropdown :trigger="['click']">
              <button class="btn-icon" @click.prevent><MoreOutlined /></button>
              <template #overlay>
                <a-menu>
                  <a-menu-item @click="toggleEnabled(p, p.enabled !== 1)">
                    <CheckCircleOutlined v-if="p.enabled === 1" style="color: #16a34a; margin-right: 6px" />
                    <CloseCircleOutlined v-else style="color: #a3a3a3; margin-right: 6px" />
                    {{ p.enabled === 1 ? '禁用' : '启用' }}
                  </a-menu-item>
                  <a-menu-item @click="openDialog(p)">
                    <EditOutlined style="margin-right: 6px" /> 编辑
                  </a-menu-item>
                  <a-menu-item @click="handleDelete(p)">
                    <span style="color: #dc2626">
                      <DeleteOutlined style="margin-right: 6px" /> 删除
                    </span>
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </template>
          <div class="card-body">
            <div class="card-tags">
              <span class="tag tag-pagetype">{{ p.pageType }}</span>
              <span class="tag" :class="rendererTagClass(p)">{{ rendererLabel(p) }}</span>
            </div>
            <span class="card-desc">{{ truncateText(p.description || (p.pageHtml ? '已配置内嵌 HTML' : '暂无描述'), 50) }}</span>
          </div>
        </EntityCard>

        <LbEmptyState
          v-if="filteredList.length === 0 && !loading"
          :icon="AppstoreOutlined"
          :title="searchText ? '没有匹配的业务页' : '还没有业务页，点击右上角注册'"
        />
      </div>
    </a-spin>

    <!-- 注册/编辑：直接进入全屏编辑（基本信息 + AI + HTML） -->
    <a-modal
      v-model:open="editorVisible"
      width="100vw"
      :centered="false"
      :mask-closable="false"
      destroy-on-close
      wrap-class-name="bp-html-fullscreen-modal"
      :confirm-loading="saving"
      :ok-button-props="{ disabled: aiBusy }"
      :cancel-button-props="{ disabled: aiBusy || saving }"
      @cancel="closeEditor"
    >
      <template #footer>
        <a-button :disabled="aiBusy || saving" @click="closeEditor">取消</a-button>
        <a-button
          :disabled="aiBusy || saving || !form.pageHtml?.trim()"
          @click="openTestPreview"
        >
          测试
        </a-button>
        <a-button
          type="primary"
          :loading="saving"
          :disabled="aiBusy"
          @click="handleSave"
        >
          确定
        </a-button>
      </template>
      <template #title>
        <span class="bp-editor-modal-title">
          {{ form.id ? '编辑业务页' : '注册业务页' }}
          <button
            type="button"
            class="bp-dev-help-btn"
            title="H5 编写技术要求"
            @click.stop="devGuideVisible = true"
          >
            <QuestionCircleOutlined />
          </button>
        </span>
      </template>
      <div class="bp-html-max">
        <div class="bp-html-max-main">
          <div class="bp-html-editor-wrap">
            <a-textarea
              v-model:value="form.pageHtml"
              class="html-editor html-editor--max"
              :auto-size="false"
              placeholder="完整 HTML 文档（含 CSS/JS 与业务接口调用）…"
            />
          </div>
        </div>

        <aside class="bp-side">
          <div class="bp-meta-panel">
            <div class="bp-meta-head">
              <div class="bp-ai-title">基本信息</div>
              <label class="bp-meta-enabled">
                <span>{{ form.enabled ? '启用' : '停用' }}</span>
                <a-switch v-model:checked="form.enabled" size="small" />
              </label>
            </div>
            <a-form layout="vertical" class="bp-meta-form" :label-col="{ flex: '0 0 auto' }">
              <a-form-item label="展示名称" required>
                <a-input
                  :value="form.displayName"
                  placeholder="如 水电燃气缴费"
                  @update:value="onDisplayNameChange"
                />
              </a-form-item>
              <a-form-item label="默认标题">
                <a-input v-model:value="form.defaultTitle" placeholder="对话卡片默认标题" />
              </a-form-item>
              <a-form-item>
                <template #label>
                  <span class="field-label">
                    描述
                    <a-tooltip
                      :trigger="['hover', 'click']"
                      placement="topLeft"
                      :overlay-style="{ maxWidth: '320px' }"
                      title="给建设者与 LLM 看的用途说明，会出现在工具可用列表的辅助信息中。"
                    >
                      <QuestionCircleOutlined class="field-help-icon" />
                    </a-tooltip>
                  </span>
                </template>
                <a-textarea
                  v-model:value="form.description"
                  :rows="2"
                  placeholder="给建设者 / LLM 的用途说明"
                />
              </a-form-item>
            </a-form>
          </div>

          <div class="bp-ai-panel">
            <div class="bp-ai-title">
              <ThunderboltOutlined />
              AI 辅助生成
            </div>
            <p class="bp-ai-desc">
              用自然语言描述业务页字段与流程，生成完整 HTML。样式按平台示例模板约束，避免与系统风格差异过大。
            </p>
            <a-textarea
              v-model:value="aiRequirement"
              class="bp-ai-input bp-ai-input--grow"
              :auto-size="false"
              placeholder="例如：请假申请页，字段含开始日期、结束日期、事由；提交调用业务接口，失败时在页内提示。"
            />
            <a-checkbox v-model:checked="aiBasedOnCurrent">基于当前代码修改</a-checkbox>
            <div class="bp-ai-actions">
              <a-button
                type="primary"
                class="bp-ai-action"
                :loading="aiGenerating"
                :disabled="aiBusy || !aiRequirement.trim()"
                @click="handleGenerateHtml"
              >
                <ThunderboltOutlined v-if="!aiGenerating" />
                {{ aiGenerating ? '生成中' : '生成' }}
              </a-button>
              <a-button
                class="bp-ai-action"
                :loading="aiNormalizing"
                :disabled="aiBusy || !form.pageHtml?.trim()"
                @click="handleNormalizeHtml"
              >
                <FormatPainterOutlined v-if="!aiNormalizing" />
                {{ aiNormalizing ? '对齐中' : '对齐样式' }}
              </a-button>
              <a-button class="bp-ai-action" :disabled="aiBusy" @click="resetHtmlTemplate">
                恢复模板
              </a-button>
            </div>
          </div>
        </aside>
      </div>
    </a-modal>

    <a-modal
      v-model:open="testPreviewVisible"
      title="H5 测试预览"
      width="520px"
      :footer="null"
      destroy-on-close
      wrap-class-name="bp-test-preview-modal"
      centered
    >
      <p class="bp-test-hint">
        本地预览当前编辑器中的 HTML。可用演示接口 <code>/__lightbot_bp_demo__</code> 验证提交；
        此处提交/取消<strong>不会</strong>写入真实对话。
      </p>
      <div class="bp-test-frame-wrap">
        <H5BusinessPageFrame :key="testPreviewKey" :payload="testPayload" />
      </div>
    </a-modal>

    <a-modal
      v-model:open="devGuideVisible"
      title="业务页 H5 编写指南"
      width="720px"
      :footer="null"
      destroy-on-close
      wrap-class-name="bp-dev-guide-modal"
    >
      <div class="bp-dev-guide">
        <section>
          <h4>一句话理解</h4>
          <p>
            你交付的是<strong>完整 HTML 页面</strong>（iframe 内嵌）。页面只负责表单与调用业务接口；
            <strong>成功请求会被宿主静默拦截并回传对话</strong>，无需自己对接对话框协议。
          </p>
        </section>

        <section>
          <h4>1. 页面结构（必须）</h4>
          <ul>
            <li>必须是完整文档：含 <code>&lt;!DOCTYPE html&gt;</code>、<code>&lt;html&gt;</code>、<code>&lt;head&gt;</code>、<code>&lt;body&gt;</code></li>
            <li>样式与脚本写在页面内即可；可自包含 CSS/JS，勿依赖外站未授权资源</li>
            <li>建议适配窄宽度（对话气泡内展示，约 320–480px）</li>
          </ul>
        </section>

        <section>
          <h4>2. 提交办理（主路径，推荐）</h4>
          <ol>
            <li>校验表单 → 失败时<strong>在页面内</strong>提示错误（不要依赖宿主）</li>
            <li>使用 <code>fetch</code> / <code>XMLHttpRequest</code> 调用你们的业务接口</li>
            <li>默认拦截方法：<code>POST</code> / <code>PUT</code> / <code>PATCH</code>，且 HTTP 成功时回传对话</li>
            <li>接口失败（4xx/5xx）：页面自行展示错误，宿主不会当作办结</li>
          </ol>
          <pre class="bp-dev-code">const res = await fetch('/your/api', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(formValues)
});
if (!res.ok) throw new Error(/* 页内提示 */);
// 成功即可：无需再调宿主 API</pre>
        </section>

        <section>
          <h4>3. 本地联调（无后端时）</h4>
          <p>
            将请求地址设为 <code>/__lightbot_bp_demo__</code>，宿主会返回模拟成功响应并完成回传，便于先跑通页面流程。
          </p>
        </section>

        <section>
          <h4>4. 取消办理</h4>
          <p>满足任一条件即可被识别为取消（点击后结束办理、回传取消）：</p>
          <ul>
            <li>按钮可见文案为「取消」或 <code>Cancel</code></li>
            <li><code>id</code> / <code>class</code> 含 <code>cancel</code>（不区分大小写）</li>
            <li>元素带属性 <code>data-lightbot-cancel</code></li>
            <li>或显式调用 <code>window.LightBot.cancel()</code></li>
          </ul>
        </section>

        <section>
          <h4>5. 办结摘要的字段名（重要）</h4>
          <p>
            回传对话时的中文标签来自页面 <strong>label 原文</strong>，平台<strong>不会</strong>根据 <code>name</code>/<code>id</code> 做词典翻译。
          </p>
          <ul>
            <li>请用 <code>&lt;label for="xxx"&gt;</code> 关联控件，或把控件包在 <code>&lt;label&gt;</code> 内</li>
            <li>也可用 <code>data-label</code> / <code>aria-label</code> 补充</li>
            <li>分栏布局时，保证 label 与控件在同一表单项容器内</li>
          </ul>
          <pre class="bp-dev-code">&lt;label for="phone"&gt;手机号
  &lt;input id="phone" name="phone" /&gt;
&lt;/label&gt;</pre>
        </section>

        <section>
          <h4>6. 预填数据（Agent 传入 props）与调用方身份</h4>
          <p>页面加载时可读取宿主注入的初始数据，两种方式等价，建议都兼容：</p>
          <ul>
            <li>同步：<code>window.__LIGHTBOT_BP_INIT__</code>（形如 <code>{ props, options, pageType, callerContext, identityHeaders }</code>）</li>
            <li>异步：监听 <code>message</code>，<code>source === 'lightbot-business-page'</code> 且 <code>type === 'init'</code>，数据在 <code>payload</code></li>
            <li><code>callerContext</code> 由平台从对话身份写入，与业务 <code>props</code> 分离；内嵌页出站请求默认自动加 <code>X-Zhiyuan-*</code> Header</li>
          </ul>
          <pre class="bp-dev-code">function applyInit(payload) {
  const p = (payload && payload.props) || {};
  if (p.phone != null) phoneInput.value = p.phone;
  if (payload?.options?.hint) hintEl.textContent = payload.options.hint;
  // 可选：const ctx = payload?.callerContext || LightBot.getCallerContext();
}
if (window.__LIGHTBOT_BP_INIT__) applyInit(window.__LIGHTBOT_BP_INIT__);
window.addEventListener('message', (e) => {
  const d = e.data;
  if (d?.source === 'lightbot-business-page' && d.type === 'init') {
    applyInit(d.payload);
  }
});</pre>
        </section>

        <section>
          <h4>7. 可选：显式 API 与捕获范围</h4>
          <ul>
            <li><code>window.LightBot.submit(values, extra?)</code>：主动办结（一般不必，成功 fetch 已足够）</li>
            <li><code>window.LightBot.cancel()</code>：主动取消</li>
            <li><code>window.LightBot.resize()</code>：通知宿主按内容高度调整（少用）</li>
            <li><code>window.LightBot.getCallerContext()</code> / <code>getIdentityHeaders()</code>：读取平台身份</li>
            <li>若同页有多个接口，可在登记配置的 options 中用 <code>captureUrlIncludes</code> / <code>captureUrlExcludes</code> 收窄自动捕获范围</li>
          </ul>
        </section>

        <section>
          <h4>8. 请勿这样做</h4>
          <ul>
            <li>不要只写 HTML 片段（缺 doctype/html），可能导致样式与桥接注入异常</li>
            <li>不要自己发明一套 postMessage 协议；需要时用上方官方 <code>LightBot.*</code></li>
            <li>不要假设平台会把 <code>name</code>/<code>phone</code> 自动翻译成中文摘要</li>
            <li>不要在成功后还弹「请回到对话框继续」之类引导——页面展示后由用户在页内操作即可</li>
          </ul>
        </section>

        <section>
          <h4>最小检查清单</h4>
          <ul class="bp-dev-checklist">
            <li>□ 完整 HTML 文档，窄屏可操作</li>
            <li>□ 提交走业务 API（或演示路径），失败页内提示</li>
            <li>□ 成功 POST/PUT/PATCH 后对话能自动收到办结</li>
            <li>□ 有可识别的「取消」</li>
            <li>□ 每个字段有可读 label，摘要不是裸英文 key</li>
            <li>□ 兼容 <code>__LIGHTBOT_BP_INIT__</code> / init message 预填</li>
          </ul>
        </section>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  AppstoreOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  FormatPainterOutlined,
  MoreOutlined,
  QuestionCircleOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons-vue'
import { Modal, message } from 'ant-design-vue'
import EntityCard from '../components/EntityCard.vue'
import LbEmptyState from '../components/common/LbEmptyState.vue'
import {
  deleteBusinessPage,
  generateBusinessPageHtml,
  listBusinessPages,
  normalizeBusinessPageHtml,
  setBusinessPageEnabled,
  upsertBusinessPage,
} from '../api/businessPage'
import { isRegisteredBusinessPage } from '../components/businessPages/businessPageRegistry'
import H5BusinessPageFrame from '../components/businessPages/H5BusinessPageFrame.vue'
import { DEFAULT_BUSINESS_PAGE_HTML } from '../utils/businessPageHtmlTemplate'
import { truncateText } from '../utils/format'
import { displayNameToPageType, isValidPageType } from '../utils/pageTypeSlug'

defineProps({
  hideHeader: { type: Boolean, default: false },
})

const loading = ref(false)
const saving = ref(false)
const editorVisible = ref(false)
const devGuideVisible = ref(false)
const testPreviewVisible = ref(false)
const testPreviewKey = ref(0)
const searchText = ref('')
const list = ref([])
const aiRequirement = ref('')
const aiBasedOnCurrent = ref(true)
const aiGenerating = ref(false)
const aiNormalizing = ref(false)
const aiBusy = computed(() => aiGenerating.value || aiNormalizing.value)

const form = reactive({
  id: null,
  pageType: '',
  displayName: '',
  defaultTitle: '',
  description: '',
  pageHtml: DEFAULT_BUSINESS_PAGE_HTML,
  enabled: true,
})

/** 预览用固定身份（平台默认透传，注册页不再暴露配置） */
const PREVIEW_CALLER_CONTEXT = {
  externalUserId: 'preview_user',
  regionId: '510100',
  profile: { name: '预览用户' },
}

const testPayload = computed(() => {
  const callerContext = { ...PREVIEW_CALLER_CONTEXT }
  const identityHeaders = {
    'X-Zhiyuan-External-User-Id': callerContext.externalUserId,
    'X-Zhiyuan-Region-Id': callerContext.regionId,
  }
  return {
    pageType: form.pageType || 'preview',
    displayName: form.displayName || '业务页预览',
    title: form.defaultTitle || form.displayName || '业务办理',
    pageHtml: form.pageHtml || '',
    props: {},
    options: {
      hint: '测试预览：提交或取消仅验证页面，不会写入对话',
      injectIdentityHeaders: true,
      exposeProfile: true,
    },
    callerContext,
    identityHeaders,
    actions: ['submit', 'cancel'],
    mode: 'inline',
  }
})

function openTestPreview() {
  if (!form.pageHtml?.trim()) {
    message.warning('请先填写页面 HTML')
    return
  }
  testPreviewKey.value += 1
  testPreviewVisible.value = true
}

/** 新建随展示名称生成；编辑沿用已有 pageType（表单不展示该字段） */
function resolvePageType() {
  if (form.id) return String(form.pageType || '').trim()
  return displayNameToPageType(form.displayName)
}

function onDisplayNameChange(val) {
  form.displayName = val
  // 新建时同步生成 pageType；编辑沿用已有值
  if (form.id) return
  form.pageType = displayNameToPageType(val)
}

const filteredList = computed(() => {
  const q = searchText.value.trim().toLowerCase()
  if (!q) return list.value
  return list.value.filter((p) =>
    [p.pageType, p.displayName, p.description]
      .filter(Boolean)
      .some((s) => String(s).toLowerCase().includes(q))
  )
})

function rendererLabel(record) {
  if (isRegisteredBusinessPage(record.pageType)) return '宿主已注入组件'
  if (record.pageHtml) return '内嵌 HTML'
  return '未配置页面'
}

function rendererTagClass(record) {
  if (isRegisteredBusinessPage(record.pageType)) return 'tag-host'
  if (record.pageHtml) return 'tag-h5'
  return 'tag-meta'
}

function resetHtmlTemplate() {
  form.pageHtml = DEFAULT_BUSINESS_PAGE_HTML
}

function seedAiRequirement() {
  if (aiRequirement.value.trim()) return
  const parts = [
    form.displayName && `页面「${form.displayName}」`,
    form.description,
    '请生成可用的业务办理表单页。',
  ].filter(Boolean)
  aiRequirement.value = parts.join('\n')
}

function closeEditor() {
  if (aiBusy.value || saving.value) return
  editorVisible.value = false
}

async function handleGenerateHtml() {
  const requirement = aiRequirement.value.trim()
  if (!requirement) {
    message.warning('请填写生成需求')
    return
  }
  aiGenerating.value = true
  try {
    const res = await generateBusinessPageHtml({
      requirement,
      pageType: resolvePageType() || undefined,
      displayName: form.displayName || undefined,
      description: form.description || undefined,
      basedOnCurrent: aiBasedOnCurrent.value,
      currentHtml: aiBasedOnCurrent.value ? (form.pageHtml || '') : undefined,
    })
    const html = res.data?.html
    if (!html) {
      message.error('未返回 HTML 内容')
      return
    }
    form.pageHtml = html
    message.success('已生成并填入编辑器')
  } catch (e) {
    message.error(e?.message || 'AI 生成失败')
  } finally {
    aiGenerating.value = false
  }
}

async function handleNormalizeHtml() {
  const currentHtml = form.pageHtml?.trim() || ''
  if (!currentHtml) {
    message.warning('请先填写页面 HTML')
    return
  }
  aiNormalizing.value = true
  try {
    const res = await normalizeBusinessPageHtml({
      currentHtml,
      pageType: resolvePageType() || undefined,
      displayName: form.displayName || undefined,
      description: form.description || undefined,
    })
    const html = res.data?.html
    if (!html) {
      message.error('未返回规范化结果')
      return
    }
    form.pageHtml = html
    message.success('已对齐平台样式')
  } catch (e) {
    message.error(e?.message || '对齐平台样式失败')
  } finally {
    aiNormalizing.value = false
  }
}

async function loadList() {
  loading.value = true
  try {
    const res = await listBusinessPages()
    list.value = res.data || []
  } finally {
    loading.value = false
  }
}

function openDialog(record) {
  if (record) {
    Object.assign(form, {
      id: record.id,
      pageType: record.pageType,
      displayName: record.displayName,
      defaultTitle: record.defaultTitle,
      description: record.description || '',
      pageHtml: record.pageHtml || DEFAULT_BUSINESS_PAGE_HTML,
      enabled: record.enabled === 1,
    })
  } else {
    Object.assign(form, {
      id: null,
      pageType: '',
      displayName: '',
      defaultTitle: '',
      description: '',
      pageHtml: DEFAULT_BUSINESS_PAGE_HTML,
      enabled: true,
    })
  }
  aiRequirement.value = ''
  aiBasedOnCurrent.value = true
  seedAiRequirement()
  editorVisible.value = true
}

async function handleSave() {
  if (!form.displayName?.trim()) {
    message.warning('展示名称不能为空')
    return Promise.reject()
  }
  const pageType = resolvePageType()
  if (!pageType || !isValidPageType(pageType)) {
    message.warning('无法从展示名称生成有效标识，请调整展示名称后重试')
    return Promise.reject()
  }
  form.pageType = pageType
  const pageHtml = form.pageHtml?.trim() || ''
  if (!pageHtml) {
    message.warning('请编写内嵌 HTML 页面内容')
    return Promise.reject()
  }
  saving.value = true
  try {
    await upsertBusinessPage({
      pageType,
      displayName: form.displayName.trim(),
      defaultTitle: form.defaultTitle || form.displayName,
      description: form.description,
      pageHtml,
      pageUrl: null,
      enabled: form.enabled,
      allowedModes: ['inline'],
      allowedActions: ['submit', 'cancel'],
      allowedPropKeys: [],
      allowedOptionKeys: ['primaryButtonText', 'cancelButtonText', 'hint'],
      defaultProps: {},
      // 身份透传由平台默认开启，注册页不再配置
      defaultOptions: {},
    })
    message.success('已保存')
    editorVisible.value = false
    await loadList()
  } catch (e) {
    message.error(e?.message || '保存失败')
    return Promise.reject()
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(record, enabled) {
  await setBusinessPageEnabled(record.id, enabled)
  record.enabled = enabled ? 1 : 0
  message.success(enabled ? '已启用' : '已禁用')
}

function handleDelete(record) {
  Modal.confirm({
    title: '删除业务页',
    content: `确认删除「${record.displayName || record.pageType}」？`,
    okType: 'danger',
    async onOk() {
      await deleteBusinessPage(record.id)
      message.success('已删除')
      await loadList()
    },
  })
}

function search(text) {
  searchText.value = text || ''
}

function refresh() {
  return loadList()
}

onMounted(loadList)

defineExpose({ openDialog, search, refresh, loading })
</script>

<style scoped>
.page {
  height: auto;
  overflow: visible;
  padding: 0;
  background: transparent;
}
.page-intro {
  margin-bottom: 16px;
  padding: 12px 14px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  font-size: 13px;
  color: #475569;
  line-height: 1.6;
}
.page-intro code {
  font-size: 12px;
  padding: 1px 5px;
  border-radius: 4px;
  background: #e2e8f0;
}
.provider-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}
.card-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 4px;
}
.card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.tag {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11px;
  line-height: 18px;
  background: #f4f4f5;
  color: #52525b;
}
.tag-pagetype {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  background: #f1f5f9;
  color: #334155;
}
.tag-host { background: #ecfdf5; color: #047857; }
.tag-h5 { background: #fdf4ff; color: #a21caf; }
.tag-meta { background: #fff7ed; color: #c2410c; }
.card-desc {
  font-size: 13px;
  color: var(--color-mute, #737373);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.status-dot {
  position: absolute;
  right: -2px;
  bottom: -2px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  border: 1.5px solid #fff;
}
.status-active { background: #22c55e; }
.status-disabled { background: #a3a3a3; }
.btn-icon {
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  color: #737373;
  display: inline-flex;
  align-items: center;
}
.btn-icon:hover { background: #f4f4f5; color: #171717; }
.btn-icon.danger:hover { background: #fef2f2; color: #dc2626; }
.field-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.field-help-icon {
  font-size: 13px;
  color: var(--color-mute, #a3a3a3);
  cursor: help;
}
.field-help-icon:hover {
  color: var(--color-body, #52525b);
}
.bp-editor-modal-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.bp-dev-help-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--color-mute, #a3a3a3);
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
}
.bp-dev-help-btn:hover {
  color: var(--color-link, #2563eb);
  background: #f4f4f5;
}
.bp-dev-guide {
  max-height: min(70vh, 640px);
  overflow-y: auto;
  padding-right: 4px;
  font-size: 13px;
  line-height: 1.6;
  color: #3f3f46;
}
.bp-dev-guide section + section {
  margin-top: 18px;
}
.bp-dev-guide h4 {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: #171717;
}
.bp-dev-guide p,
.bp-dev-guide ul,
.bp-dev-guide ol {
  margin: 0 0 8px;
}
.bp-dev-guide ul,
.bp-dev-guide ol {
  padding-left: 1.25em;
}
.bp-dev-guide li + li {
  margin-top: 4px;
}
.bp-dev-guide code {
  font-size: 12px;
  padding: 1px 5px;
  border-radius: 4px;
  background: #f4f4f5;
  color: #18181b;
}
.bp-dev-code {
  margin: 8px 0 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: #18181b;
  color: #e4e4e7;
  font-size: 12px;
  line-height: 1.5;
  overflow-x: auto;
  white-space: pre;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}
.bp-dev-checklist {
  list-style: none;
  padding-left: 0 !important;
}
.bp-dev-checklist li {
  padding: 4px 0;
}
.bp-test-hint {
  margin: 0 0 12px;
  font-size: 12px;
  line-height: 1.55;
  color: #737373;
}
.bp-test-hint code {
  font-size: 11px;
  padding: 1px 5px;
  border-radius: 4px;
  background: #f4f4f5;
  color: #18181b;
}
.bp-test-frame-wrap {
  display: flex;
  justify-content: center;
}
.bp-test-frame-wrap :deep(.h5-frame) {
  max-width: 100%;
}
.html-editor {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.45;
}
.bp-html-max {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 16px;
  flex: 1;
  min-height: 0;
  height: 100%;
}
.bp-side {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
  height: 100%;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding-right: 2px;
}
.bp-meta-panel {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fafafa;
}
.bp-ai-panel {
  flex: 1 0 auto;
  min-height: 280px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fafafa;
}
.bp-meta-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-shrink: 0;
}
.bp-meta-form :deep(.ant-form-item) {
  margin-bottom: 10px;
}
.bp-meta-form :deep(.ant-form-item:last-child) {
  margin-bottom: 0;
}
.bp-meta-form :deep(.ant-form-item-label) {
  padding-bottom: 2px;
}
.bp-meta-enabled {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  cursor: pointer;
  user-select: none;
  font-size: 13px;
  color: #52525b;
  white-space: nowrap;
}
.bp-ai-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 14px;
  color: #171717;
  flex-shrink: 0;
}
.bp-ai-desc {
  margin: 0;
  font-size: 12px;
  color: #737373;
  line-height: 1.5;
  flex-shrink: 0;
}
.bp-ai-input {
  font-size: 13px;
}
.bp-ai-input--grow {
  flex: 1;
  min-height: 120px;
  resize: none;
}
.bp-ai-panel :deep(textarea.bp-ai-input--grow) {
  height: 100% !important;
  min-height: 120px !important;
}
.bp-ai-actions {
  display: flex;
  align-items: stretch;
  gap: 8px;
  margin-top: 4px;
}
.bp-ai-action {
  flex: 1;
  min-width: 0;
  padding-inline: 6px;
  font-size: 13px;
}
.bp-html-max-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
  height: 100%;
}
.bp-html-editor-wrap {
  flex: 1;
  min-height: 0;
  display: flex;
}
.bp-html-editor-wrap :deep(textarea.html-editor--max),
.bp-html-editor-wrap :deep(textarea.ant-input) {
  flex: 1;
  width: 100%;
  height: 100% !important;
  min-height: 0 !important;
  max-height: none !important;
  resize: none;
}
</style>

<style>
/*
 * 豁免全局 modal-scroll.css：
 * .ant-modal-body { max-height: calc(100vh - 260px) } 会把全屏编辑器压矮，底部留下大块空白。
 */
.bp-html-fullscreen-modal {
  overflow: hidden;
}
.bp-html-fullscreen-modal .ant-modal {
  top: 0 !important;
  max-width: 100vw !important;
  width: 100vw !important;
  margin: 0 !important;
  padding: 0 !important;
  height: 100vh !important;
}
.bp-html-fullscreen-modal .ant-modal-content {
  display: flex;
  flex-direction: column;
  height: 100vh;
  max-height: 100vh;
  border-radius: 0;
  box-shadow: none;
}
.bp-html-fullscreen-modal .ant-modal-header {
  flex-shrink: 0;
  margin-bottom: 0;
  padding: 12px 20px;
  border-bottom: 1px solid #f0f0f0;
}
.bp-html-fullscreen-modal .ant-modal-body {
  flex: 1 1 auto !important;
  min-height: 0 !important;
  max-height: none !important;
  height: auto !important;
  overflow: hidden !important;
  padding: 12px 20px;
  display: flex;
  flex-direction: column;
}
.bp-html-fullscreen-modal .ant-modal-footer {
  flex-shrink: 0;
  margin-top: 0;
  padding: 10px 20px;
  border-top: 1px solid #f0f0f0;
}
@media (max-width: 960px) {
  .bp-html-max {
    grid-template-columns: 1fr !important;
    overflow-y: auto !important;
    height: auto !important;
  }
  .bp-html-fullscreen-modal .ant-modal-body {
    overflow-y: auto !important;
  }
  .bp-html-editor-wrap {
    min-height: 50vh;
  }
  .bp-ai-panel {
    flex: none;
  }
  .bp-ai-input--grow {
    min-height: 140px;
  }
}
</style>
