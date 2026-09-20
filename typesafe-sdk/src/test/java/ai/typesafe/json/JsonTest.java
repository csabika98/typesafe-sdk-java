package ai.typesafe.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JsonTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "null",
            "true",
            "false",
            "0",
            "-1.5",
            "12345678901234567890123",
            "\"\"",
            "\"text\"",
            "[]",
            "[1,2,3]",
            "{}",
            "{\"a\":1,\"b\":[true,null],\"c\":{\"d\":\"e\"}}"})
    void roundTripsThroughParseAndWrite(String text) {
        assertEquals(text, Json.parse(text).toJson());
    }

    @Test
    void normalisesExponentNotation() {
        assertEquals("1000", Json.parse("1e3").toJson());
        assertEquals("0.001", Json.parse("1e-3").toJson());
    }

    @Test
    void preservesNumericPrecision() {
        JsonNumber number = assertInstanceOf(JsonNumber.class, Json.parse("0.1000000000000000000001"));
        assertEquals(new BigDecimal("0.1000000000000000000001"), number.value());
    }

    @Test
    void preservesObjectMemberOrder() {
        JsonObject object = assertInstanceOf(JsonObject.class, Json.parse("{\"z\":1,\"a\":2,\"m\":3}"));
        assertEquals(List.of("z", "a", "m"), List.copyOf(object.members().keySet()));
    }

    @Test
    void readsAndWritesEscapes() {
        JsonString parsed = assertInstanceOf(
                JsonString.class, Json.parse("\"a\\\"b\\\\c\\n\\t\\u0041\\u001f\""));
        assertEquals("a\"b\\c\n\tA\u001f", parsed.value());
        assertEquals("\"a\\\"b\\\\c\\n\\tA\\u001f\"", parsed.toJson());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "{",
            "[1,]",
            "{\"a\"}",
            "{\"a\":}",
            "{a:1}",
            "'text'",
            "tru",
            "01",
            "1.",
            "1e",
            "\"unterminated",
            "\"raw\tcontrol\"",
            "{} trailing",
            "[1] [2]"})
    void rejectsInvalidJson(String text) {
        assertThrows(JsonParseException.class, () -> Json.parse(text));
    }

    @Test
    void rejectsExcessiveNesting() {
        String text = "[".repeat(300) + "]".repeat(300);
        assertThrows(JsonParseException.class, () -> Json.parse(text));
    }

    @Test
    void buildsObjectsFluently() {
        JsonObject object = Json.object()
                .put("name", "Ada")
                .put("age", 36L)
                .put("active", true)
                .put("score", 0.5)
                .put("tags", Json.array(Json.of("a"), Json.of("b")))
                .put("missing", (String) null)
                .build();

        assertEquals(
                "{\"name\":\"Ada\",\"age\":36,\"active\":true,\"score\":0.5,"
                        + "\"tags\":[\"a\",\"b\"],\"missing\":null}",
                object.toJson());
    }

    @Test
    void convertsPlainJavaValues() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("text", "hello");
        source.put("count", 2);
        source.put("ratio", 0.25);
        source.put("flag", false);
        source.put("items", List.of(1, 2));
        source.put("nothing", null);
        source.put("nested", Map.of("a", "b"));

        assertEquals(
                "{\"text\":\"hello\",\"count\":2,\"ratio\":0.25,\"flag\":false,"
                        + "\"items\":[1,2],\"nothing\":null,\"nested\":{\"a\":\"b\"}}",
                Json.from(source).toJson());
    }

    @Test
    void rejectsValuesItCannotConvert() {
        assertThrows(IllegalArgumentException.class, () -> Json.from(new Object()));
        assertThrows(IllegalArgumentException.class, () -> Json.from(Map.of(1, "keyed by int")));
        assertThrows(IllegalArgumentException.class, () -> Json.of(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Json.of(Double.POSITIVE_INFINITY));
    }

    @Test
    void nullBecomesTheNullLiteral() {
        assertSame(JsonNull.INSTANCE, Json.from(null));
        assertSame(JsonNull.INSTANCE, Json.of((String) null));
        assertEquals("null", JsonNull.INSTANCE.toString());
    }

    @Test
    void collectionsAreCopiedDefensively() {
        List<JsonValue> values = new java.util.ArrayList<>(List.of(Json.of("a")));
        JsonArray array = new JsonArray(values);
        values.add(Json.of("b"));
        assertEquals(1, array.size());
        assertThrows(UnsupportedOperationException.class, () -> array.values().add(Json.of("c")));

        Map<String, JsonValue> members = new LinkedHashMap<>(Map.of("a", Json.of("1")));
        JsonObject object = new JsonObject(members);
        members.put("b", Json.of("2"));
        assertTrue(object.find("b").isEmpty());
    }
}
