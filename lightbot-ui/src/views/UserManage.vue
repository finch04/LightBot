<template>
  <div class="user-manage">
    <!-- 搜索栏 -->
    <div class="user-toolbar">
      <a-input
        v-model:value="searchKeyword"
        placeholder="搜索用户名/昵称..."
        allow-clear
        style="width: 260px"
        @change="loadUsers"
      >
        <template #prefix><SearchOutlined /></template>
      </a-input>
      <button class="btn-refresh" @click="loadUsers" :disabled="loading">
        <ReloadOutlined :spin="loading" /> 刷新
      </button>
    </div>

    <!-- 用户表格 -->
    <a-spin :spinning="loading">
      <a-table
        :columns="columns"
        :data-source="userList"
        :pagination="pagination"
        row-key="id"
        size="middle"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'user'">
            <div class="user-cell">
              <div class="user-avatar-small">
                <img v-if="record.avatar" :src="record.avatar" alt="" class="avatar-img" />
                <span v-else class="avatar-placeholder">{{ (record.nickname || record.username || 'U')[0] }}</span>
              </div>
              <div class="user-info-cell">
                <div class="user-name-cell">{{ record.nickname || record.username }}</div>
                <div class="user-username-cell">{{ record.username }}</div>
              </div>
            </div>
          </template>
          <template v-else-if="column.key === 'role'">
            <a-tag :color="record.role === 'admin' ? 'red' : 'blue'">
              {{ record.role === 'admin' ? '管理员' : '普通用户' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 'active' ? 'green' : 'orange'">
              {{ record.status === 'active' ? '正常' : '已禁用' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'lastLoginAt'">
            {{ formatTime(record.lastLoginAt) }}
          </template>
          <template v-else-if="column.key === 'createTime'">
            {{ formatTime(record.createTime) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <div class="action-btns">
              <button class="btn-icon" @click="openDetail(record)">
                <EyeOutlined />
              </button>
              <button class="btn-icon" @click="openEdit(record)">
                <EditOutlined />
              </button>
              <button v-if="record.role !== 'admin'" class="btn-icon danger" @click="handleDelete(record)">
                <DeleteOutlined />
              </button>
            </div>
          </template>
        </template>
      </a-table>
    </a-spin>

    <!-- 编辑弹窗 -->
    <a-modal
      v-model:open="editVisible"
      title="编辑用户信息"
      :width="520"
      :maskClosable="false"
      @ok="handleSaveEdit"
      @cancel="editVisible = false"
    >
      <a-form :label-col="{ span: 5 }" :wrapper-col="{ span: 18 }">
        <a-form-item label="用户名">
          <a-input :value="editForm.username" disabled />
        </a-form-item>
        <a-form-item label="昵称">
          <a-input v-model:value="editForm.nickname" placeholder="用户昵称" :maxlength="20" show-count />
        </a-form-item>
        <a-form-item label="邮箱">
          <a-input v-model:value="editForm.email" placeholder="邮箱地址" />
        </a-form-item>
        <a-form-item label="手机">
          <a-input v-model:value="editForm.phone" placeholder="手机号码" />
        </a-form-item>
        <a-form-item label="角色">
          <a-select v-model:value="editForm.role">
            <a-select-option value="user">普通用户</a-select-option>
            <a-select-option value="admin">管理员</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="editForm.status">
            <a-select-option value="active">正常</a-select-option>
            <a-select-option value="disabled">已禁用</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 用户详情抽屉 -->
    <a-drawer
      v-model:open="detailVisible"
      :title="detailUser?.nickname || detailUser?.username || '用户详情'"
      :width="560"
      placement="right"
    >
      <div v-if="detailUser" class="detail-section">
        <div class="detail-user-header">
          <div class="detail-avatar">
            <img v-if="detailUser.avatar" :src="detailUser.avatar" alt="" class="avatar-img" />
            <span v-else class="avatar-placeholder-lg">{{ (detailUser.nickname || detailUser.username || 'U')[0] }}</span>
          </div>
          <div class="detail-user-info">
            <h3>{{ detailUser.nickname || detailUser.username }}</h3>
            <p>@{{ detailUser.username }}</p>
          </div>
        </div>

        <div class="detail-rows">
          <div class="detail-row">
            <span class="detail-label">邮箱</span>
            <span class="detail-value">{{ detailUser.email || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">手机</span>
            <span class="detail-value">{{ detailUser.phone || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">角色</span>
            <span class="detail-value">
              <a-tag :color="detailUser.role === 'admin' ? 'red' : 'blue'">
                {{ detailUser.role === 'admin' ? '管理员' : '普通用户' }}
              </a-tag>
            </span>
          </div>
          <div class="detail-row">
            <span class="detail-label">状态</span>
            <span class="detail-value">
              <a-tag :color="detailUser.status === 'active' ? 'green' : 'orange'">
                {{ detailUser.status === 'active' ? '正常' : '已禁用' }}
              </a-tag>
            </span>
          </div>
          <div class="detail-row">
            <span class="detail-label">注册时间</span>
            <span class="detail-value">{{ formatTime(detailUser.createTime) }}</span>
          </div>
        </div>

        <!-- 用户的 Agent -->
        <div class="detail-resources">
          <h4>Agent（{{ userAgents.length }}）</h4>
          <a-spin :spinning="agentsLoading">
            <div v-if="userAgents.length === 0" class="resource-empty">暂无 Agent</div>
            <div v-else class="resource-list">
              <div v-for="a in userAgents" :key="a.id" class="resource-item">
                <RobotOutlined class="resource-icon" />
                <div class="resource-info">
                  <div class="resource-name">{{ a.name }}</div>
                  <div class="resource-meta">
                    <a-tag :color="a.status === 'published' ? 'green' : 'default'" size="small">
                      {{ a.status === 'published' ? '已发布' : '草稿' }}
                    </a-tag>
                    <span class="resource-type">{{ a.agentType === 'workflow' ? '工作流' : '对话' }}</span>
                  </div>
                </div>
              </div>
            </div>
          </a-spin>
        </div>

        <!-- 用户的知识库 -->
        <div class="detail-resources">
          <h4>知识库（{{ userKnowledges.length }}）</h4>
          <a-spin :spinning="knowledgesLoading">
            <div v-if="userKnowledges.length === 0" class="resource-empty">暂无知识库</div>
            <div v-else class="resource-list">
              <div v-for="k in userKnowledges" :key="k.id" class="resource-item">
                <DatabaseOutlined class="resource-icon kb" />
                <div class="resource-info">
                  <div class="resource-name">{{ k.name }}</div>
                  <div class="resource-meta">{{ k.description || '暂无描述' }}</div>
                </div>
              </div>
            </div>
          </a-spin>
        </div>
      </div>
    </a-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import {
  SearchOutlined, ReloadOutlined, EyeOutlined, EditOutlined, DeleteOutlined,
  RobotOutlined, DatabaseOutlined,
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { listUsers, adminUpdateUser, adminDeleteUser, getUserDetail, getUserAgents, getUserKnowledges } from '../api/admin'

const loading = ref(false)
const userList = ref([])
const searchKeyword = ref('')
const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
  showSizeChanger: true,
  showTotal: (total) => `共 ${total} 个用户`,
})

const columns = [
  { title: '用户', key: 'user', dataIndex: 'username', width: 200 },
  { title: '手机', dataIndex: 'phone', width: 130 },
  { title: '角色', key: 'role', width: 100, align: 'center' },
  { title: '状态', key: 'status', width: 90, align: 'center' },
  { title: '最后登录', key: 'lastLoginAt', width: 170 },
  { title: '注册时间', key: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 110, align: 'center', fixed: 'right' },
]

// 编辑弹窗
const editVisible = ref(false)
const editingId = ref(null)
const editForm = reactive({
  username: '',
  nickname: '',
  email: '',
  phone: '',
  role: 'user',
  status: 'active',
})

// 详情抽屉
const detailVisible = ref(false)
const detailUser = ref(null)
const userAgents = ref([])
const userKnowledges = ref([])
const agentsLoading = ref(false)
const knowledgesLoading = ref(false)

onMounted(() => {
  loadUsers()
})

async function loadUsers() {
  loading.value = true
  try {
    const res = await listUsers({
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
      keyword: searchKeyword.value || undefined,
    })
    userList.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } catch (e) {
    console.error('[UserManage] 加载用户列表失败:', e)
  } finally {
    loading.value = false
  }
}

function handleTableChange(pag) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  loadUsers()
}

function openEdit(record) {
  editingId.value = record.id
  Object.assign(editForm, {
    username: record.username,
    nickname: record.nickname || '',
    email: record.email || '',
    phone: record.phone || '',
    role: record.role || 'user',
    status: record.status || 'active',
  })
  editVisible.value = true
}

async function handleSaveEdit() {
  try {
    await adminUpdateUser(editingId.value, {
      nickname: editForm.nickname,
      email: editForm.email,
      phone: editForm.phone,
      role: editForm.role,
      status: editForm.status,
    })
    message.success('更新成功')
    editVisible.value = false
    loadUsers()
  } catch (e) {
    message.error(e.response?.data?.message || '更新失败')
  }
}

function handleDelete(record) {
  Modal.confirm({
    title: '确认删除用户',
    content: `确定要删除用户「${record.nickname || record.username}」吗？该操作不可恢复，用户的所有数据将被清除。`,
    okText: '继续删除',
    okType: 'danger',
    cancelText: '取消',
    onOk() {
      return new Promise((resolve) => {
        Modal.confirm({
          title: '二次确认',
          content: `请再次确认：删除用户「${record.username}」后将无法恢复，是否继续？`,
          okText: '确认删除',
          okType: 'danger',
          cancelText: '取消',
          async onOk() {
            try {
              await adminDeleteUser(record.id)
              message.success('用户已删除')
              loadUsers()
              resolve()
            } catch (e) {
              message.error(e.response?.data?.message || '删除失败')
              resolve()
            }
          },
          onCancel() {
            resolve()
          },
        })
      })
    },
  })
}

async function openDetail(record) {
  detailUser.value = record
  detailVisible.value = true
  userAgents.value = []
  userKnowledges.value = []

  // 并行加载用户的 Agent 和知识库
  agentsLoading.value = true
  knowledgesLoading.value = true
  Promise.all([
    getUserAgents(record.id).then(res => { userAgents.value = res.data || [] }),
    getUserKnowledges(record.id).then(res => { userKnowledges.value = res.data || [] }),
  ]).finally(() => {
    agentsLoading.value = false
    knowledgesLoading.value = false
  })
}

function formatTime(t) {
  if (!t) return '-'
  const d = new Date(t)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<style scoped>
.user-manage {
  padding: 0;
}
.user-toolbar {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.btn-refresh {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: transparent;
  border: 1px solid #d9d9d9;
  border-radius: 100px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}
.btn-refresh:hover:not(:disabled) { border-color: #0070f3; color: #0070f3; }
.btn-refresh:disabled { opacity: 0.5; cursor: not-allowed; }
.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.user-avatar-small {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  background: linear-gradient(135deg, #0070f3, #7928ca);
  display: flex;
  align-items: center;
  justify-content: center;
}
.user-avatar-small .avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.user-avatar-small .avatar-placeholder {
  font-size: 14px;
  font-weight: 600;
  color: #fff;
}
.user-info-cell {
  min-width: 0;
}
.user-name-cell {
  font-size: 13px;
  font-weight: 500;
  color: #171717;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-username-cell {
  font-size: 12px;
  color: #a1a1aa;
}
.action-btns {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}
.btn-icon {
  padding: 4px 6px;
  background: transparent;
  border: none;
  color: #71717a;
  cursor: pointer;
  border-radius: 4px;
  font-size: 14px;
}
.btn-icon:hover {
  background: #f4f4f5;
  color: #171717;
}
.btn-icon.danger:hover {
  background: #fee2e2;
  color: #ef4444;
}

/* 详情抽屉 */
.detail-user-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #ebebeb;
}
.detail-avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  background: linear-gradient(135deg, #0070f3, #7928ca);
  display: flex;
  align-items: center;
  justify-content: center;
}
.detail-avatar .avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.avatar-placeholder-lg {
  font-size: 24px;
  font-weight: 600;
  color: #fff;
}
.detail-user-info h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #171717;
}
.detail-user-info p {
  margin: 4px 0 0;
  font-size: 13px;
  color: #a1a1aa;
}
.detail-rows {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 24px;
}
.detail-row {
  display: flex;
  align-items: center;
}
.detail-label {
  width: 70px;
  font-size: 13px;
  color: #71717a;
  flex-shrink: 0;
}
.detail-value {
  font-size: 13px;
  color: #171717;
}
.detail-resources {
  margin-bottom: 24px;
}
.detail-resources h4 {
  font-size: 14px;
  font-weight: 600;
  color: #171717;
  margin: 0 0 12px;
}
.resource-empty {
  font-size: 13px;
  color: #a1a1aa;
  padding: 12px 0;
}
.resource-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.resource-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: #fafafa;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
}
.resource-icon {
  font-size: 16px;
  color: #0070f3;
  flex-shrink: 0;
}
.resource-icon.kb {
  color: #059669;
}
.resource-info {
  min-width: 0;
  flex: 1;
}
.resource-name {
  font-size: 13px;
  font-weight: 500;
  color: #171717;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.resource-meta {
  font-size: 12px;
  color: #a1a1aa;
  margin-top: 2px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.resource-type {
  font-size: 12px;
  color: #71717a;
}
</style>
