package ma.bacsurv.domain;

import java.util.Optional;

/**
 * Permission, not availability. Subject only meaningful for PERMANENCE
 * (the specialist subject the teacher may cover).
 */
public record TeacherQualification(DutyRole role, Optional<Subject> subject) {

    public static TeacherQualification forRole(DutyRole role) {
        return new TeacherQualification(role, Optional.empty());
    }

    public static TeacherQualification permanenceFor(Subject subject) {
        return new TeacherQualification(DutyRole.PERMANENCE, Optional.of(subject));
    }
}
