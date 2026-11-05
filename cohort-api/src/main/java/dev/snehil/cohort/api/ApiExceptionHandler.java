package dev.snehil.cohort.api;

import dev.snehil.cohort.parse.RuleParseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain errors to RFC 9457 problem responses. */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** A bad rule: 400 with the position and a caret line, so a UI can underline the mistake. */
    @ExceptionHandler(RuleParseException.class)
    ProblemDetail invalidRule(RuleParseException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.reason());
        p.setTitle("Invalid rule");
        p.setProperty("position", e.position());
        p.setProperty("pointer", e.pointer());
        return p;
    }

    @ExceptionHandler({CohortService.BadInputException.class, IllegalArgumentException.class})
    ProblemDetail badInput(RuntimeException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        p.setTitle("Invalid input");
        return p;
    }
}
