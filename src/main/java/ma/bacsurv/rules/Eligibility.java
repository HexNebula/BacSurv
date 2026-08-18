package ma.bacsurv.rules;

import ma.bacsurv.domain.Duty;
import ma.bacsurv.domain.DutyRole;
import ma.bacsurv.domain.Subject;
import ma.bacsurv.domain.Teacher;

/**
 * eligible(teacher, duty) = available AND qualified AND no hard conflict.
 * Soft-configured subject conflict does NOT remove eligibility — it only
 * costs score at solve time.
 */
public final class Eligibility {

    private final SubjectConflictConfig subjectConflict;

    public Eligibility(SubjectConflictConfig subjectConflict) {
        this.subjectConflict = subjectConflict;
    }

    public static Eligibility withDefaults() {
        return new Eligibility(SubjectConflictConfig.defaults());
    }

    public boolean isEligible(Teacher teacher, Duty duty) {
        if (!teacher.isAvailable(duty.slot())) return false;

        Subject examSubject = duty.exam().map(e -> e.subject()).orElse(null);
        if (!teacher.isQualified(duty.role(), examSubject)) return false;

        return !isHardSubjectConflict(teacher, duty);
    }

    public boolean isHardSubjectConflict(Teacher teacher, Duty duty) {
        if (!subjectConflict.enabled()
                || subjectConflict.strength() != ConstraintStrength.HARD) return false;
        return isSubjectConflict(teacher, duty);
    }

    /** Conflict scope is the duty's exam, never the whole slot. */
    public boolean isSubjectConflict(Teacher teacher, Duty duty) {
        return switch (duty.role()) {
            case SURVEILLANCE -> duty.exam()
                    .map(e -> e.subject().equals(teacher.subject()))
                    .orElse(false);
            case PERMANENCE -> false; // own subject is required, not a conflict
            case RESERVE -> subjectConflict.forbidOwnSubjectReserve()
                    && duty.slot().exams().stream()
                           .anyMatch(e -> e.subject().equals(teacher.subject()));
        };
    }
}
