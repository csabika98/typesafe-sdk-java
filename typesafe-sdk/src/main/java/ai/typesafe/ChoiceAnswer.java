package ai.typesafe;

import java.util.Map;
import java.util.Objects;

public record ChoiceAnswer(String choice, double confidence, Map<String, Double> probabilities)
        implements Answer {
    public ChoiceAnswer {
        Objects.requireNonNull(choice, "choice");
        probabilities = Map.copyOf(Objects.requireNonNull(probabilities, "probabilities"));
    }

    @Override
    public String type() {
        return "choice";
    }
}
