/**
 * 工具结果详情弹窗统一配置
 * 避免 ant-modal-wrap 默认 overflow:auto 在视口边缘产生“页面级”滚动条
 */

/** @returns {HTMLElement} */
export function getToolResultModalContainer() {
  return document.body
}

export const toolResultModalWrapStyle = {
  overflow: 'hidden',
}

/**
 * @param {Record<string, string>} [extra]
 * @returns {Record<string, string>}
 */
export function buildToolResultModalBodyStyle(extra = {}) {
  return {
    maxHeight: 'calc(100vh - 180px)',
    overflowY: 'auto',
    overflowX: 'hidden',
    ...extra,
  }
}
