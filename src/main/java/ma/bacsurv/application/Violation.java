package ma.bacsurv.application;

import ma.bacsurv.rules.ConstraintStrength;

public record Violation(String rule, ConstraintStrength strength, String detail) {

    public static Violation hard(String rule, String detail) {
        return new Violation(rule, ConstraintStrength.HARD, detail);
    }

    public static Violation soft(String rule, String detail) {
        return new Violation(rule, ConstraintStrength.SOFT, detail);
    }
}
