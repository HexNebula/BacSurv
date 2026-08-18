package ma.bacsurv.web.persistence;

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
import jakarta.persistence.Table;
import ma.bacsurv.domain.DutyRole;

/**
 * One duty of a solved job and who holds it. This is the schedule: the solver
 * writes it, the administrator may change it, and the workload of the year is
 * counted from it.
 */
@Entity
@Table(name = "assignment")
public class AssignmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private SolveJob job;

    /** The generator's deterministic duty id, stable across regeneration. */
    @Column(name = "duty_id", nullable = false, length = 160)
    private String dutyId;

    @Column(name = "slot_ref", nullable = false, length = 50)
    private String slotRef;

    @Column(name = "exam_ref", length = 50)
    private String examRef;

    @Column(name = "room_ref", length = 50)
    private String roomRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DutyRole role;

    /** Null when the duty could not be filled. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private TeacherEntity teacher;

    @Column(nullable = false)
    private boolean pinned;

    protected AssignmentEntity() {}

    public AssignmentEntity(SolveJob job, String dutyId, String slotRef, String examRef,
                            String roomRef, DutyRole role, TeacherEntity teacher) {
        this.job = job;
        this.dutyId = dutyId;
        this.slotRef = slotRef;
        this.examRef = examRef;
        this.roomRef = roomRef;
        this.role = role;
        this.teacher = teacher;
        this.pinned = false;
    }

    public void assignTo(TeacherEntity teacher) {
        this.teacher = teacher;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public Long getId() { return id; }
    public SolveJob getJob() { return job; }
    public String getDutyId() { return dutyId; }
    public String getSlotRef() { return slotRef; }
    public String getExamRef() { return examRef; }
    public String getRoomRef() { return roomRef; }
    public DutyRole getRole() { return role; }
    public TeacherEntity getTeacher() { return teacher; }
    public boolean isPinned() { return pinned; }
}
