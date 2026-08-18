package ma.bacsurv.web.service;

import ma.bacsurv.web.persistence.SolveJob;

import java.time.Instant;

/**
 * A solve job as seen outside the service layer. Built inside the
 * transaction, so no lazy association can be touched later.
 */
public record JobView(Long id, Long operationId, String operationName, String centerName,
                      SolveJob.Status status, int timeLimitSeconds,
                      Boolean feasible, Integer hardViolations, Integer softViolations,
                      Integer unfilled, String error,
                      Instant createdAt, Instant finishedAt) {

    static JobView of(SolveJob job) {
        return new JobView(job.getId(),
                job.getOperation().getId(), job.getOperation().getReference(),
                job.getOperation().getCenter().getName(),
                job.getStatus(), job.getTimeLimitSeconds(),
                job.getFeasible(), job.getHardViolations(), job.getSoftViolations(),
                job.getUnfilled(), job.getError(),
                job.getCreatedAt(), job.getFinishedAt());
    }

    public boolean isRunning() {
        return status == SolveJob.Status.PENDING || status == SolveJob.Status.RUNNING;
    }
}
