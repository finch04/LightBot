<template>
  <div class="page">
    <div class="page-header">
      <div>
        <button class="btn-back" @click="router.push('/app/knowledge')">
          <ArrowLeftOutlined /> 返回
        </button>
        <h1 class="page-title">
          {{ knowledge.name }}
          <a-tooltip v-if="knowledge.type" :title="knowledge.type === 'milvus' ? 'Milvus' : 'PostgreSQL'">
            <CloudServerOutlined v-if="knowledge.type === 'milvus'" class="title-type-icon milvus" />
            <DatabaseOutlined v-else class="title-type-icon pg" />
          </a-tooltip>
          <span v-if="knowledge.type === 'milvus'" class="milvus-status" :class="{ connected: milvusConnected }">
            <span class="status-dot"></span>
            {{ milvusConnected ? 'Milvus 已连接' : 'Milvus 未连接' }}
          </span>
        </h1>
        <p class="page-desc">{{ knowledge.description || '暂无描述' }}</p>
        <div class="kb-stats-bar">
          <div class="kb-stat-item">
            <FileTextOutlined />
            <span class="kb-stat-value">{{ knowledge.documentCount || 0 }}</span>
            <span class="kb-stat-label">文档</span>
          </div>
          <div class="kb-stat-divider"></div>
          <div class="kb-stat-item">
            <BlockOutlined />
            <span class="kb-stat-value">{{ knowledge.chunkCount || 0 }}</span>
            <span class="kb-stat-label">分片</span>
          </div>
          <div class="kb-stat-divider"></div>
          <div class="kb-stat-item">
            <FontColorsOutlined />
            <span class="kb-stat-value">{{ formatTokenCount(knowledge.totalTokens) }}</span>
            <span class="kb-stat-label">Token</span>
          </div>
          <a-tooltip title="重新计算统计数据">
            <button class="kb-stats-refresh" :disabled="statsRepairing" @click="handleRefreshStats">
              <LoadingOutlined v-if="statsRepairing" spin />
              <ReloadOutlined v-else />
            </button>
          </a-tooltip>
        </div>
      </div>
      <div class="header-actions">
        <button class="btn-outline-sm" @click="openMembersModal">
          <TeamOutlined /> 成员
        </button>
        <button class="btn-primary-sm" @click="openEditDialog">
          <EditOutlined /> 编辑
        </button>
      </div>
    </div>

    <div class="content-grid">
      <!-- 文档列表 -->
      <div class="panel">
        <div class="panel-header">
          <h3>文档列表</h3>
          <div class="panel-header-actions">
            <a-input
              v-model:value="docSearch"
              placeholder="搜索文档名称..."
              allow-clear
              size="small"
              class="doc-search-input"
              @input="onDocSearchInput"
              @clear="docPagination.current = 1; loadDocuments()"
            >
              <template #prefix><SearchOutlined /></template>
            </a-input>
            <a-tooltip title="刷新" :open="docRefreshTipVisible" @open-change="v => { if (!v) docRefreshTipVisible = false }">
              <button class="btn-outline-sm" @click="docRefreshTipVisible = false; loadDocuments()" :disabled="docLoading">
                <ReloadOutlined :spin="docLoading" />
              </button>
            </a-tooltip>
            <button class="btn-primary-sm" @click="openUploadModal">上传文档</button>
          </div>
        </div>
        <a-spin :spinning="docLoading">
        <div class="doc-list" :class="{ 'doc-list-min': docLoading }">
          <div v-for="doc in documents" :key="doc.id" class="doc-item" @click="openDocModal(doc)">
            <a-tooltip :title="statusText(doc.status?.code || doc.status)">
              <span class="doc-status-icon" :class="doc.status?.code || doc.status">
                <CheckCircleOutlined v-if="(doc.status?.code || doc.status) === 'completed'" />
                <SyncOutlined v-else-if="(doc.status?.code || doc.status) === 'uploading' || (doc.status?.code || doc.status) === 'pending' || (doc.status?.code || doc.status) === 'processing'" spin />
                <CloseCircleOutlined v-else-if="(doc.status?.code || doc.status) === 'failed'" />
                <ExclamationCircleOutlined v-else />
              </span>
            </a-tooltip>
            <a-tooltip :title="doc.name" placement="topLeft">
              <span class="doc-name">{{ doc.name }}</span>
            </a-tooltip>
            <div class="doc-meta">
              <a-tooltip v-if="duplicateThreshold > 0 && doc.duplicateRate != null && doc.duplicateRate >= duplicateThreshold" :title="`内容重复率 ${(doc.duplicateRate * 100).toFixed(1)}%，超过阈值 ${(duplicateThreshold * 100).toFixed(0)}%`">
                <a-tag color="orange" class="doc-dup-tag">重复 {{ (doc.duplicateRate * 100).toFixed(0) }}%</a-tag>
              </a-tooltip>
              <span v-if="doc.chunkCount" class="doc-chunk-count">{{ doc.chunkCount }} 分块</span>
              <a-tooltip title="入库" v-if="(doc.status?.code || doc.status) === 'uploaded' || (doc.status?.code || doc.status) === 'failed'">
                <button class="doc-icon-btn" @click.stop="openIngestModal(doc)"><UploadOutlined /></button>
              </a-tooltip>
              <a-tooltip title="重新入库" v-if="(doc.status?.code || doc.status) === 'completed'">
                <button class="doc-icon-btn" @click.stop="openIngestModal(doc)"><RedoOutlined /></button>
              </a-tooltip>
              <a-tooltip v-if="isUrlDocument(doc)" title="同步最新内容">
                <button class="doc-icon-btn" :disabled="doc._syncing" @click.stop="handleSyncUrl(doc)">
                  <SyncOutlined :spin="doc._syncing" />
                </button>
              </a-tooltip>
              <a-tooltip title="删除">
                <button class="doc-icon-btn danger" @click.stop="deleteDoc(doc.id)"><DeleteOutlined /></button>
              </a-tooltip>
              <!-- URL 文档同步信息 -->
              <span v-if="isUrlDocument(doc)" class="doc-sync-info">
                <a-tag v-if="getSyncIntervalLabel(doc)" size="small" :color="getSyncIntervalLabel(doc) === '手动' ? 'default' : 'blue'">
                  {{ getSyncIntervalLabel(doc) }}
                </a-tag>
              </span>
            </div>
          </div>
          <div v-if="documents.length === 0" class="doc-empty">
            {{ docSearch.trim() ? '未找到匹配文档' : '暂无文档' }}
          </div>
        </div>
        </a-spin>
        <div v-if="docPagination.total > 0" class="doc-pagination">
          <a-pagination
            v-model:current="docPagination.current"
            :page-size="docPagination.pageSize"
            :total="docPagination.total"
            size="small"
            show-less-items
            :show-total="(total) => `共 ${total} 条`"
            @change="loadDocuments"
          />
        </div>
      </div>

      <!-- 检索测试 + 思维导图 -->
      <div class="panel">
        <a-tabs v-model:activeKey="activeTab">
          <a-tab-pane key="ask" tab="检索测试">
            <div class="rag-section">
              <div class="rag-messages" ref="ragRef">
                <template v-for="(turn, ti) in ragHistory" :key="ti">
                  <!-- 用户提问 -->
                  <div class="rag-msg user">
                    <div class="rag-content">{{ turn.question }}</div>
                  </div>
                  <!-- 加载中 -->
                  <div v-if="turn.loading" class="rag-msg assistant">
                    <LoadingOutlined spin /> 检索中...
                  </div>
                  <!-- 检索结果摘要 -->
                  <template v-else>
                    <div v-if="turn.results.length > 0" class="rag-msg assistant">
                      检索到 {{ turn.results.length }} 条结果
                      <template v-if="turn.results.some(r => r.resultType === 'qa_pair')">
                        （含 {{ turn.results.filter(r => r.resultType === 'qa_pair').length }} 条问答对）
                      </template>
                    </div>
                    <div v-else class="rag-msg assistant">
                      未检索到相关内容
                    </div>
                    <!-- 文档块列表 -->
                    <div v-for="(item, i) in turn.results" :key="'chunk-' + ti + '-' + i" class="chunk-result-card">
                      <div class="chunk-result-header">
                        <span class="chunk-rank">#{{ item.rank }}</span>
                        <a-tag v-if="item.resultType === 'qa_pair'" color="blue" size="small">问答对</a-tag>
                        <span v-else class="chunk-source">{{ item.documentName }}</span>
                        <span class="chunk-score">相似度 {{ (item.score * 100).toFixed(1) }}%</span>
                      </div>
                      <div class="chunk-result-content">{{ item.content }}</div>
                    </div>
                  </template>
                </template>
              </div>
              <div class="rag-input">
                <input
                  v-model="ragQuestion"
                  placeholder="输入测试问题..."
                  @keydown.enter="askRag"
                />
                <a-tooltip title="检索配置">
                  <button class="btn-outline-sm" @click="queryParamsModalRef?.open()">
                    <SettingOutlined />
                  </button>
                </a-tooltip>
                <button class="btn-primary-sm" :disabled="!ragQuestion.trim() || ragLoading" @click="askRag">
                  <SearchOutlined v-if="!ragLoading" />
                  <LoadingOutlined v-else spin />
                </button>
              </div>
              <!-- 示例问题轮播 / 空状态引导 -->
              <div v-if="ragHistory.length === 0" class="example-questions">
                <template v-if="exampleQuestions.length > 0">
                  <transition name="fade" mode="out-in">
                    <a-tooltip :title="exampleQuestions[questionRotateIndex]" :overlay-style="{ maxWidth: '400px' }">
                      <span
                        :key="questionRotateIndex"
                        class="example-question-text"
                        @click="ragQuestion = exampleQuestions[questionRotateIndex]"
                      >
                        {{ exampleQuestions[questionRotateIndex] }}
                      </span>
                    </a-tooltip>
                  </transition>
                </template>
                <div v-else class="example-questions-hint">
                  暂无示例问题，<a @click="handleGenerateQuestions" :disabled="editQuestionLoading">点击生成示例问题</a>
                </div>
              </div>
            </div>
          </a-tab-pane>
          <a-tab-pane key="mindmap" tab="思维导图">
            <div v-if="activeTab === 'mindmap'" class="rag-section">
              <div v-if="mindmapData" class="mindmap-container">
                <svg ref="mindmapSvgRef" class="mindmap-svg"></svg>
                <div class="mindmap-actions">
                  <button class="btn-primary-sm" :disabled="mindmapLoading" @click="handleGenerateMindmap">
                    {{ mindmapLoading ? '生成中...' : '重新生成' }}
                  </button>
                </div>
              </div>
              <div v-else class="mindmap-empty">
                <p v-if="documents.length === 0">请先上传文档后再生成思维导图</p>
                <p v-else-if="mindmapLoaded">暂无思维导图，点击下方按钮AI自动生成</p>
                <p v-else>加载中...</p>
                <button class="btn-primary-sm" :disabled="mindmapLoading || documents.length === 0" @click="handleGenerateMindmap">
                  {{ mindmapLoading ? '生成中...' : '生成思维导图' }}
                </button>
              </div>
            </div>
          </a-tab-pane>
          <a-tab-pane key="eval" tab="RAG 评估">
            <div class="rag-section">
              <RAGEvaluationTab ref="evalTabRef" :knowledge-id="knowledgeId" />
            </div>
          </a-tab-pane>
          <a-tab-pane key="benchmarks" tab="评估基准">
            <div class="rag-section">
              <EvaluationBenchmarks ref="benchmarksTabRef" :knowledge-id="knowledgeId" :doc-total="docPagination.total" />
            </div>
          </a-tab-pane>
          <a-tab-pane key="knowledge-graph" tab="知识图谱">
            <div v-if="activeTab === 'knowledge-graph'" class="rag-section">
              <KnowledgeGraphTab :knowledge-id="knowledgeId" :doc-total="docPagination.total" />
            </div>
          </a-tab-pane>
          <a-tab-pane key="qa-pairs" tab="问答对">
            <div class="rag-section" style="position: relative;">
              <QAPairsTab
                ref="qaPairsTabRef"
                :knowledge-id="knowledgeId"
                :doc-total="docPagination.total"
                :qa-enabled="qaEnabled"
              />
              <div v-if="!qaEnabled" class="qa-disabled-mask">
                <div class="qa-disabled-content">
                  <p>问答对检索未启用</p>
                  <button class="btn-primary-sm" @click="queryParamsModalRef?.open()">前往检索配置开启</button>
                </div>
              </div>
            </div>
          </a-tab-pane>
        </a-tabs>
      </div>
    </div>

    <!-- 文档预览/分块弹窗 -->
    <a-modal
      v-model:open="docModalVisible"
      :title="currentDoc?.name || '文档详情'"
      :width="900"
      :footer="null"
      centered
      :bodyStyle="{ padding: '0' }"
    >
      <a-tabs v-model:activeKey="docModalTab" class="doc-modal-tabs">
        <template #rightExtra>
          <div class="doc-modal-actions">
            <a-tooltip v-if="isEditableType(currentDoc?.fileType) && (currentDoc?.status?.code || currentDoc?.status) === 'completed'" title="编辑文档">
              <button class="doc-modal-action-btn" @click="openDocEditor(currentDoc)">
                <EditOutlined />
              </button>
            </a-tooltip>
            <a-tooltip title="下载">
              <button class="doc-modal-action-btn" @click="handleDownload">
                <DownloadOutlined />
              </button>
            </a-tooltip>
          </div>
        </template>
        <a-tab-pane key="info" tab="文档信息">
          <div class="tab-pane-body doc-info-pane">
            <div class="doc-info-section">
              <div class="doc-info-title">文件信息</div>
              <a-descriptions :column="2" size="small" bordered :labelStyle="{ width: '100px', minWidth: '100px', whiteSpace: 'nowrap' }">
                <a-descriptions-item label="文件名称">{{ currentDoc?.name || '-' }}</a-descriptions-item>
                <a-descriptions-item label="文件类型">{{ currentDoc?.fileType?.toUpperCase() || '-' }}</a-descriptions-item>
                <a-descriptions-item label="文件大小">{{ formatFileSize(currentDoc?.fileSize) }}</a-descriptions-item>
                <a-descriptions-item label="状态">
                  <a-tag :color="docStatusColor(currentDoc?.status)">{{ statusText(currentDoc?.status?.code || currentDoc?.status) }}</a-tag>
                </a-descriptions-item>
                <a-descriptions-item label="创建时间">{{ formatDateTime(currentDoc?.createTime) }}</a-descriptions-item>
                <a-descriptions-item label="更新时间">{{ formatDateTime(currentDoc?.updateTime) }}</a-descriptions-item>
              </a-descriptions>
            </div>
            <div class="doc-info-section">
              <div class="doc-info-title">业务信息</div>
              <a-descriptions :column="2" size="small" bordered :labelStyle="{ width: '100px', minWidth: '100px', whiteSpace: 'nowrap' }">
                <a-descriptions-item label="分块数量">{{ currentDoc?.chunkCount ?? '-' }}</a-descriptions-item>
                <a-descriptions-item label="Token 数量">{{ currentDoc?.tokenCount != null ? currentDoc.tokenCount.toLocaleString() : '-' }}</a-descriptions-item>
                <a-descriptions-item label="内容重复率">
                  <template v-if="currentDoc?.duplicateRate != null">
                    <a-tag :color="currentDoc.duplicateRate >= 0.8 ? 'red' : currentDoc.duplicateRate >= 0.5 ? 'orange' : 'green'">
                      {{ (currentDoc.duplicateRate * 100).toFixed(1) }}%
                    </a-tag>
                  </template>
                  <span v-else>-</span>
                </a-descriptions-item>
                <a-descriptions-item label="入库策略">{{ currentDoc?.embeddingJson ? parseIngestConfig(currentDoc.embeddingJson).chunkStrategy || '-' : '-' }}</a-descriptions-item>
              </a-descriptions>
            </div>
            <div v-if="parseDuplicateDetails(currentDoc?.duplicateDetails).length > 0" class="doc-info-section">
              <div class="doc-info-title">相似文档</div>
              <a-table
                :data-source="parseDuplicateDetails(currentDoc?.duplicateDetails)"
                :columns="dupDetailColumns"
                :pagination="false"
                size="small"
                bordered
              >
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'similarity'">
                    <a-tag :color="record.similarity >= 0.8 ? 'red' : 'orange'">
                      {{ (record.similarity * 100).toFixed(1) }}%
                    </a-tag>
                  </template>
                </template>
              </a-table>
            </div>
            <div v-if="currentDoc?.errorMessage && (currentDoc?.status?.code || currentDoc?.status) === 'failed'" class="doc-info-section">
              <div class="doc-info-title">错误信息</div>
              <div class="doc-info-error">
                <ExclamationCircleOutlined /> {{ currentDoc.errorMessage }}
              </div>
            </div>
          </div>
        </a-tab-pane>
        <a-tab-pane v-if="hasSourcePreview" key="source" tab="源文件预览">
          <div class="tab-pane-body">
            <FilePreview
              :fileUrl="downloadUrl"
              :fileName="currentDoc?.name"
              :fileType="currentDoc?.fileType"
              :content="previewContent"
              :loading="!previewLoaded"
            />
          </div>
        </a-tab-pane>
        <a-tab-pane key="text" tab="文本预览">
          <div class="tab-pane-body">
            <div v-if="currentDoc?.errorMessage" class="error-message">
              <ExclamationCircleOutlined /> {{ currentDoc.errorMessage }}
            </div>
            <div v-if="previewLoaded && !previewContent" class="modal-empty">
              <p>文档解析失败，无法预览文本内容</p>
              <button class="btn-primary-sm" @click="handleDownload">
                <DownloadOutlined /> 下载文件查看
              </button>
            </div>
            <div v-else-if="previewContent" class="text-content-preview">
              <div v-if="isMarkdownFile" class="markdown-content" v-html="renderedMarkdown"></div>
              <pre v-else class="plain-text">{{ previewContent }}</pre>
            </div>
            <div v-else class="modal-empty">加载中...</div>
          </div>
        </a-tab-pane>
        <a-tab-pane v-if="chunks.length > 0" key="chunks" tab="分块列表">
          <div class="tab-pane-body chunk-list-pane">
            <!-- 入库配置展示 -->
            <div v-if="currentDoc?.embeddingJson" class="ingest-config-display">
              <div class="config-title">入库配置</div>
              <div class="config-tags">
                <a-tag color="blue">策略：{{ parseIngestConfig(currentDoc.embeddingJson).chunkStrategy || '-' }}</a-tag>
                <a-tag color="blue">分片大小：{{ parseIngestConfig(currentDoc.embeddingJson).chunkSize || '-' }}</a-tag>
                <a-tag color="blue">重叠：{{ parseIngestConfig(currentDoc.embeddingJson).chunkOverlap ?? '-' }}</a-tag>
                <a-tag v-if="parseIngestConfig(currentDoc.embeddingJson).chunkDelimiter" color="blue">
                  分隔符：{{ parseIngestConfig(currentDoc.embeddingJson).chunkDelimiter }}
                </a-tag>
              </div>
            </div>
            <div class="chunk-list">
              <div v-for="(chunk, i) in chunks" :key="chunk.id" class="chunk-item" @click="openChunkDetail(chunk)">
                <div class="chunk-header">
                  <span class="chunk-index">#{{ chunk.chunkIndex ?? i + 1 }}</span>
                  <div class="chunk-header-right">
                    <span class="chunk-meta">{{ chunk.tokenCount || 0 }} tokens</span>
                    <a-tag v-if="chunk.status" :color="chunkStatusColor(chunk.status)" size="small" style="flex-shrink:0">
                      {{ chunkStatusText(chunk.status) }}
                    </a-tag>
                  </div>
                </div>
                <div class="chunk-preview">{{ chunk.content?.length > 100 ? chunk.content.substring(0, 100) + '...' : chunk.content }}</div>
              </div>
            </div>
          </div>
        </a-tab-pane>
        <a-tab-pane v-if="isDocCompleted" key="knowledge-graph" tab="知识图谱">
          <div class="tab-pane-body doc-graph-pane">
            <KnowledgeGraphTab :key="`doc-graph-${currentDoc?.id}-${docModalKey}`" :knowledge-id="knowledgeId" :document-id="currentDoc?.id" />
          </div>
        </a-tab-pane>
      </a-tabs>
    </a-modal>

    <!-- 分块详情弹窗 -->
    <a-modal
      v-model:open="chunkDetailVisible"
      :title="`分块 #${currentChunk?.chunkIndex ?? ''}`"
      :width="720"
      :footer="null"
    >
      <div class="chunk-detail-meta">
        <span>{{ currentChunk?.tokenCount || 0 }} tokens</span>
        <a-tag v-if="currentChunk?.status" :color="chunkStatusColor(currentChunk.status)" style="margin-left: 8px">
          {{ chunkStatusText(currentChunk.status) }}
        </a-tag>
      </div>
      <pre class="chunk-detail-content">{{ currentChunk?.content }}</pre>
    </a-modal>

    <!-- 上传文档弹窗 -->
    <a-modal
      v-model:open="uploadVisible"
      title="上传文档"
      :width="uploadMode === 'url' ? 640 : 520"
      :footer="null"
    >
      <div class="upload-section">
        <!-- 上传模式切换 -->
        <div class="upload-mode-switch">
          <a-segmented
            v-model:value="uploadMode"
            :options="uploadModeOptions"
            size="small"
          />
        </div>

        <!-- 文件上传区域 -->
        <div v-if="uploadMode === 'file'" class="upload-dropzone" @click="triggerFileInput" @drop.prevent="onDrop" @dragover.prevent>
          <input ref="fileInputRef" type="file" multiple accept=".md,.txt,.pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.csv,.html,.htm" style="display: none" @change="onFileSelect" />
          <p class="dropzone-text">拖拽文件到此处，或点击选择</p>
          <p class="dropzone-hint">支持 md/txt/pdf/doc/docx/ppt/pptx/xls/xlsx/csv/html，单文件最大 100MB</p>
        </div>

        <!-- OCR 开关 -->
        <div v-if="uploadMode === 'file'" class="ocr-section">
          <div class="ocr-toggle">
            <a-switch v-model:checked="ocrEnabled" size="small" @change="handleOcrToggle" />
            <span class="ocr-label">启用 OCR 识别</span>
          </div>
          <div v-if="ocrEnabled" class="ocr-status">
            <div v-if="ocrChecking" class="ocr-status-text checking">
              <LoadingOutlined spin /> 检测中...
            </div>
            <div v-else-if="ocrHealth?.healthy" class="ocr-status-text success">
              <CheckCircleOutlined /> OCR服务正常
              <div class="ocr-model-info">{{ ocrHealth.modelPath }}</div>
            </div>
            <div v-else-if="ocrHealth" class="ocr-status-text error">
              <CloseCircleOutlined /> {{ ocrHealth.message }}
            </div>
          </div>
        </div>

        <div v-if="uploadMode === 'file' && uploadFiles.length > 0" class="upload-file-list">
          <div v-for="(file, i) in uploadFiles" :key="i" class="upload-file-item">
            <span class="upload-file-name">{{ file.name }}</span>
            <span class="upload-file-status" :class="file._status">
              {{ file._status === 'uploading' ? '上传中...' : file._status === 'success' ? '上传成功' : file._status === 'error' ? '上传失败' : '' }}
            </span>
            <a-tooltip title="移除">
              <button class="btn-icon-sm danger" @click="removeUploadFile(i)">
                <CloseOutlined />
              </button>
            </a-tooltip>
          </div>
        </div>

        <!-- URL 输入区域 -->
        <div v-if="uploadMode === 'url'" class="url-input-area">
          <a-textarea
            v-model:value="urlInput"
            placeholder="输入 URL，一行一个&#10;https://example.com/article1&#10;https://example.com/article2"
            :auto-size="{ minRows: 4, maxRows: 8 }"
            class="url-textarea"
          />
          <div class="url-input-actions">
            <span class="url-hint">支持批量粘贴，自动过滤空行</span>
            <button class="btn-primary-sm" :disabled="!urlInput.trim() || urlFetching" @click="handleFetchUrls">
              {{ urlFetching ? '解析中...' : '解析 URL' }}
            </button>
          </div>
          <!-- URL 高级配置 -->
          <a-collapse ghost class="url-advanced-collapse">
            <a-collapse-panel key="url-advanced" header="高级配置">
              <a-form-item label="自定义请求头" :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
                <div v-for="(h, i) in urlHeaders" :key="i" class="url-header-row">
                  <a-input v-model:value="h.key" placeholder="Header Name" style="width: 35%" size="small" />
                  <a-input v-model:value="h.value" placeholder="Header Value" style="width: 50%" size="small" />
                  <DeleteOutlined class="url-header-delete" @click="urlHeaders.splice(i, 1)" />
                </div>
                <a-button size="small" type="dashed" @click="urlHeaders.push({ key: '', value: '' })">
                  + 添加 Header
                </a-button>
              </a-form-item>
              <a-form-item label="自动同步" :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
                <a-select v-model:value="urlSyncInterval" size="small" style="width: 200px">
                  <a-select-option value="manual">手动同步</a-select-option>
                  <a-select-option value="daily">每天同步</a-select-option>
                  <a-select-option value="weekly">每7天同步</a-select-option>
                </a-select>
              </a-form-item>
            </a-collapse-panel>
          </a-collapse>
        </div>

        <!-- URL 列表 -->
        <div v-if="uploadMode === 'url' && urlList.length > 0" class="url-list">
          <div v-for="(item, i) in urlList" :key="i" class="url-item">
            <div class="url-status-col">
              <CheckCircleOutlined v-if="item.status === 'success'" class="url-icon success" />
              <CloseCircleOutlined v-else-if="item.status === 'error'" class="url-icon error" />
              <SyncOutlined v-else class="url-icon spinning" spin />
            </div>
            <div class="url-main">
              <div class="url-row-top">
                <a
                  class="url-link"
                  :href="item.url"
                  target="_blank"
                  rel="noopener noreferrer"
                  :title="item.url"
                >{{ item.url }}</a>
                <div class="url-toolbar">
                  <a-tooltip v-if="item.status === 'success'" title="预览网页内容">
                    <button type="button" class="url-icon-btn" @click="openUrlPreview(item)">
                      <EyeOutlined />
                    </button>
                  </a-tooltip>
                  <a-tooltip title="移除">
                    <button type="button" class="url-icon-btn danger" @click="removeUrlItem(i)">
                      <CloseOutlined />
                    </button>
                  </a-tooltip>
                </div>
              </div>
              <div v-if="item.status === 'success'" class="url-row-bottom">
                <span class="url-doc-title" :title="item.title">{{ item.title }}</span>
                <span v-if="item.contentLength" class="url-char-badge">{{ formatUrlContentLength(item.contentLength) }}</span>
              </div>
              <div v-else-if="item.status === 'error'" class="url-row-bottom">
                <span class="url-error">{{ item.error }}</span>
              </div>
              <div v-else-if="item.status === 'fetching'" class="url-row-bottom">
                <span class="url-status-text">正在解析网页...</span>
              </div>
            </div>
          </div>
        </div>

        <!-- URL 空状态 -->
        <div v-if="uploadMode === 'url' && urlList.length === 0" class="url-empty-tip">
          <GlobalOutlined /> 输入 URL 后点击解析，解析成功后可预览网页内容，确认无误后再添加
        </div>

        <div class="upload-actions">
          <button class="btn-outline-sm" @click="uploadVisible = false">取消</button>
          <button v-if="uploadMode === 'file'" class="btn-primary-sm" :disabled="uploadFiles.length === 0 || uploadSubmitting" @click="handleBatchUpload">
            {{ uploadSubmitting ? '上传中...' : '开始上传' }}
          </button>
          <button v-if="uploadMode === 'url'" class="btn-primary-sm" :disabled="!hasSuccessfulUrls || urlSaving" @click="handleConfirmUrls">
            {{ urlSaving ? '添加中...' : '确认添加' }}
          </button>
        </div>
      </div>
    </a-modal>

    <!-- 重复文档检测弹窗 -->
    <a-modal
      v-model:open="dupDetectVisible"
      title="检测到重复文档"
      :width="460"
      :footer="null"
      :maskClosable="false"
    >
      <div class="dup-detect-body">
        <p>当前知识库已存在相同内容的文档：<b>{{ dupDetectName }}</b></p>
        <p class="dup-detect-hint">请选择处理方式：</p>
      </div>
      <div class="dup-detect-actions">
        <button class="btn-outline" @click="handleDupChoice('skip')">
          跳过，不上传
        </button>
        <button class="btn-outline" @click="handleDupChoice('keep-both')">
          保留两个版本
        </button>
        <button class="btn-primary-sm" @click="handleDupChoice('overwrite')">
          覆盖旧文档
        </button>
      </div>
    </a-modal>

    <!-- URL 网页预览弹窗 -->
    <a-modal
      v-model:open="urlPreviewVisible"
      :title="urlPreviewItem?.title || '网页预览'"
      :width="960"
      :footer="null"
      destroy-on-close
    >
      <div v-if="urlPreviewItem" class="url-preview-modal">
        <div class="url-preview-source">
          <GlobalOutlined />
          <a :href="urlPreviewItem.url" target="_blank" rel="noopener noreferrer">{{ urlPreviewItem.url }}</a>
        </div>
        <p v-if="urlPreviewItem.description" class="url-preview-desc">{{ urlPreviewItem.description }}</p>
        <a-tabs v-model:activeKey="urlPreviewTab">
          <a-tab-pane key="html" tab="网页预览">
            <div
              v-if="urlPreviewItem.previewHtml"
              class="url-preview-html"
              v-html="urlPreviewItem.previewHtml"
            />
            <div v-else class="url-preview-empty">暂无 HTML 预览，请查看「正文文本」</div>
          </a-tab-pane>
          <a-tab-pane key="text" tab="正文文本">
            <pre class="url-preview-text">{{ urlPreviewItem.content }}</pre>
          </a-tab-pane>
        </a-tabs>
      </div>
    </a-modal>

    <!-- 入库弹窗 -->
    <a-modal
      v-model:open="ingestVisible"
      :title="`文档入库 - ${ingestDoc?.name || ''}`"
      :width="560"
      :footer="null"
      :maskClosable="false"
    >
      <div class="ingest-section">
        <a-form :model="ingestForm" :label-col="{ span: 6 }">
          <a-form-item label="分块策略" required>
            <a-select v-model:value="ingestForm.chunkStrategy" style="width: 100%">
              <a-select-option value="general">通用分块 - 按分隔符和长度切分</a-select-option>
              <a-select-option value="book">书籍分块 - 按章节标题切分</a-select-option>
              <a-select-option value="separator">严格分隔 - 遇分隔符即切分</a-select-option>
              <a-select-option value="qa">问答对分块 - 适合FAQ/客服对话</a-select-option>
              <a-select-option value="laws">法规分块 - 按条款结构切分</a-select-option>
            </a-select>
            <div v-if="ingestForm.chunkStrategy === 'general' && knowledgeDefaultStrategy" class="default-strategy-hint">
              <a-tag color="blue">使用知识库配置: {{ strategyLabelMap[knowledgeDefaultStrategy] || knowledgeDefaultStrategy }}</a-tag>
            </div>
          </a-form-item>
          <a-form-item label="分块大小" required>
            <div style="display:flex;align-items:center;gap:6px">
              <a-input-number v-model:value="ingestForm.chunkSize" :min="100" :max="2000" :step="100" style="width: 100%" />
              <a-tooltip title="每个分块的最大Token数。过小的分块（<30 tokens）会被自动过滤，建议值 200-1000。设置过小可能导致所有分片被过滤，入库失败。">
                <QuestionCircleOutlined style="color:#a1a1aa;cursor:pointer;font-size:14px" />
              </a-tooltip>
            </div>
          </a-form-item>
          <a-form-item label="重叠百分比" required>
            <a-input-number v-model:value="ingestForm.chunkOverlap" :min="0" :max="99" :step="5" style="width: 100%" />
          </a-form-item>
          <a-form-item label="分块分隔符">
            <a-input v-model:value="ingestForm.chunkDelimiter" placeholder="默认按换行符分隔" allow-clear />
          </a-form-item>
        </a-form>

        <!-- 预览分块 -->
        <div v-if="previewChunksList.length > 0" class="preview-chunks">
          <div class="preview-header">分块预览（共 {{ previewChunksList.length }} 块）</div>
          <div class="preview-list">
            <div v-for="(chunk, i) in previewChunksList" :key="i" class="preview-item">
              <div class="preview-item-header">#{{ i + 1 }} (约 {{ Math.round(chunk.length * 1.2) }} tokens)</div>
              <div class="preview-item-content">{{ chunk.length > 200 ? chunk.substring(0, 200) + '...' : chunk }}</div>
            </div>
          </div>
        </div>

        <div class="ingest-actions">
          <button class="btn-outline-sm" :disabled="ingestPreviewing" @click="handlePreviewChunks">
            {{ ingestPreviewing ? '预览中...' : '预览分块' }}
          </button>
          <div style="flex:1"></div>
          <button class="btn-outline-sm" @click="ingestVisible = false">取消</button>
          <button class="btn-primary-sm" :disabled="ingestSubmitting" @click="handleIngest">
            {{ ingestSubmitting ? '入库中...' : '确认入库' }}
          </button>
        </div>
      </div>
    </a-modal>

    <!-- 编辑知识库弹窗 -->
    <a-modal v-model:open="editVisible" title="编辑知识库" :width="520" @ok="handleEdit" :confirm-loading="editSubmitting" :bodyStyle="{ maxHeight: '70vh', overflowY: 'auto', paddingRight: '24px' }">
      <a-form :model="editForm" :label-col="{ span: 6 }">
        <a-form-item label="名称" required>
          <a-input v-model:value="editForm.name" placeholder="知识库名称（不超过30字）" :maxlength="30" show-count />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="editForm.description" :rows="3" placeholder="知识库描述（不超过50字）" :maxlength="50" show-count />
        </a-form-item>
        <a-form-item label="Embed模型" required>
          <ModelSelect v-model="editForm.embeddingModel" model-type="embedding" placeholder="选择嵌入模型" @change="onEmbeddingModelChange" />
        </a-form-item>
        <a-form-item label="分块大小">
          <a-input-number v-model:value="editForm.chunkSize" :min="100" :max="2000" :step="100" style="width: 100%" />
        </a-form-item>
        <a-form-item label="自动生成问题">
          <div style="display: flex; align-items: center; gap: 8px;">
            <a-switch v-model:checked="editForm.autoGenerateQuestions" />
            <a-tooltip title="入库文档时AI自动生成示例问题">
              <QuestionCircleOutlined style="font-size: 14px; color: var(--color-mute); cursor: help;" />
            </a-tooltip>
          </div>
        </a-form-item>
        <a-form-item label="知识图谱">
          <div style="display: flex; align-items: center; gap: 8px;">
            <a-switch v-model:checked="editForm.graphEnabled" />
            <a-tooltip title="开启后，文档入库完成时自动触发知识图谱抽取，提取实体和关系用于图谱检索增强（RAG Graph）">
              <QuestionCircleOutlined style="font-size: 14px; color: var(--color-mute); cursor: help;" />
            </a-tooltip>
          </div>
        </a-form-item>
        <a-form-item label="内容安全扫描">
          <div style="display: flex; align-items: center; gap: 8px;">
            <a-switch v-model:checked="editForm.contentScanEnabled" />
            <a-tooltip title="上传文件/网页抓取时使用 AI 模型检测 Prompt 注入">
              <QuestionCircleOutlined style="font-size: 14px; color: var(--color-mute); cursor: help;" />
            </a-tooltip>
          </div>
        </a-form-item>
        <a-form-item v-if="editForm.contentScanEnabled" label="扫描模型" required>
          <ModelSelect v-model="editForm.scanModel" model-type="llm" placeholder="选择安全扫描模型" />
        </a-form-item>
        <a-form-item label="重复检测">
          <div style="display: flex; align-items: center; gap: 8px;">
            <a-switch v-model:checked="editForm.duplicateDetectionEnabled" />
            <a-tooltip title="文档入库时自动检测内容与已有文档的相似度，超过阈值的文档会在列表中标记橙色警告标签">
              <QuestionCircleOutlined style="font-size: 14px; color: var(--color-mute); cursor: help;" />
            </a-tooltip>
          </div>
        </a-form-item>
        <a-form-item v-if="editForm.duplicateDetectionEnabled" label="重复度阈值">
          <div style="display: flex; align-items: center; gap: 8px;">
            <a-input-number
              v-model:value="editForm.duplicateThreshold"
              :min="0.1"
              :max="1.0"
              :step="0.05"
              style="width: 160px"
            />
            <a-tooltip title="文档内容重复度阈值，超过此值的文档会被标记为重复警告（用于检测文档间重复，非检索相似度阈值）。建议值 0.7-0.9">
              <QuestionCircleOutlined style="font-size: 14px; color: var(--color-mute); cursor: help;" />
            </a-tooltip>
          </div>
        </a-form-item>
        <!-- 示例问题管理 -->
        <a-form-item label="示例问题">
          <div class="edit-questions-list">
            <div v-for="(q, qi) in editExampleQuestions" :key="qi" class="edit-question-item">
              <a-input
                v-if="q.editing"
                v-model:value="q.text"
                size="small"
                placeholder="输入示例问题（最多100字）"
                :maxlength="100"
                @press-enter="confirmEditQuestion(qi)"
                @blur="confirmEditQuestion(qi)"
              />
              <template v-else>
                <a-tooltip :title="q.text" :overlay-style="{ maxWidth: '400px' }">
                  <span class="edit-question-text" @click="startEditQuestion(qi)">{{ q.text }}</span>
                </a-tooltip>
              </template>
              <DeleteOutlined class="edit-question-delete" @click="removeEditQuestion(qi)" />
            </div>
            <div class="edit-question-actions">
              <a-button size="small" type="dashed" @click="addEditQuestion" :disabled="editExampleQuestions.length >= 10">
                <PlusOutlined /> 新增
              </a-button>
              <a-button size="small" type="dashed" :loading="editQuestionLoading" @click="aiGenerateEditQuestion" :disabled="editExampleQuestions.length >= 10">
                <RobotOutlined /> AI生成
              </a-button>
              <a-tooltip title="知识库的示例问题会展示在RAG问答界面，帮助用户快速了解知识库内容。最多10个。">
                <QuestionCircleOutlined style="font-size: 14px; color: var(--color-mute); cursor: help; margin-left: 4px;" />
              </a-tooltip>
            </div>
          </div>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 成员管理弹窗 -->
    <a-modal v-model:open="membersVisible" :width="560" :footer="null">
      <template #title>
        <span>成员管理</span>
        <QuestionCircleOutlined
          style="margin-left: 8px; color: #999; font-size: 14px; cursor: pointer;"
          @click="permHelpVisible = true"
        />
      </template>
      <div class="members-section">
        <div class="member-list">
          <div v-for="member in membersWithInfo" :key="member.userId" class="member-item">
            <div class="member-info">
              <div class="member-avatar">
                <img v-if="member.avatar" :src="member.avatar" alt="avatar" class="member-avatar-img" @error="member.avatar = ''" />
                <span v-else>{{ (member.username || member.nickname || 'U')[0] }}</span>
              </div>
              <div class="member-detail">
                <span class="member-name">{{ member.username || member.nickname || '用户' }}</span>
              </div>
              <a-tag :color="roleColor(member.role)">{{ roleText(member.role) }}</a-tag>
            </div>
            <div class="member-actions" v-if="isManagerOrCreator">
              <a-select
                v-if="member.role !== 'creator'"
                :value="member.role"
                size="small"
                style="width: 100px"
                @change="(val) => handleChangeRole(member.userId, val)"
              >
                <a-select-option value="manager">管理者</a-select-option>
                <a-select-option value="developer">开发者</a-select-option>
                <a-select-option value="viewer">查看者</a-select-option>
              </a-select>
              <a-tooltip v-if="member.role !== 'creator'" title="移除成员">
                <button
                  class="btn-icon-sm danger"
                  @click="handleRemoveMember(member.userId)"
                >
                  <CloseOutlined />
                </button>
              </a-tooltip>
            </div>
          </div>
          <div v-if="membersWithInfo.length === 0" class="empty-tip">暂无成员</div>
        </div>
        <div v-if="isManagerOrCreator" class="add-member-form">
          <button class="btn-primary-sm" @click="openInviteModal">
            <PlusOutlined /> 添加成员
          </button>
        </div>
      </div>
    </a-modal>

    <!-- 邀请成员弹窗 -->
    <a-modal v-model:open="inviteVisible" title="邀请成员" :width="480" :footer="null" :maskClosable="false">
      <div class="invite-section">
        <a-input
          v-model:value="inviteKeyword"
          placeholder="搜索用户名或昵称..."
          allow-clear
          @input="onInviteSearch"
        >
          <template #prefix><SearchOutlined /></template>
        </a-input>
        <div class="invite-results">
          <div v-for="u in inviteResults" :key="u.id" class="invite-item">
            <div class="invite-user">
              <div class="member-avatar">
                <img v-if="u.avatar" :src="u.avatar" alt="avatar" class="member-avatar-img" @error="u.avatar = ''" />
                <span v-else>{{ (u.username || u.nickname || 'U')[0] }}</span>
              </div>
              <div class="invite-info">
                <span class="invite-name">{{ u.username || u.nickname }}</span>
                <span class="invite-username">@{{ u.username }}</span>
              </div>
            </div>
            <a-select
              v-model:value="inviteRole"
              size="small"
              style="width: 90px"
            >
              <a-select-option value="manager">管理者</a-select-option>
              <a-select-option value="developer">开发者</a-select-option>
              <a-select-option value="viewer">查看者</a-select-option>
            </a-select>
            <button class="btn-primary-sm" @click="handleInvite(u.id)">邀请</button>
          </div>
          <div v-if="inviteKeyword && inviteResults.length === 0" class="empty-tip">未找到用户</div>
          <div v-if="!inviteKeyword" class="empty-tip">输入关键词搜索用户</div>
        </div>
      </div>
    </a-modal>

    <!-- 文档编辑弹窗 -->
    <DocumentEditorModal
      v-model:open="docEditorVisible"
      :document-id="docEditorDocId"
      @saved="onDocEditorSaved"
    />

    <!-- 检索配置弹窗 -->
    <QueryParamsModal
      ref="queryParamsModalRef"
      :knowledge-id="knowledgeId"
      :knowledge-type="knowledge.type || 'pg'"
      @apply="onQueryParamsApply"
      @qa-change="onQaChange"
    />

    <!-- 权限说明弹窗 -->
    <a-modal v-model:open="permHelpVisible" title="权限说明" :width="520" :footer="null">
      <div class="perm-help">
        <p>知识库采用四级角色权限体系，高权限包含低权限的所有能力：</p>
        <table class="perm-table">
          <thead>
            <tr>
              <th>功能</th>
              <th>查看者</th>
              <th>开发者</th>
              <th>管理者</th>
              <th>创建者</th>
            </tr>
          </thead>
          <tbody>
            <tr><td>查看文档/检索/问答</td><td class="yes">&#10003;</td><td class="yes">&#10003;</td><td class="yes">&#10003;</td><td class="yes">&#10003;</td></tr>
            <tr><td>查看评估/基准/思维导图</td><td class="yes">&#10003;</td><td class="yes">&#10003;</td><td class="yes">&#10003;</td><td class="yes">&#10003;</td></tr>
            <tr><td>上传/删除/入库文档</td><td class="no">&#10007;</td><td class="yes">&#10003;</td><td class="yes">&#10003;</td><td class="yes">&#10003;</td></tr>
            <tr><td>生成思维导图/示例问题</td><td class="no">&#10007;</td><td class="yes">&#10003;</td><td class="yes">&#10003;</td><td class="yes">&#10003;</td></tr>
            <tr><td>创建/删除评估基准</td><td class="no">&#10007;</td><td class="yes">&#10003;</td><td class="yes">&#10003;</td><td class="yes">&#10003;</td></tr>
            <tr><td>运行/删除评估</td><td class="no">&#10007;</td><td class="yes">&#10003;</td><td class="yes">&#10003;</td><td class="yes">&#10003;</td></tr>
            <tr><td>图谱抽取/编辑</td><td class="no">&#10007;</td><td class="yes">&#10003;</td><td class="yes">&#10003;</td><td class="yes">&#10003;</td></tr>
            <tr><td>查看问答对</td><td class="yes">&#10003;</td><td class="yes">&#10003;</td><td class="yes">&#10003;</td><td class="yes">&#10003;</td></tr>
            <tr><td>新增/编辑/删除问答对</td><td class="no">&#10007;</td><td class="yes">&#10003;</td><td class="yes">&#10003;</td><td class="yes">&#10003;</td></tr>
            <tr><td>AI生成问答对</td><td class="no">&#10007;</td><td class="yes">&#10003;</td><td class="yes">&#10003;</td><td class="yes">&#10003;</td></tr>
            <tr><td>清空图谱数据</td><td class="no">&#10007;</td><td class="no">&#10007;</td><td class="yes">&#10003;</td><td class="yes">&#10003;</td></tr>
            <tr><td>编辑知识库设置</td><td class="no">&#10007;</td><td class="no">&#10007;</td><td class="yes">&#10003;</td><td class="yes">&#10003;</td></tr>
            <tr><td>添加/移除成员</td><td class="no">&#10007;</td><td class="no">&#10007;</td><td class="yes">&#10003;</td><td class="yes">&#10003;</td></tr>
            <tr><td>删除知识库</td><td class="no">&#10007;</td><td class="no">&#10007;</td><td class="no">&#10007;</td><td class="yes">&#10003;</td></tr>
          </tbody>
        </table>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, h, nextTick, onMounted, onUnmounted, watch, defineAsyncComponent } from 'vue'
