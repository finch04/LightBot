<template>
  <!-- 知识库工具按钮（支持自定义触发器） -->
  <slot name="trigger">
    <a-tooltip
      title="查看知识库工具"
      :getPopupContainer="getPopupContainer"
      :placement="placement"
      :mouseEnterDelay="0.3"
    >
      <button class="btn-system-tool" @click="drawerVisible = true">
        <SettingOutlined /> 知识库工具
      </button>
    </a-tooltip>
  </slot>

  <!-- 知识库工具抽屉 -->
  <a-drawer
    v-model:open="drawerVisible"
    title="知识库工具"
    :width="480"
    :bodyStyle="{ padding: '16px' }"
  >
    <div class="system-tool-header">
      <span class="system-tool-desc">知识库工具在 Agent 绑定知识库后自动注入，无需手动绑定。</span>
      <a-tooltip title="如何新增知识库工具？" :getPopupContainer="getPopupContainer" placement="bottomRight">
        <button class="btn-help" @click="helpVisible = true"><QuestionCircleOutlined /></button>
      </a-tooltip>
    </div>
    <a-spin :spinning="loading">
      <div v-if="list.length === 0 && !loading" class="empty-tip">
        暂无知识库工具
      </div>
      <div v-for="t in list" :key="t.id" class="system-tool-item">
        <div class="tool-card-icon">
          <span class="type-badge badge-knowledge">知识库</span>
          {{ (t.displayName || t.name || '?')[0].toUpperCase() }}
        </div>
        <div class="tool-card-info" @click="openDetail(t)">
          <div class="tool-card-name">{{ t.displayName || t.name }}</div>
          <div class="tool-card-desc">{{ t.description || '暂无描述' }}</div>
        </div>
        <div class="tool-card-actions">
          <a-tooltip title="查看详情">
            <button class="btn-icon" @click="openDetail(t)"><EyeOutlined /></button>
          </a-tooltip>
          <a-tooltip title="测试工具" :getPopupContainer="getPopupContainer">
            <button class="btn-icon" @click="openTestDialog(t)"><PlayCircleOutlined /></button>
          </a-tooltip>
        </div>
      </div>
    </a-spin>
  </a-drawer>

  <!-- 知识库工具详情弹窗 -->
  <a-modal
    v-model:open="detailVisible"
    :title="currentTool?.displayName || currentTool?.name || '知识库工具详情'"
    :width="640"
    :footer="null"
  >
    <div class="tool-detail-modal">
      <div class="raw-toggle-bar">
        <button class="btn-text raw-toggle" @click="rawMode = !rawMode">
          <SwapOutlined /> {{ rawMode ? '格式化' : '原始格式' }}
        </button>
      </div>
      <template v-if="!rawMode">
        <div class="detail-section">
          <div class="detail-label">工具标识</div>
          <div class="detail-value">{{ currentTool?.name }}</div>
        </div>
        <div class="detail-section">
          <div class="detail-label">工具描述</div>
          <div class="detail-value">{{ currentTool?.description || '暂无描述' }}</div>
        </div>
        <div class="detail-section">
          <div class="detail-label">参数定义</div>
          <div v-if="parsedSchema.length > 0" class="schema-table-wrap">
            <table class="schema-table">
              <thead>
                <tr>
                  <th>参数名</th>
                  <th>类型</th>
                  <th>描述</th>
                  <th>必填</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="prop in parsedSchema" :key="prop.name">
                  <td class="prop-name">{{ prop.name }}</td>
                  <td class="prop-type">{{ prop.type }}</td>
                  <td class="prop-desc">{{ prop.description || '-' }}</td>
                  <td class="prop-required">{{ prop.required ? '是' : '否' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-else class="schema-empty">无参数（可直接调用）</div>
        </div>
        <div v-if="parsedSchema.length > 0" class="detail-section">
          <div class="detail-label">参数示例</div>
          <pre class="example-json">{{ exampleJson }}</pre>
        </div>
        <div v-if="hasOutputExample" class="detail-section">
          <div class="detail-label">返回示例</div>
          <table v-if="outputSchemaFields.length > 0" class="schema-table output-fields-table">
            <thead>
              <tr><th>字段名</th><th>类型</th><th>说明</th></tr>
            </thead>
            <tbody>
              <tr v-for="f in outputSchemaFields" :key="f.name">
                <td class="prop-name">{{ f.name }}</td>
                <td class="prop-type">{{ f.type }}</td>
                <td class="prop-desc">{{ f.desc }}</td>
              </tr>
            </tbody>
          </table>
          <div v-if="formattedOutputExample" class="output-example-title">示例 JSON</div>
          <pre v-if="formattedOutputExample" class="example-json output-example-json">{{ formattedOutputExample }}</pre>
        </div>
      </template>
      <template v-else>
        <div class="detail-section">
          <div class="detail-label"><CodeOutlined /> inputSchema</div>
          <pre class="example-json">{{ formatJsonRaw(currentTool?.inputSchema) }}</pre>
        </div>
        <div class="detail-section">
          <div class="detail-label"><CodeOutlined /> outputSchema</div>
          <pre class="example-json">{{ formatJsonRaw(currentTool?.outputSchema) }}</pre>
        </div>
        <div class="detail-section">
          <div class="detail-label"><CodeOutlined /> outputExample</div>
          <pre class="example-json">{{ formatJsonRaw(currentTool?.outputExample) }}</pre>
        </div>
        <div class="detail-section">
          <div class="detail-label"><CodeOutlined /> config</div>
          <pre class="example-json">{{ formatJsonRaw(currentTool?.config) }}</pre>
        </div>
      </template>
    </div>
  </a-modal>

  <!-- 测试工具弹窗 -->
  <a-modal
    v-model:open="testDialogVisible"
    :title="testTool?.displayName || testTool?.name || '测试工具'"
    :width="680"
    :footer="null"
    :maskClosable="false"
  >
    <!-- 参数说明 -->
    <div v-if="testToolParams.length > 0" class="test-params-section">
      <div class="test-params-title">参数说明</div>
      <table class="test-params-table">
        <thead>
          <tr><th>参数名</th><th>类型</th><th>必填</th><th>说明</th></tr>
        </thead>
        <tbody>
          <tr v-for="p in testToolParams" :key="p.name">
            <td><code>{{ p.name }}</code></td>
            <td>{{ p.type }}</td>
            <td><span v-if="p.required" class="param-required">是</span><span v-else class="param-optional">否</span></td>
            <td>{{ p.desc }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-else class="test-params-section">
      <div class="test-params-hint">该工具无需输入参数，直接点击执行即可</div>
    </div>
    <!-- JSON 输入 -->
    <div class="test-input-header">
      <span class="test-input-label">输入参数（JSON）</span>
      <button class="btn-text" @click="formatTestArgs">格式化</button>
    </div>
    <textarea
      v-model="testArgs"
      class="test-json-input"
      rows="6"
      spellcheck="false"
      placeholder='{"key": "value"}'
    />
    <div class="dialog-footer">
      <div></div>
      <div class="dialog-footer-right">
        <button class="btn-cancel" @click="testDialogVisible = false">关闭</button>
        <button class="btn-primary-sm" :disabled="testLoading" @click="handleTest">
          {{ testLoading ? '执行中...' : '执行测试' }}
        </button>
      </div>
    </div>
    <a-divider v-if="testResult !== null" />
    <div v-if="testResult !== null" class="test-result">
      <div class="test-result-label">执行结果</div>
      <pre class="test-result-content" :class="{ 'is-json': isJsonResult(testResult) }">{{ formatTestResult(testResult) }}</pre>
    </div>
  </a-modal>

  <!-- 知识库工具帮助弹窗 -->
  <a-modal
    v-model:open="helpVisible"
    title="如何新增知识库工具/内置工具"
    :width="680"
    :footer="null"
  >
    <div class="help-content">
      <div class="help-section">
        <h4>什么是知识库工具？</h4>
        <p>知识库工具是核心内置工具，自动注入到所有 Agent，用户无需在前端绑定。例如：<code>query_knowledge</code>（知识库检索工具）。</p>
        <p>普通内置工具需要用户在 Agent 配置中手动绑定，如计算器、联网搜索、数据库查询等。</p>
      </div>
      <div class="help-section">
        <h4>如何新增工具？</h4>
        <p>工具通过 Java 代码定义，应用启动时自动注册到数据库。步骤如下：</p>
        <div class="help-steps">
          <div class="help-step">
            <span class="step-num">1</span>
            <div>
              <b>创建工具类</b>
              <p>在类上添加 <code>@SystemTool</code> 注解（类级别配置）：</p>
              <pre class="code-block">@SystemTool(displayName = "工具名称", autoInject = false)
@Component
public class YourTool { ... }</pre>
            </div>
          </div>
          <div class="help-step">
            <span class="step-num">2</span>
            <div>
              <b>编写工具方法</b>
              <p>在方法上添加 <code>@Tool</code> 和可选的 <code>@SystemTool</code>（方法级别可覆盖 displayName）：</p>
              <pre class="code-block">@SystemTool(displayName = "方法级名称")  // 可选，覆盖类级别
@Tool(description = "工具描述")
public String yourMethod(
    @ToolParam(description = "参数描述")
    @ToolParamMeta(example = "示例值")  // 测试时的参数示例
    String param) {
    // 工具实现
}</pre>
            </div>
          </div>
          <div class="help-step">
            <span class="step-num">3</span>
            <div>
              <b>多工具类示例</b>
              <p>一个类可包含多个工具方法，每个方法单独设置 displayName：</p>
              <pre class="code-block">@SystemTool(displayName = "数据库工具集")
@Component
public class PgSqlTool {

    @SystemTool(displayName = "列出数据库表")
    @Tool(name = "pg_list_tables", ...)
    public String listTables() { ... }

    @SystemTool(displayName = "查看表结构")
    @Tool(name = "pg_describe_table", ...)
    public String describeTable(...) { ... }
}</pre>
            </div>
          </div>
        </div>
      </div>
      <div class="help-section">
        <h4>注解说明</h4>
        <table class="help-table">
          <thead>
            <tr><th>注解</th><th>位置</th><th>作用</th></tr>
          </thead>
          <tbody>
            <tr><td><code>@SystemTool</code></td><td>类/方法</td><td>标记为内置工具，设置 displayName、autoInject</td></tr>
            <tr><td><code>@Tool</code></td><td>方法</td><td>定义工具名称和描述（Spring AI 注解）</td></tr>
            <tr><td><code>@ToolParam</code></td><td>参数</td><td>定义参数描述（Spring AI 注解）</td></tr>
            <tr><td><code>@ToolParamMeta</code></td><td>参数</td><td>设置示例值、是否必填</td></tr>
          </tbody>
        </table>
      </div>
      <div class="help-section">
        <h4>知识库工具 vs 内置工具</h4>
        <table class="help-table">
          <thead>
            <tr><th>类型</th><th>autoInject</th><th>绑定方式</th><th>示例</th></tr>
          </thead>
          <tbody>
            <tr><td>知识库工具</td><td>true</td><td>自动注入所有Agent</td><td>知识库检索</td></tr>
            <tr><td>内置工具</td><td>false（默认）</td><td>用户手动绑定</td><td>计算器、联网搜索</td></tr>
          </tbody>
        </table>
      </div>
      <div class="help-section">
        <h4>displayName 优先级</h4>
        <ul class="help-list">
          <li>方法级别 <code>@SystemTool.displayName</code> 优先</li>
          <li>若方法无注解，使用类级别 <code>@SystemTool.displayName</code></li>
        </ul>
      </div>
      <div class="help-section">
        <h4>知识库工具特性</h4>
        <ul class="help-list">
          <li>自动注入：运行时自动添加到所有 Agent</li>
          <li>不可编辑：前端无法修改配置</li>
          <li>不可删除：前端无法删除</li>
        </ul>
      </div>
    </div>
  </a-modal>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { SettingOutlined, QuestionCircleOutlined, PlayCircleOutlined, EyeOutlined, SwapOutlined, CodeOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { getTools, testTool as testToolApi, getToolExampleParams } from '../api/tool'

const props = defineProps({
  placement: { type: String, default: 'top' }
})

defineExpose({ open: () => { drawerVisible.value = true } })

const drawerVisible = ref(false)
const loading = ref(false)
const list = ref([])
const detailVisible = ref(false)
const currentTool = ref(null)
const currentExampleParams = ref({}) // 详情弹窗的示例参数
const helpVisible = ref(false)
const rawMode = ref(false)

// 测试工具相关
const testDialogVisible = ref(false)
const testTool = ref(null)
const testArgs = ref('{}')
const testResult = ref(null)
const testLoading = ref(false)
const testExampleParams = ref({}) // 从后端获取的示例参数

function getPopupContainer() {
  return document.body
}

const parsedSchema = computed(() => {
  if (!currentTool.value?.inputSchema) {
    return []
  }
  try {
    const schema = typeof currentTool.value.inputSchema === 'string'
      ? JSON.parse(currentTool.value.inputSchema)
      : currentTool.value.inputSchema
    const properties = schema?.properties || {}
    const required = schema?.required || []
    return Object.entries(properties).map(([name, prop]) => ({
      name,
      type: prop.type || 'string',
      description: prop.description || '',
      required: required.includes(name)
    }))
  } catch {
    return []
  }
})

const exampleJson = computed(() => {
  // 优先使用从后端获取的示例参数
  if (currentExampleParams.value && Object.keys(currentExampleParams.value).length > 0) {
    return JSON.stringify(currentExampleParams.value, null, 2)
  }
  // 备用方案：从 parsedSchema 生成
  if (parsedSchema.value.length === 0) {
    return '{}'
  }
  const example = {}
  for (const prop of parsedSchema.value) {
    if (prop.type === 'string') example[prop.name] = prop.description || '示例值'
    else if (prop.type === 'number' || prop.type === 'integer') example[prop.name] = 0
    else if (prop.type === 'boolean') example[prop.name] = true
    else example[prop.name] = null
  }
  return JSON.stringify(example, null, 2)
})

function parseToolConfig(tool) {
  if (!tool?.config) return {}
  try {
    return typeof tool.config === 'string' ? JSON.parse(tool.config) : tool.config
  } catch {
    return {}
  }
}

const hasOutputExample = computed(() => {
  return (currentTool.value?.outputExample && currentTool.value.outputExample !== '{}') || (currentTool.value?.outputSchema && currentTool.value.outputSchema !== '{}')
})

const outputSchemaFields = computed(() => {
  if (!currentTool.value?.outputSchema || currentTool.value.outputSchema === '{}') return []
  try {
    const schema = typeof currentTool.value.outputSchema === 'string'
      ? JSON.parse(currentTool.value.outputSchema) : currentTool.value.outputSchema
    const properties = schema.properties || {}
    return Object.entries(properties).map(([name, prop]) => ({
      name,
      type: prop.type || 'string',
      desc: prop.description || '',
    }))
  } catch {
    return []
  }
})

const formattedOutputExample = computed(() => {
  const raw = currentTool.value?.outputExample
  if (!raw || raw === '{}') return ''
  try {
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
    return JSON.stringify(parsed, null, 2)
  } catch {
    return raw
  }
})

function isJsonResult(result) {
  if (!result || typeof result !== 'string') return false
  try {
    const parsed = JSON.parse(result)
    return typeof parsed === 'object' && parsed !== null
  } catch {
    return false
  }
}

function formatTestResult(result) {
  if (!result || typeof result !== 'string') return result
  try {
    const parsed = JSON.parse(result)
    if (typeof parsed === 'object' && parsed !== null) {
      return JSON.stringify(parsed, null, 2)
    }
  } catch {}
  return result
}

function formatJsonRaw(val) {
  if (!val || val === '{}') return '{}'
  try {
    const parsed = typeof val === 'string' ? JSON.parse(val) : val
    return JSON.stringify(parsed, null, 2)
  } catch {
    return val
  }
}

const testToolParams = computed(() => {
  if (!testTool.value?.inputSchema) {
    return []
  }
  try {
    const schema = typeof testTool.value.inputSchema === 'string'
      ? JSON.parse(testTool.value.inputSchema)
      : testTool.value.inputSchema
    const properties = schema?.properties || {}
    const required = schema?.required || []
    return Object.entries(properties).map(([name, prop]) => ({
      name,
      type: prop.type || 'string',
      desc: prop.description || '',
      required: required.includes(name)
    }))
  } catch {
    return []
  }
})

async function loadSystemTools() {
  loading.value = true
  try {
    const res = await getTools({ pageNum: 1, pageSize: 100, toolType: 'knowledge' })
    list.value = res.data?.records || []
  } catch (e) {
    console.error('[SystemToolDrawer] 加载知识库工具失败:', e)
  } finally {
    loading.value = false
  }
}

async function openDetail(tool) {
  currentTool.value = tool
  currentExampleParams.value = {}
  try {
    const res = await getToolExampleParams(tool.id)
    currentExampleParams.value = res.data || {}
  } catch (e) {
    console.error('[SystemToolDrawer] 获取详情示例参数失败:', e)
  }
  detailVisible.value = true
}

async function openTestDialog(tool) {
  testTool.value = tool
  testResult.value = null
  testArgs.value = '{}'
  testLoading.value = true

  // 从后端获取示例参数
  try {
    const res = await getToolExampleParams(tool.id)
    const exampleParams = res.data || {}
    testArgs.value = JSON.stringify(exampleParams, null, 2)
    testExampleParams.value = exampleParams
  } catch (e) {
    console.error('[SystemToolDrawer] 获取示例参数失败:', e)
    // 失败时使用备用方案：从 inputSchema 生成
    const schema = tool.inputSchema
    if (schema && schema !== '{}') {
      try {
        const parsed = typeof schema === 'string' ? JSON.parse(schema) : schema
        const properties = parsed?.properties || {}
        const example = {}
        for (const [name, prop] of Object.entries(properties)) {
          if (prop.type === 'string') example[name] = prop.description || '示例值'
          else if (prop.type === 'number' || prop.type === 'integer') example[name] = 0
          else if (prop.type === 'boolean') example[name] = true
          else example[name] = null
        }
        testArgs.value = JSON.stringify(example, null, 2)
      } catch {
        testArgs.value = '{}'
      }
    }
  } finally {
    testLoading.value = false
  }

  testDialogVisible.value = true
}

function formatTestArgs() {
  try {
    const obj = JSON.parse(testArgs.value)
    testArgs.value = JSON.stringify(obj, null, 2)
  } catch {
    message.warning('JSON 格式错误，无法格式化')
  }
}

async function handleTest() {
  if (!testArgs.value.trim()) {
    message.warning('请输入参数')
    return
  }
  try {
    JSON.parse(testArgs.value)
  } catch {
    message.warning('参数必须是合法的 JSON 格式')
    return
  }
  testLoading.value = true
  testResult.value = null
  try {
    const res = await testToolApi(testTool.value.id, testArgs.value)
    testResult.value = res.data
  } catch (e) {
    testResult.value = '请求失败: ' + (e.response?.data?.message || e.message || '未知错误')
  } finally {
    testLoading.value = false
  }
}

// 监听抽屉打开
watch(drawerVisible, (visible) => {
  if (visible) {
    loadSystemTools()
  }
})
</script>

<style scoped>
.btn-system-tool {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  background: #fff;
  border: 1px solid #e4e4e7;
  border-radius: 6px;
  font-size: 13px;
  color: #71717a;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}
.btn-system-tool:hover {
  border-color: #0070f3;
  color: #0070f3;
}
.system-tool-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.system-tool-desc {
  font-size: 13px;
  color: #71717a;
}
.btn-help {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: 1px solid #e4e4e7;
  border-radius: 4px;
  background: #fff;
  color: #a1a1aa;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-help:hover {
  border-color: #0070f3;
  color: #0070f3;
}
.system-tool-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: #fff;
  border: 1px solid #ebebeb;
  border-radius: 12px;
  transition: all 0.15s;
  margin-bottom: 12px;
}
.system-tool-item:hover {
  border-color: #7c3aed;
  box-shadow: 0 2px 8px rgba(124, 58, 237, 0.08);
}
.tool-card-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: linear-gradient(135deg, #10b981, #059669);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 16px;
  flex-shrink: 0;
  position: relative;
}
.type-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 4px;
  z-index: 1;
  color: #fff;
}
.badge-knowledge {
  background: #7c3aed;
}
.tool-card-info {
  flex: 1;
  min-width: 0;
  cursor: pointer;
}
.tool-card-name {
  font-size: 14px;
  font-weight: 600;
  color: #171717;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tool-card-desc {
  font-size: 12px;
  color: #a1a1aa;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tool-card-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}
.btn-icon {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #71717a;
}
.btn-icon:hover {
  background: #f5f5f5;
}
.empty-tip {
  text-align: center;
  padding: 24px;
  color: #a1a1aa;
  font-size: 13px;
}

/* 详情弹窗样式 */
.tool-detail-modal {
  max-height: 70vh;
  overflow-y: auto;
  padding-right: 8px;
}
.tool-detail-modal .detail-section {
  margin-bottom: 20px;
}
.tool-detail-modal .detail-label {
  font-size: 13px;
  color: #71717a;
  margin-bottom: 8px;
}
.tool-detail-modal .detail-value {
  font-size: 14px;
  color: #171717;
}
.tool-detail-modal .schema-table-wrap {
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  overflow: hidden;
}
.tool-detail-modal .schema-table {
  width: 100%;
  border-collapse: collapse;
}
.tool-detail-modal .schema-table th,
.tool-detail-modal .schema-table td {
  padding: 12px;
  border-bottom: 1px solid #f0f0f0;
  font-size: 13px;
}
.tool-detail-modal .schema-table th {
  background: #fafafa;
  font-weight: 500;
  color: #374151;
  white-space: nowrap;
}
.tool-detail-modal .schema-table td {
  word-break: break-word;
  overflow-wrap: break-word;
}
.tool-detail-modal .schema-table tr:last-child td {
  border-bottom: none;
}
.tool-detail-modal .prop-name {
  color: #0070f3;
  font-weight: 500;
}
.tool-detail-modal .prop-type {
  color: #71717a;
}
.tool-detail-modal .prop-desc {
  color: #52525b;
}
.tool-detail-modal .prop-required {
  color: #71717a;
}
.tool-detail-modal .schema-empty {
  text-align: center;
  padding: 16px;
  color: #a1a1aa;
  font-size: 13px;
  background: #fafafa;
  border-radius: 8px;
}
.example-json {
  background: #f5f5f5;
  border: 1px solid #e4e4e7;
  border-radius: 8px;
  padding: 12px 16px;
  font-size: 13px;
  color: #171717;
  overflow-x: auto;
  margin: 0;
  font-family: 'SF Mono', Monaco, Consolas, monospace;
}
.output-fields-table {
  margin-bottom: 12px;
}
.output-example-title {
  font-size: 12px;
  font-weight: 600;
  color: #71717a;
  margin: 12px 0 6px;
}
.output-example-json {
  background: #1e1e1e;
  color: #d4d4d4;
  border-color: #333;
  max-height: 360px;
  overflow-y: auto;
}

/* 测试工具弹窗样式 */
.test-params-section {
  margin-top: 16px;
  margin-bottom: 16px;
}
.test-params-title {
  font-size: 13px;
  font-weight: 600;
  color: #171717;
  margin-bottom: 8px;
}
.test-params-hint {
  font-size: 13px;
  color: #a1a1aa;
  font-style: italic;
}
.test-params-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.test-params-table th {
  text-align: left;
  padding: 6px 12px;
  background: #f5f5f5;
  color: #52525b;
  font-weight: 600;
  border-bottom: 1px solid #e5e5e5;
  white-space: nowrap;
}
.test-params-table td {
  padding: 6px 12px;
  border-bottom: 1px solid #f0f0f0;
  color: #171717;
  word-break: break-word;
  overflow-wrap: break-word;
}
.test-params-table code {
  background: #f5f5f5;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
  color: #0070f3;
}
.param-required {
  color: #ef4444;
  font-size: 12px;
  font-weight: 500;
}
.param-optional {
  color: #a1a1aa;
  font-size: 12px;
}
.test-input-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.test-input-label {
  font-size: 13px;
  font-weight: 600;
  color: #171717;
}
.btn-text {
  background: none;
  border: none;
  color: #0070f3;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
}
.btn-text:hover {
  background: #f0f5ff;
}
.test-json-input {
  width: 100%;
  min-height: 120px;
  padding: 12px 16px;
  border: 1px solid #d4d4d8;
  border-radius: 8px;
  font-size: 13px;
  font-family: 'SF Mono', Monaco, Consolas, monospace;
  line-height: 1.6;
  resize: vertical;
  outline: none;
  transition: border-color 0.2s;
  background: #fafafa;
  color: #171717;
  box-sizing: border-box;
}
.test-json-input:focus {
  border-color: #0070f3;
  background: #fff;
}
.dialog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
  margin-top: 16px;
}
.dialog-footer-right {
  display: flex;
  gap: 8px;
}
.btn-cancel {
  padding: 6px 14px;
  background: #fff;
  color: #71717a;
  border: 1px solid #d4d4d8;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}
.btn-cancel:hover {
  border-color: #171717;
  color: #171717;
}
.btn-primary-sm {
  padding: 6px 14px;
  background: #171717;
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}
.btn-primary-sm:hover:not(:disabled) {
  background: #27272a;
}
.btn-primary-sm:disabled {
  background: #d4d4d8;
  cursor: not-allowed;
}
.test-result-label {
  font-size: 13px;
  font-weight: 600;
  color: #171717;
  margin-bottom: 8px;
}
.test-result-content {
  background: #f5f5f5;
  border: 1px solid #e5e5e5;
  border-radius: 8px;
  padding: 12px 16px;
  font-size: 13px;
  color: #171717;
  max-height: 400px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
  font-family: 'SF Mono', Monaco, Consolas, monospace;
}
.test-result-content.is-json {
  background: #1e1e1e;
  color: #d4d4d4;
  border-color: #333;
}

/* 帮助弹窗样式 */
.help-content {
  max-height: 60vh;
  overflow-y: auto;
}
.help-section {
  margin-bottom: 20px;
}
.help-section h4 {
  font-size: 15px;
  font-weight: 600;
  color: #171717;
  margin-bottom: 8px;
}
.help-section p {
  font-size: 13px;
  color: #52525b;
  line-height: 1.6;
}
.help-section code {
  background: #f5f5f5;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
  color: #0070f3;
}
.help-steps {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 12px;
}
.help-step {
  display: flex;
  gap: 12px;
}
.help-step .step-num {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #0070f3;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  flex-shrink: 0;
}
.help-step b {
  font-size: 14px;
  color: #171717;
}
.help-step p {
  margin-top: 8px;
}
.code-block {
  background: #f5f5f5;
  border: 1px solid #e4e4e7;
  border-radius: 8px;
  padding: 12px 16px;
  font-size: 13px;
  color: #171717;
  overflow-x: auto;
  margin: 8px 0;
}
.help-list {
  list-style: disc;
  padding-left: 20px;
  font-size: 13px;
  color: #52525b;
  line-height: 1.8;
}
.help-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  margin-top: 8px;
}
.help-table th,
.help-table td {
  padding: 8px 12px;
  border: 1px solid #e5e5e5;
  text-align: left;
}
.help-table th {
  background: #f5f5f5;
  font-weight: 600;
  color: #374151;
  white-space: nowrap;
}
.help-table td {
  color: #52525b;
  word-break: break-word;
  overflow-wrap: break-word;
}

.raw-toggle-bar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}
.raw-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #0070f3;
  cursor: pointer;
  border: none;
  background: none;
  padding: 2px 6px;
  border-radius: 4px;
}
.raw-toggle:hover {
  background: #f0f5ff;
}
</style>