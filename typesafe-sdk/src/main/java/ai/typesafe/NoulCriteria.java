package ai.typesafe;

import ai.typesafe.json.JsonNull;
import ai.typesafe.json.JsonValue;

public record NoulCriteria(JsonValue whenTrue, JsonValue whenFalse) {
    public NoulCriteria {
        whenTrue = EntryType.validate(whenTrue, "criteria.true");
        whenFalse = EntryType.validate(whenFalse, "criteria.false");
    }

    public static NoulCriteria empty() {
        return new NoulCriteria(JsonNull.INSTANCE, JsonNull.INSTANCE);
    }

    public static NoulCriteria of(String whenTrue, String whenFalse) {
        return new NoulCriteria(ai.typesafe.json.Json.of(whenTrue), ai.typesafe.json.Json.of(whenFalse));
    }
}
