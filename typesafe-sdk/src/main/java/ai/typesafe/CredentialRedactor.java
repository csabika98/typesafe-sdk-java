package ai.typesafe;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class CredentialRedactor {
    private static final Set<String> SECRET_HEADERS =
            Set.of("authorization", "proxy-authorization", "x-api-key");
    private static final Set<String> OPAQUE_HEADERS = Set.of("cookie", "set-cookie");

    private CredentialRedactor() {
    }

    public static String redactHeaders(Map<String, List<String>> headers) {
        return headers.entrySet().stream()
                .map(header -> header.getKey() + ": " + redactValues(header.getKey(), header.getValue()))
                .collect(Collectors.joining(", "));
    }

    private static String redactValues(String name, List<String> values) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (OPAQUE_HEADERS.contains(lower)) {
            return "***";
        }
        if (SECRET_HEADERS.contains(lower)) {
            return values.stream()
                    .map(value -> redactSecret(lower, value))
                    .collect(Collectors.joining(", "));
        }
        return String.join(", ", values);
    }

    private static String redactSecret(String name, String value) {
        String scheme = "";
        String secret = value;
        if (name.equals("authorization") || name.equals("proxy-authorization")) {
            int separator = value.indexOf(' ');
            if (separator > 0) {
                scheme = value.substring(0, separator + 1);
                secret = value.substring(separator + 1);
            }
        }
        return secret.length() > 8
                ? scheme + "***" + secret.substring(secret.length() - 4)
                : scheme + "***";
    }
}
