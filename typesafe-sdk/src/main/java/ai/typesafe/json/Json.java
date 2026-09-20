package ai.typesafe.json;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    private Json() {
    }

    public static JsonValue ofNull() {
        return JsonNull.INSTANCE;
    }

    public static JsonValue of(String value) {
        return value == null ? JsonNull.INSTANCE : new JsonString(value);
    }

    public static JsonValue of(boolean value) {
        return value ? JsonBoolean.TRUE : JsonBoolean.FALSE;
    }

    public static JsonValue of(long value) {
        return new JsonNumber(BigDecimal.valueOf(value));
    }

    public static JsonValue of(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("JSON cannot represent " + value + ".");
        }
        return new JsonNumber(BigDecimal.valueOf(value));
    }

    public static JsonArray array(JsonValue... values) {
        return new JsonArray(Arrays.asList(values));
    }

    public static JsonArray array(Collection<? extends JsonValue> values) {
        return new JsonArray(List.copyOf(values));
    }

    public static ObjectBuilder object() {
        return new ObjectBuilder();
    }

    public static JsonValue parse(String text) {
        return JsonParser.parse(text);
    }

    public static JsonValue from(Object value) {
        return from(value, 0);
    }

    private static JsonValue from(Object value, int depth) {
        if (depth > 256) {
            throw new IllegalArgumentException("Value nesting is too deep.");
        }
        return switch (value) {
            case null -> JsonNull.INSTANCE;
            case JsonValue json -> json;
            case String string -> new JsonString(string);
            case Boolean bool -> of(bool);
            case BigDecimal decimal -> new JsonNumber(decimal);
            case Byte number -> of(number.longValue());
            case Short number -> of(number.longValue());
            case Integer number -> of(number.longValue());
            case Long number -> of(number.longValue());
            case Float number -> of(number.doubleValue());
            case Double number -> of(number.doubleValue());
            case Number number -> new JsonNumber(new BigDecimal(number.toString()));
            case Map<?, ?> map -> {
                Map<String, JsonValue> members = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String name)) {
                        throw new IllegalArgumentException("JSON object keys must be strings.");
                    }
                    members.put(name, from(entry.getValue(), depth + 1));
                }
                yield new JsonObject(members);
            }
            case Collection<?> collection -> {
                List<JsonValue> values = collection.stream()
                        .map(element -> from(element, depth + 1))
                        .toList();
                yield new JsonArray(values);
            }
            case Object[] array -> {
                List<JsonValue> values = Arrays.stream(array)
                        .map(element -> from(element, depth + 1))
                        .toList();
                yield new JsonArray(values);
            }
            default -> throw new IllegalArgumentException(
                    value.getClass().getName() + " cannot be converted to JSON. Build a JsonValue instead.");
        };
    }

    public static final class ObjectBuilder {
        private final Map<String, JsonValue> members = new LinkedHashMap<>();

        private ObjectBuilder() {
        }

        public ObjectBuilder put(String name, JsonValue value) {
            members.put(name, value == null ? JsonNull.INSTANCE : value);
            return this;
        }

        public ObjectBuilder put(String name, String value) {
            return put(name, Json.of(value));
        }

        public ObjectBuilder put(String name, boolean value) {
            return put(name, Json.of(value));
        }

        public ObjectBuilder put(String name, long value) {
            return put(name, Json.of(value));
        }

        public ObjectBuilder put(String name, double value) {
            return put(name, Json.of(value));
        }

        public ObjectBuilder putAny(String name, Object value) {
            return put(name, Json.from(value));
        }

        public JsonObject build() {
            return new JsonObject(members);
        }
    }
}
