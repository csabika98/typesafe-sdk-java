package ai.typesafe;

import static ai.typesafe.TestSupport.EMPTY_MODELS;
import static ai.typesafe.TestSupport.RESULT_JSON;
import static ai.typesafe.TestSupport.client;
import static ai.typesafe.TestSupport.noRetry;
import static ai.typesafe.TestSupport.options;
import static ai.typesafe.TestSupport.request;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.typesafe.json.Json;
import ai.typesafe.json.JsonArray;
import ai.typesafe.json.JsonNull;
import ai.typesafe.json.JsonObject;
import ai.typesafe.json.JsonValue;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TypeSafeClientTest {
    @Test
    void systemOneSerializesUpstreamRequestAndParsesTypedAnswers() {
        StubHttpClient transport = StubHttpClient.always(200, RESULT_JSON);
        SystemOneResult result;
        try (TypeSafeClient client = client(transport)) {
            result = client.systemOne(SystemOneRequest.builder()
                    .stateFrom(Map.of("text", "hello"))
                    .question("truth", Questions.noul(
                            Json.object().put("prompt", "Be strict").build(),
                            new NoulCriteria(Json.of("yes"), JsonNull.INSTANCE)))
                    .question("pick", Questions.choice(
                            Json.array(Json.of("Choose"), Json.object().put("language", "en").build()),
                            criteria("a", Json.object().put("label", "A").build(), "b", JsonNull.INSTANCE)))
                    .question("rating", Questions.score(
                            Json.of("Rate quality"),
                            List.of(Json.of("bad"), Json.object().put("label", "good").build())))
                    .additionalProperty("metadata", Json.object().put("trace", true).build())
                    .build());
        }

        HttpRequest sent = transport.lastRequest();
        assertEquals("POST", sent.method());
        assertEquals("/v1/systemone", sent.uri().getPath());
        assertEquals("Bearer secret-value", sent.headers().firstValue("authorization").orElseThrow());
        assertEquals("application/json", sent.headers().firstValue("accept").orElseThrow());
        assertEquals("typesafe-sdk/0.6.0", sent.headers().firstValue("x-typesafe-sdk").orElseThrow());

        assertEquals(0.75, assertInstanceOf(NoulAnswer.class, result.answers().get("ok")).noul());
        assertEquals(0.9, assertInstanceOf(ChoiceAnswer.class, result.answers().get("pick"))
                .probabilities().get("a"));
        ScoreAnswer score = assertInstanceOf(ScoreAnswer.class, result.answers().get("rating"));
        assertEquals(0.8, score.probabilities().get("2"));
        assertInstanceOf(JsonObject.class, score.legend().get("2"));
        assertEquals(4, result.usage().inputTokens());

        JsonObject body = assertInstanceOf(JsonObject.class, Json.parse(bodyOf(sent)));
        assertEquals("hello", string(body.get("state"), "text"));
        assertEquals("jev-latest", ((ai.typesafe.json.JsonString) body.get("model")).value());
        assertEquals(Json.of(true), ((JsonObject) body.get("metadata")).get("trace"));

        JsonObject questions = (JsonObject) body.get("questions");
        JsonObject truth = (JsonObject) questions.get("truth");
        assertEquals("noul", ((ai.typesafe.json.JsonString) truth.get("type")).value());
        assertSame(JsonNull.INSTANCE, ((JsonObject) truth.get("criteria")).get("false"));
        assertInstanceOf(JsonObject.class, ((JsonObject) questions.get("pick")).get("criteria"));
        assertInstanceOf(JsonArray.class, ((JsonObject) questions.get("rating")).get("criteria"));
    }

    @Test
    void noulDefaultsSerializeNullInstructionsAndOptionalCriteriaMembers() {
        StubHttpClient transport = StubHttpClient.always(200, RESULT_JSON);
        try (TypeSafeClient client = client(transport)) {
            client.systemOne(SystemOneRequest.builder()
                    .state("state")
                    .question("x", Questions.noul(JsonNull.INSTANCE, NoulCriteria.empty()))
                    .build());
        }

        JsonObject body = (JsonObject) Json.parse(bodyOf(transport.lastRequest()));
        JsonObject question = (JsonObject) ((JsonObject) body.get("questions")).get("x");
        assertSame(JsonNull.INSTANCE, question.get("instructions"));
        JsonObject criteria = (JsonObject) question.get("criteria");
        assertSame(JsonNull.INSTANCE, criteria.get("true"));
        assertSame(JsonNull.INSTANCE, criteria.get("false"));
    }

    @Test
    void noulWithoutCriteriaOmitsTheMember() {
        StubHttpClient transport = StubHttpClient.always(200, RESULT_JSON);
        try (TypeSafeClient client = client(transport)) {
            client.systemOne(request());
        }

        JsonObject body = (JsonObject) Json.parse(bodyOf(transport.lastRequest()));
        JsonObject question = (JsonObject) ((JsonObject) body.get("questions")).get("x");
        assertFalse(question.has("criteria"));
    }

    @Test
    void protectedHeadersCannotBeOverridden() {
        StubHttpClient transport = StubHttpClient.always(200, RESULT_JSON);
        TypeSafeClientOptions clientOptions = options()
                .defaultHeaders(Map.of(
                        "Authorization", "bad",
                        "Accept", "text/plain",
                        "User-Agent", "bad",
                        "X-Custom", "default"))
                .build();

        try (TypeSafeClient client = new TypeSafeClient(clientOptions, transport)) {
            client.systemOne(request(), RequestOptions.builder()
                    .header("Authorization", "worse")
                    .header("Accept", "text/xml")
                    .header("Content-Type", "text/plain")
                    .header("X-Custom", "call")
                    .build());
        }

        HttpRequest sent = transport.lastRequest();
        assertEquals("Bearer secret-value", sent.headers().firstValue("authorization").orElseThrow());
        assertEquals("typesafe-sdk/0.6.0", sent.headers().firstValue("user-agent").orElseThrow());
        assertEquals("application/json", sent.headers().firstValue("accept").orElseThrow());
        assertEquals("application/json", sent.headers().firstValue("content-type").orElseThrow());
        assertEquals("call", sent.headers().firstValue("x-custom").orElseThrow());
    }

    @Test
    void perCallRetryPolicyLayersEverySetting() {
        StubHttpClient transport = StubHttpClient.sequence(
                new StubResponse.Spec(418, "{}"),
                new StubResponse.Spec(200, EMPTY_MODELS));

        try (TypeSafeClient client = client(transport)) {
            client.models(RequestOptions.builder()
                    .retry(RetryOptions.builder()
                            .maxRetries(1)
                            .initialDelay(Duration.ZERO)
                            .maxDelay(Duration.ZERO)
                            .subtractiveJitter(0)
                            .statusCodes(List.of(418))
                            .respectRetryAfter(false)
                            .maxRetryAfter(Duration.ZERO)
                            .retryConnectionErrors(false)
                            .retryTimeouts(false)
                            .build())
                    .build());
        }

        assertEquals(2, transport.attempts());
        assertTrue(transport.requests().get(0).headers().firstValue("x-typesafe-retry-count").isEmpty());
        assertEquals("1", transport.requests().get(1).headers()
                .firstValue("x-typesafe-retry-count").orElseThrow());
    }

    @Test
    void retryAfterMsTakesPrecedenceOverRetryAfter() {
        StubHttpClient transport = StubHttpClient.sequence(
                new StubResponse.Spec(429, "{}", "retry-after-ms", "1", "retry-after", "30"),
                new StubResponse.Spec(200, EMPTY_MODELS));

        long start = System.nanoTime();
        try (TypeSafeClient client = client(transport)) {
            client.models();
        }

        assertEquals(2, transport.attempts());
        assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofSeconds(2)) < 0);
    }

    @Test
    void invalidRetryAfterMsFallsThroughToRetryAfter() {
        StubHttpClient transport = StubHttpClient.sequence(
                new StubResponse.Spec(429, "{}", "retry-after-ms", "invalid", "retry-after", "0"),
                new StubResponse.Spec(200, EMPTY_MODELS));

        long start = System.nanoTime();
        try (TypeSafeClient client = client(transport)) {
            client.models(RequestOptions.builder()
                    .retry(RetryOptions.builder()
                            .initialDelay(Duration.ofSeconds(5))
                            .maxDelay(Duration.ofSeconds(5))
                            .subtractiveJitter(0)
                            .build())
                    .build());
        }

        assertEquals(2, transport.attempts());
        assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofSeconds(2)) < 0);
    }

    @Test
    void overCapRetryAfterMsFallsBackToBackoffRatherThanRetryAfter() {
        StubHttpClient transport = StubHttpClient.sequence(
                new StubResponse.Spec(429, "{}", "retry-after-ms", "61000", "retry-after", "30"),
                new StubResponse.Spec(200, EMPTY_MODELS));

        long start = System.nanoTime();
        try (TypeSafeClient client = client(transport)) {
            client.models(RequestOptions.builder()
                    .retry(RetryOptions.builder()
                            .initialDelay(Duration.ZERO)
                            .maxDelay(Duration.ZERO)
                            .build())
                    .build());
        }

        assertEquals(2, transport.attempts());
        assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofSeconds(2)) < 0);
    }

    @Test
    void parsesValidationErrorsAndStripsTheLeadingBodySegment() {
        String json = """
                {"detail":[{"loc":["body","questions","x"],"msg":"bad"},\
                {"loc":["body","model"],"msg":"missing"}]}""";
        StubHttpClient transport = StubHttpClient.always(
                422, json, "x-typesafe-request-id", "req_1");

        try (TypeSafeClient client = client(transport)) {
            UnprocessableEntityException error =
                    assertThrows(UnprocessableEntityException.class, () -> client.models(noRetry()));
            assertEquals("req_1", error.requestId());
            assertTrue(error.getMessage().contains("questions.x: bad; model: missing"));
            assertFalse(error.getMessage().contains("body.questions"));
            assertInstanceOf(JsonObject.class, error.body());
        }
    }

    @Test
    void keepsRawTextBodyAndTruncatesItInTheMessage() {
        String body = "x".repeat(250);
        StubHttpClient transport = StubHttpClient.always(400, body, "content-type", "text/plain");

        try (TypeSafeClient client = client(transport)) {
            BadRequestException error =
                    assertThrows(BadRequestException.class, () -> client.models(noRetry()));
            assertEquals(body, error.body());
            assertFalse(error.getMessage().contains("x".repeat(201)));
            assertTrue(error.getMessage().contains("x".repeat(200)));
        }
    }

    @Test
    void rateLimitErrorExposesValidRetryDelay() {
        StubHttpClient transport = StubHttpClient.always(
                429, "{\"error\":{\"message\":\"slow down\"}}", "retry-after-ms", "1250");

        try (TypeSafeClient client = client(transport)) {
            RateLimitException error =
                    assertThrows(RateLimitException.class, () -> client.models(noRetry()));
            assertEquals(Duration.ofMillis(1250), error.retryDelay().orElseThrow());
            assertTrue(error.getMessage().contains("slow down"));
        }
    }

    @Test
    void mapsEveryStatusToItsOwnType() {
        Map<Integer, Class<? extends ApiException>> expected = new LinkedHashMap<>();
        expected.put(400, BadRequestException.class);
        expected.put(401, AuthenticationException.class);
        expected.put(403, PermissionDeniedException.class);
        expected.put(404, NotFoundException.class);
        expected.put(422, UnprocessableEntityException.class);
        expected.put(429, RateLimitException.class);
        expected.put(500, InternalServerException.class);
        expected.put(418, ApiException.class);

        expected.forEach((status, type) -> {
            try (TypeSafeClient client = client(StubHttpClient.always(status, "{}"))) {
                assertInstanceOf(type, assertThrows(ApiException.class, () -> client.models(noRetry())));
            }
        });
    }

    @Test
    void timeoutMapsToApiTimeoutException() {
        StubHttpClient transport = new StubHttpClient((request, attempt) -> new CompletableFuture<>());

        try (TypeSafeClient client = client(transport)) {
            ApiTimeoutException error = assertThrows(ApiTimeoutException.class, () ->
                    client.models(RequestOptions.builder()
                            .timeout(Duration.ofMillis(20))
                            .retry(RetryOptions.builder().maxRetries(0).build())
                            .build()));
            assertInstanceOf(ApiConnectionException.class, error);
            assertEquals(Duration.ofMillis(20), error.timeout());
        }
    }

    @Test
    void timeoutsAreRetried() {
        StubHttpClient transport = new StubHttpClient((request, attempt) -> attempt == 1
                ? new CompletableFuture<>()
                : CompletableFuture.completedFuture(
                        StubResponse.of(request, 200, EMPTY_MODELS)));

        try (TypeSafeClient client = client(transport)) {
            List<ModelCard> models = client.models(RequestOptions.builder()
                    .timeout(Duration.ofMillis(20))
                    .retry(RetryOptions.builder().maxRetries(1).retryTimeouts(true).build())
                    .build());
            assertTrue(models.isEmpty());
        }
        assertEquals(2, transport.attempts());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsNonPositivePerCallTimeout(long milliseconds) {
        try (TypeSafeClient client = client(StubHttpClient.always(200, EMPTY_MODELS))) {
            assertThrows(TypeSafeException.class, () -> client.models(RequestOptions.builder()
                    .timeout(Duration.ofMillis(milliseconds))
                    .build()));
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsNonPositiveClientTimeout(long milliseconds) {
        assertThrows(TypeSafeException.class, () -> new TypeSafeClient(
                options().timeout(Duration.ofMillis(milliseconds)).build(),
                StubHttpClient.always(200, EMPTY_MODELS)));
    }

    @Test
    void interruptingABlockedCallMapsToUserAbort() throws Exception {
        StubHttpClient transport = new StubHttpClient((request, attempt) -> new CompletableFuture<>());
        CountDownLatch started = new CountDownLatch(1);
        CompletableFuture<Throwable> failure = new CompletableFuture<>();

        try (TypeSafeClient client = client(transport)) {
            Thread caller = new Thread(() -> {
                started.countDown();
                try {
                    client.models(RequestOptions.builder().timeout(Duration.ofSeconds(30)).build());
                    failure.complete(null);
                } catch (Throwable thrown) {
                    failure.complete(thrown);
                }
            });
            caller.start();
            assertTrue(started.await(5, TimeUnit.SECONDS));
            Thread.sleep(100);
            caller.interrupt();

            assertInstanceOf(ApiUserAbortException.class, failure.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void cancellingTheFutureMapsToUserAbort() {
        StubHttpClient transport = new StubHttpClient((request, attempt) -> new CompletableFuture<>());

        try (TypeSafeClient client = client(transport)) {
            CompletableFuture<List<ModelCard>> pending = client.modelsAsync(
                    RequestOptions.builder().timeout(Duration.ofSeconds(30)).build());
            pending.cancel(true);
            assertInstanceOf(
                    ApiUserAbortException.class,
                    assertThrows(java.util.concurrent.ExecutionException.class, pending::get).getCause());
        }
    }

    @Test
    void cancellationWhileWaitingToRetryMapsToUserAbort() {
        StubHttpClient transport = StubHttpClient.always(500, "{}");

        try (TypeSafeClient client = client(transport)) {
            CompletableFuture<List<ModelCard>> pending = client.modelsAsync(RequestOptions.builder()
                    .retry(RetryOptions.builder()
                            .maxRetries(1)
                            .initialDelay(Duration.ofSeconds(10))
                            .maxDelay(Duration.ofSeconds(10))
                            .build())
                    .build());

            assertThrows(java.util.concurrent.TimeoutException.class,
                    () -> pending.get(100, TimeUnit.MILLISECONDS));
            pending.cancel(true);
            assertInstanceOf(
                    ApiUserAbortException.class,
                    assertThrows(java.util.concurrent.ExecutionException.class, pending::get).getCause());
        }
        assertEquals(1, transport.attempts());
    }

    @Test
    void retriesConnectionFailures() {
        StubHttpClient transport = new StubHttpClient((request, attempt) -> attempt == 1
                ? CompletableFuture.failedFuture(new IOException("body failed"))
                : CompletableFuture.completedFuture(StubResponse.of(request, 200, EMPTY_MODELS)));

        try (TypeSafeClient client = client(transport)) {
            assertTrue(client.models().isEmpty());
        }
        assertEquals(2, transport.attempts());
    }

    @Test
    void surfacesConnectionFailuresWhenRetriesAreExhausted() {
        StubHttpClient transport = new StubHttpClient((request, attempt) ->
                CompletableFuture.failedFuture(new IOException("refused")));

        try (TypeSafeClient client = client(transport)) {
            ApiConnectionException error =
                    assertThrows(ApiConnectionException.class, () -> client.models(noRetry()));
            assertInstanceOf(IOException.class, error.getCause());
        }
    }

    @Test
    void responseWrapperRetainsBodyAndRequestId() {
        StubHttpClient transport = StubHttpClient.always(
                200, EMPTY_MODELS, "x-typesafe-request-id", "req_2");

        try (TypeSafeClient client = client(transport)) {
            ApiResponse<List<ModelCard>> response = client.modelsWithResponse();
            assertEquals("req_2", response.requestId().orElseThrow());
            assertEquals(EMPTY_MODELS, response.rawBody());
            assertEquals(200, response.statusCode());
        }
    }

    @Test
    void parsesModelCards() {
        String json = """
                {"models":[{"name":"jev-latest","description":"The latest Jev model",\
                "release_date":"2026-01-01"}]}""";

        try (TypeSafeClient client = client(StubHttpClient.always(200, json))) {
            ModelCard card = client.models().get(0);
            assertEquals("jev-latest", card.name());
            assertEquals("2026-01-01", card.releaseDate());
        }
    }

    @Test
    void passesTheReleaseDateThroughWhateverItsFormat() {
        String json = """
                {"models":[{"name":"jev-latest","description":"d",\
                "release_date":"2026-09-10T18:38:01.391457+00:00"}]}""";

        try (TypeSafeClient client = client(StubHttpClient.always(200, json))) {
            assertEquals("2026-09-10T18:38:01.391457+00:00", client.models().get(0).releaseDate());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"models\":{}}",
            "{\"models\":[{}]}",
            "{\"models\":[{\"name\":null,\"description\":\"d\",\"release_date\":\"r\"}]}",
            "{\"models\":[{\"name\":\"n\",\"description\":null,\"release_date\":\"r\"}]}",
            "{\"models\":[{\"name\":\"n\",\"description\":\"d\",\"release_date\":null}]}",
            "not json"})
    void rejectsMalformedModelsShape(String json) {
        try (TypeSafeClient client = client(StubHttpClient.always(200, json))) {
            assertThrows(TypeSafeException.class, client::models);
        }
    }

    @Test
    void rejectsUnknownAnswerType() {
        String json = """
                {"model":"m","answers":{"x":{"type":"other"}},\
                "usage":{"input_tokens":1,"output_tokens":1}}""";

        try (TypeSafeClient client = client(StubHttpClient.always(200, json))) {
            TypeSafeException error =
                    assertThrows(TypeSafeException.class, () -> client.systemOne(request()));
            assertTrue(error.getMessage().contains("unknown type 'other'"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"model\":\"m\",\"answers\":{\"x\":{\"type\":\"choice\",\"choice\":null,"
                    + "\"confidence\":0.5,\"probabilities\":{}}},"
                    + "\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}",
            "{\"model\":\"m\",\"answers\":{\"x\":{\"type\":\"choice\",\"choice\":\"a\","
                    + "\"confidence\":0.5,\"probabilities\":null}},"
                    + "\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}",
            "{\"model\":\"m\",\"answers\":{\"x\":{\"type\":\"score\",\"score\":1,"
                    + "\"confidence\":0.5,\"legend\":null,\"probabilities\":{}}},"
                    + "\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}",
            "{\"model\":\"m\",\"answers\":{\"x\":{\"type\":\"score\",\"score\":1,"
                    + "\"confidence\":0.5,\"legend\":{},\"probabilities\":null}},"
                    + "\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}",
            "{\"model\":\"m\",\"answers\":{},\"usage\":{\"input_tokens\":1}}",
            "{\"answers\":{},\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}"})
    void rejectsMalformedSystemOneShape(String json) {
        try (TypeSafeClient client = client(StubHttpClient.always(200, json))) {
            assertThrows(TypeSafeException.class, () -> client.systemOne(request()));
        }
    }

    @Test
    void validatesQuestionsWithSdkErrors() {
        assertThrows(TypeSafeException.class, () -> Questions.score("instructions", "only"));
        assertThrows(TypeSafeException.class, () -> Questions.choice("instructions", Map.of()));
        assertThrows(TypeSafeException.class, () -> Questions.noul(Json.of(42)));
        assertThrows(TypeSafeException.class, () -> Questions.noul(Json.of(true)));
        assertThrows(TypeSafeException.class, () -> SystemOneRequest.builder()
                .state("state")
                .build());
        assertThrows(TypeSafeException.class, () -> SystemOneRequest.builder()
                .state(Json.of(1))
                .question("x", Questions.noul())
                .build());
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 0, 0, 0.0, 200",
            "0, -1, 0, 0.0, 200",
            "0, 0, -1, 0.0, 200",
            "0, 0, 0, -0.1, 200",
            "0, 0, 0, 1.1, 200",
            "0, 0, 0, 0.0, 99",
            "0, 0, 0, 0.0, 1000"})
    void validatesRetryConfiguration(
            int retries, int initialMillis, int maxMillis, double jitter, int status) {
        assertThrows(TypeSafeException.class, () -> new TypeSafeClient(TypeSafeClientOptions.builder()
                .apiKey("key")
                .environment(name -> null)
                .retry(RetryOptions.builder()
                        .maxRetries(retries)
                        .initialDelay(Duration.ofMillis(initialMillis))
                        .maxDelay(Duration.ofMillis(maxMillis))
                        .subtractiveJitter(jitter)
                        .statusCodes(List.of(status))
                        .build())
                .build()));
    }

    @Test
    void acceptsRetryStatus600AndExposesImmutableResolvedSettings() {
        Map<String, String> headers = new LinkedHashMap<>(Map.of("X-Test", "original"));
        List<Integer> statuses = new java.util.ArrayList<>(List.of(600));

        try (TypeSafeClient client = new TypeSafeClient(TypeSafeClientOptions.builder()
                .apiKey("key")
                .environment(name -> null)
                .baseUrl("https://example.test/base")
                .defaultModel("model")
                .logLevel(LogLevel.INFO)
                .timeout(Duration.ofSeconds(3))
                .defaultHeaders(headers)
                .retry(RetryOptions.builder().statusCodes(statuses).maxRetries(4).build())
                .build(), StubHttpClient.always(200, EMPTY_MODELS))) {
            headers.put("X-Test", "changed");
            statuses.add(601);

            assertEquals("model", client.defaultModel());
            assertEquals(LogLevel.INFO, client.logLevel());
            assertEquals(Duration.ofSeconds(3), client.timeout());
            assertEquals(4, client.retry().maxRetries());
            assertTrue(client.retry().statusCodes().contains(600));
            assertFalse(client.retry().statusCodes().contains(601));
            assertEquals("original", client.defaultHeaders().get("X-Test"));
            assertThrows(UnsupportedOperationException.class,
                    () -> client.defaultHeaders().put("X-Test", "mutated"));
            assertThrows(UnsupportedOperationException.class,
                    () -> client.retry().statusCodes().add(602));
        }
    }

    @Test
    void redactsCredentialsAndCookies() {
        String text = CredentialRedactor.redactHeaders(new LinkedHashMap<>(Map.of(
                "Authorization", List.of("Bearer long-secret-value"),
                "x-api-key", List.of("short"),
                "Cookie", List.of("session=abc; theme=dark"),
                "Set-Cookie", List.of("session=abc"),
                "Accept", List.of("application/json"))));

        assertTrue(text.contains("Bearer ***alue"));
        assertTrue(text.contains("x-api-key: ***"));
        assertFalse(text.contains("long-secret"));
        assertFalse(text.contains("session=abc"));
        assertTrue(text.contains("Cookie: ***"));
        assertTrue(text.contains("application/json"));
    }

    @Test
    void loggerNeverReceivesAnUnredactedApiKey() {
        TestSupport.RecordingLogger logger = new TestSupport.RecordingLogger();

        try (TypeSafeClient client = new TypeSafeClient(
                options().logger(logger).logLevel(LogLevel.DEBUG).build(),
                StubHttpClient.always(200, EMPTY_MODELS))) {
            client.models();
        }

        assertFalse(logger.message().contains("secret-value"));
        assertTrue(logger.message().contains("Bearer ***alue"));
    }

    @Test
    void loggerRespectsTheConfiguredLevel() {
        TestSupport.RecordingLogger logger = new TestSupport.RecordingLogger();

        try (TypeSafeClient client = new TypeSafeClient(
                options().logger(logger).logLevel(LogLevel.INFO).build(),
                StubHttpClient.always(200, EMPTY_MODELS))) {
            client.models();
        }

        assertFalse(logger.levels().contains(LogLevel.DEBUG));
        assertTrue(logger.levels().contains(LogLevel.INFO));
    }

    @Test
    void loggerIsSilentWhenTurnedOff() {
        TestSupport.RecordingLogger logger = new TestSupport.RecordingLogger();

        try (TypeSafeClient client = new TypeSafeClient(
                options().logger(logger).logLevel(LogLevel.OFF).build(),
                StubHttpClient.always(200, EMPTY_MODELS))) {
            client.models();
        }

        assertTrue(logger.levels().isEmpty());
    }

    @Test
    void explicitOptionsWinBlankEnvironmentIsIgnoredAndBaseUrlIsNormalized() {
        StubHttpClient transport = StubHttpClient.always(200, EMPTY_MODELS);
        Map<String, String> environment = Map.of(
                "TYPESAFE_API_KEY", "environment-key",
                "TYPESAFE_BASE_URL", "   ");

        try (TypeSafeClient client = new TypeSafeClient(TypeSafeClientOptions.builder()
                .apiKey("explicit-key")
                .baseUrl("https://example.test/base///")
                .environment(TestSupport.environment(environment))
                .build(), transport)) {
            client.models();
        }

        HttpRequest sent = transport.lastRequest();
        assertEquals("Bearer explicit-key", sent.headers().firstValue("authorization").orElseThrow());
        assertEquals("example.test", sent.uri().getHost());
        assertEquals("/base/v1/models", sent.uri().getPath());
    }

    @Test
    void fallsBackToTheEnvironmentWhenNothingIsSet() {
        Map<String, String> environment = Map.of(
                "TYPESAFE_API_KEY", "environment-key",
                "TYPESAFE_BASE_URL", "https://env.test",
                "TYPESAFE_DEFAULT_MODEL", "env-model",
                "TYPESAFE_LOG_LEVEL", "error");

        try (TypeSafeClient client = new TypeSafeClient(TypeSafeClientOptions.builder()
                .environment(TestSupport.environment(environment))
                .build(), StubHttpClient.always(200, EMPTY_MODELS))) {
            assertEquals("https://env.test", client.baseUrl().toString());
            assertEquals("env-model", client.defaultModel());
            assertEquals(LogLevel.ERROR, client.logLevel());
        }
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void explicitBlankKeyOrModelDoesNotFallThroughToTheEnvironment(boolean blankKey) {
        Map<String, String> environment = Map.of(
                "TYPESAFE_API_KEY", "environment-key",
                "TYPESAFE_DEFAULT_MODEL", "environment-model");

        assertThrows(TypeSafeException.class, () -> new TypeSafeClient(TypeSafeClientOptions.builder()
                .apiKey(blankKey ? "   " : "explicit-key")
                .defaultModel(blankKey ? "explicit-model" : "   ")
                .environment(TestSupport.environment(environment))
                .build()));
    }

    @Test
    void missingApiKeyIsAnSdkError() {
        TypeSafeException error = assertThrows(TypeSafeException.class, () ->
                new TypeSafeClient(TypeSafeClientOptions.builder().environment(name -> null).build()));
        assertTrue(error.getMessage().contains("API key is required"));
    }

    @Test
    void apiErrorMessagesMatchUpstreamStyle() {
        assertEquals("400 status code (no body)", messageFor(400, ""));
        assertEquals("400 bad input", messageFor(400, "{\"message\":\"bad input\"}"));
        assertEquals("400 boom", messageFor(400, "{\"error\":\"boom\"}"));
        assertEquals("400 nested", messageFor(400, "{\"error\":{\"message\":\"nested\"}}"));
        assertEquals("400 " + "x".repeat(200) + "…", messageFor(400, "x".repeat(201)));
    }

    @ParameterizedTest
    @CsvSource({
            "trace, TRACE",
            "debug, DEBUG",
            "info, INFO",
            "warn, WARN",
            "error, ERROR",
            "off, OFF"})
    void acceptsUpstreamEnvironmentLogLevels(String value, LogLevel expected) {
        try (TypeSafeClient client = new TypeSafeClient(TypeSafeClientOptions.builder()
                .apiKey("key")
                .environment(TestSupport.environment(Map.of("TYPESAFE_LOG_LEVEL", value)))
                .build(), StubHttpClient.always(200, EMPTY_MODELS))) {
            assertEquals(expected, client.logLevel());
        }
    }

    @Test
    void invalidEnvironmentLogLevelThrows() {
        assertThrows(TypeSafeException.class, () -> new TypeSafeClient(TypeSafeClientOptions.builder()
                .apiKey("key")
                .environment(TestSupport.environment(Map.of("TYPESAFE_LOG_LEVEL", "verbose-ish")))
                .build()));
    }

    @Test
    void invalidBaseUrlThrows() {
        assertThrows(TypeSafeException.class, () -> new TypeSafeClient(TypeSafeClientOptions.builder()
                .apiKey("key")
                .baseUrl("not-a-url")
                .environment(name -> null)
                .build()));
    }

    @Test
    void injectedHttpClientIsNotClosed() {
        StubHttpClient transport = StubHttpClient.always(200, EMPTY_MODELS);
        new TypeSafeClient(options().build(), transport).close();
        assertFalse(transport.isClosed());
    }

    private static String messageFor(int status, String body) {
        try (TypeSafeClient client = client(StubHttpClient.always(status, body))) {
            return assertThrows(ApiException.class, () -> client.models(noRetry())).getMessage();
        }
    }

    private static Map<String, JsonValue> criteria(Object... pairs) {
        Map<String, JsonValue> criteria = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            criteria.put((String) pairs[index], (JsonValue) pairs[index + 1]);
        }
        return criteria;
    }

    private static String string(JsonValue value, String member) {
        return ((ai.typesafe.json.JsonString) ((JsonObject) value).get(member)).value();
    }

    private static String bodyOf(HttpRequest request) {
        return request.bodyPublisher()
                .map(BodyCollector::collect)
                .orElseThrow(() -> new IllegalStateException("The request had no body."));
    }
}
