package com.example.tutorconnect.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  Object missing(Exception e, HttpServletRequest r) {
    return error(HttpStatus.NOT_FOUND, "Page or endpoint not found.", r);
  }

  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  Object method(Exception e, HttpServletRequest r) {
    return error(HttpStatus.METHOD_NOT_ALLOWED, "This request method is not supported.", r);
  }

  @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
  Object malformed(Exception e, HttpServletRequest r) {
    return error(HttpStatus.BAD_REQUEST, "Invalid request body.", r);
  }

  @ExceptionHandler(AppException.class)
  Object application(AppException e, HttpServletRequest r) {
    return error(e.status(), e.getMessage(), r);
  }

  @ExceptionHandler({
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class,
    BindException.class,
    IllegalArgumentException.class
  })
  Object invalid(Exception e, HttpServletRequest r) {
    return error(HttpStatus.BAD_REQUEST, "Please check the submitted values.", r);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  Object conflict(Exception e, HttpServletRequest r) {
    return error(HttpStatus.CONFLICT, "This change conflicts with an existing record.", r);
  }

  @ExceptionHandler(org.springframework.dao.PessimisticLockingFailureException.class)
  Object busy(Exception e, HttpServletRequest r) {
    return error(
        HttpStatus.SERVICE_UNAVAILABLE, "The database is busy. Please try again shortly.", r);
  }

  @ExceptionHandler(Exception.class)
  Object unexpected(Exception e, HttpServletRequest r) {
    log.error("Unhandled request failure on {}", r.getRequestURI(), e);
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to complete this request.", r);
  }

  private Object error(HttpStatus status, String message, HttpServletRequest r) {
    if (r.getRequestURI().startsWith("/api/"))
      return ResponseEntity.status(status)
          .body(Map.of("status", status.value(), "message", message));
    var view = new ModelAndView("error");
    view.setStatus(status);
    view.addObject("status", status.value());
    view.addObject("message", message);
    return view;
  }
}
