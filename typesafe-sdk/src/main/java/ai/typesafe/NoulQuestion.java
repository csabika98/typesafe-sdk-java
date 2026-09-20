package ai.typesafe;

import ai.typesafe.json.JsonValue;

public record NoulQuestion(JsonValue instructions, NoulCriteria criteria) implements Question {
    public NoulQuestion {
        instructions = EntryType.validate(instructions, "instructions");
    }

    @Override
    public String type() {
        return "noul";
    }
}
