package ma.bacsurv.web.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One scheduling run of a center: its slots, its exams, its history. */
@Entity
@Table(name = "operation")
public class OperationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "center_id", nullable = false)
    private CenterEntity center;

    @Column(nullable = false)
    private String reference;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "operation", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("date asc, startTime asc")
    private List<ExamSlotEntity> slots = new ArrayList<>();

    protected OperationEntity() {}

    public OperationEntity(CenterEntity center, String reference, String type) {
        this.center = center;
        this.reference = reference;
        this.type = type;
        this.createdAt = Instant.now();
    }

    public void addSlot(ExamSlotEntity slot) {
        slots.add(slot);
    }

    public Long getId() { return id; }
    public CenterEntity getCenter() { return center; }
    public String getReference() { return reference; }
    public String getType() { return type; }
    public Instant getCreatedAt() { return createdAt; }
    public List<ExamSlotEntity> getSlots() { return slots; }
}