import { renderMarkdownSync } from '@/utils/markdown_preview'
import { sanitizeHtml } from '@/utils/sanitize'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeftOutlined, EditOutlined, TeamOutlined, PlusOutlined, CloseOutlined, SearchOutlined,
  CheckCircleOutlined, ClockCircleOutlined, SyncOutlined, CloseCircleOutlined, ExclamationCircleOutlined,
  DownloadOutlined, LoadingOutlined, ReloadOutlined, QuestionCircleOutlined, DeleteOutlined, RobotOutlined,
  UploadOutlined, RedoOutlined,
  GlobalOutlined, EyeOutlined, SettingOutlined,
  DatabaseOutlined, CloudServerOutlined,
  FileTextOutlined, BlockOutlined, FontColorsOutlined,
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import {
  getKnowledge, updateKnowledge, getDocuments, uploadDocument, uploadDocuments, deleteDocument,
  previewDocument, getDocumentDownloadUrl, getChunks, searchKnowledge,
  generateMindmap, getMindmap, getKnowledgeMembers, addKnowledgeMember, updateKnowledgeMemberRole,
  removeKnowledgeMember, ingestDocument, previewChunks, getDefaultIngestConfig, checkOcrHealth,
  generateExampleQuestions, getExampleQuestions, updateExampleQuestions, generateOneExampleQuestion,
  fetchUrlDocument, previewUrlDocument, saveUrlDocument, getQueryParams, checkMilvusHealth,
  refreshKnowledgeStats, syncUrlDocument,
} from '../api/knowledge'
import { searchUsers } from '../api/auth'
import request from '../utils/request'
import { getProvidersWithModels } from '../api/modelProvider'
import ModelSelect from '../components/ModelSelect.vue'
import { useUserStore } from '../stores/user'
import { Transformer } from 'markmap-lib'
import { Markmap } from 'markmap-view'
import FilePreview from '../components/FilePreview.vue'
import RAGEvaluationTab from '../components/eval/RAGEvaluationTab.vue'
import EvaluationBenchmarks from '../components/eval/EvaluationBenchmarks.vue'
import KnowledgeGraphTab from '../components/KnowledgeGraphTab.vue'
import QAPairsTab from '../components/QAPairsTab.vue'
import QueryParamsModal from '../components/QueryParamsModal.vue'
const DocumentEditorModal = defineAsyncComponent(() =>
  import('../components/DocumentEditor/DocumentEditorModal.vue')
)

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const knowledgeId = route.params.id

