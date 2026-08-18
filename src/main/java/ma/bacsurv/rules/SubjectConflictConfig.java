package ma.bacsurv.rules;

/**
 * Own-subject surveillance rule. Default HARD, but configurable —
 * the 2026 official text has not been verified yet.
 * Reserve is slot-scoped (no exam), so own-subject reserve is allowed
 * unless an academy forbids it.
 */
public record SubjectConflictConfig(boolean enabled, ConstraintStrength strength,
                                    boolean forbidOwnSubjectReserve) {

    public static SubjectConflictConfig defaults() {
        return new SubjectConflictConfig(true, ConstraintStrength.HARD, false);
    }
}
