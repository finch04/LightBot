<template>
  <a-modal
    :open="open"
    title="远程安装 Skill"
    :width="760"
    :maskClosable="false"
    :footer="null"
    @cancel="handleCancel"
  >
    <!-- 步骤一：浏览 + 选择 -->
    <div v-if="step === 'browse'">
      <a-tabs v-model:activeKey="activeTab" class="remote-tabs">
        <!-- Tab 1: 按仓库拉取 -->
        <a-tab-pane key="repo" tab="按仓库拉取">
          <div class="fetch-row">
            <a-input
              v-model:value="repoSource"
              placeholder="来源仓库，如 anthropics/skills 或 GitHub URL"
              @pressEnter="handleFetchRepo"
            />
            <button class="btn-fetch" :disabled="fetching" @click="handleFetchRepo">
              {{ fetching ? '拉取中...' : '拉取技能' }}
            </button>
          </div>
          <div class="repo-hint-text">
            支持 `owner/repo` 或 GitHub URL。可前往
            <a href="https://skills.sh/" target="_blank" rel="noopener noreferrer">skills.sh</a>
            查询开源 skills。 也支持 ModelScope 单个 Skill 地址，每次仅限安装一个：
            `https://modelscope.cn/skills/&lt;skill-id&gt;`。 Skill ID 可在
            <a href="https://modelscope.cn/skills" target="_blank" rel="noopener noreferrer">ModelScope Skill 市场</a>
            进入详情后从地址栏获取。
          </div>

          <a-spin :spinning="fetching">
            <div v-if="repoSkills.length > 0" class="skill-list">
              <!-- 单个 Skill 自动选中展示 -->
              <template v-if="repoSkills.length === 1">
                <div class="single-skill-card">
                  <div class="single-skill-name">{{ repoSkills[0].name }}</div>
                  <div class="single-skill-meta">{{ repoSkills[0].description || '暂无描述' }}</div>
                  <div v-if="repoSkills[0].source === 'modelscope'" class="single-skill-badge">
                    <a-tag color="blue">ModelScope</a-tag>
                  </div>
                </div>
              </template>
              <!-- 多选列表 -->
              <template v-else>
                <a-checkbox-group v-model:value="selectedRepoSlugs" style="width: 100%">
                  <div v-for="skill in repoSkills" :key="skill.name" class="skill-item">
                    <a-checkbox :value="skill.name">
                      <span class="skill-name">{{ skill.name }}</span>
                    </a-checkbox>
                    <span class="skill-desc">{{ skill.description || '暂无描述' }}</span>
                  </div>
                </a-checkbox-group>
              </template>
            </div>
            <div v-else-if="repoFetched && !fetching" class="empty-tip">
              该仓库未发现包含 SKILL.md 的技能目录
            </div>
          </a-spin>
        </a-tab-pane>

        <!-- Tab 2: 全局搜索 -->
        <a-tab-pane key="search">
          <template #tab>
            <span>全局搜索发现 <QuestionCircleOutlined class="search-help-icon" @click.stop="searchGuideVisible = true" /></span>
          </template>
          <div class="fetch-row">
            <a-input
              v-model:value="searchKeyword"
              placeholder="输入 web、python 等关键字进行全局查找"
              @pressEnter="handleSearch"
            />
            <button class="btn-fetch" :disabled="searching" @click="handleSearch">
              {{ searching ? '搜索中...' : '查找技能' }}
            </button>
          </div>
          <div class="repo-hint-text">
            直接输入关键字检索 skills.sh 上的开源 Skills 并批量拉取安装。无需配置任何 Token。
          </div>

          <a-spin :spinning="searching">
            <div v-if="searchResults.length > 0" class="skill-list">
              <a-checkbox-group v-model:value="selectedSearchSlugs" style="width: 100%">
                <div v-for="skill in searchResults" :key="skill.source + '/' + skill.name" class="skill-item">
                  <a-checkbox :value="skill.name" :disabled="!skill.source">
                    <span class="skill-name">{{ skill.name }}</span>
                  </a-checkbox>
                  <span class="skill-desc">{{ skill.description || '暂无描述' }}</span>
                  <span v-if="skill.installs" class="skill-installs">{{ skill.installs }}</span>
                  <span v-if="skill.source" class="skill-repo">{{ skill.source }}</span>
                </div>
              </a-checkbox-group>
            </div>
            <div v-else-if="searchDone && !searching" class="empty-tip">
              未搜索到相关 Skill
            </div>
          </a-spin>
        </a-tab-pane>
      </a-tabs>

      <div class="browse-footer">
        <button class="btn-cancel" @click="handleCancel">取消</button>
        <button
          class="btn-primary-sm"
          :disabled="currentSelected.length === 0"
          @click="handlePrepare"
        >
          解析并确认（已选 {{ currentSelected.length }} 个）
        </button>
      </div>
    </div>

    <!-- 步骤二：预览 + 确认 -->
    <div v-if="step === 'confirm'">
      <a-spin :spinning="preparing">
        <div v-if="previews.length > 0">
          <p style="font-size: 13px; color: var(--color-mute); margin-bottom: 12px">
            以下 {{ previews.length }} 个 Skill 将被安装：
          </p>
          <div v-for="(preview, idx) in previews" :key="idx" class="preview-card" :class="{ 'preview-installed': committing && idx < commitProgress, 'preview-installing': committing && idx === commitProgress }">
            <div class="preview-header">
              <span class="preview-slug">{{ preview.slug }}</span>
              <span v-if="committing && idx < commitProgress" class="preview-status-tag done">已安装</span>
              <span v-else-if="committing && idx === commitProgress" class="preview-status-tag installing"><a-spin size="small" /> 安装中</span>
              <span v-else-if="preview.version" class="preview-version">v{{ preview.version }}</span>
            </div>
            <div v-if="preview.description" class="preview-desc">{{ preview.description }}</div>
            <div class="preview-meta">
              <span v-if="preview.toolDependencies?.length" class="preview-tag">
                工具: {{ preview.toolDependencies.join(', ') }}
              </span>
              <span v-if="preview.skillDependencies?.length" class="preview-tag">
                依赖: {{ preview.skillDependencies.join(', ') }}
              </span>
            </div>
          </div>
        </div>
        <div v-else-if="!preparing" class="empty-tip">
          未获取到 Skill 预览信息
        </div>
      </a-spin>

      <div class="browse-footer">
        <button class="btn-cancel" @click="step = 'browse'" :disabled="committing">返回</button>
        <button
          class="btn-primary-sm"
          :disabled="previews.length === 0 || committing"
          @click="handleCommit"
        >
          <a-spin v-if="committing" size="small" style="margin-right: 6px" />
          {{ committing ? `安装中 (${commitProgress}/${previews.length})` : '确认安装' }}
        </button>
      </div>
    </div>

    <!-- 全局搜索说明弹窗 -->
    <a-modal v-model:open="searchGuideVisible" title="全局搜索安装说明" :width="560" :footer="null">
      <div class="guide">
        <div class="guide-section">
          <div class="guide-h3">什么是全局搜索？</div>
          <p>全局搜索基于 <a href="https://skills.sh/" target="_blank" rel="noopener noreferrer">skills.sh</a> 社区索引，可直接输入关键字（如 <code>web</code>、<code>python</code>、<code>git</code>）发现开源 Skills 并一键安装，无需手动输入仓库地址。</p>
        </div>
        <div class="guide-section">
          <div class="guide-h3">工作原理</div>
          <p>系统调用 <code>npx -y skills find &lt;keyword&gt;</code> 命令检索 skills.sh 社区维护的 Skill 索引，返回匹配的 Skill 列表及其来源仓库和安装量。整个过程<strong>无需配置 GitHub Token</strong>，也不依赖 GitHub API。</p>
        </div>
        <div class="guide-section">
          <div class="guide-h3">安装流程</div>
          <div class="guide-step">
            <span class="guide-num">1</span>
            <div><b>搜索</b><p>输入关键字，查看匹配的 Skill 列表及安装量。</p></div>
          </div>
          <div class="guide-step">
            <span class="guide-num">2</span>
            <div><b>选择</b><p>勾选需要的 Skill（暂不支持跨仓库同时安装）。</p></div>
          </div>
          <div class="guide-step">
            <span class="guide-num">3</span>
            <div><b>确认</b><p>系统自动下载并解析 SKILL.md 元数据，确认后一键安装。</p></div>
          </div>
        </div>
      </div>
    </a-modal>
  </a-modal>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { message } from 'ant-design-vue'
