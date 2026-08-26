package ma.bacsurv.web.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An exam center: the scope of one solve, and the owner of rooms and teachers. */
@Entity
@Table(name = "center")
public class CenterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /**
     * How the establishment is identified on paper.
     *
     * <p>The académie régionale, the direction provinciale, the commune and the
     * ministerial reference of the centre. The solver never reads any of it —
     * it exists to be printed at the head of a convocation and a room list, and
     * it is nullable because a centre works perfectly well before anybody has
     * typed its administrative address.
     */
    private String academy;

    private String directorate;

    private String commune;

    @Column(name = "ministerial_reference")
    private String ministerialReference;

    /**
     * When anything the centre owns and a distribution depends on last moved:
     * the pool, the absences, the rooms, the catalogue. Compared against a
     * job's finish time to know whether what is on screen is still current.
     */
    @Column(name = "changed_at")
    private java.time.Instant changedAt = java.time.Instant.now();

    protected CenterEntity() {}

    public CenterEntity(String name) {
        this.name = name;
    }

    public Long getId() { return id; }

    public java.time.Instant getChangedAt() { return changedAt; }

    /** Called by every service that alters what a distribution is built from. */
    public void touch() { this.changedAt = java.time.Instant.now(); }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAcademy() { return academy; }
    public void setAcademy(String academy) { this.academy = academy; }

    public String getDirectorate() { return directorate; }
    public void setDirectorate(String directorate) { this.directorate = directorate; }

    public String getCommune() { return commune; }
    public void setCommune(String commune) { this.commune = commune; }

    public String getMinisterialReference() { return ministerialReference; }
    public void setMinisterialReference(String ministerialReference) {
        this.ministerialReference = ministerialReference;
    }
}
