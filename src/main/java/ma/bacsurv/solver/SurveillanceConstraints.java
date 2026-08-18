package ma.bacsurv.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import ma.bacsurv.domain.DutyRole;
import ma.bacsurv.domain.Teacher;

import java.time.LocalDate;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * MODEL.md encoded for Timefold. Hard = legality, soft = quality.
 * Soft weights: pair/room repetition 10, consecutive-days 20,
 * per-role balance 1 per squared duty (sum-of-squares ⇒ balancing).
 */
public class SurveillanceConstraints implements ConstraintProvider {

    static final int WEIGHT_REPEATED_ROOM = 10;
    static final int WEIGHT_REPEATED_PAIR = 10;
    static final int WEIGHT_CONSECUTIVE_DAYS = 20;
    static final int UNFAIRNESS_SCALE = 1000;
    static final int WEIGHT_TOTAL_BALANCE = 3;
    static final int WEIGHT_ROLE_BALANCE = 1;
    static final int WEIGHT_HALF_DAY_IMBALANCE = 2;
    static final int WEIGHT_SAME_GENDER_PAIR = 1;
    static final int WEIGHT_IDLE_TEACHER = 5000;
    static final int MAX_CONSECUTIVE_DAYS = 3;

    @Override
    public Constraint[] defineConstraints(ConstraintFactory f) {
        return new Constraint[] {
                // hard
                noOverlapInSlot(f),
                teacherAvailable(f),
                permanenceRequiresSpecialist(f),
                noOwnSubjectSurveillance(f),
                // soft
                avoidRepeatedRoom(f),
                avoidRepeatedPair(f),
                maxConsecutiveDays(f),
                balanceTotal(f),
                noIdleTeacher(f),
                balanceHalfDays(f),
                mixedGenderPair(f),
                balanceRole(f, DutyRole.SURVEILLANCE, "balance surveillance load"),
                balanceRole(f, DutyRole.RESERVE, "balance reserve load"),
                balanceRole(f, DutyRole.PERMANENCE, "balance permanence load"),
        };
    }

    // H2
    Constraint noOverlapInSlot(ConstraintFactory f) {
        return f.forEachUniquePair(DutyAssignment.class,
                        Joiners.equal(DutyAssignment::slotId),
                        Joiners.equal(DutyAssignment::getTeacher))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("teacher twice in same slot");
    }

