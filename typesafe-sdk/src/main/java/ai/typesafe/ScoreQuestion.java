package ai.typesafe;

import ai.typesafe.json.JsonValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record ScoreQuestion(JsonValue instructions, List<JsonValue> criteria) implements Question {
    public ScoreQuestion {
        instructions = EntryType.validate(instructions, "instructions");
        Objects.requireNonNull(criteria, "criteria");
        if (criteria.size() < 2) {
            throw new TypeSafeException("Score criteria must contain at least two entries.");
        }
        List<JsonValue> copy = new ArrayList<>(criteria.size());
        for (int index = 0; index < criteria.size(); index++) {
            copy.add(EntryType.validate(criteria.get(index), "criteria[" + index + "]"));
        }
        criteria = Collections.unmodifiableList(copy);
    }

    @Override
    public String type() {
        return "score";
    }
}
