package com.codestep.problem;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ProblemRepository {

    private final Map<String, Problem> problems = new LinkedHashMap<>();

    public ProblemRepository(ObjectMapper mapper) throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath:problems/*.json");
        List<Problem> loaded = new ArrayList<>();
        for (Resource resource : resources) {
            try (InputStream in = resource.getInputStream()) {
                loaded.add(mapper.readValue(in, Problem.class));
            }
        }
        loaded.sort(Comparator.comparingInt(Problem::order));
        loaded.forEach(problem -> problems.put(problem.slug(), problem));
    }

    public List<Problem> findAll() {
        return List.copyOf(problems.values());
    }

    public Optional<Problem> findBySlug(String slug) {
        return Optional.ofNullable(problems.get(slug));
    }
}
