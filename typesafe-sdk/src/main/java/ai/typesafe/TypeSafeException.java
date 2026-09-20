package ai.typesafe;

public class TypeSafeException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public TypeSafeException(String message) {
        super(message);
    }

    public TypeSafeException(String message, Throwable cause) {
        super(message, cause);
    }
}
