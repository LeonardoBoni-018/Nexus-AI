package com.nexusai.common.exception;

import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Respostas de erro padronizadas (RFC 9457 Problem Details), sem stack traces. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ProblemDetail api(ApiException e) {
        return problem(e.getStatus(), e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dados invalidos");
        pd.setProperty("fields", fields);
        return pd;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraint(ConstraintViolationException e) {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Parametros invalidos");
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class
    })
    ProblemDetail malformed(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Requisicao malformada ou parametro invalido");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail denied(AccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, "FORBIDDEN", "Acesso negado");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail locking(OptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, "CONCURRENT_UPDATE", "Registro alterado por outra operacao; tente novamente");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Erro inesperado", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Erro interno do servidor");
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.getReasonPhrase());
        pd.setProperty("code", code);
        return pd;
    }
}