const knowledge = ref({})
const milvusConnected = ref(false)
const statsRepairing = ref(false)
const documents = ref([])
const docLoading = ref(false)
const docRefreshTipVisible = ref(false)
const docSearch = ref('')
const docPagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
})

let docSearchTimer = null
function onDocSearchInput() {
  clearTimeout(docSearchTimer)
  docSearchTimer = setTimeout(() => {
    docPagination.current = 1
    loadDocuments()
  }, 300)
}

const strategyLabelMap = {
  general: '通用分块', book: '书籍分块', separator: '严格分隔', qa: '问答对分块', laws: '法规分块',
}
const knowledgeDefaultStrategy = computed(() => {
  try {
    const cfg = typeof knowledge.value.config === 'string' ? JSON.parse(knowledge.value.config) : (knowledge.value.config || {})
    return cfg.defaultChunkStrategy || ''
  } catch { return '' }
})
const duplicateThreshold = computed(() => {
  try {
    const cfg = typeof knowledge.value.config === 'string' ? JSON.parse(knowledge.value.config) : (knowledge.value.config || {})
    return cfg.duplicateDetectionEnabled ? (cfg.duplicateThreshold ?? 0.8) : -1
  } catch { return -1 }
})
const activeTab = ref('ask')
const evalTabRef = ref(null)
const benchmarksTabRef = ref(null)
const qaPairsTabRef = ref(null)
const ragQuestion = ref('')
const ragHistory = ref([])
const ragLoading = ref(false)
const ragRef = ref(null)
const mindmapData = ref(null)
const mindmapLoading = ref(false)
const mindmapSvgRef = ref(null)
const mindmapLoaded = ref(false)

