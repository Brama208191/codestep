package com.codestep.problem;

import java.util.List;
import java.util.Map;

public record TraceStep(
        List<Object> array,
        Map<String, Integer> pointers,
        List<Integer> highlight,
        Map<String, Object> vars,
        String note
) {
}
