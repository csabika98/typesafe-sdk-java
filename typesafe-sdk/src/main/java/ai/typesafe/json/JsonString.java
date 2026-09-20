package ai.typesafe.json;

import java.util.Objects;

public record JsonString(String value) implements JsonValue {
    public JsonString {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public String toJson() {
        StringBuilder builder = new StringBuilder(value.length() + 2);
        JsonWriter.writeString(value, builder);
        return builder.toString();
    }

    @Override
    public String toString() {
        return toJson();
    }
}
