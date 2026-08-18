package ma.bacsurv.domain;

public record Subject(String name) {
    public Subject {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("subject name required");
    }
}
