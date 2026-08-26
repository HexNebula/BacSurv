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
                      Instant createdAt, Instant finishedAt, boolean stale) {

    static JobView of(SolveJob job) {
        return new JobView(job.getId(),
                job.getOperation().getId(), job.getOperation().getReference(),
                job.getOperation().getCenter().getName(),
                job.getStatus(), job.getTimeLimitSeconds(),
                job.getFeasible(), job.getHardViolations(), job.getSoftViolations(),
                job.getUnfilled(), job.getError(),
                job.getCreatedAt(), job.getFinishedAt(), isStale(job));
    }

    /**
     * Whether the session has moved since this distribution was computed.
     *
     * <p>A finished job and its screen look the same whether nothing has
     * changed or four rooms, an absence and the staffing rule have. Both scopes
     * count: the session's own timetable and rules, and the centre's pool,
     * rooms and catalogue, which invalidate every session under it.
     */
    private static boolean isStale(SolveJob job) {
        if (job.getFinishedAt() == null) return false;
        Instant session = job.getOperation().getChangedAt();
        Instant centre = job.getOperation().getCenter().getChangedAt();
        return (session != null && session.isAfter(job.getFinishedAt()))
                || (centre != null && centre.isAfter(job.getFinishedAt()));
    }

    public boolean isRunning() {
        return status == SolveJob.Status.PENDING || status == SolveJob.Status.RUNNING;
    }
}
