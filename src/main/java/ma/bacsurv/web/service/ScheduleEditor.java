package ma.bacsurv.web.service;

import ma.bacsurv.application.ScheduleValidator;
import ma.bacsurv.application.Violation;
import ma.bacsurv.domain.Duty;
import ma.bacsurv.domain.DutyRole;
import ma.bacsurv.domain.Teacher;
import ma.bacsurv.rules.Eligibility;
import ma.bacsurv.web.persistence.AssignmentEntity;
import ma.bacsurv.web.persistence.AssignmentRepository;
import ma.bacsurv.web.persistence.TeacherEntity;
import ma.bacsurv.web.persistence.TeacherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Changing an assignment by hand.
 *
 * The administrator keeps the last word, but never silently: a change is
 * reviewed first and the answer says exactly which rules it would break and
 * which preferences it would worsen. Breaches are returned as codes with
 * arguments so the pages can say it in French or in Arabic.
 */
@Service
public class ScheduleEditor {

    /** A rule the proposed change would break. {@code code} keys a message. */
    public record Breach(String code, List<String> args) {

        static Breach of(String code, String... args) {
            return new Breach(code, List.of(args));
        }
    }

    /** What would happen if this change were saved. */
    public record ChangeReview(String dutyId, String dutyDescription,
                               String currentHolder, String newHolder,
                               List<Breach> breaches,
                               Map<String, Integer> preferenceChanges,
                               int newHolderDutiesBefore, int newHolderDutiesAfter) {

        public boolean isLegal() {
            return breaches.isEmpty();
        }

        /** Preferences that get worse; the ones that improve are shown too. */
        public boolean worsensPreferences() {
            return preferenceChanges.values().stream().anyMatch(delta -> delta > 0);
        }
    }

    private final ScheduleService schedules;
    private final AssignmentRepository assignments;
    private final TeacherRepository teachers;
    private final Eligibility eligibility = Eligibility.withDefaults();

    public ScheduleEditor(ScheduleService schedules, AssignmentRepository assignments,
                          TeacherRepository teachers) {
        this.schedules = schedules;
        this.assignments = assignments;
        this.teachers = teachers;
    }

    /** A teacher who could be put on a duty, as offered in the change form. */
    public record Candidate(Long id, String matricule, String name, String subject) {}

    @Transactional(readOnly = true)
    public List<Candidate> candidates(long jobId) {
        ScheduleService.Materialised schedule = schedules.materialise(jobId)
                .orElseThrow(() -> new IllegalArgumentException("no schedule for job " + jobId));
        return schedule.pool().teacherById().entrySet().stream()
                .map(entry -> new Candidate(entry.getKey(), entry.getValue().matricule(),
                        entry.getValue().name(), entry.getValue().subject().name()))
                .sorted(java.util.Comparator.comparing(Candidate::name))
                .toList();
    }

    /** One duty of a stored schedule, for the page that edits it. */
    public record DutyView(String dutyId, String description, String holder, boolean pinned) {}

