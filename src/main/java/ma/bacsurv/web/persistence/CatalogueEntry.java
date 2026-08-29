package ma.bacsurv.web.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;

/**
 * One entry of a centre's own lists — a subject it examines, a filière it runs.
 *
 * <p>Both behave identically: a name, unique within the centre, chosen from a
 * picker rather than typed. They are separate tables because they are separate
 * lists to the administrator, and sharing a table would mean every query had to
 * remember to filter by kind.
 */
@MappedSuperclass
public abstract class CatalogueEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "center_id", nullable = false)
    private CenterEntity center;

    /**
     * The name in Arabic — and the one that is compared.
     *
     * <p>Teachers and épreuves store this string, and the solver matches a
     * teacher's subject to an épreuve's by exact equality. There can therefore
     * only ever be one such name: a second one that anything joined on would
     * let a teacher stored as « Mathématiques » stop matching an épreuve stored
     * as « الرياضيات », and the conflict rule would quietly stop applying.
     */
    @Column(nullable = false, length = 120)
    private String name;

    /**
     * The same entry in French, for documents written in French.
     *
     * <p>A label. Nothing copies it onto a teacher or an épreuve, nothing
     * matches on it, and an entry without one prints in Arabic.
     */
    @Column(name = "name_fr", length = 120)
    private String nameFr;

    protected CatalogueEntry() {}

    protected CatalogueEntry(CenterEntity center, String name) {
        this(center, name, null);
    }

    protected CatalogueEntry(CenterEntity center, String name, String nameFr) {
        this.center = center;
        this.name = name;
        this.nameFr = nameFr;
    }

    public void rename(String name) {
        this.name = name;
    }

    /** The French label, changed on its own: it names nothing else. */
    public void relabel(String nameFr) {
        this.nameFr = nameFr;
    }

    public Long getId() { return id; }
    public CenterEntity getCenter() { return center; }
    public String getName() { return name; }
    public String getNameFr() { return nameFr; }
}
