package ai.typesafe.json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JsonParser {
    private static final int MAX_DEPTH = 256;

    private final String text;
    private int index;

    private JsonParser(String text) {
        this.text = text;
    }

    static JsonValue parse(String text) {
        if (text == null) {
            throw new JsonParseException("JSON text cannot be null.");
        }
        JsonParser parser = new JsonParser(text);
        parser.skipWhitespace();
        JsonValue value = parser.readValue(0);
        parser.skipWhitespace();
        if (parser.index != text.length()) {
            throw parser.error("unexpected trailing content");
        }
        return value;
    }

    private JsonValue readValue(int depth) {
        if (depth > MAX_DEPTH) {
            throw error("nesting is too deep");
        }
        char character = peek();
        return switch (character) {
            case '{' -> readObject(depth);
            case '[' -> readArray(depth);
            case '"' -> new JsonString(readString());
            case 't' -> readLiteral("true", JsonBoolean.TRUE);
            case 'f' -> readLiteral("false", JsonBoolean.FALSE);
            case 'n' -> readLiteral("null", JsonNull.INSTANCE);
            default -> readNumber();
        };
    }

    private JsonValue readObject(int depth) {
        expect('{');
        Map<String, JsonValue> members = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            index++;
            return new JsonObject(members);
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw error("expected a member name");
            }
            String name = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            members.put(name, readValue(depth + 1));
            skipWhitespace();
            char character = next();
            if (character == '}') {
                return new JsonObject(members);
            }
            if (character != ',') {
                throw error("expected ',' or '}'");
            }
        }
    }

    private JsonValue readArray(int depth) {
        expect('[');
        List<JsonValue> values = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            index++;
            return new JsonArray(values);
        }
        while (true) {
            skipWhitespace();
            values.add(readValue(depth + 1));
            skipWhitespace();
            char character = next();
            if (character == ']') {
                return new JsonArray(values);
            }
            if (character != ',') {
                throw error("expected ',' or ']'");
            }
        }
    }

    private String readString() {
        expect('"');
        StringBuilder builder = new StringBuilder();
        while (true) {
            char character = next();
            if (character == '"') {
                return builder.toString();
            }
            if (character == '\\') {
                char escape = next();
                switch (escape) {
                    case '"' -> builder.append('"');
                    case '\\' -> builder.append('\\');
                    case '/' -> builder.append('/');
                    case 'b' -> builder.append('\b');
                    case 'f' -> builder.append('\f');
                    case 'n' -> builder.append('\n');
                    case 'r' -> builder.append('\r');
                    case 't' -> builder.append('\t');
                    case 'u' -> builder.append(readUnicodeEscape());
                    default -> throw error("invalid escape '\\" + escape + "'");
                }
            } else if (character < 0x20) {
                throw error("unescaped control character");
            } else {
                builder.append(character);
            }
        }
    }

    private char readUnicodeEscape() {
        if (index + 4 > text.length()) {
            throw error("truncated unicode escape");
        }
        String digits = text.substring(index, index + 4);
        index += 4;
        try {
            return (char) Integer.parseInt(digits, 16);
        } catch (NumberFormatException exception) {
            throw error("invalid unicode escape '\\u" + digits + "'");
        }
    }

    private JsonValue readLiteral(String literal, JsonValue value) {
        if (!text.startsWith(literal, index)) {
            throw error("invalid literal");
        }
        index += literal.length();
        return value;
    }

    private JsonValue readNumber() {
        int start = index;
        if (peek() == '-') {
            index++;
        }
        readIntegerPart();
        if (index < text.length() && text.charAt(index) == '.') {
            index++;
            readDigits();
        }
        if (index < text.length() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
            index++;
            if (index < text.length() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
                index++;
            }
            readDigits();
        }
        String literal = text.substring(start, index);
        try {
            return new JsonNumber(new BigDecimal(literal));
        } catch (NumberFormatException exception) {
            throw error("invalid number '" + literal + "'");
        }
    }

    private void readIntegerPart() {
        int start = index;
        readDigits();
        if (index - start > 1 && text.charAt(start) == '0') {
            throw error("numbers cannot have a leading zero");
        }
    }

    private void readDigits() {
        int start = index;
        while (index < text.length() && text.charAt(index) >= '0' && text.charAt(index) <= '9') {
            index++;
        }
        if (index == start) {
            throw error("expected a digit");
        }
    }

    private void skipWhitespace() {
        while (index < text.length()) {
            char character = text.charAt(index);
            if (character == ' ' || character == '\t' || character == '\n' || character == '\r') {
                index++;
            } else {
                return;
            }
        }
    }

    private char peek() {
        if (index >= text.length()) {
            throw error("unexpected end of input");
        }
        return text.charAt(index);
    }

    private char next() {
        char character = peek();
        index++;
        return character;
    }

    private void expect(char expected) {
        char character = next();
        if (character != expected) {
            throw error("expected '" + expected + "'");
        }
    }

    private JsonParseException error(String message) {
        return new JsonParseException("Invalid JSON at offset " + index + ": " + message + ".");
    }
}
