package ma.bacsurv.web.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    /**
     * The school year this session belongs to. What the pool is drawn from and
     * what fairness is counted inside — the régionale, the nationale and the
     * rattrapage of one year share a queue, and the next year starts a new one.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_year_id", nullable = false)
    private SchoolYearEntity schoolYear;

    @Column(nullable = false)
    private String reference;

    @Column(nullable = false, length = 40)
    private String type;

    /**
     * The year this session examines: {@code BAC1} or {@code BAC2}.
     *
     * <p>Held rather than derived from the type. A candidat libre's regional
     * rattrapage is a first-year session sat in the second-year season, so
     * « rattrapage, therefore 2BAC » is wrong — and wrong silently, since all
     * it does is offer the wrong filières.
     */
    @Column(nullable = false, length = 10)
    private String level;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Whether this session is still being prepared, or is the répartition that
     * actually went out.
     *
     * <p>The distinction is not cosmetic: it is what cumulative fairness counts.
     * A draft may be solved as often as anybody likes and none of it reaches the
     * privilege queue, so an administrator can try the nationale in April
     * without the régionale inheriting turns nobody took. Settling a session is
     * the act that makes its duties history — and, from that moment, the reason
     * it can no longer be deleted or quietly edited.
     */
    public enum State { DRAFT, SETTLED }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private State state = State.DRAFT;

    /**
     * The days the session runs over. Held on the session itself so its
     * planning grid can be drawn before any épreuve exists — read back from
     * the slots, an empty session would have no days at all.
     */
    @Column(name = "starts_on")
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    /**
     * When this session's own inputs last moved — its timetable, its rules. The
     * centre carries the rest; a distribution is stale when either has changed
     * since it was solved.
     */
    @Column(name = "changed_at")
    private Instant changedAt = Instant.now();

    @OneToMany(mappedBy = "operation", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("date asc, startTime asc")
    private List<ExamSlotEntity> slots = new ArrayList<>();

    protected OperationEntity() {}

    public OperationEntity(CenterEntity center, SchoolYearEntity schoolYear,
                           String reference, String type) {
        this(center, schoolYear, reference, type, null, null);
    }

    public OperationEntity(CenterEntity center, SchoolYearEntity schoolYear, String reference,
                           String type, LocalDate startsOn, LocalDate endsOn) {
        this.center = center;
        this.schoolYear = schoolYear;
        this.reference = reference;
        this.type = type;
        this.level = ma.bacsurv.domain.OperationType.levelOf(type);
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.createdAt = Instant.now();
    }

    public void addSlot(ExamSlotEntity slot) {
        slots.add(slot);
    }

    public Long getId() { return id; }

    public Instant getChangedAt() { return changedAt; }

    /** Called by every service that alters what a distribution is built from. */
    public void touch() { this.changedAt = Instant.now(); }
    public CenterEntity getCenter() { return center; }
    public SchoolYearEntity getSchoolYear() { return schoolYear; }
    public String getReference() { return reference; }
    public String getType() { return type; }

    /** BAC1 or BAC2 — what this session examines, and whose filières it runs. */
    public String getLevel() { return level; }
    public Instant getCreatedAt() { return createdAt; }

    public State getState() { return state; }
    public boolean isSettled() { return state == State.SETTLED; }

    /** This répartition is the one that goes out; from here it is history. */
    public void settle() { this.state = State.SETTLED; }

    /**
     * Back to a draft. Whatever this session's duties were counting for in the
     * queue, they stop counting — which is why nothing calls this without
     * having said so first.
     */
    public void reopen() { this.state = State.DRAFT; }
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
