package com.lightbot.common;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 全局异常处理。
 * <p>对 SSE（Accept: text/event-stream）请求直接写响应体，避免
 * {@code ResponseEntity&lt;Result&gt;} 内容协商失败导致业务错误无法回传。</p>
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ObjectMapper objectMapper;

    @ExceptionHandler(NotLoginException.class)
    public void handleNotLogin(NotLoginException e, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        log.info("未登录访问: {}", e.getMessage());
        writeError(request, response, HttpStatus.UNAUTHORIZED.value(), Result.fail(401, "未登录或登录已过期"));
    }

    @ExceptionHandler(NotRoleException.class)
    public void handleNotRole(NotRoleException e, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        log.warn("角色校验失败: {}", e.getMessage());
        writeError(request, response, HttpStatus.FORBIDDEN.value(),
                Result.fail(403, "无权访问，需要" + e.getRole() + "角色"));
    }

    @ExceptionHandler(NotPermissionException.class)
    public void handleNotPermission(NotPermissionException e, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        log.warn("权限校验失败: {}", e.getMessage());
        writeError(request, response, HttpStatus.FORBIDDEN.value(), Result.fail(403, "无权访问"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public void handleValidation(MethodArgumentNotValidException e, HttpServletRequest request,
                                 HttpServletResponse response) throws IOException {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst().orElse("参数校验失败");
        log.info("参数校验失败: {}", message);
        writeError(request, response, HttpStatus.BAD_REQUEST.value(), Result.fail(400, message));
    }

    @ExceptionHandler(org.springframework.validation.BindException.class)
    public void handleBind(org.springframework.validation.BindException e, HttpServletRequest request,
                           HttpServletResponse response) throws IOException {
        String message = e.getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst().orElse("参数绑定失败");
        log.info("参数绑定失败: {}", message);
        writeError(request, response, HttpStatus.BAD_REQUEST.value(), Result.fail(400, message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public void handleConstraint(ConstraintViolationException e, HttpServletRequest request,
                                 HttpServletResponse response) throws IOException {
        log.info("约束校验失败: {}", e.getMessage());
        writeError(request, response, HttpStatus.BAD_REQUEST.value(), Result.fail(400, e.getMessage()));
    }

    @ExceptionHandler(BizException.class)
    public void handleBiz(BizException e, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        writeError(request, response, e.getHttpStatus().value(), Result.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public void handleNotReadable(org.springframework.http.converter.HttpMessageNotReadableException e,
                                  HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.info("请求体解析失败: {}", e.getMessage());
        writeError(request, response, HttpStatus.BAD_REQUEST.value(), Result.fail(400, "请求体格式错误"));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public void handleNoHandler(NoHandlerFoundException e, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        log.info("接口不存在: {} {}", e.getHttpMethod(), e.getRequestURL());
        writeError(request, response, HttpStatus.NOT_FOUND.value(), Result.fail(404, "接口不存在"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public void handleNoResource(NoResourceFoundException e, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        log.info("资源不存在: {}", e.getMessage());
        writeError(request, response, HttpStatus.NOT_FOUND.value(), Result.fail(404, "资源不存在"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public void handleMethodNotAllowed(HttpRequestMethodNotSupportedException e, HttpServletRequest request,
                                       HttpServletResponse response) throws IOException {
        log.info("请求方法不支持: method={}", e.getMethod());
        writeError(request, response, HttpStatus.METHOD_NOT_ALLOWED.value(), Result.fail(405, "请求方法不支持"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public void handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e, HttpServletRequest request,
                                            HttpServletResponse response) throws IOException {
        log.info("不支持的媒体类型: {}", e.getContentType());
        writeError(request, response, HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                Result.fail(415, "不支持的Content-Type"));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public void handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException e, HttpServletRequest request,
                                             HttpServletResponse response) throws IOException {
        log.warn("内容协商失败: {}", e.getMessage());
        writeError(request, response, HttpStatus.NOT_ACCEPTABLE.value(),
                Result.fail(406, "无法按请求的 Accept 返回响应"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public void handleMaxUploadSize(MaxUploadSizeExceededException e, HttpServletRequest request,
                                    HttpServletResponse response) throws IOException {
        log.info("上传文件过大: {}", e.getMessage());
        writeError(request, response, HttpStatus.BAD_REQUEST.value(), Result.fail(400, "上传文件大小超过限制"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public void handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request,
                                   HttpServletResponse response) throws IOException {
        boolean pathVariable = e.getParameter().hasParameterAnnotation(PathVariable.class);
        if (pathVariable) {
            log.info("路径参数无效: name={}, value={}", e.getName(), e.getValue());
            writeError(request, response, HttpStatus.NOT_FOUND.value(), Result.fail(404, "资源不存在"));
            return;
        }
        log.info("请求参数格式错误: name={}, value={}", e.getName(), e.getValue());
        writeError(request, response, HttpStatus.BAD_REQUEST.value(), Result.fail(400, "请求参数格式错误"));
    }

    /**
     * SSE / DeferredResult 异步超时：连接到期或客户端已断开，属预期收尾，勿记为系统故障。
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncTimeout(AsyncRequestTimeoutException e, HttpServletRequest request,
                                   HttpServletResponse response) {
        String path = request != null ? request.getRequestURI() : "-";
        if (response != null && response.isCommitted()) {
            log.debug("异步请求超时（响应已提交）: path={}", path);
            return;
        }
        if (response == null) {
            log.warn("异步请求超时: path={}", path);
            return;
        }
        log.warn("异步请求超时: path={}", path);
        try {
            // SSE 超时后客户端会自行重连；若仍可写则给一个轻量结束，避免刷「系统内部错误」
            if (isSseRequest(request)) {
                response.setStatus(HttpStatus.OK.value());
                response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write(": timeout\n\n");
                response.getWriter().flush();
                return;
            }
            writeError(request, response, HttpStatus.SERVICE_UNAVAILABLE.value(),
                    Result.fail(503, "请求超时，请重试"));
        } catch (IOException io) {
            log.debug("异步超时写响应失败: path={}, err={}", path, io.getMessage());
        }
    }

    /**
     * 客户端主动断开（关页/刷新/切路由）导致写 SSE 失败，属预期，勿记为系统故障。
     * <p>常见于任务计数心跳：浏览器已断连，服务端仍 send → Broken pipe，
     * Spring 异步错误派发进全局异常处理。</p>
     */
    @ExceptionHandler(IOException.class)
    public void handleIOException(IOException e, HttpServletRequest request, HttpServletResponse response) {
        String path = request != null ? request.getRequestURI() : "-";
        if (isClientAbort(e)) {
            log.debug("客户端已断开: path={}, err={}", path, e.getMessage());
            return;
        }
        // 非断连类 IO：仍按系统异常处理
        log.error("系统异常", e);
        try {
            writeError(request, response, HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    Result.fail(500, "系统内部错误"));
        } catch (IOException ignored) {
            // ignore
        }
    }

    @ExceptionHandler(Exception.class)
    public void handleException(Exception e, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        // 部分容器把断连包成非 IOException 的运行时异常
        if (isClientAbort(e)) {
            String path = request != null ? request.getRequestURI() : "-";
            log.debug("客户端已断开: path={}, err={}", path, e.getMessage());
            return;
        }
        log.error("系统异常", e);
        writeError(request, response, HttpStatus.INTERNAL_SERVER_ERROR.value(), Result.fail(500, "系统内部错误"));
    }

    /**
     * 直接写响应，绕过 Accept 内容协商（SSE 流式接口前置业务异常依赖此路径）。
     */
    private void writeError(HttpServletRequest request, HttpServletResponse response, int httpStatus, Result<?> body)
            throws IOException {
        if (response.isCommitted()) {
            log.debug("响应已提交，跳过错误写入: status={}, body={}", httpStatus, body);
            return;
        }
        response.resetBuffer();
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        String json = objectMapper.writeValueAsString(body);
        if (isSseOnlyAccept(request)) {
            response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            response.getWriter().write("event: error\ndata: " + json + "\n\n");
        } else {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(json);
        }
        response.getWriter().flush();
    }

    private boolean isSseRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    /** Broken pipe / Connection reset / Tomcat ClientAbort 等客户端断连 */
    private boolean isClientAbort(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            String name = cur.getClass().getName();
            if (name.contains("ClientAbortException")) {
                return true;
            }
            String msg = cur.getMessage();
            if (msg != null) {
                String m = msg.toLowerCase();
                if (m.contains("broken pipe")
                        || m.contains("connection reset")
                        || m.contains("connection reset by peer")
                        || m.contains("aborted")
                        || m.contains("你的主机中的软件中止了一个已建立的连接")) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
    }

    /** 客户端只接受 SSE、不接受 JSON 时走 event-stream 错误通道 */
    private boolean isSseOnlyAccept(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        boolean acceptJson = accept == null
                || accept.contains(MediaType.APPLICATION_JSON_VALUE)
                || accept.contains("*/*");
        return accept != null
                && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE)
                && !acceptJson;
    }
}
