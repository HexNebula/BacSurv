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
 * How many duties of one role a teacher was given by one finished job.
 * Summing these rows over a center's past operations gives the prior
 * workload that keeps the year's total fair.
 */
@Entity
@Table(name = "job_workload")
public class JobWorkload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private SolveJob job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private TeacherEntity teacher;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DutyRole role;

    @Column(name = "duty_count", nullable = false)
    private int dutyCount;

    protected JobWorkload() {}

    public JobWorkload(SolveJob job, TeacherEntity teacher, DutyRole role, int dutyCount) {
        this.job = job;
        this.teacher = teacher;
        this.role = role;
        this.dutyCount = dutyCount;
    }

    public Long getId() { return id; }
    public SolveJob getJob() { return job; }
    public TeacherEntity getTeacher() { return teacher; }
    public DutyRole getRole() { return role; }
    public int getDutyCount() { return dutyCount; }
}