// 文档编辑弹窗
const docEditorVisible = ref(false)
const docEditorDocId = ref(null)

const EDITABLE_FILE_TYPES = new Set(['md', 'txt', 'csv'])
function isEditableType(fileType) {
  return fileType && EDITABLE_FILE_TYPES.has(fileType.toLowerCase())
}

function openDocEditor(doc) {
  docModalVisible.value = false
  docEditorDocId.value = doc.id
  docEditorVisible.value = true
}

function onDocEditorSaved() {
  loadDocuments()
}

// 文档弹窗
const docModalVisible = ref(false)
const docModalTab = ref('info')
const docModalKey = ref(0)
const currentDoc = ref(null)
const previewContent = ref('')
const previewLoaded = ref(false)
const downloadUrl = ref('')
const chunks = ref([])

/** 解析文档入库配置JSON，缓存结果避免重复解析 */
let _ingestConfigCache = new Map()
function parseIngestConfig(json) {
  if (!json) return {}
  if (_ingestConfigCache.has(json)) return _ingestConfigCache.get(json)
  try {
    const config = typeof json === 'string' ? JSON.parse(json) : json
    _ingestConfigCache.set(json, config)
    return config
  } catch {
    return {}
  }
}

// 分块详情弹窗
const chunkDetailVisible = ref(false)
const currentChunk = ref(null)

// 上传弹窗
const uploadVisible = ref(false)
const uploadSubmitting = ref(false)
const uploadFiles = ref([])
const fileInputRef = ref(null)
const ocrEnabled = ref(false)
const ocrChecking = ref(false)
const ocrHealth = ref(null)

// 上传模式切换
const uploadMode = ref('file')
const uploadModeOptions = [
  { value: 'file', label: '上传文件' },
  { value: 'url', label: '解析 URL' },
]

// URL 抓取
const urlInput = ref('')
const urlList = ref([])
const urlFetching = ref(false)
const urlPreviewVisible = ref(false)
const urlPreviewItem = ref(null)
const urlPreviewTab = ref('html')
const urlSaving = ref(false)
const urlHeaders = ref([])
const urlSyncInterval = ref('manual')

// 重复文档检测弹窗
const dupDetectVisible = ref(false)
const dupDetectName = ref('')
const dupPendingFiles = ref([])

// 计算属性：是否有成功的 URL
const hasSuccessfulUrls = computed(() => urlList.value.some(item => item.status === 'success'))

// 入库弹窗
const ingestVisible = ref(false)
const ingestDoc = ref(null)
const ingestSubmitting = ref(false)
const ingestPreviewing = ref(false)
const previewChunksList = ref([])
const ingestForm = reactive({
  chunkStrategy: 'general',
  chunkSize: 512,
  chunkOverlap: 10,
  chunkDelimiter: '',
})

// 编辑弹窗
const editVisible = ref(false)
const editSubmitting = ref(false)
const selectedEmbeddingModelId = ref(null)
const editForm = reactive({
  name: '',
  description: '',
  embeddingModel: '',
  chunkSize: 512,
  autoGenerateQuestions: false,
  graphEnabled: false,
  contentScanEnabled: false,
  scanModel: '',
  duplicateDetectionEnabled: false,
  duplicateThreshold: 0.8,
})
const editExampleQuestions = ref([])
const editQuestionLoading = ref(false)

const queryParamsModalRef = ref(null)
const searchOverrides = ref(null)
const qaEnabled = ref(true)

const exampleQuestions = ref([])
const exampleQuestionsLoaded = ref(false)
const questionRotateIndex = ref(0)
const shownQuestionIndices = ref(new Set())
let questionRotateTimer = null

function pickRandomQuestionIndex() {
  const len = exampleQuestions.value.length
  if (len === 0) return 0
  // 全部轮完则重置
  if (shownQuestionIndices.value.size >= len) {
    shownQuestionIndices.value.clear()
  }
  let idx
  do {
    idx = Math.floor(Math.random() * len)
  } while (shownQuestionIndices.value.has(idx))
  shownQuestionIndices.value.add(idx)
  return idx
}

// 成员管理
const members = ref([])
const membersWithInfo = ref([])
const currentMemberRole = ref(null)
const membersVisible = ref(false)
const permHelpVisible = ref(false)
const inviteVisible = ref(false)
const inviteKeyword = ref('')
const inviteResults = ref([])
const inviteRole = ref('viewer')
let inviteSearchTimer = null

const isManagerOrCreator = computed(() => {
  return currentMemberRole.value === 'creator' || currentMemberRole.value === 'manager'
})