    @Transactional(readOnly = true)
    public DutyView duty(long jobId, String dutyId) {
        ScheduleService.Materialised schedule = schedules.materialise(jobId)
                .orElseThrow(() -> new IllegalArgumentException("no schedule for job " + jobId));
        Duty duty = schedule.duties().stream()
                .filter(d -> d.id().equals(dutyId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no duty " + dutyId));
        boolean pinned = assignments.findByJobIdAndDutyId(jobId, dutyId)
                .map(AssignmentEntity::isPinned).orElse(false);
        return new DutyView(dutyId, describe(duty),
                duty.assignedTeacher().map(Teacher::name).orElse(null), pinned);
    }

    /** Reviews the change without touching anything. */
    @Transactional(readOnly = true)
    public ChangeReview review(long jobId, String dutyId, Long newTeacherId) {
        ScheduleService.Materialised schedule = schedules.materialise(jobId)
                .orElseThrow(() -> new IllegalArgumentException("no schedule for job " + jobId));

        Duty duty = schedule.duties().stream()
                .filter(d -> d.id().equals(dutyId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no duty " + dutyId));

        Teacher replacement = newTeacherId == null ? null
                : Optional.ofNullable(schedule.pool().teacherById().get(newTeacherId))
                        .orElseThrow(() -> new IllegalArgumentException(
                                "teacher " + newTeacherId + " is not in this centre's pool"));

        Teacher current = duty.assignedTeacher().orElse(null);
        List<Breach> breaches = replacement == null
                ? List.of()
                : breaches(duty, replacement, schedule.duties());

        Map<String, Integer> softBefore = softCounts(schedule.duties());
        int before = dutiesOf(replacement, schedule.duties());

        duty.assign(replacement); // in-memory only: these objects are rebuilt per call
        Map<String, Integer> softAfter = softCounts(schedule.duties());
        int after = dutiesOf(replacement, schedule.duties());

        return new ChangeReview(dutyId, describe(duty),
                current == null ? null : current.name(),
                replacement == null ? null : replacement.name(),
                breaches, difference(softBefore, softAfter), before, after);
    }

    /**
     * Saves the change. A change that breaks a rule is refused unless the
     * administrator insists, and even then it stays visible in the validation
     * summary of the schedule.
     */
    @Transactional
    public ChangeReview apply(long jobId, String dutyId, Long newTeacherId, boolean force) {
        ChangeReview review = review(jobId, dutyId, newTeacherId);
        if (!review.isLegal() && !force) {
            throw new IllegalChangeException(review);
        }
        AssignmentEntity row = assignments.findByJobIdAndDutyId(jobId, dutyId)
                .orElseThrow(() -> new IllegalArgumentException("no duty " + dutyId));
        TeacherEntity teacher = newTeacherId == null ? null
                : teachers.findById(newTeacherId).orElseThrow(
                        () -> new IllegalArgumentException("no teacher " + newTeacherId));
        row.assignTo(teacher);
        return review;
    }

    /** Pinning protects a hand-made decision from the next solve. */
    @Transactional
    public void pin(long jobId, String dutyId, boolean pinned) {
        assignments.findByJobIdAndDutyId(jobId, dutyId)
                .orElseThrow(() -> new IllegalArgumentException("no duty " + dutyId))
                .setPinned(pinned);
    }

    /** The four hard rules a single reassignment can break. */
    private List<Breach> breaches(Duty duty, Teacher teacher, List<Duty> schedule) {
        List<Breach> breaches = new ArrayList<>();

        if (!teacher.isAvailable(duty.slot())) {
            breaches.add(Breach.of("change.breach.unavailable",
                    teacher.name(), duty.slot().date().toString()));
        }
        schedule.stream()
                .filter(other -> !other.id().equals(duty.id()))
                .filter(other -> other.slot().id().equals(duty.slot().id()))
                .filter(other -> other.assignedTeacher().map(t -> t.equals(teacher)).orElse(false))
                .findFirst()
                .ifPresent(clash -> breaches.add(Breach.of("change.breach.busy",
                        teacher.name(), describe(clash))));

        String subject = duty.exam().map(e -> e.subject().name()).orElse(null);
        if (duty.role() == DutyRole.PERMANENCE
                && !teacher.isQualified(DutyRole.PERMANENCE,
                        duty.exam().map(e -> e.subject()).orElse(null))) {
            breaches.add(Breach.of("change.breach.notSpecialist", teacher.name(), subject));
        }
        if (eligibility.isHardSubjectConflict(teacher, duty)) {
            breaches.add(Breach.of("change.breach.ownSubject", teacher.name(), subject));
        }
        return breaches;
    }

    private int dutiesOf(Teacher teacher, List<Duty> schedule) {
        if (teacher == null) return 0;
        return (int) schedule.stream()
                .filter(d -> d.assignedTeacher().map(t -> t.equals(teacher)).orElse(false))
                .count();
    }

    private Map<String, Integer> softCounts(List<Duty> schedule) {
        return ScheduleValidator.withDefaults().validate(schedule).softViolations().stream()
                .collect(Collectors.groupingBy(Violation::rule,
                        TreeMap::new, Collectors.summingInt(v -> 1)));
    }

    /** Only the rules whose count moved, positive meaning worse. */
    private Map<String, Integer> difference(Map<String, Integer> before, Map<String, Integer> after) {
        Map<String, Integer> changes = new LinkedHashMap<>();
        java.util.stream.Stream.concat(before.keySet().stream(), after.keySet().stream())
                .distinct().sorted()
                .forEach(rule -> {
                    int delta = after.getOrDefault(rule, 0) - before.getOrDefault(rule, 0);
                    if (delta != 0) changes.put(rule, delta);
                });
        return changes;
    }

    /**
     * Identifies a duty using data only — slot, subject, room. The role is
     * deliberately left out: it is a word, and words belong in the message
     * bundles, not in strings built here.
     */
    private static String describe(Duty duty) {
        StringBuilder description = new StringBuilder(duty.slot().id());
        duty.exam().ifPresent(exam -> description.append(" · ").append(exam.subject().name()));
        duty.room().ifPresent(room -> description.append(" · ").append(room.label()));
        return description.toString();
    }

    /** Refuses a change that breaks a rule, carrying the reasons back. */
    public static class IllegalChangeException extends RuntimeException {

        private final transient ChangeReview review;

        IllegalChangeException(ChangeReview review) {
            super("change breaks " + review.breaches().size() + " rule(s)");
            this.review = review;
        }

        public ChangeReview review() {
            return review;
        }
    }
}
