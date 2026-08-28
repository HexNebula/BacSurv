package ma.bacsurv.web.service;

import ma.bacsurv.application.StaffingCheck;
import ma.bacsurv.web.persistence.ExamEntity;
import ma.bacsurv.web.persistence.ExamSlotEntity;
import ma.bacsurv.web.persistence.OperationEntity;
import ma.bacsurv.web.persistence.OperationRepository;
import ma.bacsurv.web.persistence.RoomRepository;
import ma.bacsurv.web.persistence.SolveJob;
import ma.bacsurv.web.persistence.SolveJobRepository;
import ma.bacsurv.web.persistence.StreamEntity;
import ma.bacsurv.web.persistence.StreamRepository;
import ma.bacsurv.web.persistence.TeacherEntity;
import ma.bacsurv.web.persistence.TeacherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What is left to do before a session can be distributed.
 *
 * <p>The application knows perfectly well whether a session is ready — it has
 * the rooms, the pool, the timetable and the staffing check — but until now it
 * only said so at the moment somebody pressed solve, in the form of a refusal.
 * An administrator who is not an IT person should be able to see what is
 * missing before spending an afternoon on the wrong screen.
 *
 * <p>Every step reports one of three states. {@code READY} means done.
 * {@code TODO} means nothing is there yet. {@code CHECK} is the interesting one:
 * something is there but will not survive contact with the solver — a filière
 * with no rooms, a subject nobody teaches, an hour needing more people than the
 * centre has.
 */
@Service
public class ReadinessService {

    private final OperationRepository operations;
    private final StreamRepository streams;
    private final RoomRepository rooms;
    private final TeacherRepository teachers;
    private final SolveJobRepository jobs;
    private final SolveService solveService;

    public ReadinessService(OperationRepository operations, StreamRepository streams,
                            RoomRepository rooms, TeacherRepository teachers,
                            SolveJobRepository jobs, SolveService solveService) {
        this.operations = operations;
        this.streams = streams;
        this.rooms = rooms;
        this.teachers = teachers;
        this.jobs = jobs;
        this.solveService = solveService;
    }

    public enum State { READY, CHECK, TODO }

    /**
     * One step, its state, and a sentence saying where it stands.
     *
     * <p>The detail is a message key with arguments rather than a sentence,
     * so it reads in the language the administrator chose.
     */
    public record Step(String key, State state, String detail, List<String> args, String screen) {}

    public record Readiness(Long sessionId, String reference, Long centerId, String centerName,
                            List<Step> steps, String next) {}

    @Transactional(readOnly = true)
    public Readiness of(long sessionId) {
        OperationEntity operation = operations.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("session.unknown"));
        long centerId = operation.getCenter().getId();

        List<Step> steps = new ArrayList<>();
        steps.add(rooms(centerId));
        steps.add(pool(operation));
        steps.add(filieres(sessionId));
        steps.add(timetable(operation));

        // the staffing check needs a timetable and a pool to say anything at
        // all: run on an empty session it finds no duty, therefore no shortage,
        // and would cheerfully report that everything is fine
        boolean canBeChecked = steps.stream().allMatch(step -> step.state() == State.READY);
        steps.add(canBeChecked
                ? staffing(sessionId)
                : new Step("staffing", State.TODO, "staffing.waiting", List.of(), "schedule"));
        steps.add(distribution(sessionId));
        steps.add(settled(operation, steps.getLast()));

        String next = steps.stream()
                .filter(step -> step.state() != State.READY)
                .map(Step::key)
                .findFirst()
                .orElse(null);

