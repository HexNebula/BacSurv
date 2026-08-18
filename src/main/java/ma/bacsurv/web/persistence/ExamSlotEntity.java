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
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** A time slot of an operation; the subjects live on its exams. */
@Entity
@Table(name = "exam_slot")
public class ExamSlotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operation_id", nullable = false)
    private OperationEntity operation;

    @Column(nullable = false)
    private String reference;

    @Column(name = "slot_date", nullable = false)
    private LocalDate date;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "ordinal_in_day", nullable = false)
    private int ordinalInDay;

    @Column(name = "reserve_count", nullable = false)
    private int reserveCount;

    @OneToMany(mappedBy = "slot", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<ExamEntity> exams = new ArrayList<>();

    protected ExamSlotEntity() {}

    public ExamSlotEntity(OperationEntity operation, String reference, LocalDate date,
                          LocalTime startTime, LocalTime endTime,
                          int ordinalInDay, int reserveCount) {
        this.operation = operation;
        this.reference = reference;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.ordinalInDay = ordinalInDay;
        this.reserveCount = reserveCount;
    }

    public void addExam(ExamEntity exam) {
        exams.add(exam);
    }

    public Long getId() { return id; }
    public OperationEntity getOperation() { return operation; }
    public String getReference() { return reference; }
    public LocalDate getDate() { return date; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public int getOrdinalInDay() { return ordinalInDay; }
    public int getReserveCount() { return reserveCount; }
    public List<ExamEntity> getExams() { return exams; }
}
