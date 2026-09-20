package ai.typesafe;

public record NoulAnswer(double noul) implements Answer {
    @Override
    public String type() {
        return "noul";
    }
}
