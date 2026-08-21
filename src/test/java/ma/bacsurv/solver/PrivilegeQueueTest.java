package ma.bacsurv.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.constraint.ConstraintMatchTotal;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ma.bacsurv.TestFixtures;
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
 * Surveillance is the work; réserve and permanence are turns. Two teachers can
 * hold the same number of duties and still be treated unequally if one of them
 * takes every light duty — so the schedule is scored on the mix, not only on
 * the count.
 *
 * <p>Each case compares two complete schedules and reads the penalty of the
 * privilege queue alone. Comparing total scores would prove less than it looks:
 * hoarding the light duties also repeats a room and unbalances surveillance, so
 * the totals would separate even with the queue removed.
 */
class PrivilegeQueueTest {

    private static final String QUEUE = "privilege queue";

    private static final Exam EXAM = Exam.of("E-FR", TestFixtures.FRENCH,
            TestFixtures.ARTS, List.of(TestFixtures.R1, TestFixtures.R2));

    private static ExamSlot slot(String id, int day) {
        return new ExamSlot(id, LocalDate.of(2026, 6, day),
                LocalTime.of(8, 0), LocalTime.of(10, 0), 1, List.of(EXAM), 1);
    }

    /** Teachers of maths, so the French paper is never their own subject. */
    private static Teacher teacher(String id) {
        return TestFixtures.teacher(id, TestFixtures.MATH);
    }

    @Test
    void spreadingPrivilegesBeatsHoardingThemAtEqualTotals() {
        ExamSlot s1 = slot("SL1", 1);
        ExamSlot s2 = slot("SL2", 2);
        // distinct rooms, so neither arrangement is penalised for a repeated room
        Duty surv1 = Duty.surveillance("D1", s1, EXAM, TestFixtures.R1);
        Duty res1 = Duty.reserve("D2", s1);
        Duty surv2 = Duty.surveillance("D3", s2, EXAM, TestFixtures.R2);
        Duty res2 = Duty.reserve("D4", s2);

        Teacher amine = teacher("A");
        Teacher badr = teacher("B");
        List<Teacher> pool = List.of(amine, badr);

        // Amine takes both réserves, Badr does both surveillances.
        // Two duties each — the totals say this is fair, the work says otherwise.
        Map<Duty, Teacher> hoarded = Map.of(
                res1, amine, res2, amine,
                surv1, badr, surv2, badr);
        // one turn each, one surveillance each
        Map<Duty, Teacher> shared = Map.of(
                surv1, amine, res2, amine,
                res1, badr, surv2, badr);

        assertEquals(0, score(hoarded, pool).hardScore(), "both schedules are legal");
        assertEquals(0, score(shared, pool).hardScore(), "both schedules are legal");
        assertEquals(0, penaltyOf(QUEUE, shared, pool), "one turn each is a clean round");
        assertTrue(penaltyOf(QUEUE, hoarded, pool) < 0,
                "two turns for one teacher while the other has none must be penalised");
    }

    @Test
    void aTeacherWhoAlreadyHadATurnLastSessionGoesLast() {
        ExamSlot s1 = slot("SL1", 1);
        Duty surveillance = Duty.surveillance("D1", s1, EXAM, TestFixtures.R1);
        Duty reserve = Duty.reserve("D2", s1);

        // Amine finished the previous session one turn ahead of Badr
        Teacher amine = teacher("A").withPrivilegeCarry(1);
        Teacher badr = teacher("B");
        List<Teacher> pool = List.of(amine, badr);

        Map<Duty, Teacher> repeatsAmine = Map.of(reserve, amine, surveillance, badr);
        Map<Duty, Teacher> relievesBadr = Map.of(reserve, badr, surveillance, amine);

        assertEquals(0, penaltyOf(QUEUE, relievesBadr, pool),
                "Badr's turn levels the round Amine already led");
        assertTrue(penaltyOf(QUEUE, repeatsAmine, pool) < 0,
                "a second turn for Amine while Badr is still owed one must be penalised");
    }

