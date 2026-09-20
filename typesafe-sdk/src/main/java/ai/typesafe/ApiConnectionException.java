package ai.typesafe;

public class ApiConnectionException extends TypeSafeException {
    private static final long serialVersionUID = 1L;

    public ApiConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
