package ai.typesafe;

public sealed interface Answer permits NoulAnswer, ChoiceAnswer, ScoreAnswer {
    String type();
}
