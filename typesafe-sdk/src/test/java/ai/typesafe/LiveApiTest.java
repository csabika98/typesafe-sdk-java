package ai.typesafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.typesafe.json.Json;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
class LiveApiTest {

    private static TypeSafeClient client() {
        return new TypeSafeClient(TypeSafeClientOptions.builder()
                .timeout(Duration.ofSeconds(30))
                .build());
    }

    @Test
    void listsModels() {
        try (TypeSafeClient client = client()) {
            List<ModelCard> models = client.models();
            assertFalse(models.isEmpty(), "The API returned no models.");
            models.forEach(model -> {
                assertNotNull(model.name());
                assertNotNull(model.description());
                assertNotNull(model.releaseDate());
            });
            System.out.println("Models: " + models.stream().map(ModelCard::name).toList());
        }
    }

    @Test
    void answersOneQuestionOfEachType() {
        try (TypeSafeClient client = client()) {
            ApiResponse<SystemOneResult> response = client.systemOneWithResponse(
                    SystemOneRequest.builder()
                            .state(Json.object()
                                    .put("ticket", Json.object()
                                            .put("subject", "Checkout fails with a 500 error")
                                            .put("body", "Paying with a saved card returns an error page.")
                                            .build())
                                    .build())
                            .question("is_bug", Questions.noul(
                                    "Does the ticket describe a defect in the product?",
                                    NoulCriteria.of(
                                            "The customer reports broken behaviour.",
                                            "The ticket is a question or a billing issue.")))
                            .question("category", Questions.choiceOf(
                                    "Which team should own this ticket?",
                                    "billing", "Payments, invoices, and refunds.",
                                    "platform", "Availability and errors of the website.",
                                    "account", "Login, permissions, and provisioning."))
                            .question("urgency", Questions.score(
                                    "How urgent is this ticket?",
                                    "not urgent", "low", "medium", "high", "drop everything"))
                            .build());

            SystemOneResult result = response.data();
            assertEquals(3, result.answers().size());

            NoulAnswer isBug = assertInstanceOf(NoulAnswer.class, result.answers().get("is_bug"));
            assertTrue(isBug.noul() >= 0 && isBug.noul() <= 1);

            ChoiceAnswer category = assertInstanceOf(ChoiceAnswer.class, result.answers().get("category"));
            assertTrue(category.probabilities().containsKey(category.choice()));

            ScoreAnswer urgency = assertInstanceOf(ScoreAnswer.class, result.answers().get("urgency"));
            assertFalse(urgency.legend().isEmpty());

            assertTrue(result.usage().inputTokens() > 0);
            System.out.println("Model      : " + result.model());
            System.out.println("Request id : " + response.requestId().orElse("(none)"));
            System.out.println("is_bug     : " + isBug.noul());
            System.out.println("category   : " + category.choice() + " " + category.probabilities());
            System.out.println("urgency    : " + urgency.score() + " legend " + urgency.legend());
            System.out.println("usage      : " + result.usage());
        }
    }
}
