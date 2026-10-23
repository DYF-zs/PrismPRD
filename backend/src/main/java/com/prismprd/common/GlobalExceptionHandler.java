package com.prismprd.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.util.DisconnectedClientHelper;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadSize(Exception e) {
        return error(413, "上传文件不能超过 10 MiB");
    }

    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(Exception e) {
        return error(400, "缺少上传文件");
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException e) {
        HttpStatus status = HttpStatus.resolve(e.getCode());
        if (status == null) {
            status = HttpStatus.BAD_REQUEST;
        }
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .orElse("请求参数不合法");
        return error(400, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof RequestBodyLimits.TooLargeException) {
                return error(413, "请求体不能超过 2 MiB");
            }
        }
        return error(400, "请求体格式错误");
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse<Void>> handleParameter(Exception e) {
        return error(400, "请求参数类型或格式错误");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethod(Exception e) {
        return error(405, "请求方法不支持");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaType(Exception e) {
        return error(415, "请使用 application/json 请求体");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingResource(Exception e) {
        return error(404, "接口不存在");
    }

    @ExceptionHandler(TaskRejectedException.class)
    public ResponseEntity<ApiResponse<Void>> handleOverload(Exception e) {
        return error(503, "任务队列已满，请稍后重试");
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException e) {
        return error(404, e.getMessage());
    }

    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handlePermission(PermissionDeniedException e) {
        return error(403, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException e) {
        return error(409, e.getMessage());
    }

    @ExceptionHandler({AsyncRequestTimeoutException.class, AsyncRequestNotUsableException.class})
    public void handleStreamDisconnect(Exception e, HttpServletResponse response) {
        // A disconnected or expired stream cannot carry another JSON response.
        if (!response.isCommitted()) response.setStatus(503);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception e) {
        log.error("unexpected error", e);
        return error(500, "服务内部错误");
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiResponse<Void>> handleIo(IOException e, HttpServletResponse response) {
        // Some servlet containers dispatch a raw IOException after an SSE client closes.
        // A committed response cannot be replaced with JSON; unrelated I/O failures remain errors.
        if (response.isCommitted() || DisconnectedClientHelper.isClientDisconnectedException(e)) {
            log.debug("response connection closed", e);
            return null;
        }
        return handleGeneric(e);
    }

    private ResponseEntity<ApiResponse<Void>> error(int status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(ApiResponse.error(status, message));
    }
}
