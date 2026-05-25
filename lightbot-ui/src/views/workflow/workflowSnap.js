/**
 * 工作流连线预览路径（水平贝塞尔，无磁吸）
 */

/** 拖线预览：水平贝塞尔，与 WorkflowBezierEdge 一致 */
export function getConnectionPath(fromX, fromY, toX, toY) {
  const c1x = fromX + (toX - fromX) * 0.5
  const c1y = fromY
  const c2x = fromX + (toX - fromX) * 0.5
  const c2y = toY
  return {
    d: `M${fromX},${fromY} C ${c1x} ${c1y} ${c2x} ${c2y} ${toX},${toY}`,
    endX: toX,
    endY: toY,
    snapped: false,
    mode: null,
  }
}

/** @deprecated */
export function getSnappedConnectionPath(fromX, fromY, toX, toY) {
  return getConnectionPath(fromX, fromY, toX, toY)
}
