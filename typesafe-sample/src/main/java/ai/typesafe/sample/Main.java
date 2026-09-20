package ai.typesafe.sample;

import ai.typesafe.ApiConnectionException;
import ai.typesafe.ApiException;
import ai.typesafe.ApiResponse;
import ai.typesafe.ApiTimeoutException;
import ai.typesafe.ApiUserAbortException;
import ai.typesafe.Answer;
import ai.typesafe.AuthenticationException;
import ai.typesafe.ChoiceAnswer;
import ai.typesafe.LogLevel;
import ai.typesafe.ModelCard;
import ai.typesafe.NoulAnswer;
import ai.typesafe.NoulCriteria;
import ai.typesafe.Questions;
import ai.typesafe.RateLimitException;
import ai.typesafe.RequestOptions;
import ai.typesafe.RetryOptions;
import ai.typesafe.ScoreAnswer;
import ai.typesafe.SystemOneRequest;
import ai.typesafe.SystemOneResult;
import ai.typesafe.TypeSafeClient;
import ai.typesafe.TypeSafeClientOptions;
import ai.typesafe.TypeSafeException;
import ai.typesafe.json.Json;
import ai.typesafe.json.JsonValue;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Main {
    private static final List<CompletableFuture<?>> IN_FLIGHT = new CopyOnWriteArrayList<>();

    private Main() {
    }

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (IN_FLIGHT.isEmpty()) {
                return;
            }
            System.out.println();
            System.out.println("Cancellation requested...");
            IN_FLIGHT.forEach(pending -> pending.cancel(true));
        }));

        System.exit(run());
    }

    private static int run() {
        try (TypeSafeClient client = new TypeSafeClient(TypeSafeClientOptions.builder()
                .timeout(Duration.ofSeconds(30))
                .retry(RetryOptions.builder().maxRetries(3).build())
                .logLevel(LogLevel.INFO)
                .logger((level, message) ->
                        System.err.println("[typesafe:" + level.name().toLowerCase(java.util.Locale.ROOT)
                                + "] " + message))
                .build())) {
            System.out.println("Base URL      : " + client.baseUrl());
            System.out.println("Default model : " + client.defaultModel());
            System.out.println();

            listModels(client);
            evaluateSupportTicket(client);
            return 0;
        } catch (ApiUserAbortException exception) {
            System.err.println("Request aborted by the user.");
            return 130;
        } catch (ApiTimeoutException exception) {
            System.err.println("Request timed out after " + exception.timeout() + ". Consider raising it.");
            return 1;
        } catch (RateLimitException exception) {
            String retryIn = exception.retryDelay().map(delay -> " Retry in " + delay + ".").orElse("");
            System.err.println("Rate limited (request " + exception.requestId() + ")." + retryIn);
            return 1;
        } catch (AuthenticationException exception) {
            System.err.println("Authentication failed: " + exception.getMessage()
                    + ". Check TYPESAFE_API_KEY.");
            return 1;
        } catch (ApiException exception) {
            System.err.println("API error " + exception.statusCode() + ": " + exception.getMessage()
                    + " (request " + exception.requestId() + ")");
            if (exception.body() != null) {
                System.err.println("Body: " + exception.body());
            }
            return 1;
        } catch (ApiConnectionException exception) {
            System.err.println("Could not reach the TypeSafe API: " + exception.getMessage());
            return 1;
        } catch (TypeSafeException exception) {
            System.err.println("TypeSafe SDK error: " + exception.getMessage());
            return 1;
        }
    }

    private static void listModels(TypeSafeClient client) {
        System.out.println("Available models");
        System.out.println("----------------");

        for (ModelCard model : await(client.modelsAsync())) {
            System.out.printf("  %-14s %-11s %s%n",
                    model.name(), releaseDate(model), model.description());
        }
        System.out.println();
    }

    private static void evaluateSupportTicket(TypeSafeClient client) {
        JsonValue state = Json.object()
                .put("ticket", Json.object()
                        .put("subject", "The checkout process on the website fails with a 500 error")
                        .put("body", "Every attempt to pay with a saved card returns an error page. "
                                + "This started this morning.")
                        .put("customer_tier", "enterprise")
                        .build())
                .build();

        SystemOneRequest request = SystemOneRequest.builder()
                .state(state)
                .question("is_bug", Questions.noul(
                        "Does the ticket describe a defect in the product?",
                        NoulCriteria.of(
                                "The customer reports broken or incorrect behaviour.",
                                "The ticket is a question, feature request, or billing issue.")))
                .question("category", Questions.choiceOf(
                        "Which team should own this ticket?",
                        "billing", "Payments, invoices, and refunds.",
                        "platform", "Availability, errors, and performance of the website.",
                        "account", "Login, permissions, and provisioning."))
                .question("urgency", Questions.score(
                        "How urgent is this ticket?",
                        "not urgent", "low", "medium", "high", "drop everything"))
                .build();

        ApiResponse<SystemOneResult> response = await(client.systemOneWithResponseAsync(
                request,
                RequestOptions.builder().timeout(Duration.ofSeconds(20)).build()));
        SystemOneResult result = response.data();

        System.out.println("Evaluation");
        System.out.println("----------");
        System.out.println("  model      : " + result.model());
        System.out.println("  request id : " + response.requestId().orElse("(none)"));

        for (Map.Entry<String, Answer> answer : result.answers().entrySet()) {
            System.out.printf("  %-10s : %s%n", answer.getKey(), describe(answer.getValue()));
        }

        System.out.println("  usage      : " + result.usage().inputTokens() + " in / "
                + result.usage().outputTokens() + " out");
        System.out.println();
    }

    private static String releaseDate(ModelCard model) {
        String value = model.releaseDate();
        try {
            return java.time.OffsetDateTime.parse(value).toLocalDate().toString();
        } catch (java.time.format.DateTimeParseException exception) {
            return value.length() > 11 ? value.substring(0, 11) : value;
        }
    }

    private static String describe(Answer answer) {
        return switch (answer) {
            case NoulAnswer noul -> String.format("noul %.1f%%", noul.noul() * 100);
            case ChoiceAnswer choice -> String.format(
                    "%s (confidence %.1f%%) %s",
                    choice.choice(), choice.confidence() * 100, choice.probabilities());
            case ScoreAnswer score -> String.format(
                    "%.2f (confidence %.1f%%) legend: %s",
                    score.score(), score.confidence() * 100, score.legend());
        };
    }

    private static <T> T await(CompletableFuture<T> pending) {
        IN_FLIGHT.add(pending);
        try {
            return pending.join();
        } catch (java.util.concurrent.CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw exception;
        } finally {
            IN_FLIGHT.remove(pending);
        }
    }
}
