<template>
  <a-modal
    :open="open"
    title="远程安装 Skill"
    :width="700"
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
              placeholder="GitHub 仓库地址，如 owner/repo 或 https://github.com/owner/repo"
              @pressEnter="handleFetchRepo"
            />
            <button class="btn-fetch" :disabled="fetching" @click="handleFetchRepo">
              {{ fetching ? '拉取中...' : '拉取技能' }}
            </button>
          </div>
          <a-spin :spinning="fetching">
            <div v-if="repoSkills.length > 0" class="skill-list">
              <a-checkbox-group v-model:value="selectedRepoSlugs" style="width: 100%">
                <div v-for="skill in repoSkills" :key="skill.name" class="skill-item">
                  <a-checkbox :value="skill.name">
                    <span class="skill-name">{{ skill.name }}</span>
                  </a-checkbox>
                  <span class="skill-desc">{{ skill.description || '—' }}</span>
                </div>
              </a-checkbox-group>
            </div>
            <div v-else-if="repoFetched && !fetching" class="empty-tip">
              该仓库未发现包含 SKILL.md 的技能目录
            </div>
          </a-spin>
        </a-tab-pane>

        <!-- Tab 2: 全局搜索 -->
        <a-tab-pane key="search" tab="全局搜索">
          <div class="fetch-row">
            <a-input
              v-model:value="searchKeyword"
              placeholder="输入关键词搜索 GitHub 上的 Skill（如 research、design）"
              @pressEnter="handleSearch"
            />
            <button class="btn-fetch" :disabled="searching" @click="handleSearch">
              {{ searching ? '搜索中...' : '搜索' }}
            </button>
          </div>
          <a-spin :spinning="searching">
            <div v-if="searchResults.length > 0" class="skill-list">
              <a-checkbox-group v-model:value="selectedSearchSlugs" style="width: 100%">
                <div v-for="skill in searchResults" :key="skill.repo + '/' + skill.name" class="skill-item">
                  <a-checkbox :value="skill.name" :disabled="!skill.repo">
                    <span class="skill-name">{{ skill.name }}</span>
                  </a-checkbox>
                  <span class="skill-desc">{{ skill.description || '—' }}</span>
                  <span v-if="skill.repo" class="skill-repo">{{ skill.repo }}</span>
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
          下一步（{{ currentSelected.length }} 个技能）
        </button>
      </div>
    </div>

    <!-- 步骤二：预览 + 确认 -->
    <div v-if="step === 'confirm'">
      <a-spin :spinning="preparing">
        <div v-if="previews.length > 0">
          <p style="font-size: 13px; color: #71717a; margin-bottom: 12px">
            以下 {{ previews.length }} 个 Skill 将被安装：
          </p>
          <div v-for="(preview, idx) in previews" :key="idx" class="preview-card">
            <div class="preview-header">
              <span class="preview-slug">{{ preview.slug }}</span>
              <span v-if="preview.version" class="preview-version">v{{ preview.version }}</span>
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
        <button class="btn-cancel" @click="step = 'browse'">返回</button>
        <button
          class="btn-primary-sm"
          :disabled="previews.length === 0 || committing"
          @click="handleCommit"
        >
          {{ committing ? `安装中 (${commitProgress}/${previews.length})...` : '确认安装' }}
        </button>
      </div>
    </div>
  </a-modal>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { message } from 'ant-design-vue'
import { listRemoteSkills, searchRemoteSkills, prepareRemoteInstall, commitRemoteInstall } from '../api/skill'

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

// 确认阶段
const preparing = ref(false)
const previews = ref([])
const committing = ref(false)
const commitProgress = ref(0)
const draftId = ref(null)

const currentSelected = computed(() =>
  activeTab.value === 'repo' ? selectedRepoSlugs.value : selectedSearchSlugs.value
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
    if (repoSkills.value.length === 0) message.info('该仓库未发现 Skill')
  } catch (e) {
    // interceptor handles error
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
    // interceptor handles error
  } finally {
    searching.value = false
  }
}

async function handlePrepare() {
  const slugs = currentSelected.value
  if (slugs.length === 0) return

  // 全局搜索时需要确定 source（从搜索结果中取 repo）
  let source = currentSource.value
  if (activeTab.value === 'search') {
    // 搜索结果中每个 skill 带有 repo 字段，按 repo 分组
    const repoMap = {}
    for (const skill of searchResults.value) {
      if (slugs.includes(skill.name) && skill.repo) {
        if (!repoMap[skill.repo]) repoMap[skill.repo] = []
        repoMap[skill.repo].push(skill.name)
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
    message.error('准备安装失败，请检查仓库地址和所选技能')
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
  margin-bottom: 12px;
}
.btn-fetch {
  flex-shrink: 0;
  padding: 0 24px;
  height: 32px;
  background: #171717;
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
.skill-list {
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 4px;
}
.skill-item {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding: 8px 12px;
  border-radius: 6px;
}
.skill-item:hover { background: #fafafa; }
.skill-name {
  font-weight: 500;
  font-size: 14px;
  min-width: 100px;
  flex-shrink: 0;
}
.skill-desc {
  font-size: 13px;
  color: #71717a;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.skill-repo {
  font-size: 12px;
  color: #0369a1;
  background: #f0f9ff;
  padding: 2px 8px;
  border-radius: 4px;
  flex-shrink: 0;
}
.preview-card {
  padding: 12px 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  margin-bottom: 8px;
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
  background: #fdf2f8;
  padding: 2px 8px;
  border-radius: 4px;
}
.preview-version {
  font-size: 12px;
  color: #0369a1;
  background: #f0f9ff;
  padding: 2px 8px;
  border-radius: 4px;
}
.preview-desc {
  font-size: 13px;
  color: #52525b;
  margin-bottom: 4px;
}
.preview-meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.preview-tag {
  font-size: 12px;
  color: #71717a;
  background: #f5f5f5;
  padding: 2px 8px;
  border-radius: 4px;
}
.browse-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}
.empty-tip {
  text-align: center;
  padding: 32px;
  color: #a1a1aa;
  font-size: 14px;
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
.btn-cancel:hover { border-color: #171717; color: #171717; }
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
.btn-primary-sm:hover:not(:disabled) { background: #27272a; }
.btn-primary-sm:disabled { background: #d4d4d8; cursor: not-allowed; }
</style>