import { QuestionCircleOutlined } from '@ant-design/icons-vue'
import { listRemoteSkills, searchRemoteSkills, prepareRemoteInstall, commitRemoteInstall, cleanupRemoteDraft } from '../api/skill'

const MODELSCOPE_PREFIX = 'https://modelscope.cn/skills/'

const props = defineProps({ open: Boolean })
const emit = defineEmits(['update:open', 'installed'])

const step = ref('browse')
const activeTab = ref('repo')

// 仓库拉取
const repoSource = ref('')
const fetching = ref(false)
const repoFetched = ref(false)
const repoSkills = ref([])
const selectedRepoSlugs = ref([])

// 全局搜索
const searchKeyword = ref('')
const searching = ref(false)
const searchDone = ref(false)
const searchResults = ref([])
const selectedSearchSlugs = ref([])
const searchGuideVisible = ref(false)

// 确认阶段
const preparing = ref(false)
const previews = ref([])
const committing = ref(false)
const commitProgress = ref(0)
const draftId = ref(null)

const isModelScope = computed(() => repoSource.value.trim().startsWith(MODELSCOPE_PREFIX))

const currentSelected = computed(() =>
  activeTab.value === 'repo'
    ? (repoSkills.value.length === 1
        ? [repoSkills.value[0].name]
        : selectedRepoSlugs.value)
    : selectedSearchSlugs.value
)
const currentSource = computed(() =>
  activeTab.value === 'repo' ? repoSource.value.trim() : ''
)

