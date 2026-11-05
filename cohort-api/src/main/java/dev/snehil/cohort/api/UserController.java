package dev.snehil.cohort.api;

import dev.snehil.cohort.api.Dtos.EvaluationResponse;
import dev.snehil.cohort.api.Dtos.MembershipDto;
import dev.snehil.cohort.api.Dtos.UserCohortsResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
public class UserController {
    private final CohortService service;

    public UserController(CohortService service) {
        this.service = service;
    }

    /**
     * Evaluates one user and stores the result.
     *
     * @param mode {@code all} (every matching cohort) or {@code priority} (only the highest-priority one)
     * @param asOf evaluate as if today were this date (useful for demos and backfills)
     */
    @PostMapping("/users/{id}/evaluate")
    public EvaluationResponse evaluate(@PathVariable String id,
                                       @RequestBody CohortService.ProfileInput body,
                                       @RequestParam(defaultValue = "all") String mode,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return EvaluationResponse.of(service.evaluate(id, body, mode, asOf));
    }

    /** The user's cohorts as of their last evaluation. */
    @GetMapping("/users/{id}/cohorts")
    public UserCohortsResponse cohorts(@PathVariable String id) {
        return new UserCohortsResponse(id, service.memberships(id).stream().map(MembershipDto::of).toList());
    }
}
