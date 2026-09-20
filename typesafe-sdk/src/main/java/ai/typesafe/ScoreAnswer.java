package ai.typesafe;

import ai.typesafe.json.JsonValue;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record ScoreAnswer(
        double score,
        double confidence,
        Map<String, JsonValue> legend,
        Map<String, Double> probabilities) implements Answer {
    public ScoreAnswer {
        Objects.requireNonNull(legend, "legend");
        legend = Collections.unmodifiableMap(new LinkedHashMap<>(legend));
        probabilities = Map.copyOf(Objects.requireNonNull(probabilities, "probabilities"));
    }

    @Override
    public String type() {
        return "score";
    }
}
