package ma.bacsurv.application;

import ma.bacsurv.domain.DutyRole;
import ma.bacsurv.domain.Teacher;
import ma.bacsurv.rules.ConstraintStrength;

import java.util.List;
import java.util.Map;

public record ValidationReport(List<Violation> violations,
                               Map<Teacher, Map<DutyRole, Long>> workloadByTeacher) {

    /** Feasible = zero hard violations. Soft violations are normal. */
    public boolean isFeasible() {
        return violations.stream().noneMatch(v -> v.strength() == ConstraintStrength.HARD);
    }

    public List<Violation> hardViolations() {
        return violations.stream().filter(v -> v.strength() == ConstraintStrength.HARD).toList();
    }

    public List<Violation> softViolations() {
        return violations.stream().filter(v -> v.strength() == ConstraintStrength.SOFT).toList();
    }
}
