package com.company.procurement.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Central translation of exceptions into RFC 7807 problem responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final ProblemDetailFactory problemDetailFactory;

  public GlobalExceptionHandler(ProblemDetailFactory problemDetailFactory) {
    this.problemDetailFactory = problemDetailFactory;
  }

  @ExceptionHandler(DomainException.class)
  public ProblemDetail handleDomainException(DomainException ex, HttpServletRequest request) {
    HttpStatus status = statusFor(ex);
    if (status.is5xxServerError()) {
      log.error("Domain failure code={} path={}", ex.getCode(), request.getRequestURI(), ex);
    } else {
      log.debug("Business failure code={} path={}", ex.getCode(), request.getRequestURI());
    }
    return problemDetailFactory.create(status, ex.getCode(), ex.getMessage(), request, List.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleBeanValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    List<ValidationError> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(GlobalExceptionHandler::toValidationError)
            .sorted(Comparator.comparing(ValidationError::field))
            .toList();
    return problemDetailFactory.create(
        HttpStatus.BAD_REQUEST,
        ErrorCodes.VALIDATION_FAILED,
        "Request validation failed",
        request,
        errors);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  public ProblemDetail handleMethodValidation(
      HandlerMethodValidationException ex, HttpServletRequest request) {
    List<ValidationError> errors =
        ex.getParameterValidationResults().stream()
            .flatMap(
                result ->
                    result.getResolvableErrors().stream()
                        .map(
                            error ->
                                new ValidationError(
                                    result.getMethodParameter().getParameterName(),
                                    error.getDefaultMessage())))
            .toList();
    return problemDetailFactory.create(
        HttpStatus.BAD_REQUEST,
        ErrorCodes.VALIDATION_FAILED,
        "Request validation failed",
        request,
        errors);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ProblemDetail handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest request) {
    List<ValidationError> errors =
        ex.getConstraintViolations().stream()
            .map(
                violation ->
                    new ValidationError(
                        violation.getPropertyPath().toString(), violation.getMessage()))
            .toList();
    return problemDetailFactory.create(
        HttpStatus.BAD_REQUEST,
        ErrorCodes.VALIDATION_FAILED,
        "Request validation failed",
        request,
        errors);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ProblemDetail handleTypeMismatch(
      MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
    return problemDetailFactory.create(
        HttpStatus.BAD_REQUEST,
        ErrorCodes.VALIDATION_FAILED,
        "Invalid value for parameter '%s'".formatted(ex.getName()),
        request,
        List.of(new ValidationError(ex.getName(), "has an invalid type")));
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ProblemDetail handleMissingParameter(
      MissingServletRequestParameterException ex, HttpServletRequest request) {
    return problemDetailFactory.create(
        HttpStatus.BAD_REQUEST,
        ErrorCodes.VALIDATION_FAILED,
        "Missing required parameter '%s'".formatted(ex.getParameterName()),
        request,
        List.of(new ValidationError(ex.getParameterName(), "is required")));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ProblemDetail handleUnreadableBody(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    log.debug("Malformed request body path={}", request.getRequestURI());
    return problemDetailFactory.create(
        HttpStatus.BAD_REQUEST,
        ErrorCodes.MALFORMED_REQUEST,
        "Request body is missing or malformed",
        request,
        List.of());
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ProblemDetail handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
    return problemDetailFactory.create(
        HttpStatus.NOT_FOUND,
        ErrorCodes.RESOURCE_NOT_FOUND,
        "Resource not found",
        request,
        List.of());
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
    log.warn("Access denied path={}", request.getRequestURI());
    return problemDetailFactory.create(
        HttpStatus.FORBIDDEN,
        ErrorCodes.FORBIDDEN,
        "You do not have permission to perform this action",
        request,
        List.of());
  }

  @ExceptionHandler(AuthenticationException.class)
  public ProblemDetail handleAuthentication(
      AuthenticationException ex, HttpServletRequest request) {
    return problemDetailFactory.create(
        HttpStatus.UNAUTHORIZED,
        ErrorCodes.UNAUTHORIZED,
        "Authentication is required",
        request,
        List.of());
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ProblemDetail handleDataIntegrity(
      DataIntegrityViolationException ex, HttpServletRequest request) {
    log.warn("Data integrity violation path={}", request.getRequestURI());
    return problemDetailFactory.create(
        HttpStatus.CONFLICT,
        ErrorCodes.CONFLICT,
        "Request conflicts with existing data",
        request,
        List.of());
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ProblemDetail handleOptimisticLock(
      OptimisticLockingFailureException ex, HttpServletRequest request) {
    log.warn("Optimistic lock failure path={}", request.getRequestURI());
    return problemDetailFactory.create(
        HttpStatus.CONFLICT,
        ErrorCodes.CONFLICT,
        "The resource was modified by another request; reload and retry",
        request,
        List.of());
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
    log.error("Unhandled exception path={}", request.getRequestURI(), ex);
    return problemDetailFactory.create(
        HttpStatus.INTERNAL_SERVER_ERROR,
        ErrorCodes.INTERNAL_ERROR,
        "An unexpected error occurred",
        request,
        List.of());
  }

  private static HttpStatus statusFor(DomainException ex) {
    if (ex instanceof FeatureNotEnabledException) {
      return HttpStatus.FORBIDDEN;
    }
    if (ex instanceof ResourceNotFoundException) {
      return HttpStatus.NOT_FOUND;
    }
    if (ex instanceof BadRequestException) {
      return HttpStatus.BAD_REQUEST;
    }
    if (ex instanceof PreconditionRequiredException) {
      return HttpStatus.PRECONDITION_REQUIRED;
    }
    if (ex instanceof PreconditionFailedException) {
      return HttpStatus.PRECONDITION_FAILED;
    }
    if (ex instanceof com.company.procurement.shared.storage.FileStorageException) {
      return HttpStatus.INTERNAL_SERVER_ERROR;
    }
    if (ex instanceof IllegalStateTransitionException || ex instanceof ConflictException) {
      return HttpStatus.CONFLICT;
    }
    if (ex instanceof BusinessRuleException) {
      return HttpStatus.UNPROCESSABLE_ENTITY;
    }
    return HttpStatus.UNPROCESSABLE_ENTITY;
  }

  private static ValidationError toValidationError(FieldError fieldError) {
    return new ValidationError(fieldError.getField(), fieldError.getDefaultMessage());
  }
}
