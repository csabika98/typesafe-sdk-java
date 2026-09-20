package ai.typesafe;

@FunctionalInterface
public interface TypeSafeLogger {
    void log(LogLevel level, String message);
}
