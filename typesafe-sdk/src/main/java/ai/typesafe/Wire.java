package ai.typesafe;

import ai.typesafe.json.Json;
import ai.typesafe.json.JsonArray;
import ai.typesafe.json.JsonNumber;
import ai.typesafe.json.JsonObject;
import ai.typesafe.json.JsonParseException;
import ai.typesafe.json.JsonString;
import ai.typesafe.json.JsonValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Wire {
    private Wire() {
    }

    static String serialize(SystemOneRequest request) {
        Map<String, JsonValue> body = new LinkedHashMap<>(request.additionalProperties());
        body.put("state", request.state());
        body.put("questions", serializeQuestions(request.questions()));
        if (request.model() != null) {
            body.put("model", Json.of(request.model()));
        }
        return new JsonObject(body).toJson();
    }

    private static JsonObject serializeQuestions(Map<String, Question> questions) {
        Map<String, JsonValue> members = new LinkedHashMap<>();
        questions.forEach((name, question) -> members.put(name, serializeQuestion(question)));
        return new JsonObject(members);
    }

    private static JsonObject serializeQuestion(Question question) {
        Map<String, JsonValue> members = new LinkedHashMap<>();
        members.put("type", Json.of(question.type()));
        members.put("instructions", question.instructions());
        switch (question) {
            case NoulQuestion noul -> {
                if (noul.criteria() != null) {
                    members.put("criteria", Json.object()
                            .put("true", noul.criteria().whenTrue())
                            .put("false", noul.criteria().whenFalse())
                            .build());
                }
            }
            case ChoiceQuestion choice -> members.put("criteria", new JsonObject(choice.criteria()));
            case ScoreQuestion score -> members.put("criteria", new JsonArray(score.criteria()));
        }
        return new JsonObject(members);
    }

    static List<ModelCard> parseModels(String text) {
        JsonValue root = parse(text, "models response");
        if (!(root instanceof JsonObject object)
                || !(object.get("models") instanceof JsonArray models)) {
            throw new TypeSafeException(
                    "Malformed models response: expected an object with a models array.");
        }
        List<ModelCard> cards = new ArrayList<>(models.size());
        for (JsonValue entry : models) {
            if (!(entry instanceof JsonObject card)) {
                throw new TypeSafeException("Malformed models response: a model entry is not an object.");
            }
            cards.add(new ModelCard(
                    requiredString(card, "name", "a model entry"),
                    requiredString(card, "description", "a model entry"),
                    requiredString(card, "release_date", "a model entry")));
        }
        return List.copyOf(cards);
    }

    static SystemOneResult parseSystemOne(String text) {
        JsonValue root = parse(text, "System One response");
        if (!(root instanceof JsonObject object)
                || !(object.get("model") instanceof JsonString model)
                || !(object.get("answers") instanceof JsonObject answers)
                || !(object.get("usage") instanceof JsonObject usage)
                || !(usage.get("input_tokens") instanceof JsonNumber inputTokens)
                || !(usage.get("output_tokens") instanceof JsonNumber outputTokens)) {
            throw new TypeSafeException("Malformed System One response.");
        }

        Map<String, Answer> parsed = new LinkedHashMap<>();
        answers.members().forEach((name, value) -> parsed.put(name, parseAnswer(name, value)));

        try {
            return new SystemOneResult(
                    model.value(),
                    parsed,
                    new Usage(inputTokens.intValueExact(), outputTokens.intValueExact()));
        } catch (ArithmeticException exception) {
            throw new TypeSafeException("Malformed System One response: token counts are not integers.");
        }
    }

    private static Answer parseAnswer(String name, JsonValue value) {
        if (!(value instanceof JsonObject answer) || !(answer.get("type") instanceof JsonString type)) {
            throw new TypeSafeException("Malformed answer '" + name + "': missing type.");
        }
        return switch (type.value()) {
            case "noul" -> new NoulAnswer(requiredNumber(answer, "noul", name));
            case "choice" -> new ChoiceAnswer(
                    requiredString(answer, "choice", "answer '" + name + "'"),
                    requiredNumber(answer, "confidence", name),
                    requiredProbabilities(answer, "probabilities", name));
            case "score" -> new ScoreAnswer(
                    requiredNumber(answer, "score", name),
                    requiredNumber(answer, "confidence", name),
                    requiredObject(answer, "legend", name).members(),
                    requiredProbabilities(answer, "probabilities", name));
            default -> throw new TypeSafeException(
                    "Malformed answer '" + name + "': unknown type '" + type.value() + "'.");
        };
    }

    private static String requiredString(JsonObject object, String member, String owner) {
        if (object.get(member) instanceof JsonString string) {
            return string.value();
        }
        throw new TypeSafeException("Malformed response: " + owner + " is missing '" + member + "'.");
    }

    private static double requiredNumber(JsonObject object, String member, String name) {
        if (object.get(member) instanceof JsonNumber number) {
            return number.doubleValue();
        }
        throw new TypeSafeException("Malformed answer '" + name + "': '" + member + "' is not a number.");
    }

    private static JsonObject requiredObject(JsonObject object, String member, String name) {
        if (object.get(member) instanceof JsonObject nested) {
            return nested;
        }
        throw new TypeSafeException("Malformed answer '" + name + "': '" + member + "' is not an object.");
    }

    private static Map<String, Double> requiredProbabilities(JsonObject object, String member, String name) {
        JsonObject nested = requiredObject(object, member, name);
        Map<String, Double> probabilities = new LinkedHashMap<>();
        nested.members().forEach((key, value) -> {
            if (!(value instanceof JsonNumber number)) {
                throw new TypeSafeException(
                        "Malformed answer '" + name + "': '" + member + "." + key + "' is not a number.");
            }
            probabilities.put(key, number.doubleValue());
        });
        return probabilities;
    }

    private static JsonValue parse(String text, String description) {
        try {
            return Json.parse(text);
        } catch (JsonParseException exception) {
            throw new TypeSafeException("Malformed " + description + ": invalid JSON.", exception);
        }
    }
}
