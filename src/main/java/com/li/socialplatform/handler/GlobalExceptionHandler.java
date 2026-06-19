package com.li.socialplatform.handler;

import com.li.socialplatform.common.constant.MessageConstant;
import com.li.socialplatform.common.exception.BizException;
import com.li.socialplatform.pojo.entity.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * @author e69d8e
 * @since 2025/12/8 16:03
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler  {
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result> handleBizException(BizException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Result.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Result.error(message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .findFirst()
                .orElse("参数校验失败");
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Result.error(message));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Result> handleMissingServletRequestPart(MissingServletRequestPartException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Result.error("缺少必要的文件参数"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result> handleMissingServletRequestParameter(MissingServletRequestParameterException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Result.error("缺少必要的请求参数: " + e.getParameterName()));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Result> handleMultipartException(MultipartException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Result.error("文件上传异常"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result> handleNoResourceFound(NoResourceFoundException e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Result.error("资源不存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result> handleException(Exception e) {
        log.error("服务器异常", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(MessageConstant.EXCEPTION));
    }
}