async function loadKnowledge() {
  const res = await getKnowledge(knowledgeId)
  knowledge.value = res.data

  // Milvus 类型知识库检查连接状态
  if (res.data.type === 'milvus') {
    checkMilvusHealth(knowledgeId).then(r => {
      milvusConnected.value = r.data?.available ?? false
    }).catch(() => {
      milvusConnected.value = false
    })
  }

  // 解析问答对启用状态
  try {
    const qp = res.data.queryParams
    if (qp && qp !== '{}') {
      const parsed = typeof qp === 'string' ? JSON.parse(qp) : qp
      qaEnabled.value = parsed.qa_enabled !== false
    } else {
      qaEnabled.value = true
    }
  } catch {
    qaEnabled.value = true
  }

  // 解析示例问题
  try {
    const eq = res.data.exampleQuestions
    if (eq) {
      const parsed = typeof eq === 'string' ? JSON.parse(eq) : eq
      exampleQuestions.value = Array.isArray(parsed) ? parsed : []
    } else {
      exampleQuestions.value = []
    }
  } catch {
    exampleQuestions.value = []
  }
  exampleQuestionsLoaded.value = true
  shownQuestionIndices.value.clear()
}

async function handleRefreshStats() {
  statsRepairing.value = true
  try {
    await refreshKnowledgeStats(knowledgeId)
    await loadKnowledge()
    message.success('统计已更新')
  } catch {
    // interceptor已处理错误提示
  } finally {
    statsRepairing.value = false
  }
}

// ---- 文档状态轮询 ----
const PROCESSING_STATUSES = new Set(['uploading', 'pending', 'processing'])
let docPollTimer = null
const DOC_POLL_INTERVAL = 1000

function hasProcessingDocs() {
  return documents.value.some(d => {
    const s = d.status?.code || d.status
    return PROCESSING_STATUSES.has(s)
  })
}

function startDocPoll() {
  stopDocPoll()
  docPollTimer = setInterval(async () => {
    await loadDocuments(true)
    if (!hasProcessingDocs()) {
      stopDocPoll()
      loadKnowledge()
    }
  }, DOC_POLL_INTERVAL)
}

function stopDocPoll() {
  if (docPollTimer) {
    clearInterval(docPollTimer)
    docPollTimer = null
  }
}

async function loadDocuments(isBackground = false) {
  if (!isBackground) docLoading.value = true
  try {
    const [res] = await Promise.all([
      getDocuments(knowledgeId, {
        keyword: docSearch.value.trim() || undefined,
        pageNum: docPagination.current,
        pageSize: docPagination.pageSize,
      }),
      new Promise(r => setTimeout(r, 300)),
    ])
    documents.value = res.data?.records || []
    docPagination.total = res.data?.total || 0
  } finally {
    if (!isBackground) docLoading.value = false
    if (docPollTimer && !hasProcessingDocs()) {
      stopDocPoll()
    }
  }
}

async function handleUpload(file) {
  try {
    await uploadDocument(knowledgeId, file)
    message.success('上传成功')
    await loadDocuments()
    setTimeout(startDocPoll, 1500)
  } catch (e) {
    // interceptor已处理错误提示
  }
  return false
}

function handleDupChoice(force) {
  dupDetectVisible.value = false
  if (force === 'skip') {
    uploadFiles.value = []
    return
  }
  // overwrite 或 keep-both：重新上传
  uploadFiles.value = dupPendingFiles.value
  nextTick(() => handleBatchUpload(force))
}

// ========== 上传弹窗 ==========

function openUploadModal() {
  uploadFiles.value = []
  ocrEnabled.value = false
  ocrHealth.value = null
  uploadMode.value = 'file'
  urlInput.value = ''
  urlList.value = []
  uploadVisible.value = true
}

function triggerFileInput() {
  fileInputRef.value?.click()
}

function onFileSelect(e) {
  const files = Array.from(e.target.files || [])
  addUploadFiles(files)
  e.target.value = ''
}

function onDrop(e) {
  const files = Array.from(e.dataTransfer?.files || [])
  addUploadFiles(files)
}

const MAX_FILE_SIZE = 100 * 1024 * 1024 // 100MB

function addUploadFiles(files) {
  for (const file of files) {
    if (file.size > MAX_FILE_SIZE) {
      message.warning(`${file.name} 超过100MB限制，已跳过`)
      continue
    }
    const exists = uploadFiles.value.some(f => f.name === file.name && f.size === file.size)
    if (!exists) {
      uploadFiles.value.push(Object.assign(file, { _status: 'pending' }))
    }
  }
}

function removeUploadFile(index) {
  uploadFiles.value.splice(index, 1)
}

async function handleOcrToggle(checked) {
  if (checked) {
    ocrChecking.value = true
    ocrHealth.value = null
    try {
      const res = await checkOcrHealth()
      ocrHealth.value = res.data
    } catch (e) {
      ocrHealth.value = { healthy: false, message: 'OCR服务检测失败' }
    } finally {
      ocrChecking.value = false
    }
  } else {
    ocrHealth.value = null
  }
}

async function handleBatchUpload(force = null) {
  if (uploadFiles.value.length === 0 || uploadSubmitting.value) return
  uploadSubmitting.value = true

  try {
    const files = uploadFiles.value.map(f => f)
    await uploadDocuments(knowledgeId, files, ocrEnabled.value, force)
    message.success(`文档上传任务已提交，共 ${files.length} 个文件，可在任务中心查看进度`)
    uploadVisible.value = false
    uploadFiles.value = []
    await loadDocuments()
    setTimeout(startDocPoll, 1500)
  } catch (e) {
    // 检测到重复文档时，弹出选择弹窗
    const resData = e?.response?.data
    if (resData?.code === 60002) {
      const msg = resData.message || ''
      // 消息格式："xxx.pdf已是相同内容文件，无需重复上传"
      const match = msg.match(/^(.+?)已是相同内容文件/)
      dupDetectName.value = match ? match[1] : '未知文档'
      dupPendingFiles.value = uploadFiles.value.map(f => f)
      dupDetectVisible.value = true
    }
  } finally {
    uploadSubmitting.value = false
  }
}

// ========== URL 抓取功能 ==========

function isValidUrl(str) {
  try {
    const url = new URL(str)
    return url.protocol === 'http:' || url.protocol === 'https:'
  } catch {
    return false
  }
}

/** 检测是否为内网/私有地址（前端基础校验，非安全兜底） */
function isInternalUrl(urlStr) {
  try {
    const url = new URL(urlStr)
    const host = url.hostname
    if (['localhost', '127.0.0.1', '0.0.0.0', '::1', '[::1]'].includes(host)) return true
    // 10.x / 172.16-31.x / 192.168.x
    if (/^10\.\d+\.\d+\.\d+$/.test(host)) return true
    if (/^172\.(1[6-9]|2\d|3[01])\.\d+\.\d+$/.test(host)) return true
    if (/^192\.168\.\d+\.\d+$/.test(host)) return true
    // 169.254.x.x 元数据端点
    if (/^169\.254\.\d+\.\d+$/.test(host)) return true
    return false
  } catch {
    return false
  }
}

async function handleFetchUrls() {
  const text = urlInput.value.trim()
  if (!text) return

  // 1. 解析 URL 列表（按换行分隔，过滤空行）
  const lines = text.split(/[\r\n]+/).map(l => l.trim()).filter(l => l)
  const validUrls = lines.filter(isValidUrl)

  if (validUrls.length === 0) {
    message.warning('未检测到有效的 URL')
    return
  }

  // 2. 添加新 URL 到列表（去重），直接创建并添加对象
  const pendingItems = []
  for (const url of validUrls) {
    if (urlList.value.some(u => u.url === url)) continue
    const item = { url, status: 'pending', title: '', error: '' }
    urlList.value.push(item)
    pendingItems.push(item)
  }

  if (pendingItems.length === 0) {
    message.info('所有 URL 已在列表中')
    return
  }

  // 3. 安全警告弹窗：检测内网地址并提示用户
  const internalUrls = pendingItems.filter(item => isInternalUrl(item.url))

  const confirmed = await new Promise(resolve => {
    Modal.confirm({
      title: 'URL 安全确认',
      content: h('div', [
        h('div', {
          style: 'max-height:200px;overflow-y:auto;background:#fafafa;border-radius:6px;padding:8px 12px;margin-bottom:12px',
        }, pendingItems.map(item =>
          h('div', { style: 'word-break:break-all;padding:2px 0;font-size:13px' }, item.url)
        )),
        h('div', { style: 'margin-bottom:6px;line-height:1.6' }, '即将访问以上 URL 获取网页内容，请确认链接来源可信。'),
        ...(internalUrls.length > 0 ? [
          h('div', { style: 'margin-bottom:6px;line-height:1.6' }, [
            h('span', { style: 'color:#ff4d4f;font-weight:600' },
              `检测到 ${internalUrls.length} 个内网/本地地址，可能涉及内网资源访问风险，请务必确认。`)
          ])
        ] : []),
      ]),
      okText: '确认解析',
      cancelText: '取消',
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    })
  })
  if (!confirmed) return

  urlInput.value = ''
  urlFetching.value = true

  // 4. 并行抓取每个新添加的 URL
  const promises = pendingItems.map(item => fetchSingleUrl(item))
  await Promise.allSettled(promises)

  urlFetching.value = false
}

async function fetchSingleUrl(item) {
  item.status = 'fetching'
  try {
    const res = await previewUrlDocument(knowledgeId, item.url)
    const data = res.data || {}
    item.status = 'success'
    item.title = data.title || '网页内容'
    item.content = data.content || ''
    item.previewHtml = sanitizeHtml(data.previewHtml || '')
    item.contentLength = data.contentLength || (item.content?.length ?? 0)
    item.description = data.description || ''
    item.suggestedFileName = data.suggestedFileName || ''
  } catch (e) {
    item.status = 'error'
    item.error = e.response?.data?.message || e.message || '解析失败'
  }
}

function formatUrlContentLength(length) {
  if (!length) return ''
  if (length >= 10000) {
    return `${(length / 10000).toFixed(length >= 100000 ? 0 : 1)} 万字`
  }
  if (length >= 1000) {
    return `${(length / 1000).toFixed(1)}k 字`
  }
  return `${length.toLocaleString()} 字`
}

function openUrlPreview(item) {
  urlPreviewItem.value = item
  urlPreviewTab.value = item.previewHtml ? 'html' : 'text'
  urlPreviewVisible.value = true
}

function removeUrlItem(index) {
  urlList.value.splice(index, 1)
}

async function handleConfirmUrls() {
  const successItems = urlList.value.filter(item => item.status === 'success' && item.content)
  if (successItems.length === 0) {
    message.warning('请至少解析一个成功的 URL')
    return
  }

  // 构建 syncConfig
  const validHeaders = urlHeaders.value.filter(h => h.key && h.key.trim())
  const syncConfig = {}
  if (validHeaders.length > 0) {
    syncConfig.headers = {}
    validHeaders.forEach(h => { syncConfig.headers[h.key.trim()] = h.value || '' })
  }
  syncConfig.syncInterval = urlSyncInterval.value
  const syncConfigJson = JSON.stringify(syncConfig)

  urlSaving.value = true
  let saved = 0
  const errors = []
  for (const item of successItems) {
    try {
      await saveUrlDocument(knowledgeId, {
        url: item.url,
        title: item.title,
        content: item.content,
        syncConfig: syncConfigJson,
      })
      saved++
    } catch (e) {
      errors.push(`${item.url}: ${e.response?.data?.message || e.message || '保存失败'}`)
    }
  }
  urlSaving.value = false

  if (saved > 0) {
    message.success(`已添加 ${saved} 个网页文档，可在文档列表中查看`)
    uploadVisible.value = false
    urlList.value = []
    urlInput.value = ''
    urlHeaders.value = []
    urlSyncInterval.value = 'manual'
    await loadDocuments()
    setTimeout(startDocPoll, 1500)
  }
  if (errors.length > 0) {
    message.error(errors[0])
  }
}

// ========== 入库弹窗 ==========

async function openIngestModal(doc) {
  ingestDoc.value = doc
  previewChunksList.value = []

  // 加载知识库默认配置
  try {
    const res = await getDefaultIngestConfig(route.params.id)
    const defaults = res.data || {}
    Object.assign(ingestForm, {
      chunkStrategy: defaults.chunkStrategy || 'general',
      chunkSize: defaults.chunkSize || 512,
      chunkOverlap: defaults.chunkOverlap || 10,
      chunkDelimiter: defaults.chunkDelimiter || '',
    })
  } catch {
    Object.assign(ingestForm, {
      chunkStrategy: 'general',
      chunkSize: 512,
      chunkOverlap: 10,
      chunkDelimiter: '',
    })
  }

  ingestVisible.value = true
}

async function handlePreviewChunks() {
  if (!ingestDoc.value) return
  ingestPreviewing.value = true
  try {
    const data = {
      chunkStrategy: ingestForm.chunkStrategy,
      chunkSize: ingestForm.chunkSize,
      chunkOverlap: ingestForm.chunkOverlap,
      chunkDelimiter: ingestForm.chunkDelimiter || null,
    }
    const res = await previewChunks(ingestDoc.value.id, data)
    previewChunksList.value = res.data || []
    if (previewChunksList.value.length === 0) {
      message.info('未产生分块，请检查文档内容')
    }
  } catch (e) {
    // interceptor已处理错误提示
  } finally {
    ingestPreviewing.value = false
  }
}

async function handleIngest() {
  if (!ingestDoc.value) return
  ingestSubmitting.value = true
  try {
    const data = {
      chunkStrategy: ingestForm.chunkStrategy,
      chunkSize: ingestForm.chunkSize,
      chunkOverlap: ingestForm.chunkOverlap,
      chunkDelimiter: ingestForm.chunkDelimiter || null,
    }
    await ingestDocument(ingestDoc.value.id, data)
    message.success('入库任务已提交，可在「任务中心」查看进度')
    ingestVisible.value = false
    await loadDocuments()
    setTimeout(startDocPoll, 1500)
  } catch (e) {
    // interceptor已处理错误提示
  } finally {
    ingestSubmitting.value = false
  }
}

