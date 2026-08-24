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

    @Column(nullable = false, length = 120)
    private String name;

    protected CatalogueEntry() {}

    protected CatalogueEntry(CenterEntity center, String name) {
        this.center = center;
        this.name = name;
    }

    public void rename(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public CenterEntity getCenter() { return center; }
    public String getName() { return name; }
}
