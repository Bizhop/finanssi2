package fi.bizhop.finanssi2.web;

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
 * The one error format of the API: every failure is a ProblemDetail whose {@code detail} is what the frontend shows.
 * The base class renders Spring's own exceptions (including ResponseStatusException) the same way.
 */
@RestControllerAdvice
public class ApiErrors extends ResponseEntityExceptionHandler {
    /** Only request bodies that fail to parse; other Jackson failures are server errors */
    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(@NonNull HttpMessageNotReadableException e, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request body or unexpected fields");
        return handleExceptionInternal(e, problem, headers, status, request);
    }

    @ExceptionHandler(GameNotFoundException.class)
    ProblemDetail notFound(GameNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail missing() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Not found");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidArgument() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request");
    }

    @ExceptionHandler({NotAllowedException.class, NotYourTurn.class})
    ProblemDetail notAllowed(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(RuleViolation.class)
    ProblemDetail ruleViolation(RuleViolation e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrentChange() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The game was changed at the same time; reload and try again");
    }
}