// ========== 文档弹窗 ==========

async function openDocModal(doc) {
  currentDoc.value = doc
  docModalTab.value = 'info'
  previewContent.value = ''
  previewLoaded.value = false
  downloadUrl.value = ''
  chunks.value = []
  docModalKey.value++
  docModalVisible.value = true

  // 并行加载预览、下载链接和分块
  const [previewRes, downloadRes, chunksRes] = await Promise.allSettled([
    previewDocument(doc.id),
    getDocumentDownloadUrl(doc.id),
    getChunks(doc.id),
  ])
  if (previewRes.status === 'fulfilled') {
    previewContent.value = previewRes.value.data || ''
  }
  previewLoaded.value = true
  if (downloadRes.status === 'fulfilled') {
    downloadUrl.value = downloadRes.value.data?.url || ''
  }
  if (chunksRes.status === 'fulfilled') {
    chunks.value = (chunksRes.value.data || []).sort((a, b) => (a.chunkIndex ?? 0) - (b.chunkIndex ?? 0))
  }
}

async function handleDownload() {
  if (!currentDoc.value?.id) return
  try {
    const blob = await request.get(`/knowledge/documents/${currentDoc.value.id}/download-file`, {
      responseType: 'blob',
      silent: true,
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = currentDoc.value?.name || 'download'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    setTimeout(() => URL.revokeObjectURL(url), 10000)
  } catch {
    message.error('下载失败')
  }
}

function openChunkDetail(chunk) {
  currentChunk.value = chunk
  chunkDetailVisible.value = true
}

// ========== 编辑知识库 ==========

function onEmbeddingModelChange({ modelId }) {
  selectedEmbeddingModelId.value = modelId
}

async function openEditDialog() {
  const k = knowledge.value
  // 解析已有的config JSONB
  let config = {}
  try {
    config = typeof k.config === 'string' ? JSON.parse(k.config) : (k.config || {})
  } catch { config = {} }

  // 解析当前 embeddingModel 对应的 providerId，构造复合值
  let embeddingComposite = null
  if (k.embeddingModel) {
    try {
      const provRes = await getProvidersWithModels('embedding')
      for (const p of (provRes.data || [])) {
        if ((p.models || []).some(m => m.modelId === k.embeddingModel)) {
          embeddingComposite = `${String(p.id)}:${k.embeddingModel}`
          break
        }
      }
    } catch { /* ignore */ }
  }

  // 解析扫描模型复合值
  let scanModelComposite = ''
  if (config.scanModelProviderId && config.scanModelId) {
    scanModelComposite = `${config.scanModelProviderId}:${config.scanModelId}`
  }

  selectedEmbeddingModelId.value = k.embeddingModel || null
  Object.assign(editForm, {
    name: k.name || '',
    description: k.description || '',
    embeddingModel: embeddingComposite,
    chunkSize: config.defaultChunkSize ?? 512,
    autoGenerateQuestions: config.autoGenerateQuestions ?? false,
    graphEnabled: k.graphEnabled ?? false,
    contentScanEnabled: config.contentScanEnabled ?? false,
    scanModel: scanModelComposite,
    duplicateDetectionEnabled: config.duplicateDetectionEnabled ?? false,
    duplicateThreshold: config.duplicateThreshold ?? 0.8,
  })

  // 加载示例问题
  try {
    const res = await getExampleQuestions(knowledgeId)
    editExampleQuestions.value = (res.data || []).map(q => ({ text: q, editing: false }))
  } catch { editExampleQuestions.value = [] }

  editVisible.value = true
}

async function handleEdit() {
  if (!editForm.name.trim()) return message.warning('请输入名称')
  if (!editForm.embeddingModel) return message.warning('请选择 Embed 模型')
  if (editForm.contentScanEnabled && !editForm.scanModel) return message.warning('请选择安全扫描模型')
  editSubmitting.value = true
  try {
    // 解析扫描模型（providerId 保持字符串，避免 Long 精度丢失）
    let scanModelProviderId = null, scanModelId = null
    if (editForm.scanModel) {
      const [pid, mid] = editForm.scanModel.split(':')
      scanModelProviderId = pid || null
      scanModelId = mid || null
    }
    const config = JSON.stringify({
      defaultChunkSize: editForm.chunkSize,
      autoGenerateQuestions: editForm.autoGenerateQuestions,
      contentScanEnabled: editForm.contentScanEnabled,
      scanModelProviderId,
      scanModelId,
      duplicateDetectionEnabled: editForm.duplicateDetectionEnabled,
      duplicateThreshold: editForm.duplicateThreshold,
    })
    await updateKnowledge({
      id: knowledgeId,
      name: editForm.name,
      description: editForm.description,
      embeddingModel: selectedEmbeddingModelId.value,
      graphEnabled: editForm.graphEnabled,
      config,
    })
    // 保存示例问题
    const questions = editExampleQuestions.value.map(q => q.text).filter(t => t && t.trim())
    const overLimit = questions.find(q => q.length > 100)
    if (overLimit) {
      return message.warning('示例问题不能超过100字，请检查')
    }
    await updateExampleQuestions(knowledgeId, questions)
    message.success('更新成功')
    editVisible.value = false
    loadKnowledge()
  } finally {
    editSubmitting.value = false
  }
}

// ========== 编辑弹窗 - 示例问题管理 ==========

function addEditQuestion() {
  if (editExampleQuestions.value.length >= 10) {
    return message.warning('最多保留10个示例问题')
  }
  editExampleQuestions.value.push({ text: '', editing: true })
}

function removeEditQuestion(index) {
  editExampleQuestions.value.splice(index, 1)
}

function startEditQuestion(index) {
  editExampleQuestions.value[index].editing = true
}

function confirmEditQuestion(index) {
  const q = editExampleQuestions.value[index]
  if (!q.text || !q.text.trim()) {
    editExampleQuestions.value.splice(index, 1)
    return
  }
  if (q.text.trim().length > 100) {
    message.warning('示例问题不能超过100字')
    return
  }
  q.editing = false
}

async function aiGenerateEditQuestion() {
  if (editExampleQuestions.value.length >= 10) {
    return message.warning('最多保留10个示例问题')
  }
  editQuestionLoading.value = true
  try {
    const res = await generateOneExampleQuestion(knowledgeId)
    const question = res.data
    if (question) {
      const trimmed = question.length > 100 ? question.substring(0, 100) : question
      editExampleQuestions.value.push({ text: trimmed, editing: false })
      message.success('生成成功')
    }
  } catch (e) {
    // interceptor handled
  } finally {
    editQuestionLoading.value = false
  }
}

// ========== RAG 问答 - 生成示例问题 ==========

function onQueryParamsApply(params) {
  searchOverrides.value = params
  if (params.qa_enabled !== undefined) {
    qaEnabled.value = params.qa_enabled
  }
}

function onQaChange(enabled) {
  qaEnabled.value = enabled
}

async function handleGenerateQuestions() {
  editQuestionLoading.value = true
  try {
    const res = await generateOneExampleQuestion(knowledgeId)
    const question = res.data
    if (question) {
      exampleQuestions.value.push(question)
      if (exampleQuestions.value.length === 1) {
        questionRotateIndex.value = 0
      }
      message.success('生成成功')
    }
  } catch (e) {
    // interceptor handled
  } finally {
    editQuestionLoading.value = false
  }
}

// ========== 删除文档 ==========

function deleteDoc(docId) {
  Modal.confirm({
    title: '确认删除',
    content: '删除后文档将无法恢复，是否继续？',
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await deleteDocument(docId)
        message.success('删除成功')
        loadDocuments()
        loadKnowledge()
      } catch (e) {
        // interceptor已处理错误提示
      }
    },
  })
}

function isUrlDocument(doc) {
  try {
    const meta = JSON.parse(doc.metadata || '{}')
    return !!meta.sourceUrl
  } catch { return false }
}

function getSyncIntervalLabel(doc) {
  try {
    const meta = JSON.parse(doc.metadata || '{}')
    const interval = meta.syncInterval || 'manual'
    const labels = { manual: '手动', daily: '每天', weekly: '每7天' }
    return labels[interval] || '手动'
  } catch { return '手动' }
}

async function handleSyncUrl(doc) {
  doc._syncing = true
  try {
    await syncUrlDocument(doc.id)
    message.success('同步完成')
    await loadDocuments()
  } catch {
    // interceptor已处理错误提示
  } finally {
    doc._syncing = false
  }
}

// ========== 检索测试 ==========

async function askRag() {
  const q = ragQuestion.value.trim()
  if (!q || ragLoading.value) return
  ragQuestion.value = ''
  ragLoading.value = true

  const turn = reactive({ question: q, results: [], loading: true })
  ragHistory.value.push(turn)

  // 滚动到底部
  nextTick(() => {
    if (ragRef.value) ragRef.value.scrollTop = ragRef.value.scrollHeight
  })

  try {
    const res = await searchKnowledge(knowledgeId, q, searchOverrides.value)
    turn.results = res.data || []
  } catch (e) {
    // interceptor 已处理错误提示
  } finally {
    turn.loading = false
    ragLoading.value = false
    nextTick(() => {
      if (ragRef.value) ragRef.value.scrollTop = ragRef.value.scrollHeight
    })
  }
}

// 文件类型判断（用于文本预览tab）
// Excel/CSV/Word 转为 Markdown 表格，MD 文件本身是 Markdown
const isDocCompleted = computed(() => {
  const s = currentDoc.value?.status?.code || currentDoc.value?.status
  return s === 'completed'
})
const isMarkdownFile = computed(() => ['md', 'xlsx', 'xls', 'csv', 'doc', 'docx'].includes(currentDoc.value?.fileType))
// Office文档（doc/docx/xls/xlsx/ppt/pptx）不支持源文件预览，只展示文本化后的内容
const hasSourcePreview = computed(() => {
  const ft = currentDoc.value?.fileType
  if (!ft) return false
  return !['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx'].includes(ft)
})

// Markdown渲染
const renderedMarkdown = computed(() => {
  if (!previewContent.value) return ''
  return renderMarkdownSync(previewContent.value)
})

function statusText(s) {
  const map = { uploading: '上传中', uploaded: '待入库', pending: '分块中', processing: '向量化中', completed: '已完成', failed: '失败' }
  return map[s] || s
}

function chunkStatusText(s) {
  const code = s?.code || s
  const map = { chunked: '已分块', vectorizing: '向量化中', vectorized: '已向量化', failed: '失败' }
  return map[code] || code
}

function chunkStatusColor(s) {
  const code = s?.code || s
  const map = { chunked: 'default', vectorizing: 'processing', vectorized: 'success', failed: 'error' }
  return map[code] || 'default'
}

function formatFileSize(bytes) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function formatTokenCount(count) {
  if (!count || count <= 0) return '0'
  if (count >= 1000000) return (count / 1000000).toFixed(1) + 'M'
  if (count >= 1000) return (count / 1000).toFixed(1) + 'K'
  return String(count)
}

function docStatusColor(status) {
  const s = status?.code || status
  const map = { uploading: 'orange', uploaded: 'default', pending: 'blue', processing: 'orange', completed: 'green', failed: 'red' }
  return map[s] || 'default'
}

