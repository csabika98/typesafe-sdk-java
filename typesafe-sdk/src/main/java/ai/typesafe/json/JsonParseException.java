package ai.typesafe.json;

public final class JsonParseException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public JsonParseException(String message) {
        super(message);
    }
}
