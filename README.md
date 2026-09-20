# Unofficial TypeSafe.ai Java SDK

An idiomatic Java port of [`typesafe-ai/typesafe-sdk-js`](https://github.com/typesafe-ai/typesafe-sdk-js)
v0.6.0, by way of the .NET community port. The library targets Java 21, builds with Maven, and has
**no runtime dependencies**: the HTTP client is `java.net.http`, and JSON is handled by a small
parser and writer inside the SDK.

> [!IMPORTANT]
> This is an unofficial community port and is not supported by TypeSafe. Use it at your own risk.

## Quickstart

```java
import ai.typesafe.*;
import ai.typesafe.json.Json;

try (TypeSafeClient client = new TypeSafeClient(
        TypeSafeClientOptions.builder().apiKey("your-api-key").build())) {

    SystemOneResult result = client.systemOne(SystemOneRequest.builder()
            .state(Json.object().put("text", "Evaluate this statement.").build())
            .question("safe", Questions.noul(
                    "Assess safety.",
                    NoulCriteria.of("The statement is safe.", "The statement is unsafe.")))
            .question("topic", Questions.choiceOf(
                    "Choose a topic.",
                    "science", "Scientific content",
                    "other", null))
            .question("quality", Questions.score(
                    "Rate quality.", "poor", "good", "excellent"))
            .build());

    double safeProbability = ((NoulAnswer) result.answers().get("safe")).noul();
    List<ModelCard> models = client.models();
}
```

Answers are a sealed hierarchy, so a `switch` over them is exhaustive and needs no default:

```java
String describe(Answer answer) {
    return switch (answer) {
        case NoulAnswer noul -> "noul " + noul.noul();
        case ChoiceAnswer choice -> choice.choice() + " @ " + choice.confidence();
        case ScoreAnswer score -> score.score() + " @ " + score.confidence();
    };
}
```

State, instructions, and criterion descriptions are `JsonValue`s: a JSON string, object, array, or
null. A number or boolean is rejected, as upstream. Build them with `Json.of`, `Json.object()`,
`Json.array(...)`, or `Json.from(...)` for a plain `Map`, `List`, or array:

```java
JsonValue state = Json.from(Map.of("subject", "Checkout fails", "tier", "enterprise"));
```

Question maps cannot be empty, choice criteria cannot be empty, and score criteria need at least two
ordered entries. Noul criteria are optional and use the JSON keys `true` and `false`.
`SystemOneRequest.Builder.additionalProperty` forwards extra top-level JSON fields.

## Blocking and asynchronous calls

Every call comes in both forms:

```java
SystemOneResult result = client.systemOne(request);
CompletableFuture<SystemOneResult> pending = client.systemOneAsync(request);
```

Cancelling the future, or interrupting a thread blocked on the synchronous form, raises
`ApiUserAbortException` rather than `CancellationException`, so callers see one exception hierarchy.
In-flight HTTP is cancelled with it.

## Configuration and retries

Each setting resolves to the explicit value, then a non-blank environment variable, then the default:

| Option | Environment | Default |
|---|---|---|
| API key | `TYPESAFE_API_KEY` | required |
| Base URL | `TYPESAFE_BASE_URL` | `https://api.typesafe.ai` |
| Model | `TYPESAFE_DEFAULT_MODEL` | `jev-latest` |
| Log level | `TYPESAFE_LOG_LEVEL` (`trace`, `debug`, `info`, `warn`, `error`, `off`) | `WARN` |
| Timeout | — | 10 seconds |

A blank explicit value is an error rather than a fall through to the environment.

The retry defaults are two retries, 500 ms initial exponential backoff, a 5 second delay cap, 0.25
subtractive jitter, statuses 408/429/500–599, `Retry-After` support with a 60 second cap, and retries
for connection failures and timeouts. A syntactically valid, non-negative `retry-after-ms` takes
precedence over `Retry-After`; an invalid value falls through to `Retry-After`, while a valid value
above the cap falls back to backoff.

`TypeSafeClientOptions.retry(...)` partially overrides the defaults. `RequestOptions.retry(...)` then
partially overrides the client policy for one call, including every retry setting. Retry requests
carry a 1-based `X-TypeSafe-Retry-Count`.

```java
List<ModelCard> models = client.models(RequestOptions.builder()
        .timeout(Duration.ofSeconds(20))
        .retry(RetryOptions.builder().maxRetries(1).build())
        .header("X-Trace-Id", traceId)
        .build());
```

Resolved settings are readable from the client through `baseUrl()`, `defaultModel()`, `logLevel()`,
`timeout()`, `retry()`, and `defaultHeaders()`. Returned headers and retry status sets are
unmodifiable copies taken at construction.

## HTTP ownership, responses, and logging

Authentication, `Accept`, content type, User-Agent, `X-TypeSafe-SDK`, runtime, and retry headers are
protected from option overrides. User-Agent and `X-TypeSafe-SDK` are `typesafe-sdk/0.6.0`.

An injected `HttpClient` is never closed by `TypeSafeClient`; one it creates itself is closed with it.
`systemOneWithResponse` and `modelsWithResponse` return an `ApiResponse<T>` holding the parsed data,
the status code, the response headers, the raw body text, and `requestId()` from
`x-typesafe-request-id`. Nothing needs closing: the body has already been read.

Provide a `TypeSafeLogger` for logging integration. Without one, the SDK writes to `System.err`,
filtered by the log level. Credential logging keeps the authentication scheme and, only for secrets
longer than eight characters, the final four characters. Cookie and Set-Cookie values are fully
redacted.

## API errors

API errors preserve the parsed JSON, the raw non-JSON text, or a null body. Validation message
locations omit the leading `body` segment. Unknown answer types and malformed response shapes fail
with `TypeSafeException`.

| JavaScript SDK | .NET SDK | Java SDK |
|---|---|---|
| `TypeSafe` | `TypeSafeClient` | `TypeSafeClient` |
| `client.systemOne({ state, questions })` | `client.SystemOneAsync(...)` | `client.systemOne(...)` / `systemOneAsync(...)` |
| `client.models.list()` | `client.ModelsAsync()` | `client.models()` / `modelsAsync()` |
| `APIPromise.withResponse()` | `*WithResponseAsync()` | `*WithResponse` / `*WithResponseAsync` |
| `APIError` | `ApiError` | `ApiException` |
| `BadRequestError` | `BadRequestError` | `BadRequestException` |
| `AuthenticationError` | `AuthenticationError` | `AuthenticationException` |
| `PermissionDeniedError` | `PermissionDeniedError` | `PermissionDeniedException` |
| `NotFoundError` | `NotFoundError` | `NotFoundException` |
| `UnprocessableEntityError` | `UnprocessableEntityError` | `UnprocessableEntityException` |
| `RateLimitError` | `RateLimitError` | `RateLimitException` |
| `InternalServerError` | `InternalServerError` | `InternalServerException` |
| `APIConnectionError` | `ApiConnectionError` | `ApiConnectionException` |
| `APITimeoutError` | `ApiTimeoutError` | `ApiTimeoutException` |
| `APIUserAbortError` | `ApiUserAbortError` | `ApiUserAbortException` |

Every SDK exception is unchecked and descends from `TypeSafeException`, so a call site can catch one
type or none at all.

### Where the Java port differs from the .NET port

- **Timeouts.** .NET can inherit a timeout from an injected `HttpClient`. `java.net.http.HttpClient`
  has no such setting, so the timeout always comes from `TypeSafeClientOptions` or `RequestOptions`
  and is applied per request. It covers reading the response body, not only the headers.
- **Cancellation.** There is no `CancellationToken` parameter. Cancel the returned
  `CompletableFuture`, or interrupt the thread blocked on a synchronous call.
- **Disposal.** `ApiResponse<T>` is a plain record and is not closeable, because the body is
  already buffered.
- **JSON.** Instead of `object?` plus reflection, the API takes `JsonValue`, which makes the
  "string, object, array, or null" rule a compile-time matter wherever possible and keeps the jar
  dependency free.

## Modules

| Path | Contents |
|---|---|
| `typesafe-sdk` | The library and its tests |
| `typesafe-sample` | A runnable console example |

## Sample console application

`typesafe-sample` lists the available models, evaluates a support ticket with one `noul`, one
`choice`, and one `score` question, prints the typed answers, request id, and token usage, and maps
every SDK error to a distinct exit code.

```bash
export TYPESAFE_API_KEY="sk-..."
mvn -q -pl typesafe-sample -am exec:java
```

Point the sample at a different host with `TYPESAFE_BASE_URL`, or change the model with
`TYPESAFE_DEFAULT_MODEL`. Press Ctrl+C to cancel an in-flight request; the sample reports that as
`ApiUserAbortException` and exits with 130.

## Building and testing

```bash
mvn verify
mvn -pl typesafe-sdk test
mvn -pl typesafe-sdk package
```

The unit tests need no network: they run against a stub `HttpClient`.

`LiveApiTest` calls the real API and is skipped unless `TYPESAFE_API_KEY` is set, so `mvn verify`
is safe without a key and exercises the service when one is present:

```bash
export TYPESAFE_API_KEY="sk-..."
mvn -pl typesafe-sdk test -Dtest=LiveApiTest
```

It lists the models and asks one `noul`, one `choice`, and one `score` question, which is two API
calls in total.
# typesafe-sdk-java
