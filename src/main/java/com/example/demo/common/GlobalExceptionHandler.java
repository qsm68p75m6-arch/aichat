package com.example.demo.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log= LoggerFactory.getLogger(GlobalExceptionHandler.class);
    //业务异常
    @ExceptionHandler(BusinessException.class)
    public  Result handleBusinessException(BusinessException e){
        return Result.fail(e.getMessage());
    }

    // 🟢 参数绑定异常（如 ?page=abc）
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public Result handleTypeMismatchException(Exception e) {
        return Result.fail("参数类型错误");
    }

    // 🟡 参数校验异常
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result handleValidException(MethodArgumentNotValidException e) {

        List<FieldError> errors = e.getBindingResult().getFieldErrors();

        String message = errors.isEmpty()
                ? "参数错误"
                : errors.get(0).getDefaultMessage();

        return Result.fail(message);
    }

    // 🔵 数据库/SQL 异常 — 返回友好提示，不暴露SQL细节
    @ExceptionHandler(DataAccessException.class)
    public Result handleDataAccessException(DataAccessException e) {
       log.error("数据库操作异常",e);// 后台打印完整日志（方便开发调试）
        return Result.fail("数据操作失败，请检查参数是否合法");
    }

    // 🔴 其他未知异常 — 只显示简单提示
    @ExceptionHandler(Exception.class)
    public Result handleSystemException(Exception e) {
       log.error("系统未知异常",e); // 打印日志（开发用）
        return Result.fail("系统异常，请稍后重试");
    }

}