    // H3
    Constraint teacherAvailable(ConstraintFactory f) {
        return f.forEach(DutyAssignment.class)
                .filter(a -> !a.getTeacher().isAvailable(a.getDuty().slot()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("teacher unavailable");
    }

    // H4
    Constraint permanenceRequiresSpecialist(ConstraintFactory f) {
        return f.forEach(DutyAssignment.class)
                .filter(a -> !a.getTeacher().isQualified(a.role(), a.examSubject()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("teacher not qualified");
    }

    // H5 — scope is the duty's exam, not the slot
    Constraint noOwnSubjectSurveillance(ConstraintFactory f) {
        return f.forEach(DutyAssignment.class)
                .filter(a -> a.isSurveillance()
                        && a.getTeacher().subject().equals(a.examSubject()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("own-subject surveillance");
    }

    // S — AvoidRepeatedRoomAssignment
    Constraint avoidRepeatedRoom(ConstraintFactory f) {
        return f.forEachUniquePair(DutyAssignment.class,
                        Joiners.equal(DutyAssignment::getTeacher),
                        Joiners.equal(a -> a.isSurveillance()
                                ? a.getDuty().room().orElseThrow().id() : "!" + a.getId()))
                .filter((a, b) -> a.isSurveillance() && b.isSurveillance())
                .penalize(HardSoftScore.ofSoft(WEIGHT_REPEATED_ROOM))
                .asConstraint("repeated room for teacher");
    }

    // S — AvoidRepeatedPair: unordered, pairwise within a room of one exam
    Constraint avoidRepeatedPair(ConstraintFactory f) {
        return f.forEachUniquePair(DutyAssignment.class,
                        Joiners.equal(DutyAssignment::roomKey))
                .map(SurveillanceConstraints::pairKey)
                .groupBy(Function.identity(), ConstraintCollectors.count())
                .filter((pair, n) -> n > 1)
                .penalize(HardSoftScore.ofSoft(WEIGHT_REPEATED_PAIR), (pair, n) -> n - 1)
                .asConstraint("repeated teacher pair");
    }

    private static String pairKey(DutyAssignment a, DutyAssignment b) {
        String x = a.getTeacher().id(), y = b.getTeacher().id();
        return x.compareTo(y) <= 0 ? x + "|" + y : y + "|" + x;
    }

    // S — max consecutive working days
    Constraint maxConsecutiveDays(ConstraintFactory f) {
        return f.forEach(DutyAssignment.class)
                .groupBy(DutyAssignment::getTeacher,
                        ConstraintCollectors.toSet(DutyAssignment::date))
                .filter((t, days) -> longestRun(days) > MAX_CONSECUTIVE_DAYS)
                .penalize(HardSoftScore.ofSoft(WEIGHT_CONSECUTIVE_DAYS),
                        (t, days) -> longestRun(days) - MAX_CONSECUTIVE_DAYS)
                .asConstraint("consecutive days");
    }

    private static int longestRun(Set<LocalDate> days) {
        int longest = 0, run = 0;
        LocalDate prev = null;
        for (LocalDate day : new TreeSet<>(days)) {
            run = (prev != null && prev.plusDays(1).equals(day)) ? run + 1 : 1;
            longest = Math.max(longest, run);
            prev = day;
        }
        return longest;
    }

    // S — per-role balance via Timefold's loadBalance collector. The third
    // argument seeds each teacher with prior(role) from earlier operations of
    // the year, so a teacher loaded in June is naturally relieved in July.
    // unfairness() ≈ std deviation of loads; scaled to int soft points.
    Constraint balanceRole(ConstraintFactory f, DutyRole role, String name) {
        return f.forEach(DutyAssignment.class)
                .filter(a -> a.role() == role)
                .groupBy(ConstraintCollectors.loadBalance(
                        DutyAssignment::getTeacher,
                        a -> 1L,
                        a -> a.getTeacher().prior(role)))
                .penalize(HardSoftScore.ONE_SOFT,
                        lb -> unfairnessPoints(lb, WEIGHT_ROLE_BALANCE))
                .asConstraint(name);
    }

    // S1 — total workload balance (cumulative via priorTotal). Weighted above
    // the per-role terms: the roles shape the mix, this caps the sum.
    Constraint balanceTotal(ConstraintFactory f) {
        return f.forEach(DutyAssignment.class)
                .groupBy(ConstraintCollectors.loadBalance(
                        DutyAssignment::getTeacher,
                        a -> 1L,
                        a -> a.getTeacher().priorTotal()))
                .penalize(HardSoftScore.ONE_SOFT,
                        lb -> unfairnessPoints(lb, WEIGHT_TOTAL_BALANCE))
                .asConstraint("balance total load");
    }

    /**
     * A teacher who receives nothing is invisible to the load-balance
     * collector above, which only ever sees teachers that appear in the
     * assignment stream. Without this, the cheapest way to look balanced is
     * to leave people out entirely — and that is precisely unfair. Penalising
     * idleness keeps every teacher inside the balance.
     */
    Constraint noIdleTeacher(ConstraintFactory f) {
        return f.forEach(Teacher.class)
                .ifNotExists(DutyAssignment.class,
                        Joiners.equal(Function.identity(), DutyAssignment::getTeacher))
                .penalize(HardSoftScore.ofSoft(WEIGHT_IDLE_TEACHER))
                .asConstraint("teacher left idle");
    }

    private static int unfairnessPoints(
            ai.timefold.solver.core.api.score.stream.common.LoadBalance<Teacher> lb,
            int weight) {
        return lb.unfairness()
                .multiply(java.math.BigDecimal.valueOf((long) UNFAIRNESS_SCALE * weight))
                .intValue();
    }

    // S — AM/PM balance per teacher
    Constraint balanceHalfDays(ConstraintFactory f) {
        return f.forEach(DutyAssignment.class)
                .groupBy(DutyAssignment::getTeacher,
                        ConstraintCollectors.sum(a ->
                                a.getDuty().slot().halfDay() == ma.bacsurv.domain.HalfDay.AM ? 1 : 0),
                        ConstraintCollectors.sum(a ->
                                a.getDuty().slot().halfDay() == ma.bacsurv.domain.HalfDay.PM ? 1 : 0))
                .penalize(HardSoftScore.ofSoft(WEIGHT_HALF_DAY_IMBALANCE),
                        (t, am, pm) -> Math.abs(am - pm))
                .asConstraint("half-day balance");
    }

    // S — prefer mixed-gender room pairs; only counts when both genders known
    Constraint mixedGenderPair(ConstraintFactory f) {
        return f.forEachUniquePair(DutyAssignment.class,
                        Joiners.equal(DutyAssignment::roomKey))
                .filter((a, b) -> a.getTeacher().gender().isPresent()
                        && b.getTeacher().gender().isPresent()
                        && a.getTeacher().gender().equals(b.getTeacher().gender()))
                .penalize(HardSoftScore.ofSoft(WEIGHT_SAME_GENDER_PAIR))
                .asConstraint("same-gender pair");
    }
}
