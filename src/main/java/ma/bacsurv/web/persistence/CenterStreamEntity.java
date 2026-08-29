package ma.bacsurv.web.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A filière this centre runs.
 *
 * <p>Distinct from {@link StreamEntity}, which is one filière <em>within a
 * session</em> together with the rooms it occupies there. This is the centre's
 * catalogue: the list a session picks from.
 */
@Entity
@Table(name = "center_stream")
public class CenterStreamEntity extends CatalogueEntry {

    /**
     * The level whose candidates sit it: {@code BAC1} or {@code BAC2}.
     *
     * <p>The same name can belong to both — a centre runs Sciences
     * expérimentales at each level — so this is part of what makes a filière
     * itself, not a label on it, and the name alone is not unique.
     */
    @Column(nullable = false, length = 10)
    private String level;

    protected CenterStreamEntity() {}

    public CenterStreamEntity(CenterEntity center, String name, String level) {
        this(center, name, null, level);
    }

    public CenterStreamEntity(CenterEntity center, String name, String nameFr, String level) {
        super(center, name, nameFr);
        this.level = level;
    }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    /**
     * The level a session of this type examines.
     *
     * <p>Only the régional is 1BAC; the national and its rattrapage are both
     * 2BAC. This lives here rather than in a screen because it is a fact about
     * the Moroccan calendar, not about how the interface is arranged.
     */
    public static String levelOf(String operationType) {
        return "REGIONAL_1BAC".equals(operationType) ? "BAC1" : "BAC2";
    }
}
