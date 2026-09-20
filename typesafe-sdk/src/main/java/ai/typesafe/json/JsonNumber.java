package ai.typesafe.json;

import java.math.BigDecimal;
import java.util.Objects;

public record JsonNumber(BigDecimal value) implements JsonValue {
    public JsonNumber {
        Objects.requireNonNull(value, "value");
    }

    public double doubleValue() {
        return value.doubleValue();
    }

    public int intValueExact() {
        return value.intValueExact();
    }

    @Override
    public String toJson() {
        return value.scale() <= 0 ? value.toBigInteger().toString() : value.toPlainString();
    }

    @Override
    public String toString() {
        return toJson();
    }
}
