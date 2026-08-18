package ma.bacsurv.domain;

/** Filière — e.g. Sciences Expérimentales, Lettres, Sciences Maths. */
public record Stream(String name) {
    public Stream {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("stream name required");
    }
}
