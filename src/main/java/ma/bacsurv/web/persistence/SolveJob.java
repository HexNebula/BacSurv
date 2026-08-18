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

import java.time.Instant;

/**
 * One solve request. State lives in the database, not in memory, so the
 * job model survives a restart and later a move to worker processes.
 */
@Entity
@Table(name = "solve_job")
public class SolveJob {

    public enum Status { PENDING, RUNNING, DONE, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operation_id", nullable = false)
    private OperationEntity operation;

    /** The file this operation was imported from, when there was one. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operation_file_id")
    private OperationFile operationFile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "time_limit_seconds", nullable = false)
    private int timeLimitSeconds;

    @Column(columnDefinition = "text")
    private String error;

    private Boolean feasible;

    @Column(name = "hard_violations")
    private Integer hardViolations;

    @Column(name = "soft_violations")
    private Integer softViolations;

    private Integer unfilled;

    /** Serialised ScheduleWriter.Result — the schedule as returned to clients. */
    @Column(columnDefinition = "text")
    private String result;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected SolveJob() {}

    public SolveJob(OperationEntity operation, OperationFile operationFile, int timeLimitSeconds) {
        this.operation = operation;
        this.operationFile = operationFile;
        this.timeLimitSeconds = timeLimitSeconds;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
    }

    public void markRunning() {
        this.status = Status.RUNNING;
        this.startedAt = Instant.now();
    }

    public void markDone(String result, boolean feasible,
                         int hardViolations, int softViolations, int unfilled) {
        this.status = Status.DONE;
        this.result = result;
        this.feasible = feasible;
        this.hardViolations = hardViolations;
        this.softViolations = softViolations;
        this.unfilled = unfilled;
        this.finishedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.status = Status.FAILED;
        this.error = error;
        this.finishedAt = Instant.now();
    }

    public Long getId() { return id; }
    public OperationEntity getOperation() { return operation; }
    public OperationFile getOperationFile() { return operationFile; }
    public Status getStatus() { return status; }
    public int getTimeLimitSeconds() { return timeLimitSeconds; }
    public String getError() { return error; }
    public Boolean getFeasible() { return feasible; }
    public Integer getHardViolations() { return hardViolations; }
    public Integer getSoftViolations() { return softViolations; }
    public Integer getUnfilled() { return unfilled; }
    public String getResult() { return result; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
}
