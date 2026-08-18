package ma.bacsurv.domain;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class Teacher {

    private final String id;
    private final String name;
    private final Subject subject;
    private final String establishment;
    private final Gender gender; // nullable — only used by optional mixed-pair preference
    private final Set<Unavailability> unavailabilities;
    private final Set<TeacherQualification> qualifications;
    /** Duties carried from past operations of the year — cumulative fairness offset. */
    private final Map<DutyRole, Integer> priorWorkload;

    public Teacher(String id, String name, Subject subject, String establishment,
                   Gender gender,
                   Set<Unavailability> unavailabilities,
                   Set<TeacherQualification> qualifications) {
        this(id, name, subject, establishment, gender, unavailabilities, qualifications, Map.of());
    }

    public Teacher(String id, String name, Subject subject, String establishment,
                   Gender gender,
                   Set<Unavailability> unavailabilities,
                   Set<TeacherQualification> qualifications,
                   Map<DutyRole, Integer> priorWorkload) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.subject = Objects.requireNonNull(subject);
        this.establishment = establishment;
        this.gender = gender;
        this.unavailabilities = Set.copyOf(unavailabilities);
        this.qualifications = Set.copyOf(qualifications);
        this.priorWorkload = Map.copyOf(priorWorkload);
    }

    /** Copy with cumulative history from previous operations of the year. */
    public Teacher withPriorWorkload(Map<DutyRole, Integer> prior) {
        return new Teacher(id, name, subject, establishment, gender,
                unavailabilities, qualifications, prior);
    }

    public int prior(DutyRole role) { return priorWorkload.getOrDefault(role, 0); }

    public int priorTotal() {
        return priorWorkload.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Default qualifications: surveillance + reserve + permanence for own subject. */
    public static Teacher withDefaults(String id, String name, Subject subject, String establishment) {
        return new Teacher(id, name, subject, establishment, null, Set.of(), Set.of(
                TeacherQualification.forRole(DutyRole.SURVEILLANCE),
                TeacherQualification.forRole(DutyRole.RESERVE),
                TeacherQualification.permanenceFor(subject)));
    }

    public boolean isAvailable(ExamSlot slot) {
        return unavailabilities.stream().noneMatch(u -> u.covers(slot));
    }

    public boolean isQualified(DutyRole role, Subject examSubject) {
        return qualifications.stream().anyMatch(q ->
                q.role() == role
                && (role != DutyRole.PERMANENCE
                    || q.subject().map(s -> s.equals(examSubject)).orElse(false)));
    }

    public String id() { return id; }
    public String name() { return name; }
    public Subject subject() { return subject; }
    public String establishment() { return establishment; }
    public Optional<Gender> gender() { return Optional.ofNullable(gender); }
    public Set<Unavailability> unavailabilities() { return unavailabilities; }
    public Set<TeacherQualification> qualifications() { return qualifications; }

    @Override public boolean equals(Object o) {
        return o instanceof Teacher t && id.equals(t.id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() { return name + " (" + subject.name() + ")"; }
}
