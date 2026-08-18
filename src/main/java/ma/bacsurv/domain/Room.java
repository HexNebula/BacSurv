package ma.bacsurv.domain;

public record Room(String id, String label) {
    public Room {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("room id required");
    }
}