    @Test
    void reserveAndPermanenceShareOneQueue() {
        // Permanence is drawn from a different pool than réserve — only the
        // subject's specialists — but having had one still means having had a
        // turn, so it must not be followed by the other.
        ExamSlot s1 = slot("SL1", 1);
        ExamSlot s2 = slot("SL2", 2);
        Duty reserveDay1 = Duty.reserve("D1", s1);
        Duty permanenceDay2 = Duty.permanence("D2", s2, EXAM);
        Duty survDay1 = Duty.surveillance("D3", s1, EXAM, TestFixtures.R1);
        Duty survDay2 = Duty.surveillance("D4", s2, EXAM, TestFixtures.R2);

        // both are French specialists, so either may hold the French permanence
        Teacher amine = TestFixtures.teacher("A", TestFixtures.FRENCH);
        Teacher badr = TestFixtures.teacher("B", TestFixtures.FRENCH);
        List<Teacher> pool = List.of(amine, badr);

        Map<Duty, Teacher> bothToAmine = Map.of(
                reserveDay1, amine, permanenceDay2, amine,
                survDay1, badr, survDay2, badr);
        Map<Duty, Teacher> oneEach = Map.of(
                reserveDay1, amine, permanenceDay2, badr,
                survDay1, badr, survDay2, amine);

        assertEquals(0, penaltyOf(QUEUE, oneEach, pool),
                "a réserve and a permanence held by different people is one round");
        assertTrue(penaltyOf(QUEUE, bothToAmine, pool) < 0,
                "a réserve then a permanence is two turns, not one of each");
    }

    @Test
    void surveillanceStaysBalancedOnItsOwn() {
        // the queue governs the turns; the work is still spread separately
        ExamSlot s1 = slot("SL1", 1);
        ExamSlot s2 = slot("SL2", 2);
        Duty surv1 = Duty.surveillance("D1", s1, EXAM, TestFixtures.R1);
        Duty surv2 = Duty.surveillance("D2", s2, EXAM, TestFixtures.R2);

        Teacher amine = teacher("A");
        Teacher badr = teacher("B");
        List<Teacher> pool = List.of(amine, badr);

        int oneEach = penaltyOf("balance surveillance load",
                Map.of(surv1, amine, surv2, badr), pool);
        int allToAmine = penaltyOf("balance surveillance load",
                Map.of(surv1, amine, surv2, amine), pool);

        assertEquals(0, oneEach);
        assertTrue(allToAmine < 0,
                "a teacher left without surveillance must still register as unbalanced");
    }

    /** Scores a complete, hand-made schedule without running the solver. */
    private static HardSoftScore score(Map<Duty, Teacher> schedule, List<Teacher> pool) {
        return manager().update(planOf(schedule, pool));
    }

    /** The soft points one named constraint costs this schedule. */
    private static int penaltyOf(String constraintName,
                                 Map<Duty, Teacher> schedule, List<Teacher> pool) {
        ConstraintMatchTotal<HardSoftScore> total =
                manager().explain(planOf(schedule, pool)).getConstraintMatchTotalMap()
                        .values().stream()
                        .filter(t -> t.getConstraintRef().constraintName().equals(constraintName))
                        .findFirst().orElse(null);
        return total == null ? 0 : total.getScore().softScore();
    }

    private static SurveillancePlan planOf(Map<Duty, Teacher> schedule, List<Teacher> pool) {
        List<DutyAssignment> assignments = schedule.entrySet().stream()
                .map(entry -> {
                    entry.getKey().assign(entry.getValue());
                    return new DutyAssignment(entry.getKey());
                })
                .toList();
        return new SurveillancePlan(assignments, pool, SchedulingPolicy.defaults());
    }

    private static SolutionManager<SurveillancePlan, HardSoftScore> manager() {
        SolverConfig config = new SolverConfig()
                .withSolutionClass(SurveillancePlan.class)
                .withEntityClasses(DutyAssignment.class)
                .withConstraintProviderClass(SurveillanceConstraints.class);
        return SolutionManager.create(SolverFactory.create(config));
    }
}
