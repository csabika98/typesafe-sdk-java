package ai.typesafe;

public final class ConsoleLogger implements TypeSafeLogger {
    @Override
    public void log(LogLevel level, String message) {
        System.err.println("[TypeSafe] " + level + ": " + message);
    }
}
