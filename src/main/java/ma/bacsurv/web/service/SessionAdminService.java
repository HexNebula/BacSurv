package ma.bacsurv.web.service;

import ma.bacsurv.web.persistence.AssignmentEntity;
import ma.bacsurv.web.persistence.AssignmentRepository;
import ma.bacsurv.web.persistence.OperationEntity;
import ma.bacsurv.web.persistence.OperationRepository;
import ma.bacsurv.web.persistence.SolveJob;
import ma.bacsurv.web.persistence.SolveJobRepository;
import ma.bacsurv.web.persistence.StreamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The life of a session: draft, settled, and gone.
 *
 * <p>Removing a session is not the same kind of act as removing a room. A room
 * is a fact about the building; a session that has been distributed is a record
 * of work people did, and the privilege queue is built by counting it. Delete
 * one and every teacher who took a réserve or a permanence in it is owed that
 * turn again — silently, with nothing on any screen to say so.
 *
 * <p>Which is why a session carries a state. While it is a draft it counts for
 * nothing and may be deleted freely; the administrator is only told what he is
 * about to lose in typing. Once he settles it, it is the répartition that went
 * out: it counts, it cannot be deleted, and its planning cannot be edited
 * underneath it. Reopening is allowed — a wrong date should not be permanent —
 * but it says first what it costs.
 */
@Service
public class SessionAdminService {

    private final OperationRepository operations;
    private final SolveJobRepository jobs;
    private final AssignmentRepository assignments;
    private final StreamRepository streams;

    public SessionAdminService(OperationRepository operations, SolveJobRepository jobs,
                               AssignmentRepository assignments, StreamRepository streams) {
        this.operations = operations;
        this.jobs = jobs;
        this.assignments = assignments;
        this.streams = streams;
    }

    /**
     * What acting on this session would cost, in the terms an administrator
     * recognises: filières and épreuves are the typing, duties and teachers are
     * the queue.
     *
     * <p>The figures come from here rather than from the screen's own guesses,
     * so the sentence in a confirmation is true rather than plausible.
     */
    public record Impact(Long sessionId, String reference, String state,
                         int streamCount, int examCount, int solveCount,
                         int dutyCount, int teacherCount, boolean deletable) {}

    @Transactional(readOnly = true)
    public Impact impact(long sessionId) {
        OperationEntity session = session(sessionId);
        List<AssignmentEntity> duties = dutiesOfNewestSolve(sessionId);

        return new Impact(session.getId(), session.getReference(), session.getState().name(),
                streams.ofOperation(sessionId).size(),
                session.getSlots().stream().mapToInt(slot -> slot.getExams().size()).sum(),
                jobs.ofOperation(sessionId).size(),
                duties.size(), teachersIn(duties),
                !session.isSettled());
    }

    /**
     * Delete a session and everything entered under it.
     *
     * <p>Refused while the session is settled. The refusal names the session and
     * how many teachers its duties are keeping in the queue, because "it counts
     * in the history" means nothing to somebody looking at a list of sessions.
     *
     * <p>The solves go first. Slots, filières and the session's own rules follow
     * the row out by cascade; solve jobs were the one child that did not, and
     * removing them takes their assignments and duty counts with them.
     */
    @Transactional
    public long delete(long sessionId) {
        OperationEntity session = session(sessionId);
        if (session.isSettled()) {
            throw new RefusedException("session.settled",
                    session.getReference(),
                    String.valueOf(teachersIn(dutiesOfNewestSolve(sessionId))));
        }
        // a solve still running would write its result back into rows that are
        // about to disappear: harmless, because the write is guarded by a
        // findById, but it wastes minutes and then says nothing
        if (jobs.ofOperation(sessionId).stream().anyMatch(
                job -> job.getStatus() == SolveJob.Status.PENDING
                        || job.getStatus() == SolveJob.Status.RUNNING)) {
            throw new RefusedException("session.solving", session.getReference());
        }
        long centerId = session.getCenter().getId();

        jobs.deleteAll(jobs.ofOperation(sessionId));
        // the trial duties leave with the trials: nothing of a draft survives it
        jobs.flush();
        operations.delete(session);
        session.getCenter().touch();
        return centerId;
    }

