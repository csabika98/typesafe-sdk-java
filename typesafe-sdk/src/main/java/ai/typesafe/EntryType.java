package ai.typesafe;

import ai.typesafe.json.JsonArray;
import ai.typesafe.json.JsonNull;
import ai.typesafe.json.JsonObject;
import ai.typesafe.json.JsonString;
import ai.typesafe.json.JsonValue;

final class EntryType {
    private EntryType() {
    }

    static JsonValue validate(JsonValue value, String name) {
        JsonValue resolved = value == null ? JsonNull.INSTANCE : value;
        if (resolved instanceof JsonNull
                || resolved instanceof JsonString
                || resolved instanceof JsonObject
                || resolved instanceof JsonArray) {
            return resolved;
        }
        throw new TypeSafeException(name + " must be a JSON string, object, array, or null.");
    }
}
