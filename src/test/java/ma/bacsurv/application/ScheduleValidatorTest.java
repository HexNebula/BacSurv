package ma.bacsurv.application;

import ma.bacsurv.TestFixtures;
import ma.bacsurv.domain.Duty;
import ma.bacsurv.domain.DutyRole;
import ma.bacsurv.domain.Teacher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleValidatorTest {

    private final ScheduleValidator validator = ScheduleValidator.withDefaults();
    private List<Duty> duties;
    private List<Teacher> pool;

    @BeforeEach
    void setUp() {
        duties = new DutyGenerator().generate(TestFixtures.singleSlotOperation());
        // 13 duties, 13 teachers of neutral subject → clean fill possible
        pool = IntStream.rangeClosed(1, 13)
                .mapToObj(i -> TestFixtures.teacher("T" + i, TestFixtures.MATH))
                .toList();
    }

    @Test
    void unfilledDutyIsHardViolation() {
        ValidationReport report = validator.validate(duties);
        assertFalse(report.isFeasible());
        assertEquals(13, report.hardViolations().stream()
                .filter(v -> v.rule().equals("H1-coverage")).count());
    }

    @Test
    void cleanScheduleIsFeasible() {
        fillCleanly();
        ValidationReport report = validator.validate(duties);
        assertTrue(report.isFeasible(), () -> report.hardViolations().toString());
    }

    @Test
    void doubleBookingSameSlotIsHardViolation() {
        fillCleanly();
        // give teacher of duty 0 also duty 1 (same slot)
        duties.get(1).assign(duties.get(0).assignedTeacher().orElseThrow());
        ValidationReport report = validator.validate(duties);
        assertTrue(report.hardViolations().stream()
                .anyMatch(v -> v.rule().equals("H2-overlap")));
    }

    @Test
    void ownSubjectSurveillanceIsHardViolation() {
        fillCleanly();
        Teacher hgTeacher = TestFixtures.teacher("T-HG", TestFixtures.HG);
        Duty hgRoomDuty = duties.stream()
                .filter(d -> d.role() == DutyRole.SURVEILLANCE
                        && d.exam().orElseThrow().subject().equals(TestFixtures.HG))
                .findFirst().orElseThrow();
        hgRoomDuty.assign(hgTeacher);
        ValidationReport report = validator.validate(duties);
        assertTrue(report.hardViolations().stream()
                .anyMatch(v -> v.rule().equals("H5-subject-conflict")));
    }

    @Test
    void mathTeacherMaySurveilFrenchExamInSameSlotAsNothingMathRuns() {
        // subject conflict scope = exam, not slot: MATH teachers surveilling
        // HG and FRENCH exams is legal even though both run in this slot
        fillCleanly();
        ValidationReport report = validator.validate(duties);
        assertTrue(report.hardViolations().stream()
                .noneMatch(v -> v.rule().equals("H5-subject-conflict")));
    }

    @Test
    void permanenceRequiresSpecialist() {
        fillCleanly();
        // MATH teacher on French permanence → H4
        Duty frenchPermanence = duties.stream()
                .filter(d -> d.role() == DutyRole.PERMANENCE
                        && d.exam().orElseThrow().subject().equals(TestFixtures.FRENCH))
                .findFirst().orElseThrow();
        frenchPermanence.assign(TestFixtures.teacher("T-M", TestFixtures.MATH));
        ValidationReport report = validator.validate(duties);
        assertTrue(report.hardViolations().stream()
                .anyMatch(v -> v.rule().equals("H4-qualification")));
    }

    @Test
    void workloadCountsPerRole() {
        fillCleanly();
        ValidationReport report = validator.validate(duties);
        long totalCounted = report.workloadByTeacher().values().stream()
                .flatMap(m -> m.values().stream()).mapToLong(Long::longValue).sum();
        assertEquals(13, totalCounted);
    }

    /** Assign each duty a distinct teacher, specialists on permanence. */
    private void fillCleanly() {
        Teacher hgSpecialist = TestFixtures.teacher("T-HGP", TestFixtures.HG);
        Teacher frSpecialist = TestFixtures.teacher("T-FRP", TestFixtures.FRENCH);
        int next = 0;
        for (Duty d : duties) {
            switch (d.role()) {
                case PERMANENCE -> d.assign(
                        d.exam().orElseThrow().subject().equals(TestFixtures.HG)
                                ? hgSpecialist : frSpecialist);
                default -> d.assign(pool.get(next++));
            }
        }
    }
}
