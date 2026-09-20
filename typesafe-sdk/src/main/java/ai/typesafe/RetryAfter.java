package ai.typesafe;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.net.http.HttpHeaders;
import java.util.Optional;

final class RetryAfter {
    private RetryAfter() {
    }

    static Optional<Duration> read(HttpHeaders headers, Duration maximum) {
        Optional<String> milliseconds = headers.firstValue("retry-after-ms");
        if (milliseconds.isPresent()) {
            Optional<Double> parsed = parseDouble(milliseconds.get());
            if (parsed.isPresent()) {
                double value = parsed.get();
                return value <= maximum.toMillis()
                        ? Optional.of(Duration.ofMillis((long) value))
                        : Optional.empty();
            }
        }

        return headers.firstValue("retry-after")
                .flatMap(RetryAfter::parseRetryAfter)
                .filter(delay -> !delay.isNegative() && delay.compareTo(maximum) <= 0);
    }

    private static Optional<Double> parseDouble(String value) {
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isFinite(parsed) && parsed >= 0 ? Optional.of(parsed) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static Optional<Duration> parseRetryAfter(String value) {
        String trimmed = value.trim();
        try {
            return Optional.of(Duration.ofSeconds(Long.parseLong(trimmed)));
        } catch (NumberFormatException ignored) {
        }
        try {
            ZonedDateTime date = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME);
            return Optional.of(Duration.between(ZonedDateTime.now(date.getZone()), date));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }
}