function formatDateTime(val) {
  if (!val) return '-'
  // 后端 LocalDateTime 可能是数组 [2026,5,31,10,30,0] 或 ISO 字符串
  let d
  if (Array.isArray(val)) {
    d = new Date(val[0], val[1] - 1, val[2], val[3] || 0, val[4] || 0, val[5] || 0)
  } else {
    d = new Date(val)
  }
  if (isNaN(d.getTime())) return String(val)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

// ========== 相似文档详情 ==========
const dupDetailColumns = [
  { title: '文档名称', dataIndex: 'documentName', key: 'documentName', ellipsis: true },
  { title: '相似度', dataIndex: 'similarity', key: 'similarity', width: 100, align: 'center' },
]

function parseDuplicateDetails(val) {
  if (!val) return []
  try {
    const arr = typeof val === 'string' ? JSON.parse(val) : val
    return Array.isArray(arr) ? arr : []
  } catch { return [] }
}

// ========== 思维导图 ==========

async function loadMindmap() {
  if (mindmapLoaded.value) return
  mindmapLoaded.value = true
  try {
    const res = await getMindmap(knowledgeId)
    if (res.data) {
      mindmapData.value = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
      await nextTick()
      renderMindmap()
    }
  } catch (e) {
    // 未生成过思维导图，忽略
  }
}

// 切换到思维导图tab时才加载/重新渲染
watch(activeTab, (tab) => {
  if (tab === 'mindmap') {
    nextTick(() => {
      if (mindmapData.value) {
        renderMindmap()
      } else {
        loadMindmap()
      }
    })
  } else if (tab === 'eval') {
    nextTick(() => {
      evalTabRef.value?.loadBenchmarks()
      evalTabRef.value?.loadResults()
    })
  } else if (tab === 'benchmarks') {
    nextTick(() => benchmarksTabRef.value?.loadBenchmarks())
  } else if (tab === 'qa-pairs') {
    nextTick(() => qaPairsTabRef.value?.loadData())
  }
})

async function handleGenerateMindmap() {
  if (documents.value.length === 0) {
    message.warning('请先上传文档后再生成思维导图')
    return
  }
  mindmapLoading.value = true
  try {
    const res = await generateMindmap(knowledgeId)
    mindmapData.value = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
    message.success('思维导图生成成功')
    await nextTick()
    renderMindmap()
  } catch (e) {
    // interceptor已处理错误提示
  } finally {
    mindmapLoading.value = false
  }
}

function renderMindmap() {
  if (!mindmapSvgRef.value || !mindmapData.value) return
  try {
    const tree = mindmapData.value
    if (!tree || !tree.content) {
      message.error('思维导图数据结构异常，请重新生成')
      mindmapData.value = null
      return
    }
    const md = jsonToMarkdown(tree, 0)
    const transformer = new Transformer()
    const { root } = transformer.transform(md)
    mindmapSvgRef.value.innerHTML = ''
    Markmap.create(mindmapSvgRef.value, null, root)
  } catch (e) {
    console.error('[Mindmap] 渲染失败:', e)
    message.error('思维导图渲染失败，请重新生成')
    mindmapData.value = null
  }
}

function jsonToMarkdown(node, level) {
  const prefix = '#'.repeat(level + 1)
  let md = `${prefix} ${node.content}\n`
  if (node.children) {
    for (const child of node.children) {
      md += jsonToMarkdown(child, level + 1)
    }
  }
  return md
}

// ========== 成员管理 ==========

async function loadMembers() {
  try {
    const res = await getKnowledgeMembers(knowledgeId)
    members.value = res.data || []
    // 后端已连表查询返回用户昵称、头像，直接使用
    membersWithInfo.value = members.value
    // 判断当前用户角色
    const userId = userStore.user?.id
    const myMember = members.value.find(m => String(m.userId) === String(userId))
    currentMemberRole.value = myMember?.role || null
  } catch (e) {
    // interceptor已处理错误提示
  }
}

function openMembersModal() {
  membersVisible.value = true
  loadMembers()
}

function openInviteModal() {
  inviteKeyword.value = ''
  inviteResults.value = []
  inviteRole.value = 'viewer'
  inviteVisible.value = true
}

function onInviteSearch() {
  clearTimeout(inviteSearchTimer)
  const kw = inviteKeyword.value.trim()
  if (!kw) {
    inviteResults.value = []
    return
  }
  inviteSearchTimer = setTimeout(async () => {
    try {
      const res = await searchUsers(kw)
      // 过滤掉已经是成员的用户
      const memberIds = new Set(members.value.map(m => String(m.userId)))
      inviteResults.value = (res.data || []).filter(u => !memberIds.has(String(u.id)))
    } catch { /* ignore */ }
  }, 300)
}

async function handleInvite(userId) {
  try {
    await addKnowledgeMember(knowledgeId, String(userId), inviteRole.value)
    message.success('邀请成功')
    inviteVisible.value = false
    loadMembers()
  } catch (e) {
    // interceptor已处理错误提示
  }
}

async function handleChangeRole(userId, role) {
  try {
    await updateKnowledgeMemberRole(knowledgeId, userId, role)
    message.success('角色更新成功')
    loadMembers()
  } catch (e) {
    // interceptor已处理错误提示
  }
}

async function handleRemoveMember(userId) {
  Modal.confirm({
    title: '确认移除',
    content: '确定要移除该成员吗？',
    okText: '确认',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await removeKnowledgeMember(knowledgeId, userId)
        message.success('成员已移除')
        loadMembers()
      } catch (e) {
        // interceptor已处理错误提示
      }
    },
  })
}

function roleText(role) {
  const map = { creator: '创建者', manager: '管理者', developer: '开发者', viewer: '查看者' }
  return map[role] || role
}

function roleColor(role) {
  const map = { creator: 'red', manager: 'orange', developer: 'blue', viewer: 'green' }
  return map[role] || 'default'
}

onMounted(async () => {
  loadKnowledge()
  await loadDocuments()
  loadMembers()

  // 页面加载时如有处理中的文档，自动开启轮询
  if (hasProcessingDocs()) {
    startDocPoll()
  }

  // 从聊天页跳转过来时，自动打开对应文档的预览弹窗
  const docId = route.query.docId
  if (docId) {
    const doc = documents.value.find(d => String(d.id) === String(docId))
    if (doc) {
      openDocModal(doc)
    }
    // 清除 query 参数，避免刷新重复打开
    router.replace({ query: {} })
  }

  // 示例问题轮播：每 3 秒随机切换，不重复
  questionRotateTimer = setInterval(() => {
    if (exampleQuestions.value.length > 0) {
      questionRotateIndex.value = pickRandomQuestionIndex()
    }
  }, 3000)
})

onUnmounted(() => {
  if (questionRotateTimer) {
    clearInterval(questionRotateTimer)
    questionRotateTimer = null
  }
  stopDocPoll()
  _ingestConfigCache.clear()
  _ingestConfigCache = null
})
</script>

<style scoped>
.page {
  padding: 20px 24px;
  height: 100vh;
  overflow-y: auto;
  background: var(--color-canvas-soft);
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.btn-back {
  background: none;
  border: none;
  color: var(--color-mute);
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 8px;
}
.btn-back:hover {
  color: var(--color-link);
}
.page-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--color-ink);
  margin-bottom: 4px;
}
.title-type-icon {
  font-size: 18px;
  vertical-align: middle;
  margin-left: 8px;
  cursor: help;
}
.title-type-icon.pg {
  color: #3b82f6;
}
.title-type-icon.milvus {
  color: #8b5cf6;
}
.milvus-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  font-weight: 500;
  color: #ef4444;
  margin-left: 12px;
  padding: 2px 10px;
  border-radius: 100px;
  background: var(--color-error-bg);
  vertical-align: middle;
}
.milvus-status.connected {
  color: #16a34a;
  background: var(--color-success-bg);
}
.milvus-status .status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ef4444;
}
.milvus-status.connected .status-dot {
  background: #16a34a;
}
.page-desc {
  font-size: 14px;
  color: var(--color-mute);
}
.kb-stats-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 12px;
}
.kb-stat-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-mute);
}
.kb-stat-value {
  font-weight: 600;
  font-size: 14px;
  color: var(--color-ink);
}
.kb-stat-label {
  color: var(--color-mute);
}
.kb-stat-divider {
  width: 1px;
  height: 16px;
  background: #ebebeb;
}
.kb-stats-refresh {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--color-mute);
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
}
.kb-stats-refresh:hover:not(:disabled) {
  color: var(--color-primary, #0070f3);
  background: rgba(0, 112, 243, 0.06);
}
.kb-stats-refresh:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 2.5fr) minmax(0, 3fr);
  gap: 15px;
}
.panel {
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  padding: 16px;
  min-width: 0;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.panel-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-ink);
  flex-shrink: 0;
}
.panel-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.doc-search-input {
  width: 180px;
}
.header-actions {
  display: flex;
  gap: 8px;
}
.btn-outline-sm {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
  background: var(--color-canvas);
  color: var(--color-ink);
  border: 1px solid var(--color-hairline);
  border-radius: 100px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}
.btn-outline-sm:hover {
  border-color: var(--color-link);
  color: var(--color-link);
}
.btn-primary-sm {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: 100px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}
.btn-primary-sm:hover:not(:disabled) {
  background: #27272a;
}
.btn-primary-sm:disabled {
  background: var(--color-hairline-strong);
  color: var(--color-mute);
  cursor: not-allowed;
}
:global([data-theme="dark"]) .btn-primary-sm {
  border: 1px solid rgba(255, 255, 255, 0.15);
}
:global([data-theme="dark"]) .btn-primary-sm:hover:not(:disabled) {
  background: #3f3f46;
  border-color: rgba(255, 255, 255, 0.25);
}

.doc-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.doc-list-min {
  min-height: 120px;
}
.doc-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.15s;
  overflow: hidden;
}
.doc-item:hover {
  border-color: var(--color-link);
}
.doc-item :deep(.ant-tooltip-wrapper) {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}
.doc-status-icon {
  font-size: 16px;
  flex-shrink: 0;
  width: 20px;
  text-align: center;
}
.doc-status-icon.uploading { color: #d97706; }
.doc-status-icon.uploaded { color: var(--color-mute); }
.doc-status-icon.pending { color: #2563eb; }
.doc-status-icon.processing { color: #d97706; }
.doc-status-icon.completed { color: #16a34a; }
.doc-status-icon.failed { color: #dc2626; }
.doc-name {
  display: block;
  font-size: 14px;
  line-height: 1.5;
  color: var(--color-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.doc-chunk-count {
  font-size: 12px;
  color: var(--color-mute);
  flex-shrink: 0;
}
.doc-dup-tag {
  font-size: 11px;
  line-height: 18px;
  padding: 0 4px;
  flex-shrink: 0;
}
.doc-meta {
  display: flex;
  align-items: center;
  gap: 2px;
  font-size: 13px;
  color: var(--color-mute);
  flex-shrink: 0;
  margin-left: auto;
}
.doc-icon-btn {
  width: 26px;
  height: 26px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--color-mute);
  font-size: 14px;
  transition: background 0.15s, color 0.15s;
}
.doc-icon-btn:hover {
  background: var(--color-canvas-soft-2);
  color: var(--color-ink);
}
.doc-icon-btn.danger:hover {
  background: var(--color-error-bg);
  color: #dc2626;
}
.doc-empty {
  text-align: center;
  padding: 40px;
  color: var(--color-mute);
}
.doc-pagination {
  display: flex;
  justify-content: center;
  padding: 8px 0;
}

/* RAG & 思维导图共用 */
.rag-section {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 220px);
}
.rag-messages {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-bottom: 12px;
  padding-right: 8px;
}
.rag-msg.user {
  align-self: flex-end;
  background: var(--color-canvas-soft-2);
  padding: 8px 12px;
  border-radius: 8px;
  max-width: 80%;
  font-size: 14px;
}
.rag-msg.assistant {
  align-self: flex-start;
  background: var(--color-info-bg);
  padding: 12px 16px;
  border-radius: 8px;
  max-width: 85%;
  font-size: 14px;
  line-height: 1.6;
}

/* 检索结果 - 文档块卡片 */
.chunk-result-card {
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  padding: 12px 16px;
  transition: border-color 0.15s;
}
.chunk-result-card:hover {
  border-color: var(--color-link);
}
.chunk-result-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
}
.chunk-rank {
  background: #6366f1;
  color: #fff;
  border-radius: 4px;
  padding: 1px 6px;
  font-weight: 600;
  font-size: 12px;
}
.chunk-source {
  color: var(--color-body);
  font-weight: 500;
}
.chunk-score {
  margin-left: auto;
  color: #16a34a;
  font-size: 12px;
  font-weight: 500;
}
.chunk-result-content {
  font-size: 13px;
  color: var(--color-ink);
  line-height: 1.6;
  max-height: 120px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
}
.rag-input {
  display: flex;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid var(--color-hairline);
}
.rag-input input {
  flex: 1;
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 14px;
  outline: none;
  color: var(--color-ink);
  background: var(--color-canvas);
}
.rag-input input:focus {
  border-color: var(--color-ink);
}

/* 示例问题 */
.example-questions {
  padding-top: 10px;
  overflow: hidden;
  max-width: 100%;
}
.example-question-text {
  font-size: 13px;
  color: var(--color-mute);
  cursor: pointer;
  transition: color 0.15s;
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}
.example-question-text:hover {
  color: var(--color-link);
}
.example-questions-hint {
  font-size: 13px;
  color: var(--color-mute);
  padding-top: 10px;
}
.example-questions-hint a {
  color: var(--color-link);
  cursor: pointer;
}
.example-questions-hint a:hover {
  color: #4f46e5;
  text-decoration: underline;
}

/* 编辑弹窗 - 示例问题列表 */
.edit-questions-list {
  width: 100%;
}
.edit-question-item {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}
.edit-question-text {
  flex: 1;
  font-size: 13px;
  color: var(--color-ink);
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.edit-question-text:hover {
  background: var(--color-canvas-soft-2);
}
.edit-question-delete {
  color: var(--color-mute);
  cursor: pointer;
  font-size: 12px;
  flex-shrink: 0;
}
.edit-question-delete:hover {
  color: #dc2626;
}
.edit-question-actions {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}

/* 示例问题轮播过渡 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.4s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* RAG消息中的markdown样式 */
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  margin-top: 12px;
  margin-bottom: 8px;
  font-weight: 600;
}
.markdown-body :deep(p) {
  margin-bottom: 8px;
}
.markdown-body :deep(code) {
  background: rgba(0,0,0,0.06);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
}
.markdown-body :deep(pre) {
  background: rgba(0,0,0,0.04);
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 8px 0;
}
.markdown-body :deep(pre code) {
  background: none;
  padding: 0;
}
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 20px;
  margin-bottom: 8px;
}
.markdown-body :deep(li) {
  margin-bottom: 4px;
}
.markdown-body :deep(strong) {
  font-weight: 600;
}

/* 思维导图 */
.mindmap-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.mindmap-svg {
  flex: 1;
  width: 100%;
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  background: var(--color-canvas);
  --markmap-text-color: var(--color-ink);
  --markmap-circle-open-bg: var(--color-canvas);
  --markmap-code-bg: var(--color-canvas-soft-2);
  --markmap-code-color: var(--color-ink);
}
.mindmap-svg :deep(.markmap-link) {
  stroke: var(--color-mute) !important;
}
.mindmap-svg :deep(.markmap-node > circle) {
  stroke: var(--color-mute) !important;
  fill: var(--color-canvas) !important;
}
.mindmap-actions {
  display: flex;
  justify-content: flex-end;
}
.mindmap-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--color-mute);
}
.mindmap-empty p {
  margin-bottom: 16px;
}

/* 文档弹窗 */
.doc-modal-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}
.doc-modal-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  background: none;
  border: 1px solid transparent;
  border-radius: 6px;
  color: var(--color-body);
  cursor: pointer;
  font-size: 15px;
  transition: all 0.15s;
}
.doc-modal-action-btn:hover {
  background: var(--color-canvas-soft-2);
  border-color: var(--color-hairline);
  color: var(--color-ink);
}
.doc-modal-tabs {
  margin-top: 4px;
}
.doc-modal-tabs :deep(.ant-tabs-nav) {
  margin: 0;
  padding: 8px 24px 0;
  background: var(--color-canvas-soft);
  border-bottom: 1px solid var(--color-hairline);
}
.doc-modal-tabs :deep(.ant-tabs-content-holder) {
  padding: 0;
}
.tab-pane-body {
  height: 520px;
  overflow: auto;
}
.chunk-list-pane {
  height: 520px;
  overflow: hidden;
}
.doc-graph-pane {
  height: 520px;
  overflow: hidden;
}
.doc-info-pane {
  height: 520px;
  overflow: auto;
  padding: 16px 24px;
}
.doc-info-section {
  margin-bottom: 20px;
}
.doc-info-section:last-child {
  margin-bottom: 0;
}
.doc-info-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-ink);
  margin-bottom: 10px;
}
.doc-info-error {
  background: var(--color-error-bg);
  border: 1px solid var(--color-error-soft);
  border-radius: 6px;
  padding: 10px 14px;
  color: #dc2626;
  font-size: 13px;
  white-space: pre-line;
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.ingest-config-display {
  padding: 10px 12px;
  margin-bottom: 10px;
  background: var(--color-canvas-soft);
  border: 1px solid var(--color-border-slate);
  border-radius: 6px;
}
.config-title {
  font-size: 12px;
  color: var(--color-mute);
  margin-bottom: 6px;
  font-weight: 500;
}
.config-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.text-content-preview {
  padding: 20px 28px 20px 24px;
}
.plain-text {
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  color: var(--color-ink);
}
.markdown-content {
  font-size: 14px;
  line-height: 1.8;
  color: var(--color-ink);
}
.markdown-content h1 {
  font-size: 20px;
  font-weight: 700;
  margin: 24px 0 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--color-hairline);
}
.markdown-content h2 {
  font-size: 17px;
  font-weight: 600;
  margin: 20px 0 10px;
}
.markdown-content h3,
.markdown-content h4 {
  font-size: 15px;
  font-weight: 600;
  margin: 16px 0 8px;
}
.markdown-content p {
  margin: 0 0 12px;
}
.markdown-content code {
  background: var(--color-canvas-soft-2);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  color: #e11d48;
}
.markdown-content pre {
  background: #f8f9fa;
  padding: 14px 16px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 12px 0;
  border: 1px solid var(--color-hairline);
}
.markdown-content pre code {
  background: none;
  padding: 0;
  color: inherit;
}
.markdown-content ul,
.markdown-content ol {
  padding-left: 24px;
  margin: 0 0 12px;
}
.markdown-content li {
  margin-bottom: 4px;
}
.markdown-content blockquote {
  border-left: 3px solid #a1a1aa;
  padding: 4px 0 4px 16px;
  margin: 12px 0;
  color: var(--color-mute);
  background: var(--color-canvas-soft);
}
.markdown-content table {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
  font-size: 13px;
}
.markdown-content th,
.markdown-content td {
  border: 1px solid var(--color-hairline);
  padding: 8px 12px;
  text-align: left;
}
.markdown-content th {
  background: #f8f9fa;
  font-weight: 600;
  color: var(--color-ink);
}
.markdown-content tr:hover td {
  background: var(--color-canvas-soft);
}
.markdown-content img {
  max-width: 100%;
  border-radius: 4px;
}
.markdown-content hr {
  border: none;
  border-top: 1px solid var(--color-hairline);
  margin: 16px 0;
}
.error-message {
  background: var(--color-error-bg);
  border-bottom: 1px solid #fecaca;
  padding: 10px 20px;
  color: #dc2626;
  margin-right: 10px;
  font-size: 13px;
  white-space: pre-line;
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.modal-empty {
  text-align: center;
  padding: 40px;
  color: var(--color-mute);
}

/* 分块列表 */
.chunk-list {
  height: 500px;
  overflow-y: auto;
  overflow-x: hidden;
  padding-right: 8px;
}
.chunk-list::-webkit-scrollbar {
  width: 6px;
}
.chunk-list::-webkit-scrollbar-thumb {
  background: #d4d4d8;
  border-radius: 3px;
}
.chunk-list::-webkit-scrollbar-thumb:hover {
  background: #a1a1aa;
}
.chunk-item {
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  background: var(--color-canvas);
  cursor: pointer;
  margin-bottom: 8px;
  overflow: hidden;
}
.chunk-item:hover {
  border-color: var(--color-link);
}
.chunk-header {
  height: 30px;
  line-height: 30px;
  padding: 0 12px;
  background: var(--color-canvas-soft);
  border-bottom: 1px solid var(--color-hairline);
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.chunk-index {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-ink);
}
.chunk-meta {
  font-size: 12px;
  color: var(--color-mute);
}
.chunk-preview {
  height: 50px;
  line-height: 20px;
  padding: 5px 12px;
  font-size: 13px;
  color: var(--color-body);
  overflow: hidden;
}

/* 分块详情弹窗 */
.chunk-detail-meta {
  font-size: 12px;
  color: var(--color-mute);
  margin-bottom: 12px;
}
.chunk-detail-content {
  background: var(--color-canvas-soft-2);
  padding: 16px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  max-height: 500px;
  overflow-y: auto;
}

/* 成员管理 */
.members-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.member-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.member-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
}
.member-info {
  display: flex;
  align-items: center;
  gap: 10px;
}
.member-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #0070f3;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
  overflow: hidden;
}
.member-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.member-detail {
  display: flex;
  flex-direction: column;
}
.member-name {
  font-size: 14px;
  color: var(--color-ink);
  font-weight: 500;
}
.member-id {
  font-size: 12px;
  color: var(--color-mute);
}
.member-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.btn-icon-sm {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-mute);
  font-size: 12px;
}
.btn-icon-sm:hover {
  background: var(--color-canvas-soft-2);
}
.btn-icon-sm.danger:hover {
  color: var(--color-error);
  background: var(--color-error-soft);
}
.add-member-form {
  display: flex;
  gap: 8px;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid var(--color-hairline);
}
.empty-tip {
  text-align: center;
  padding: 24px;
  color: var(--color-mute);
  font-size: 13px;
}

