#!/usr/bin/env bash
# LightBot 后端启停脚本
# 用法: ./start.sh {start|stop|restart|status}

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="${ROOT_DIR}/lightbot-server"
RUN_DIR="${ROOT_DIR}/.run"
LOG_DIR="${ROOT_DIR}/logs"
PID_FILE="${RUN_DIR}/lightbot-server.pid"
LOG_FILE="${LOG_DIR}/lightbot-server.log"

# 从父 pom 读取版本；失败则回退
APP_VERSION="$(
  sed -n 's/.*<version>\([^<]*\)<\/version>.*/\1/p' "${ROOT_DIR}/pom.xml" 2>/dev/null | head -1
)"
APP_VERSION="${APP_VERSION:-2.1.0}"
JAR_FILE="${SERVER_DIR}/target/lightbot-server-${APP_VERSION}.jar"

# 可选：JAVA_OPTS / SPRING_PROFILES_ACTIVE
JAVA_OPTS="${JAVA_OPTS:-}"
SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-}"

mkdir -p "${RUN_DIR}" "${LOG_DIR}"

usage() {
  cat <<EOF
用法: $(basename "$0") {start|stop|restart|status}

  start    根目录 mvn clean install -DskipTests 后启动后端
  stop     停止后端
  restart  停止 → 重新 clean install → 启动
  status   查看运行状态

环境变量（可选）:
  JAVA_OPTS               传给 JVM，例如: -Xms512m -Xmx2g
  SPRING_PROFILES_ACTIVE  Spring Profile，例如: local
EOF
}

is_running() {
  local pid
  if [[ -f "${PID_FILE}" ]]; then
    pid="$(cat "${PID_FILE}" 2>/dev/null || true)"
    if [[ -n "${pid}" ]] && kill -0 "${pid}" 2>/dev/null; then
      return 0
    fi
  fi
  # 兼容非本脚本拉起的进程
  if pgrep -f "lightbot-server-${APP_VERSION}\\.jar" >/dev/null 2>&1; then
    return 0
  fi
  return 1
}

resolve_pid() {
  if [[ -f "${PID_FILE}" ]]; then
    local pid
    pid="$(cat "${PID_FILE}" 2>/dev/null || true)"
    if [[ -n "${pid}" ]] && kill -0 "${pid}" 2>/dev/null; then
      echo "${pid}"
      return 0
    fi
  fi
  pgrep -f "lightbot-server-${APP_VERSION}\\.jar" | head -1 || true
}

build_all() {
  echo "[LightBot] 根目录执行: mvn clean install -DskipTests"
  (cd "${ROOT_DIR}" && mvn clean install -DskipTests)
  if [[ ! -f "${JAR_FILE}" ]]; then
    echo "[LightBot] 构建失败：未找到 ${JAR_FILE}" >&2
    exit 1
  fi
  echo "[LightBot] 构建完成: ${JAR_FILE}"
}

cmd_start() {
  if is_running; then
    echo "[LightBot] 已在运行 (pid=$(resolve_pid))，请先 stop / restart"
    return 0
  fi

  build_all

  echo "[LightBot] 启动中..."
  echo "[LightBot] jar=${JAR_FILE}"
  echo "[LightBot] log=${LOG_FILE}"

  # set -u 下空数组 "${args[@]}" 会报 unbound variable，改为条件拼参
  local profile_arg=()
  if [[ -n "${SPRING_PROFILES_ACTIVE}" ]]; then
    profile_arg=(--spring.profiles.active="${SPRING_PROFILES_ACTIVE}")
  fi

  # shellcheck disable=SC2086
  nohup java ${JAVA_OPTS} -jar "${JAR_FILE}" ${profile_arg[@]+"${profile_arg[@]}"} \
    >>"${LOG_FILE}" 2>&1 &
  local pid=$!
  echo "${pid}" >"${PID_FILE}"

  sleep 1
  if kill -0 "${pid}" 2>/dev/null; then
    echo "[LightBot] 启动成功 (pid=${pid})"
    echo "[LightBot] 默认地址: http://localhost:8081"
  else
    echo "[LightBot] 启动失败，请查看日志: ${LOG_FILE}" >&2
    rm -f "${PID_FILE}"
    exit 1
  fi
}

cmd_stop() {
  local pid
  pid="$(resolve_pid)"
  if [[ -z "${pid}" ]]; then
    echo "[LightBot] 未在运行"
    rm -f "${PID_FILE}"
    return 0
  fi

  echo "[LightBot] 停止中 (pid=${pid})..."
  kill "${pid}" 2>/dev/null || true

  local i
  for i in $(seq 1 30); do
    if ! kill -0 "${pid}" 2>/dev/null; then
      break
    fi
    sleep 1
  done

  if kill -0 "${pid}" 2>/dev/null; then
    echo "[LightBot] 优雅停止超时，强制结束..."
    kill -9 "${pid}" 2>/dev/null || true
  fi

  rm -f "${PID_FILE}"
  echo "[LightBot] 已停止"
}

cmd_restart() {
  cmd_stop
  cmd_start
}

cmd_status() {
  if is_running; then
    echo "[LightBot] 运行中 (pid=$(resolve_pid))"
    echo "[LightBot] log=${LOG_FILE}"
  else
    echo "[LightBot] 未在运行"
    exit 1
  fi
}

main() {
  local action="${1:-}"
  case "${action}" in
    start) cmd_start ;;
    stop) cmd_stop ;;
    restart) cmd_restart ;;
    status) cmd_status ;;
    -h|--help|help)
      usage
      ;;
    "")
      usage
      exit 1
      ;;
    *)
      echo "未知命令: ${action}" >&2
      usage
      exit 1
      ;;
  esac
}

main "$@"