watch(() => props.open, (val) => {
  if (val) {
    step.value = 'browse'
    activeTab.value = 'repo'
    repoSource.value = ''
    repoFetched.value = false
    repoSkills.value = []
    selectedRepoSlugs.value = []
    searchKeyword.value = ''
    searchDone.value = false
    searchResults.value = []
    selectedSearchSlugs.value = []
    previews.value = []
    draftId.value = null
    commitProgress.value = 0
  }
})

async function handleFetchRepo() {
  if (!repoSource.value.trim()) return message.warning('请输入仓库地址')
  fetching.value = true
  repoFetched.value = false
  repoSkills.value = []
  selectedRepoSlugs.value = []
  try {
    const res = await listRemoteSkills(repoSource.value.trim())
    repoSkills.value = res.data || []
    repoFetched.value = true
    // ModelScope 单个 Skill 自动选中
    if (isModelScope.value && repoSkills.value.length === 1) {
      selectedRepoSlugs.value = [repoSkills.value[0].name]
    }
    if (repoSkills.value.length === 0) message.info('该仓库未发现 Skill')
  } catch (e) {
    const msg = e?.response?.data?.message || e?.message || '拉取失败'
    message.error(msg)
  } finally {
    fetching.value = false
  }
}

async function handleSearch() {
  if (!searchKeyword.value.trim()) return message.warning('请输入搜索关键词')
  searching.value = true
  searchDone.value = false
  searchResults.value = []
  selectedSearchSlugs.value = []
  try {
    const res = await searchRemoteSkills(searchKeyword.value.trim())
    searchResults.value = res.data || []
    searchDone.value = true
    if (searchResults.value.length === 0) message.info('未搜索到相关 Skill')
  } catch (e) {
    const msg = e?.response?.data?.message || e?.message || '搜索失败'
    message.error(msg)
  } finally {
    searching.value = false
  }
}

