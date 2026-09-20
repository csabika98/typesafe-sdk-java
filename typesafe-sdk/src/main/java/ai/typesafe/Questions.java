package ai.typesafe;

import ai.typesafe.json.Json;
import ai.typesafe.json.JsonValue;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Questions {
    private Questions() {
    }

    public static NoulQuestion noul() {
        return new NoulQuestion(Json.ofNull(), null);
    }

    public static NoulQuestion noul(String instructions) {
        return new NoulQuestion(Json.of(instructions), null);
    }

    public static NoulQuestion noul(JsonValue instructions) {
        return new NoulQuestion(instructions, null);
    }

    public static NoulQuestion noul(String instructions, NoulCriteria criteria) {
        return new NoulQuestion(Json.of(instructions), criteria);
    }

    public static NoulQuestion noul(JsonValue instructions, NoulCriteria criteria) {
        return new NoulQuestion(instructions, criteria);
    }

    public static ChoiceQuestion choice(String instructions, Map<String, JsonValue> criteria) {
        return new ChoiceQuestion(Json.of(instructions), criteria);
    }

    public static ChoiceQuestion choice(JsonValue instructions, Map<String, JsonValue> criteria) {
        return new ChoiceQuestion(instructions, criteria);
    }

    public static ChoiceQuestion choiceOf(String instructions, String... namesAndDescriptions) {
        if (namesAndDescriptions.length % 2 != 0) {
            throw new TypeSafeException("Choice criteria must be given as name and description pairs.");
        }
        Map<String, JsonValue> criteria = new LinkedHashMap<>();
        for (int index = 0; index < namesAndDescriptions.length; index += 2) {
            criteria.put(namesAndDescriptions[index], Json.of(namesAndDescriptions[index + 1]));
        }
        return new ChoiceQuestion(Json.of(instructions), criteria);
    }

    public static ScoreQuestion score(String instructions, List<JsonValue> criteria) {
        return new ScoreQuestion(Json.of(instructions), criteria);
    }

    public static ScoreQuestion score(JsonValue instructions, List<JsonValue> criteria) {
        return new ScoreQuestion(instructions, criteria);
    }

    public static ScoreQuestion score(String instructions, String... labels) {
        List<JsonValue> criteria = Arrays.stream(labels).map(Json::of).toList();
        return new ScoreQuestion(Json.of(instructions), criteria);
    }
}
