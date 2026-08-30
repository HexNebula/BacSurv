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
    private final SessionConflictService conflicts;

    public SessionAdminService(OperationRepository operations, SolveJobRepository jobs,
                               AssignmentRepository assignments, StreamRepository streams,
                               SessionConflictService conflicts) {
        this.operations = operations;
        this.jobs = jobs;
        this.assignments = assignments;
        this.streams = streams;
        this.conflicts = conflicts;
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
     * Corrects a session's name and its dates.
     *
     * <p>The two are not the same kind of thing, and are refused differently.
     * The reference is a label: nothing joins on it, no duty remembers it, so
     * it may be corrected at any time — including once the session is settled,
     * which is precisely when a wrong name is worth fixing, since it is printed
     * on every convocation that went out.
     *
     * <p>The dates are not a label. They bound the planning grid and they
     * decide which school year the session belongs to, and the year is what
     * fairness is counted inside. So they are refused while the session is
     * settled, refused when épreuves already sit outside the new range, and
     * refused when the move would carry the session into another year — that
     * last one silently reshuffles the privilege queue of two years at once,
     * and there is no version of it an administrator would recognise as what
     * he asked for.
     *
     * <p>Without this, correcting a mistyped name meant deleting the session
     * and typing its whole timetable again, and for a settled one it could not
     * be done at all. Reopening was documented as the way out of a wrong
     * reference; it never was, because nothing downstream of it could edit one.
     */
    @Transactional
    public long edit(long sessionId, String reference, java.time.LocalDate startsOn,
                     java.time.LocalDate endsOn) {
        OperationEntity session = session(sessionId);

        String cleaned = reference == null ? "" : reference.trim();
        if (cleaned.isEmpty()) throw new IllegalArgumentException("session.reference.required");
        if (startsOn == null || endsOn == null) {
            throw new IllegalArgumentException("session.dates");
        }
        if (endsOn.isBefore(startsOn)) {
            throw new IllegalArgumentException("session.dates.reversed");
        }

        boolean datesMoved = !Objects.equals(session.getStartsOn(), startsOn)
                || !Objects.equals(session.getEndsOn(), endsOn);

        if (datesMoved) {
            if (session.isSettled()) {
                throw new RefusedException("session.dates.settled", session.getReference());
            }
            // an épreuve outside the new range would vanish from the grid it is
            // planned on, and be neither visible nor removable
            List<java.time.LocalDate> orphaned = session.getSlots().stream()
                    .map(slot -> slot.getDate())
                    .filter(date -> date != null && (date.isBefore(startsOn) || date.isAfter(endsOn)))
                    .distinct()
                    .sorted()
                    .toList();
            if (!orphaned.isEmpty()) {
                throw new RefusedException("session.dates.outsideSlots",
                        String.valueOf(orphaned.size()),
                        orphaned.getFirst().toString());
            }
            String year = ma.bacsurv.web.persistence.SchoolYearEntity.labelOf(startsOn);
            if (!year.equals(session.getSchoolYear().getLabel())) {
                throw new RefusedException("session.dates.otherYear",
                        session.getSchoolYear().getLabel(), year);
            }
        }

        session.rename(cleaned);
        if (datesMoved) {
            session.setDates(startsOn, endsOn);
            // the grid the distribution was computed over has moved
            session.touch();
        }
        // a reference on its own touches nothing: it reaches no rule, and
        // marking the session as changed would make a settled one unsettleable
        return session.getCenter().getId();
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
        refuseIfConcurrent(session);
        session.settle();
    }

    /**
     * Refuse a répartition that cannot happen beside the ones already settled.
     *
     * <p>Settling is where a plan stops being a proposal and becomes a claim
     * about the building, so it is the only place this can be checked and mean
     * something. Checking at distribution time would not: the other session may
     * still be a draft then, and comparing against a draft finds nothing —
     * which is exactly how a centre ends up with two settled sessions putting
     * one teacher in two rooms at eight o'clock.
     *
     * <p>Here the order does not matter. Whichever of two colliding sessions is
     * settled second is the one refused, so there is no sequence of steps that
     * reaches the broken state.
     *
     * <p>The refusal names what collides rather than saying the sessions
     * overlap, because the administrator's next act is to move a room or
     * redistribute, and he cannot choose between them without knowing which.
     */
    private void refuseIfConcurrent(OperationEntity session) {
        SessionConflictService.Conflicts found = conflicts.of(session);
        if (found.isEmpty()) return;

        String named = String.join(", ", found.sessions());
        if (!found.rooms().isEmpty()) {
            throw new RefusedException("session.settle.roomsBusy", named,
                    String.valueOf(found.rooms().size()),
                    found.rooms().getFirst().room());
        }
        throw new RefusedException("session.settle.teachersBusy", named,
                String.valueOf(found.teachers().size()),
                found.teachers().getFirst().teacher());
    }

    /**
     * This session's own inputs moved after the job finished.
     *
     * <p>Its own, and not the centre's. Settling is a statement about the past
     * — this is the répartition that went out — and the centre's clock moves
     * every time a teacher is imported, an absence recorded or a room
     * relabelled. Refusing to record what happened in June because somebody
     * edited the pool in July is a category error, and it would leave a real
     * distribution permanently unrecordable.
     *
     * <p>What is still refused is a genuine mismatch: an épreuve added after
     * the solve means the distribution does not cover the timetable it would be
     * locking. The readiness guide reports the wider staleness, centre included,
     * because there it is advice rather than a verdict.
     */
    private static boolean changedSince(SolveJob job, OperationEntity session) {
        if (job.getFinishedAt() == null) return false;
        java.time.Instant own = session.getChangedAt();
        return own != null && own.isAfter(job.getFinishedAt());
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
        // deliberately no touch(): reopening changes nothing the distribution
        // was computed from. Marking the inputs as moved would make the session
        // permanently stale, and settling it again would require a re-solve —
        // a different répartition from the one already handed out. Correcting a
        // reference is meant to cost nothing; editing the timetable afterwards
        // is what makes it stale, and that stamps changedAt itself.
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
