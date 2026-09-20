package ai.typesafe;

import ai.typesafe.json.JsonValue;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record ChoiceQuestion(JsonValue instructions, Map<String, JsonValue> criteria) implements Question {
    public ChoiceQuestion {
        instructions = EntryType.validate(instructions, "instructions");
        Objects.requireNonNull(criteria, "criteria");
        if (criteria.isEmpty()) {
            throw new TypeSafeException("Choice criteria cannot be empty.");
        }
        Map<String, JsonValue> copy = new LinkedHashMap<>();
        for (Map.Entry<String, JsonValue> criterion : criteria.entrySet()) {
            String name = criterion.getKey();
            if (name == null || name.isEmpty()) {
                throw new TypeSafeException("Choice criterion names cannot be empty.");
            }
            copy.put(name, EntryType.validate(criterion.getValue(), "criteria." + name));
        }
        criteria = Collections.unmodifiableMap(copy);
    }

    @Override
    public String type() {
        return "choice";
    }
}