async function handlePrepare() {
  const slugs = currentSelected.value
  if (!slugs || slugs.length === 0) {
    message.warning('请先选择要安装的 Skill')
    return
  }

  // 全局搜索时需要确定 source（从搜索结果中取 source）
  let source = currentSource.value
  if (activeTab.value === 'search') {
    const repoMap = {}
    for (const skill of searchResults.value) {
      if (slugs.includes(skill.name) && skill.source) {
        if (!repoMap[skill.source]) repoMap[skill.source] = []
        repoMap[skill.source].push(skill.name)
      }
    }
    const repos = Object.keys(repoMap)
    if (repos.length === 0) return message.warning('所选 Skill 缺少仓库信息')
    if (repos.length > 1) return message.warning('暂不支持跨仓库安装，请选择同一仓库的 Skill')
    source = repos[0]
  }

  preparing.value = true
  step.value = 'confirm'
  previews.value = []
  try {
    const res = await prepareRemoteInstall(source, slugs)
    previews.value = res.data || []
    if (previews.value.length > 0) draftId.value = previews.value[0].draftId
  } catch (e) {
    step.value = 'browse'
    // axios 拦截器已展示后端错误信息，此处不重复提示
  } finally {
    preparing.value = false
  }
}

async function handleCommit() {
  if (!draftId.value || previews.value.length === 0) return
  committing.value = true
  commitProgress.value = 0
  let successCount = 0
  try {
    for (const preview of previews.value) {
      await commitRemoteInstall(draftId.value, preview.slug)
      commitProgress.value++
      successCount++
    }
    message.success(`成功安装 ${successCount} 个 Skill`)
    emit('update:open', false)
    emit('installed')
  } catch (e) {
    message.warning(`已安装 ${successCount}/${previews.value.length} 个，部分失败`)
    if (successCount > 0) emit('installed')
  } finally {
    committing.value = false
    // 清理 MinIO 草稿文件（best effort）
    if (draftId.value) {
      cleanupRemoteDraft(draftId.value).catch(() => {})
    }
  }
}

function handleCancel() {
  emit('update:open', false)
}
</script>

