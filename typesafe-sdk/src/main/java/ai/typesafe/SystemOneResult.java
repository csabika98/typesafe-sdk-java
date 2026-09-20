package ai.typesafe;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record SystemOneResult(String model, Map<String, Answer> answers, Usage usage) {
    public SystemOneResult {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(usage, "usage");
        answers = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(answers, "answers")));
    }
}