    /**
     * Declare this session's répartition to be the one that goes out.
     *
     * <p>Only from here do its duties become history, and from here its
     * planning and its assignments are locked. So what is settled has to be
     * worth locking: a distribution with unstaffed duties would enter the queue
     * as work nobody can have done, and one solved before the timetable last
     * moved answers a question the session is no longer asking.
     *
     * <p>The readiness guide only offers this step once the distribution reads
     * READY, so the happy path never reaches these refusals. They are here
     * because the endpoint is reachable without the guide.
     */
    @Transactional
    public void settle(long sessionId) {
        OperationEntity session = session(sessionId);
        if (session.isSettled()) return;

        SolveJob newest = newestSolve(sessionId);
        if (newest == null) {
            throw new IllegalArgumentException("session.settle.noDistribution");
        }
        if (newest.getHardViolations() > 0 || newest.getUnfilled() > 0) {
            throw new RefusedException("session.settle.broken",
                    String.valueOf(newest.getHardViolations()),
                    String.valueOf(newest.getUnfilled()));
        }
        if (changedSince(newest, session)) {
            throw new IllegalArgumentException("session.settle.stale");
        }
        session.settle();
    }

    /**
     * The session's own inputs, or the centre's, moved after the job finished.
     *
     * <p>Same reading as the readiness guide's "la répartition date d'avant vos
     * derniers changements": a timetable or a set of rules edited after the
     * solve means the paper on screen answers a question that has changed.
     */
    private static boolean changedSince(SolveJob job, OperationEntity session) {
        if (job.getFinishedAt() == null) return false;
        java.time.Instant own = session.getChangedAt();
        java.time.Instant centre = session.getCenter().getChangedAt();
        return (own != null && own.isAfter(job.getFinishedAt()))
                || (centre != null && centre.isAfter(job.getFinishedAt()));
    }

    /**
     * Back to a draft, so the session can be corrected or removed.
     *
     * <p>What this costs is on {@link #impact}: the duties of this session stop
     * counting the moment it is reopened, and the next distribution will offer
     * réserve and permanence to people it currently considers already served.
     * The screen says so before asking; here it is simply carried out.
     */
    @Transactional
    public void reopen(long sessionId) {
        OperationEntity session = session(sessionId);
        if (!session.isSettled()) return;
        session.reopen();
        // its inputs are editable again, and any distribution shown against it
        // is now a draft's distribution
        session.touch();
    }

    /**
     * The guard the editing services call: a settled session's planning is the
     * planning that was printed, and must not move underneath it.
     */
    public static void mustBeEditable(OperationEntity session) {
        if (session.isSettled()) {
            throw new RefusedException("session.settled.locked", session.getReference());
        }
    }

    private SolveJob newestSolve(long sessionId) {
        return jobs.ofOperation(sessionId).stream()
                .filter(job -> job.getStatus() == SolveJob.Status.DONE)
                .max(Comparator.comparing(SolveJob::getId))
                .orElse(null);
    }

    /** The duties the queue would actually be counting for this session. */
    private List<AssignmentEntity> dutiesOfNewestSolve(long sessionId) {
        SolveJob newest = newestSolve(sessionId);
        return newest == null ? List.of() : assignments.findOfJob(newest.getId());
    }

    private static int teachersIn(List<AssignmentEntity> duties) {
        return (int) duties.stream()
                .map(AssignmentEntity::getTeacher)
                .filter(Objects::nonNull)
                .map(teacher -> teacher.getId())
                .distinct()
                .count();
    }

    private OperationEntity session(long sessionId) {
        return operations.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("session.unknown"));
    }
}