<style scoped>
.remote-tabs {
  margin-bottom: 0;
}
.remote-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 12px;
}
.fetch-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
.btn-fetch {
  flex-shrink: 0;
  padding: 0 24px;
  height: 32px;
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
}
.btn-fetch:hover:not(:disabled) { background: #27272a; }
.btn-fetch:disabled { background: #d4d4d8; cursor: not-allowed; }
.repo-hint-text {
  font-size: 12px;
  color: var(--color-mute);
  line-height: 1.6;
  margin-bottom: 12px;
  padding: 8px 12px;
  background: var(--color-canvas-soft);
  border-radius: 6px;
}
.repo-hint-text a {
  color: #2563eb;
  text-decoration: none;
}
.repo-hint-text a:hover { text-decoration: underline; }
.skill-list {
  max-height: 300px;
  overflow-y: auto;
  overflow-x: auto;
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  padding: 4px;
  scrollbar-width: thin;
}
.skill-list::-webkit-scrollbar {
  width: 4px;
  height: 4px;
}
.skill-list::-webkit-scrollbar-thumb {
  background: #d4d4d8;
  border-radius: 2px;
}
.skill-list::-webkit-scrollbar-track {
  background: transparent;
}
.skill-item {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding: 8px 12px;
  border-radius: 6px;
}
.skill-item:hover { background: var(--color-canvas-soft); }
.skill-name {
  font-weight: 500;
  font-size: 14px;
  min-width: 100px;
  flex-shrink: 0;
}
.skill-desc {
  font-size: 13px;
  color: var(--color-mute);
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.skill-repo {
  font-size: 12px;
  color: #0369a1;
  background: var(--color-info-bg);
  padding: 2px 8px;
  border-radius: 4px;
  flex-shrink: 0;
}
.single-skill-card {
  padding: 16px;
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  background: var(--color-canvas-soft);
}
.single-skill-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-ink);
  margin-bottom: 4px;
}
.single-skill-meta {
  font-size: 13px;
  color: var(--color-mute);
}
.single-skill-badge {
  margin-top: 8px;
}
.preview-card {
  padding: 12px 16px;
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  margin-bottom: 8px;
  transition: border-color 0.2s, background 0.2s;
}
.preview-installed {
  border-color: #86efac;
  background: var(--color-success-bg);
}
.preview-installing {
  border-color: #93c5fd;
  background: var(--color-info-bg);
}
.preview-status-tag {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.preview-status-tag.done {
  color: #15803d;
  background: var(--color-success-bg);
}
.preview-status-tag.installing {
  color: #1d4ed8;
  background: var(--color-info-bg);
}
.preview-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.preview-slug {
  font-family: 'SF Mono', Monaco, Consolas, monospace;
  font-size: 13px;
  font-weight: 600;
  color: #be185d;
  background: var(--color-purple-bg);
  padding: 2px 8px;
  border-radius: 4px;
}
.preview-version {
  font-size: 12px;
  color: #0369a1;
  background: var(--color-info-bg);
  padding: 2px 8px;
  border-radius: 4px;
}
.preview-desc {
  font-size: 13px;
  color: var(--color-body);
  margin-bottom: 4px;
}
.preview-meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.preview-tag {
  font-size: 12px;
  color: var(--color-mute);
  background: var(--color-canvas-soft-2);
  padding: 2px 8px;
  border-radius: 4px;
}
.browse-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--color-hairline);
}
.empty-tip {
  text-align: center;
  padding: 32px;
  color: var(--color-mute);
  font-size: 14px;
}
.btn-cancel {
  padding: 6px 14px;
  background: var(--color-canvas);
  color: var(--color-mute);
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}
.btn-cancel:hover { border-color: var(--color-ink); color: var(--color-ink); }
.btn-primary-sm {
  padding: 6px 14px;
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}
.btn-primary-sm:hover:not(:disabled) { background: #27272a; }
.btn-primary-sm:disabled { background: #d4d4d8; cursor: not-allowed; }
.search-help-icon {
  color: var(--color-mute);
  cursor: pointer;
  font-size: 14px;
  margin-left: 4px;
}
.search-help-icon:hover { color: #d97706; }
.skill-installs {
  font-size: 12px;
  color: #16a34a;
  background: var(--color-success-bg);
  padding: 2px 8px;
  border-radius: 4px;
  flex-shrink: 0;
}
/* 全局搜索说明弹窗样式 */
.guide { max-height: 60vh; overflow-y: auto; }
.guide-section { margin-bottom: 20px; }
.guide-section:last-child { margin-bottom: 0; }
.guide-h3 { font-size: 15px; font-weight: 600; color: var(--color-ink); margin-bottom: 8px; }
.guide-section p { font-size: 13px; color: var(--color-body); line-height: 1.6; margin: 0 0 8px; }
.guide-section code { font-size: 12px; background: var(--color-canvas-soft-2); padding: 1px 4px; border-radius: 4px; }
.guide-step { display: flex; gap: 12px; margin-bottom: 12px; }
.guide-num { width: 22px; height: 22px; border-radius: 50%; background: var(--color-warn-bg); color: #b45309; font-size: 12px; font-weight: 600; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.guide-step b { display: block; font-size: 13px; margin-bottom: 4px; }
.guide-step p { margin: 0; font-size: 12px; color: var(--color-mute); }
</style>
