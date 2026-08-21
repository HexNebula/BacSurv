package ma.bacsurv.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ma.bacsurv.TestFixtures;
import ma.bacsurv.application.ScheduleValidator;
import ma.bacsurv.domain.Duty;
import ma.bacsurv.domain.Exam;
import ma.bacsurv.domain.ExamSlot;
import ma.bacsurv.domain.Teacher;
import ma.bacsurv.rules.SchedulingPolicy;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A real 2BAC session put two Philosophie papers in the same afternoon:
 * 15:00–18:00 for the literary streams, 15:00–17:00 for the scientific ones.
 * Different end times make them different slots, and a rule written on slot
 * identity let one teacher hold a room in both and still called the schedule
 * feasible. What makes two duties incompatible is sharing a moment, not
 * sharing a label.
 */
class OverlappingSlotsTest {

    private static final LocalDate DAY = LocalDate.of(2026, 6, 5);

    private static final Exam LONG_PAPER = Exam.of("E-LONG", TestFixtures.FRENCH,
            TestFixtures.ARTS, List.of(TestFixtures.R1));
    private static final Exam SHORT_PAPER = Exam.of("E-SHORT", TestFixtures.FRENCH,
            TestFixtures.SCIENCES, List.of(TestFixtures.R2));

    /** 15:00–18:00, the literary paper. */
    private static ExamSlot longSlot() {
        return new ExamSlot("S-LONG", DAY, LocalTime.of(15, 0), LocalTime.of(18, 0),
                1, List.of(LONG_PAPER), 0);
    }

    /** 15:00–17:00 the same afternoon, the scientific one. */
    private static ExamSlot shortSlot() {
        return new ExamSlot("S-SHORT", DAY, LocalTime.of(15, 0), LocalTime.of(17, 0),
                1, List.of(SHORT_PAPER), 0);
    }

    /** 08:00–11:00, a genuinely separate séance. */
    private static ExamSlot morningSlot() {
        return new ExamSlot("S-AM", DAY, LocalTime.of(8, 0), LocalTime.of(11, 0),
                1, List.of(SHORT_PAPER), 0);
    }

    @Test
    void slotsOfDifferentLengthStartingTogetherDoOverlap() {
        assertTrue(longSlot().overlaps(shortSlot()));
        assertTrue(shortSlot().overlaps(longSlot()));
    }

    @Test
    void aMorningAndAnAfternoonDoNotOverlap() {
        assertTrue(!morningSlot().overlaps(longSlot()),
                "08:00-11:00 and 15:00-18:00 are two séances of one day");
    }

    @Test
    void slotsThatMerelyTouchDoNotOverlap() {
        ExamSlot until11 = new ExamSlot("A", DAY, LocalTime.of(8, 0), LocalTime.of(11, 0),
                1, List.of(SHORT_PAPER), 0);
        ExamSlot from11 = new ExamSlot("B", DAY, LocalTime.of(11, 0), LocalTime.of(13, 0),
                1, List.of(SHORT_PAPER), 0);
        assertTrue(!until11.overlaps(from11), "one ends as the other begins");
    }

    @Test
    void theSolverRefusesATeacherHeldByTwoOverlappingSlots() {
        Teacher amine = TestFixtures.teacher("A", TestFixtures.MATH);
        Duty inLongPaper = Duty.surveillance("D1", longSlot(), LONG_PAPER, TestFixtures.R1);
        Duty inShortPaper = Duty.surveillance("D2", shortSlot(), SHORT_PAPER, TestFixtures.R2);

        HardSoftScore score = score(Map.of(inLongPaper, amine, inShortPaper, amine),
                List.of(amine));

        assertEquals(-1, score.hardScore(),
                "one teacher cannot hold a room from 15:00 to 18:00 and another from 15:00 to 17:00");
    }

    @Test
    void theSolverStillAllowsAMorningAndAnAfternoon() {
        Teacher amine = TestFixtures.teacher("A", TestFixtures.MATH);
        Duty morning = Duty.surveillance("D1", morningSlot(), SHORT_PAPER, TestFixtures.R2);
        Duty afternoon = Duty.surveillance("D2", longSlot(), LONG_PAPER, TestFixtures.R1);

        assertEquals(0, score(Map.of(morning, amine, afternoon, amine), List.of(amine)).hardScore(),
                "two séances of the same day remain legal");
    }

    @Test
    void theValidatorReportsTheSameClash() {
        Teacher amine = TestFixtures.teacher("A", TestFixtures.MATH);
        Duty inLongPaper = Duty.surveillance("D1", longSlot(), LONG_PAPER, TestFixtures.R1);
        Duty inShortPaper = Duty.surveillance("D2", shortSlot(), SHORT_PAPER, TestFixtures.R2);
        inLongPaper.assign(amine);
        inShortPaper.assign(amine);

        var report = ScheduleValidator.withDefaults()
                .validate(List.of(inLongPaper, inShortPaper));

        assertEquals(1, report.violations().stream()
                        .filter(v -> v.rule().equals("H2-overlap")).count(),
                "the validator must catch what the solver refuses: " + report.violations());
    }

    private static HardSoftScore score(Map<Duty, Teacher> schedule, List<Teacher> pool) {
        List<DutyAssignment> assignments = schedule.entrySet().stream()
                .map(entry -> {
                    entry.getKey().assign(entry.getValue());
                    return new DutyAssignment(entry.getKey());
                })
                .toList();
        SolverConfig config = new SolverConfig()
                .withSolutionClass(SurveillancePlan.class)
                .withEntityClasses(DutyAssignment.class)
                .withConstraintProviderClass(SurveillanceConstraints.class);
        return SolutionManager.<SurveillancePlan, HardSoftScore>create(
                        SolverFactory.<SurveillancePlan>create(config))
                .update(new SurveillancePlan(assignments, pool, SchedulingPolicy.defaults()));
    }
}
