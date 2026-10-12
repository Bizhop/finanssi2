package fi.bizhop.finanssi2.web;

import fi.bizhop.finanssi2.error.ApiException;
import fi.bizhop.finanssi2.error.CodedError;
import fi.bizhop.finanssi2.error.ErrorCode;
import fi.bizhop.finanssi2.game.engine.NotYourTurn;
import fi.bizhop.finanssi2.game.engine.RuleViolation;
import fi.bizhop.finanssi2.game.service.GameNotFoundException;
import fi.bizhop.finanssi2.game.service.NotAllowedException;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.NoSuchElementException;
import org.jspecify.annotations.Nullable;

/**
 * The one error format of the API: every failure is a ProblemDetail with a stable {@code code} (see {@link ErrorCode}) and, where the
 * wording needs values, {@code params}. The frontend words the error by code; {@code detail} is the English fallback.
 * The base class renders Spring's own exceptions (including ResponseStatusException) the same way.
 */
@RestControllerAdvice
public class ApiErrors extends ResponseEntityExceptionHandler {
    private static ProblemDetail problem(HttpStatus status, @Nullable String detail, ErrorCode code) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code.name());
        return problem;
    }

    private static ProblemDetail problem(HttpStatus status, RuntimeException e) {
        var coded = (CodedError) e;
        var problem = problem(status, e.getMessage(), coded.code());
        if (!coded.params().isEmpty()) problem.setProperty("params", coded.params());
        return problem;
    }

    /** Spring's own exceptions get a generic code, so every error body has one */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(@NonNull Exception e, @Nullable Object body, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode statusCode, @NonNull WebRequest request) {
        var response = super.handleExceptionInternal(e, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem && (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
            problem.setProperty("code", (statusCode.value() == 404 ? ErrorCode.NOT_FOUND : ErrorCode.INVALID_REQUEST).name());
        }
        return response;
    }

    /** Only request bodies that fail to parse; other Jackson failures are server errors */
    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(@NonNull HttpMessageNotReadableException e, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        var problem = problem(HttpStatus.BAD_REQUEST, "Invalid request body or unexpected fields", ErrorCode.INVALID_BODY);
        return handleExceptionInternal(e, problem, headers, status, request);
    }

    @ExceptionHandler(ApiException.class)
    ProblemDetail api(ApiException e) {
        return problem(e.status(), e);
    }

    @ExceptionHandler(GameNotFoundException.class)
    ProblemDetail notFound(GameNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail missing() {
        return problem(HttpStatus.NOT_FOUND, "Not found", ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidArgument() {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler({NotAllowedException.class, NotYourTurn.class})
    ProblemDetail notAllowed(RuntimeException e) {
        return problem(HttpStatus.FORBIDDEN, e);
    }

    @ExceptionHandler(RuleViolation.class)
    ProblemDetail ruleViolation(RuleViolation e) {
        return problem(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrentChange() {
        return problem(HttpStatus.CONFLICT, "The game was changed at the same time; reload and try again", ErrorCode.CONCURRENT_CHANGE);
    }
}
