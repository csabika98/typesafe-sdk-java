package ai.typesafe;

import ai.typesafe.json.JsonValue;

public sealed interface Question permits NoulQuestion, ChoiceQuestion, ScoreQuestion {
    String type();

    JsonValue instructions();
}