        return new Readiness(operation.getId(), operation.getReference(),
                centerId, operation.getCenter().getName(), List.copyOf(steps), next);
    }

    private Step rooms(long centerId) {
        int count = rooms.findByCenterIdOrderByReferenceAsc(centerId).size();
        return count == 0
                ? new Step("rooms", State.TODO, "rooms.none", List.of(), "center")
                : new Step("rooms", State.READY, "rooms.count", List.of(String.valueOf(count)),
                        "center");
    }

    /** The pool of this session's year — not the centre's whole register. */
    private Step pool(OperationEntity operation) {
        List<TeacherEntity> pool = teachers.findPoolOfYear(operation.getSchoolYear().getId());
        if (pool.isEmpty()) {
            return new Step("teachers", State.TODO, "teachers.none", List.of(), "teachers");
        }
        return new Step("teachers", State.READY, "teachers.count",
                List.of(String.valueOf(pool.size())), "teachers");
    }

    private Step filieres(long sessionId) {
        List<StreamEntity> declared = streams.ofOperation(sessionId);
        if (declared.isEmpty()) {
            return new Step("filieres", State.TODO, "filieres.none", List.of(), "schedule");
        }
        // a filière with nowhere to sit cannot hold an épreuve at all
        long roomless = declared.stream().filter(s -> s.getRooms().isEmpty()).count();
        if (roomless > 0) {
            return new Step("filieres", State.CHECK, "filieres.roomless",
                    List.of(String.valueOf(roomless)), "schedule");
        }
        return new Step("filieres", State.READY, "filieres.count",
                List.of(String.valueOf(declared.size())), "schedule");
    }

    private Step timetable(OperationEntity operation) {
        List<ExamEntity> exams = operation.getSlots().stream()
                .flatMap(slot -> slot.getExams().stream()).toList();
        if (exams.isEmpty()) {
            return new Step("timetable", State.TODO, "timetable.none", List.of(), "schedule");
        }

        // a filière that sits nothing is a column the administrator forgot
        Set<String> sitting = exams.stream().map(ExamEntity::getStream).collect(Collectors.toSet());
        long idle = streams.ofOperation(operation.getId()).stream()
                .filter(stream -> !sitting.contains(stream.getName()))
                .count();
        if (idle > 0) {
            return new Step("timetable", State.CHECK, "timetable.idleFiliere",
                    List.of(String.valueOf(idle)), "schedule");
        }

        int slots = (int) operation.getSlots().stream()
                .filter(slot -> !slot.getExams().isEmpty()).count();
        return new Step("timetable", State.READY, "timetable.count",
                List.of(String.valueOf(exams.size()), String.valueOf(slots)), "schedule");
    }

    /**
     * The check that actually stops a solve: an hour needing more people than
     * the centre has, or a duty nobody is eligible for.
     */
    private Step staffing(long sessionId) {
        // deliberately not wrapped in a try: a failure here happens inside its
        // own transaction, and swallowing it would leave this one marked
        // rollback-only while pretending nothing went wrong
        List<StaffingCheck.Unfillable> unfillable = solveService.unfillableDuties(sessionId);
        List<StaffingCheck.Shortage> shortages = solveService.staffingShortages(sessionId);

        if (!unfillable.isEmpty()) {
            StaffingCheck.Unfillable first = unfillable.getFirst();
            return new Step("staffing", State.CHECK, "staffing.unfillable",
                    List.of(String.valueOf(unfillable.size()),
                            first.subject() == null ? "" : first.subject()), "teachers");
        }
        if (!shortages.isEmpty()) {
            StaffingCheck.Shortage worst = shortages.stream()
                    .max(java.util.Comparator.comparingInt(StaffingCheck.Shortage::missing))
                    .orElseThrow();
            return new Step("staffing", State.CHECK, "staffing.shortage",
                    List.of(String.valueOf(worst.missing()), worst.date().toString(),
                            worst.at().toString()), "teachers");
        }
        return new Step("staffing", State.READY, "staffing.ok", List.of(), "schedule");
    }

    private Step distribution(long sessionId) {
        List<SolveJob> ofSession = jobs.findAll().stream()
                .filter(job -> job.getOperation() != null
                        && job.getOperation().getId().equals(sessionId))
                .toList();

        SolveJob latest = ofSession.stream()
                .filter(job -> job.getStatus() == SolveJob.Status.DONE)
                .max(java.util.Comparator.comparing(SolveJob::getId))
                .orElse(null);

        if (latest == null) {
            return new Step("distribution", State.TODO, "distribution.none", List.of(), "results");
        }
        // solved, but the session moved afterwards: the screen is showing an
        // answer to a question that has since changed
        if (latest.getFinishedAt() != null && changedSince(latest)) {
            return new Step("distribution", State.CHECK, "distribution.stale", List.of(), "results");
        }
        if (latest.getHardViolations() > 0 || latest.getUnfilled() > 0) {
            return new Step("distribution", State.CHECK, "distribution.broken",
                    List.of(String.valueOf(latest.getHardViolations()),
                            String.valueOf(latest.getUnfilled())), "results");
        }
        return new Step("distribution", State.READY, "distribution.ok", List.of(), "results");
    }

    /**
     * The last step, and the one that makes the rest count.
     *
     * <p>A distribution sitting on the screen is still a draft's distribution:
     * it repays nobody, and the next session will offer réserve and permanence
     * to the people who served in it. Arrêter la répartition is what turns the
     * duties into history — so it belongs on the path the administrator already
     * walks, not behind a setting he would never find.
     */
    private Step settled(OperationEntity operation, Step distribution) {
        if (operation.isSettled()) {
            return new Step("settled", State.READY, "settled.done", List.of(), "results");
        }
        // nothing worth settling yet: say so rather than asking for an act that
        // would be refused
        return distribution.state() == State.READY
                ? new Step("settled", State.TODO, "settled.pending", List.of(), "results")
                : new Step("settled", State.TODO, "settled.waiting", List.of(), "results");
    }

    /** The session's own inputs, or the centre's, moved after the job finished. */
    private boolean changedSince(SolveJob job) {
        java.time.Instant session = job.getOperation().getChangedAt();
        java.time.Instant centre = job.getOperation().getCenter().getChangedAt();
        return (session != null && session.isAfter(job.getFinishedAt()))
                || (centre != null && centre.isAfter(job.getFinishedAt()));
    }
}
