package ai.typesafe.json;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record JsonObject(Map<String, JsonValue> members) implements JsonValue {
    public JsonObject {
        Objects.requireNonNull(members, "members");
        members = Collections.unmodifiableMap(new LinkedHashMap<>(members));
    }

    public JsonValue get(String name) {
        return members.get(name);
    }

    public Optional<JsonValue> find(String name) {
        return Optional.ofNullable(members.get(name));
    }

    public boolean has(String name) {
        return members.containsKey(name);
    }

    @Override
    public String toJson() {
        StringBuilder builder = new StringBuilder();
        JsonWriter.write(this, builder);
        return builder.toString();
    }

    @Override
    public String toString() {
        return toJson();
    }
}
