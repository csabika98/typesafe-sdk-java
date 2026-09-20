package ai.typesafe;

public final class ApiUserAbortException extends TypeSafeException {
    private static final long serialVersionUID = 1L;

    public ApiUserAbortException(String message) {
        super(message);
    }

    public ApiUserAbortException(String message, Throwable cause) {
        super(message, cause);
    }
}
