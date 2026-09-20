package ai.typesafe;

import ai.typesafe.json.Json;
import ai.typesafe.json.JsonValue;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record SystemOneRequest(
        JsonValue state,
        Map<String, Question> questions,
        String model,
        Map<String, JsonValue> additionalProperties) {
    public SystemOneRequest {
        state = EntryType.validate(state, "state");
        Objects.requireNonNull(questions, "questions");
        if (questions.isEmpty()) {
            throw new TypeSafeException("Questions cannot be empty.");
        }
        Map<String, Question> questionCopy = new LinkedHashMap<>();
        for (Map.Entry<String, Question> question : questions.entrySet()) {
            String name = question.getKey();
            if (name == null || name.isEmpty()) {
                throw new TypeSafeException("Question names cannot be empty.");
            }
            if (question.getValue() == null) {
                throw new TypeSafeException("Question '" + name + "' cannot be null.");
            }
            questionCopy.put(name, question.getValue());
        }
        questions = Collections.unmodifiableMap(questionCopy);
        additionalProperties = additionalProperties == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(additionalProperties));
    }

    public static Builder builder() {
        return new Builder();
    }

    public SystemOneRequest withModel(String model) {
        return new SystemOneRequest(state, questions, model, additionalProperties);
    }

    public static final class Builder {
        private JsonValue state = Json.ofNull();
        private final Map<String, Question> questions = new LinkedHashMap<>();
        private String model;
        private final Map<String, JsonValue> additionalProperties = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder state(JsonValue state) {
            this.state = state;
            return this;
        }

        public Builder state(String state) {
            return state(Json.of(state));
        }

        public Builder stateFrom(Object state) {
            return state(Json.from(state));
        }

        public Builder question(String name, Question question) {
            questions.put(name, question);
            return this;
        }

        public Builder questions(Map<String, Question> questions) {
            this.questions.putAll(questions);
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder additionalProperty(String name, JsonValue value) {
            additionalProperties.put(name, value);
            return this;
        }

        public SystemOneRequest build() {
            return new SystemOneRequest(state, questions, model, additionalProperties);
        }
    }
}