/* 权限说明 */
.perm-help p { margin-bottom: 12px; font-size: 13px; color: var(--color-body); line-height: 1.6; }
.perm-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.perm-table th, .perm-table td {
  border: 1px solid var(--color-hairline);
  padding: 6px 8px;
  text-align: center;
}
.perm-table th {
  background: var(--color-canvas-soft);
  font-weight: 600;
  color: var(--color-ink);
}
.perm-table td:first-child {
  text-align: left;
  color: var(--color-body);
}
.perm-table .yes { color: #52c41a; font-weight: 600; }
.perm-table .no { color: #d9d9d9; }

/* 上传弹窗 */
.upload-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.upload-form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.upload-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-ink);
}
.upload-dropzone {
  border: 2px dashed #d4d4d8;
  border-radius: 8px;
  padding: 32px;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.2s;
}
.upload-dropzone:hover {
  border-color: var(--color-link);
}
.dropzone-text {
  font-size: 14px;
  color: var(--color-body);
  margin-bottom: 4px;
}
.dropzone-hint {
  font-size: 12px;
  color: var(--color-mute);
}
.upload-file-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 200px;
  overflow-y: auto;
}
.upload-file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
}
.upload-file-name {
  flex: 1;
  font-size: 13px;
  color: var(--color-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.upload-file-status {
  font-size: 12px;
  color: var(--color-mute);
}
.upload-file-status.uploading {
  color: #d97706;
}
.upload-file-status.success {
  color: #16a34a;
}
.upload-file-status.error {
  color: #dc2626;
}

/* OCR 开关 */
.ocr-section {
  background: var(--color-canvas-soft);
  border-radius: 8px;
  padding: 12px;
}
.ocr-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ocr-label {
  font-size: 13px;
  color: var(--color-body);
}
.ocr-status {
  margin-top: 8px;
}
.ocr-status-text {
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.ocr-status-text.success {
  color: #16a34a;
}
.ocr-status-text.error {
  color: #dc2626;
}
.ocr-status-text.checking {
  color: #d97706;
}
.ocr-model-info {
  margin-top: 4px;
  padding-left: 18px;
  font-size: 12px;
  white-space: pre-line;
  line-height: 1.6;
}

.upload-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--color-hairline);
}

/* 上传模式切换 */
.upload-mode-switch {
  margin-bottom: 12px;
}

/* URL 输入区域 */
.url-input-area {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.url-textarea {
  width: 100%;
}
.url-input-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.url-hint {
  font-size: 12px;
  color: var(--color-mute);
}
.url-advanced-collapse {
  margin-top: 4px;
}
.url-advanced-collapse :deep(.ant-collapse-header) {
  font-size: 13px !important;
  color: var(--color-mute) !important;
  padding: 4px 0 !important;
}
.url-header-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.url-header-delete {
  cursor: pointer;
  color: var(--color-mute);
  transition: color 0.15s;
}
.url-header-delete:hover {
  color: #ef4444;
}

/* 文档同步信息 */
.doc-sync-info {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: 6px;
  font-size: 12px;
  color: var(--color-mute);
}
.doc-sync-info .ant-tag {
  font-size: 11px;
  line-height: 18px;
  padding: 0 5px;
  margin: 0;
}

/* URL 列表 */
.url-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 300px;
  overflow-y: auto;
  margin-top: 8px;
}
.url-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  background: var(--color-canvas-soft);
  transition: border-color 0.15s, background 0.15s;
}
.url-item:hover {
  border-color: var(--color-hairline);
  background: var(--color-canvas);
}
.url-status-col {
  flex-shrink: 0;
  width: 18px;
  padding-top: 2px;
}
.url-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.url-row-top {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.url-link {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  color: var(--color-ink);
  text-decoration: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}
.url-link:hover {
  color: #2563eb;
  text-decoration: underline;
}
.url-toolbar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 2px;
}
.url-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--color-mute);
  font-size: 14px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.url-icon-btn:hover {
  background: var(--color-canvas-soft-2);
  color: #2563eb;
}
.url-icon-btn.danger:hover {
  background: var(--color-error-bg);
  color: #dc2626;
}
.url-row-bottom {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.url-doc-title {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 500;
  color: #18181b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.url-char-badge {
  flex-shrink: 0;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 500;
  line-height: 1.4;
  color: var(--color-body);
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 999px;
}
.url-status-text {
  font-size: 12px;
  color: var(--color-mute);
}
.url-preview-modal {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.url-preview-source {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--color-mute);
  word-break: break-all;
}
.url-preview-desc {
  margin: 0;
  font-size: 13px;
  color: var(--color-body);
  background: var(--color-canvas-soft);
  padding: 8px 10px;
  border-radius: 6px;
}
.url-preview-html {
  max-height: 480px;
  overflow: auto;
  padding: 12px;
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  background: var(--color-canvas);
  font-size: 14px;
  line-height: 1.7;
}
.url-preview-html :deep(img) {
  max-width: 100%;
  height: auto;
}
.url-preview-html :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 8px 0;
}
.url-preview-html :deep(th),
.url-preview-html :deep(td) {
  border: 1px solid var(--color-hairline);
  padding: 6px 8px;
}
.url-preview-text {
  max-height: 480px;
  overflow: auto;
  margin: 0;
  padding: 12px;
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  background: var(--color-canvas-soft);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.url-preview-empty {
  padding: 24px;
  text-align: center;
  color: var(--color-mute);
  font-size: 13px;
}
.url-icon {
  font-size: 14px;
}
.url-icon.success {
  color: #16a34a;
}
.url-icon.error {
  color: #dc2626;
}
.url-icon.spinning {
  color: #d97706;
}
.url-error {
  font-size: 12px;
  color: #dc2626;
  line-height: 1.5;
  word-break: break-word;
}
.url-empty-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  background: var(--color-canvas-soft);
  border: 1px dashed #d4d4d8;
  border-radius: 8px;
  color: var(--color-mute);
  font-size: 13px;
  margin-top: 8px;
}

.form-hint {
  font-size: 12px;
  color: var(--color-mute);
  margin-top: 2px;
}

/* 入库弹窗 */
.ingest-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.default-strategy-hint {
  margin-top: -8px;
}
.preview-chunks {
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  overflow: hidden;
}
.preview-header {
  background: var(--color-canvas-soft);
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-body);
  border-bottom: 1px solid var(--color-hairline);
}
.preview-list {
  max-height: 300px;
  overflow-y: auto;
}
.preview-item {
  padding: 8px 12px;
  border-bottom: 1px solid #f5f5f5;
}
.preview-item:last-child {
  border-bottom: none;
}
.preview-item-header {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-mute);
  margin-bottom: 4px;
}
.preview-item-content {
  font-size: 13px;
  color: var(--color-body);
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}
.ingest-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--color-hairline);
}
.chunk-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 邀请弹窗 */
.invite-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.invite-results {
  max-height: 360px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.invite-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
}
.invite-user {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}
.invite-info {
  display: flex;
  flex-direction: column;
}
.invite-name {
  font-size: 14px;
  color: var(--color-ink);
  font-weight: 500;
}
.invite-username {
  font-size: 12px;
  color: var(--color-mute);
}

.qa-disabled-mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(2px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
  border-radius: 8px;
}
.qa-disabled-content {
  text-align: center;
  color: var(--color-mute);
}
.qa-disabled-content p {
  font-size: 15px;
  margin-bottom: 12px;
}
.dup-detect-body {
  margin-bottom: 20px;
}
.dup-detect-body p {
  margin-bottom: 8px;
  font-size: 14px;
  line-height: 1.6;
}
.dup-detect-hint {
  color: var(--color-mute);
  font-size: 13px;
}
.dup-detect-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
