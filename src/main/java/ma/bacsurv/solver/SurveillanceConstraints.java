package ma.bacsurv.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import ai.timefold.solver.core.api.score.stream.bi.BiConstraintStream;
import ma.bacsurv.domain.DutyRole;
import ma.bacsurv.domain.Teacher;
import ma.bacsurv.rules.SchedulingPolicy;

import java.time.LocalDate;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * MODEL.md encoded for Timefold. Hard = legality, soft = quality.
 *
 * <p>Fairness is two objectives, not one: surveillance — the work — is spread
 * evenly, while réserve and permanence share a single queue of turns. The
 * privilege queue outweighs the rest, since a repeated turn is the unfairness
 * teachers actually notice.
 */
public class SurveillanceConstraints implements ConstraintProvider {

    static final int WEIGHT_REPEATED_ROOM = 10;
    static final int WEIGHT_REPEATED_PAIR = 10;
    static final int WEIGHT_CONSECUTIVE_DAYS = 20;
    static final int UNFAIRNESS_SCALE = 1000;
    static final int WEIGHT_TOTAL_BALANCE = 3;
    static final int WEIGHT_SURVEILLANCE_BALANCE = 3;
    static final int WEIGHT_PRIVILEGE_QUEUE = 8;
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
                maxConsecutiveDaysAsRule(f),
                minimumGapBetweenDuties(f),
                balanceTotal(f),
                noIdleTeacher(f),
                balanceHalfDays(f),
                mixedGenderPair(f),
                balanceSurveillance(f),
                reserveQueue(f),
                permanenceQueue(f),
        };
    }

    /**
     * H2 — nobody is in two places at once.
     *
     * <p>Compares clocks, not slot labels. Slots that start together and end
     * at different hours are different slots, so a rule written on slot
     * identity lets a teacher hold a room from 15:00 to 18:00 and another
     * from 15:00 to 17:00 and calls the schedule feasible. Duties of one slot
     * share their times exactly, so they still overlap and stay covered; a
     * morning and an afternoon do not touch and stay allowed.
     */
    Constraint noOverlapInSlot(ConstraintFactory f) {
        return f.forEachUniquePair(DutyAssignment.class,
                        Joiners.equal(DutyAssignment::getTeacher),
                        Joiners.overlapping(DutyAssignment::startsAt, DutyAssignment::endsAt))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("teacher in two places at once");
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

    /**
     * S — consecutive working days, capped at whatever the centre configured.
     * Soft by default: at high density a run of four days is unavoidable, and
     * a hard rule there would make an ordinary centre unschedulable.
     */
    Constraint maxConsecutiveDays(ConstraintFactory f) {
        return f.forEach(DutyAssignment.class)
                .groupBy(DutyAssignment::getTeacher,
                        ConstraintCollectors.toSet(DutyAssignment::date))
                .join(SchedulingPolicy.class)
                .filter((t, days, policy) -> !policy.consecutiveDaysIsHard()
                        && longestRun(days) > policy.maxConsecutiveWorkingDays())
                .penalize(HardSoftScore.ONE_SOFT,
                        (t, days, policy) -> (longestRun(days) - policy.maxConsecutiveWorkingDays())
                                * policy.consecutiveDaysWeight())
                .asConstraint("consecutive days");
    }

    /** The same limit when an académie imposes it as an absolute maximum. */
    Constraint maxConsecutiveDaysAsRule(ConstraintFactory f) {
        return f.forEach(DutyAssignment.class)
                .groupBy(DutyAssignment::getTeacher,
                        ConstraintCollectors.toSet(DutyAssignment::date))
                .join(SchedulingPolicy.class)
                .filter((t, days, policy) -> policy.consecutiveDaysIsHard()
                        && longestRun(days) > policy.maxConsecutiveWorkingDays())
                .penalize(HardSoftScore.ONE_HARD,
                        (t, days, policy) -> longestRun(days) - policy.maxConsecutiveWorkingDays())
                .asConstraint("consecutive days limit exceeded");
    }

    /**
     * S — rest between two duties of the same day. Nothing in the confirmed
     * procedure requires it, so it only applies when a centre asks for it,
     * typically so people can move rooms, be briefed and sign.
     */
    Constraint minimumGapBetweenDuties(ConstraintFactory f) {
        return f.forEachUniquePair(DutyAssignment.class,
                        Joiners.equal(DutyAssignment::getTeacher),
                        Joiners.equal(DutyAssignment::date))
                .join(SchedulingPolicy.class)
                .filter((a, b, policy) -> policy.enforcesGap()
                        && gapInMinutes(a, b) < policy.minimumGapBetweenDutiesMinutes())
                .penalize(HardSoftScore.ONE_SOFT, (a, b, policy) -> policy.minimumGapWeight())
                .asConstraint("rest between duties");
    }

    /** Minutes between the end of the earlier duty and the start of the later one. */
    private static long gapInMinutes(DutyAssignment a, DutyAssignment b) {
        var first = a.getDuty().slot().startTime().isBefore(b.getDuty().slot().startTime())
                ? a.getDuty().slot() : b.getDuty().slot();
        var second = first == a.getDuty().slot() ? b.getDuty().slot() : a.getDuty().slot();
        return java.time.Duration.between(first.endTime(), second.startTime()).toMinutes();
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

    /**
     * S2 — surveillance is the real work, so it is spread evenly, seeded with
     * what earlier operations already handed out.
     */
    Constraint balanceSurveillance(ConstraintFactory f) {
        return loadPerTeacher(f, DutyAssignment::isSurveillance)
                .groupBy(ConstraintCollectors.loadBalance(
                        (Teacher t, Long load) -> t,
                        (t, load) -> load,
                        (t, load) -> t.prior(DutyRole.SURVEILLANCE)))
                .penalize(HardSoftScore.ONE_SOFT,
                        lb -> unfairnessPoints(lb, WEIGHT_SURVEILLANCE_BALANCE))
                .asConstraint("balance surveillance load");
    }

    /**
     * S3 and S4 — two queues, not one.
     *
     * <p>They used to share a counter, and it produced the opposite of
     * fairness. Permanence is not chosen: an épreuve needs a specialist of its
     * subject, so the solver picks which of the four colleagues, never
     * whether. With the counters joined, a teacher conscripted into three
     * permanences read as three times served and was passed over for réserve
     * — charged for work he never volunteered for, then locked out of the
     * turn that is genuinely rest. Being scarce became a penalty, and it fell
     * on exactly the subjects with fewest specialists.
     *
     * <p>Worse, nothing balanced permanence itself. Five permanences over four
     * specialists should fall 2-1-1-1; the owner's year fell 3-2-0-0, because
     * the sum was level and the sum was all that was watched. The two who took
     * none collected the réserves and everybody's total looked right.
     *
     * <p>Separately, each queue does what the joined one was meant to do: a
     * teacher who has had a réserve waits for the next until his colleagues
     * have had theirs, and permanence levels inside the group that can
     * actually sit it. The seed is that role's own {@code privilegeCarry}.
     *
     * <p>Both stay preferences rather than rules. Scarcity can still force a
     * repeat — five permanences and four specialists means somebody takes two
     * — and 39 réserves among 41 teachers means two people end the year
     * without one.
     */
    Constraint reserveQueue(ConstraintFactory f) {
        return queueFor(f, DutyRole.RESERVE, "reserve queue");
    }

    Constraint permanenceQueue(ConstraintFactory f) {
        return queueFor(f, DutyRole.PERMANENCE, "permanence queue");
    }

    /**
     * The zero rows include teachers who could never hold this role — every
     * teacher of a subject that is not examined sits at zero permanence for
     * good. That is a constant offset, identical for every candidate
     * solution, so it cannot sway a comparison; taking it out would cost a
     * per-subject grouping to buy nothing.
     */
    private Constraint queueFor(ConstraintFactory f, DutyRole role, String name) {
        return loadPerTeacher(f, a -> a.getDuty().role() == role)
                .groupBy(ConstraintCollectors.loadBalance(
                        (Teacher t, Long load) -> t,
                        (t, load) -> load,
                        (t, load) -> t.privilegeCarry(role)))
                .penalize(HardSoftScore.ONE_SOFT,
                        lb -> unfairnessPoints(lb, WEIGHT_PRIVILEGE_QUEUE))
                .asConstraint(name);
    }

    /**
     * One row per matching duty carrying a load of 1, plus a zero row for
     * every teacher holding none of them.
     *
     * <p>The zero rows are the point. A stream filtered to one kind of duty
     * only ever contains the teachers already doing it, and a group of one
     * has no imbalance — so six permanences to a single specialist scored
     * exactly like two each to three of them. Teachers sitting at zero have
     * to be visible for the balance to mean anything.
     */
    private BiConstraintStream<Teacher, Long> loadPerTeacher(
            ConstraintFactory f, Predicate<DutyAssignment> matching) {
        return f.forEach(DutyAssignment.class)
                .filter(matching::test)
                .map(DutyAssignment::getTeacher, a -> 1L)
                .concat(f.forEach(Teacher.class)
                        .ifNotExists(DutyAssignment.class,
                                Joiners.equal(Function.identity(), DutyAssignment::getTeacher),
                                Joiners.filtering((t, a) -> matching.test(a)))
                        .expand(t -> 0L));
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
