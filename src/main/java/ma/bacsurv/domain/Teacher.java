package ma.bacsurv.domain;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class Teacher {

    private final String id;
    /** Numéro de matricule / رقم التأجير — the staff number the administration uses. */
    private final String matricule;
    private final String name;
    private final Subject subject;
    private final String establishment;
    private final Gender gender; // nullable — only used by optional mixed-pair preference
    private final Set<Unavailability> unavailabilities;
    private final Set<TeacherQualification> qualifications;
    /** Duties carried from past operations of the year — cumulative fairness offset. */
    private final Map<DutyRole, Integer> priorWorkload;
    /**
     * Privilege turns already taken beyond the slowest colleague in the
     * previous session. Almost always 0 or 1: a completed round cancels
     * itself out, so this cannot accumulate across sessions.
     */
    private final int privilegeCarry;

    public Teacher(String id, String matricule, String name, Subject subject, String establishment,
                   Gender gender,
                   Set<Unavailability> unavailabilities,
                   Set<TeacherQualification> qualifications) {
        this(id, matricule, name, subject, establishment, gender,
                unavailabilities, qualifications, Map.of());
    }

    public Teacher(String id, String matricule, String name, Subject subject, String establishment,
                   Gender gender,
                   Set<Unavailability> unavailabilities,
                   Set<TeacherQualification> qualifications,
                   Map<DutyRole, Integer> priorWorkload) {
        this(id, matricule, name, subject, establishment, gender,
                unavailabilities, qualifications, priorWorkload, 0);
    }

    public Teacher(String id, String matricule, String name, Subject subject, String establishment,
                   Gender gender,
                   Set<Unavailability> unavailabilities,
                   Set<TeacherQualification> qualifications,
                   Map<DutyRole, Integer> priorWorkload,
                   int privilegeCarry) {
        this.privilegeCarry = privilegeCarry;
        this.id = Objects.requireNonNull(id);
        this.matricule = requireText(matricule, "matricule");
        this.name = Objects.requireNonNull(name);
        this.subject = Objects.requireNonNull(subject);
        this.establishment = establishment;
        this.gender = gender;
        this.unavailabilities = Set.copyOf(unavailabilities);
        this.qualifications = Set.copyOf(qualifications);
        this.priorWorkload = Map.copyOf(priorWorkload);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(field + " is required");
        return value;
    }

    /** Copy with cumulative history from previous operations of the year. */
    public Teacher withPriorWorkload(Map<DutyRole, Integer> prior) {
        return new Teacher(id, matricule, name, subject, establishment, gender,
                unavailabilities, qualifications, prior, privilegeCarry);
    }

    /** Copy with the privilege turns carried over from the previous session. */
    public Teacher withPrivilegeCarry(int carry) {
        return new Teacher(id, matricule, name, subject, establishment, gender,
                unavailabilities, qualifications, priorWorkload, carry);
    }

    public int prior(DutyRole role) { return priorWorkload.getOrDefault(role, 0); }

    public int privilegeCarry() { return privilegeCarry; }

    public int priorTotal() {
        return priorWorkload.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Default qualifications: surveillance + reserve + permanence for own subject. */
    public static Teacher withDefaults(String id, String matricule, String name,
                                       Subject subject, String establishment) {
        return new Teacher(id, matricule, name, subject, establishment, null, Set.of(), Set.of(
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
    public String matricule() { return matricule; }
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
