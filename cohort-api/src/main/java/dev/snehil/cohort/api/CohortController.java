package dev.snehil.cohort.api;

import dev.snehil.cohort.api.Dtos.CohortRequest;
import dev.snehil.cohort.api.Dtos.CohortResponse;
import dev.snehil.cohort.api.Dtos.RuleRequest;
import dev.snehil.cohort.api.Dtos.RuleResponse;
import dev.snehil.cohort.domain.Cohort;
import dev.snehil.cohort.rule.Rule;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
public class CohortController {
    private final CohortService service;

    public CohortController(CohortService service) {
        this.service = service;
    }

    /** Create, or replace an existing cohort with the same id (its version goes up by one). */
    @PostMapping("/cohorts")
    public ResponseEntity<CohortResponse> save(@Valid @RequestBody CohortRequest req) {
        Cohort c = service.save(req.name(), req.priority(), req.rule(), req.id());
        HttpStatus status = c.version() == 1 ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).location(URI.create("/cohorts/" + c.id())).body(CohortResponse.of(c));
    }

    @GetMapping("/cohorts")
    public List<CohortResponse> list() {
        return service.list().stream().map(CohortResponse::of).toList();
    }

    @GetMapping("/cohorts/{id}")
    public ResponseEntity<CohortResponse> get(@PathVariable String id) {
        return ResponseEntity.of(service.find(id).map(CohortResponse::of));
    }

    @DeleteMapping("/cohorts/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /** Checks a rule without saving it. Invalid rules get a 400 pointing at the problem. */
    @PostMapping("/rules/validate")
    public RuleResponse validate(@Valid @RequestBody RuleRequest req) {
        Rule r = service.validate(req.rule());
        return new RuleResponse(true, r.render(), r.attributes().stream().sorted().toList(), r.timeDependent());
    }
}
