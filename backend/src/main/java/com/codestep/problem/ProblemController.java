package com.codestep.problem;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/problems")
public class ProblemController {

    private final ProblemRepository repository;

    public ProblemController(ProblemRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<ProblemSummary> list() {
        return repository.findAll().stream().map(ProblemSummary::from).toList();
    }

    @GetMapping("/{slug}")
    public ProblemDetail get(@PathVariable String slug) {
        return repository.findBySlug(slug)
                .map(ProblemDetail::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Problem not found"));
    }

    public record ProblemSummary(String slug, String title, String topic, String pattern, int order, int difficulty,
                                 Mode mode) {

        static ProblemSummary from(Problem p) {
            return new ProblemSummary(p.slug(), p.title(), p.topic(), p.pattern(), p.order(), p.difficulty(), p.mode());
        }
    }

    public record ProblemDetail(
            String slug,
            String title,
            String topic,
            String pattern,
            int difficulty,
            Mode mode,
            String description,
            String starterCode,
            List<String> hints,
            WorkedExample workedExample,
            List<TraceStep> walkthrough,
            List<SampleCase> samples
    ) {

        static ProblemDetail from(Problem p) {
            List<SampleCase> samples = p.sampleTests().stream()
                    .map(t -> new SampleCase(t.displayInput(), t.expected()))
                    .toList();
            return new ProblemDetail(p.slug(), p.title(), p.topic(), p.pattern(), p.difficulty(), p.mode(), p.description(),
                    p.starterCode(), p.hints(), p.workedExample(), p.walkthrough(), samples);
        }
    }

    public record SampleCase(String input, String expected) {
    }
}
