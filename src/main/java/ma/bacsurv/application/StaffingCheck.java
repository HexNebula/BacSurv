package ma.bacsurv.application;

import ma.bacsurv.domain.Duty;
import ma.bacsurv.domain.DutyRole;
import ma.bacsurv.domain.ExamOperation;
import ma.bacsurv.domain.ExamSlot;
import ma.bacsurv.domain.Teacher;
import ma.bacsurv.rules.Eligibility;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Can this pool staff this operation at all?
 *
 * The binding limit is per moment, not per operation: a teacher can hold only
 * one duty at a time, so an hour needing 47 people cannot be run by a pool of
 * 45 however long the solver thinks about it. Checking first turns a wall of
 * unexplained hard violations into one clear sentence.
 *
 * <p>The moment, not the slot. Two papers of one afternoon can start together
 * and end at different hours, which makes them two slots running at once —
 * counting them separately says 12 and 12 against a pool of 45 and passes,
 * while the centre actually has to put 24 people in rooms at 15:00.
 */
public final class StaffingCheck {

    /**
     * A moment the pool cannot cover, and the slot or simultaneous slots
     * that make it up.
     */
    public record Shortage(List<String> slotIds, LocalDate date, LocalTime at,
                           int required, int available) {

        public Shortage {
            slotIds = List.copyOf(slotIds);
        }

        public int missing() {
            return required - available;
        }

        /** Several slots at once, rather than a single overbooked séance. */
        public boolean isConcurrent() {
            return slotIds.size() > 1;
        }

        /** Label for a message: one slot id, or the simultaneous ones joined. */
        public String slotId() {
            return String.join(" + ", slotIds);
        }
    }

    /**
     * A duty nobody in the pool may legally take, whatever the numbers say.
     *
     * <p>Typically a permanence whose subject has no specialist in the pool —
     * the specialists are absent that day, or the centre examines a subject it
     * does not staff. Counting heads never sees it: the hour has plenty of
     * people, just nobody allowed to sit in that chair. Left to the solver it
     * surfaces after a full search as a qualification violation naming a
     * teacher who did nothing wrong.
     */
    public record Unfillable(String dutyId, DutyRole role, String subject,
                             String slotId, LocalDate date, LocalTime at) {

        /** What the administrator has to fix: a specialist of this subject. */
        public boolean needsSpecialist() {
            return role == DutyRole.PERMANENCE;
        }
    }

    private final Eligibility eligibility;

    public StaffingCheck(Eligibility eligibility) {
        this.eligibility = eligibility;
    }

    public static StaffingCheck withDefaults() {
        return new StaffingCheck(Eligibility.withDefaults());
    }

    /** Counts availability under the same eligibility rules as the solve. */
    public static StaffingCheck forPolicy(ma.bacsurv.rules.SchedulingPolicy policy) {
        return new StaffingCheck(new Eligibility(policy.subjectConflict()));
    }

    /**
     * Duties no one in the pool could take. Reported per subject and slot
     * rather than per duty: a subject with no specialist produces one
     * unfillable permanence per slot it is examined in, and naming the
     * subject once per slot is what the administrator acts on.
     */
    public List<Unfillable> unfillable(List<Teacher> pool, List<Duty> duties) {
        List<Unfillable> impossible = new ArrayList<>();
        Set<String> alreadyReported = new HashSet<>();

        for (Duty duty : duties) {
            if (pool.stream().anyMatch(teacher -> eligibility.isEligible(teacher, duty))) continue;

            String subject = duty.exam().map(exam -> exam.subject().name()).orElse(null);
            if (!alreadyReported.add(duty.slot().id() + "/" + duty.role() + "/" + subject)) continue;

            impossible.add(new Unfillable(duty.id(), duty.role(), subject,
                    duty.slot().id(), duty.slot().date(), duty.slot().startTime()));
        }
        return List.copyOf(impossible);
    }

    public List<Shortage> check(ExamOperation operation, List<Teacher> pool, List<Duty> duties) {
        Map<String, List<Duty>> dutiesBySlot = duties.stream()
                .collect(Collectors.groupingBy(duty -> duty.slot().id()));

        List<ExamSlot> staffed = operation.slots().stream()
                .filter(slot -> !dutiesBySlot.getOrDefault(slot.id(), List.of()).isEmpty())
                .toList();

        List<Shortage> shortages = new ArrayList<>();
        Set<List<String>> alreadyReported = new HashSet<>();

        // Demand can only rise when something starts, so every slot's start
        // is a moment worth measuring, and no moment between two starts can
        // be worse than the start that precedes it.
        for (ExamSlot opening : staffed) {
            List<ExamSlot> running = staffed.stream()
                    .filter(slot -> slot.covers(opening.date(), opening.startTime()))
                    .toList();

            List<Duty> concurrent = running.stream()
                    .flatMap(slot -> dutiesBySlot.get(slot.id()).stream())
                    .toList();

            // a teacher counts once, however many of these duties they could
            // take: they can only hold one of them
            long available = pool.stream()
                    .filter(teacher -> concurrent.stream()
                            .anyMatch(duty -> eligibility.isEligible(teacher, duty)))
                    .count();

            if (available >= concurrent.size()) continue;

            List<String> ids = running.stream().map(ExamSlot::id).sorted().toList();
            if (alreadyReported.add(ids)) {
                shortages.add(new Shortage(ids, opening.date(), opening.startTime(),
                        concurrent.size(), (int) available));
            }
        }
        return List.copyOf(shortages);
    }
}
