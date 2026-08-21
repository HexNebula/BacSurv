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
import java.time.LocalDate;
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

    /**
     * The days the session runs over. Held on the session itself so its
     * planning grid can be drawn before any épreuve exists — read back from
     * the slots, an empty session would have no days at all.
     */
    @Column(name = "starts_on")
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @OneToMany(mappedBy = "operation", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("date asc, startTime asc")
    private List<ExamSlotEntity> slots = new ArrayList<>();

    protected OperationEntity() {}

    public OperationEntity(CenterEntity center, String reference, String type) {
        this(center, reference, type, null, null);
    }

    public OperationEntity(CenterEntity center, String reference, String type,
                           LocalDate startsOn, LocalDate endsOn) {
        this.center = center;
        this.reference = reference;
        this.type = type;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
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

    public LocalDate getStartsOn() { return startsOn; }
    public LocalDate getEndsOn() { return endsOn; }
    public void setDates(LocalDate startsOn, LocalDate endsOn) {
        this.startsOn = startsOn;
        this.endsOn = endsOn;
    }

    /**
     * The days the planning grid shows: what was declared, or failing that
     * what the épreuves already entered imply — a session imported before the
     * dates were held here still has to be readable.
     */
    public List<LocalDate> days() {
        LocalDate from = startsOn, to = endsOn;
        if (from == null || to == null) {
            from = slots.stream().map(ExamSlotEntity::getDate).min(LocalDate::compareTo).orElse(null);
            to = slots.stream().map(ExamSlotEntity::getDate).max(LocalDate::compareTo).orElse(null);
        }
        if (from == null || to == null || to.isBefore(from)) return List.of();

        List<LocalDate> days = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) days.add(day);
        return List.copyOf(days);
    }
}
