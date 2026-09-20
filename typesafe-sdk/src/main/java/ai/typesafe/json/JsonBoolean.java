package ai.typesafe.json;

public record JsonBoolean(boolean value) implements JsonValue {
    public static final JsonBoolean TRUE = new JsonBoolean(true);

    public static final JsonBoolean FALSE = new JsonBoolean(false);

    @Override
    public String toJson() {
        return Boolean.toString(value);
    }

    @Override
    public String toString() {
        return toJson();
    }
}
