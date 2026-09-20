package ai.typesafe.json;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public record JsonArray(List<JsonValue> values) implements JsonValue, Iterable<JsonValue> {
    public JsonArray {
        values = List.copyOf(Objects.requireNonNull(values, "values"));
    }

    public int size() {
        return values.size();
    }

    public JsonValue get(int index) {
        return values.get(index);
    }

    @Override
    public Iterator<JsonValue> iterator() {
        return values.iterator();
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
