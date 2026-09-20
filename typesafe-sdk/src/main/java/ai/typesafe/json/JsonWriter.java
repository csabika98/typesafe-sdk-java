package ai.typesafe.json;

import java.util.Map;

final class JsonWriter {
    private JsonWriter() {
    }

    static void write(JsonValue value, StringBuilder builder) {
        switch (value) {
            case JsonNull ignored -> builder.append("null");
            case JsonBoolean bool -> builder.append(bool.value());
            case JsonNumber number -> builder.append(number.toJson());
            case JsonString string -> writeString(string.value(), builder);
            case JsonArray array -> {
                builder.append('[');
                boolean first = true;
                for (JsonValue element : array.values()) {
                    if (!first) {
                        builder.append(',');
                    }
                    first = false;
                    write(element, builder);
                }
                builder.append(']');
            }
            case JsonObject object -> {
                builder.append('{');
                boolean first = true;
                for (Map.Entry<String, JsonValue> member : object.members().entrySet()) {
                    if (!first) {
                        builder.append(',');
                    }
                    first = false;
                    writeString(member.getKey(), builder);
                    builder.append(':');
                    write(member.getValue(), builder);
                }
                builder.append('}');
            }
        }
    }

    static void writeString(String value, StringBuilder builder) {
        builder.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        builder.append('"');
    }
}
