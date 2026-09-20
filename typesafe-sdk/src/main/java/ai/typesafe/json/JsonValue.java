package ai.typesafe.json;

public sealed interface JsonValue
        permits JsonNull, JsonBoolean, JsonNumber, JsonString, JsonArray, JsonObject {
    String toJson();
}
