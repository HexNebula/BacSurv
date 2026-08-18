package ma.bacsurv.web.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A room of a center. {@code reference} is the code used in input files (R1, R2…). */
@Entity
@Table(name = "room")
public class RoomEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "center_id", nullable = false)
    private CenterEntity center;

    @Column(nullable = false)
    private String reference;

    @Column(nullable = false)
    private String label;

    protected RoomEntity() {}

    public RoomEntity(CenterEntity center, String reference, String label) {
        this.center = center;
        this.reference = reference;
        this.label = label;
    }

    public Long getId() { return id; }
    public CenterEntity getCenter() { return center; }
    public String getReference() { return reference; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
}
