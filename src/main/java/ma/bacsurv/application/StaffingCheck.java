package ma.bacsurv.application;

import ma.bacsurv.domain.Duty;
import ma.bacsurv.domain.ExamOperation;
import ma.bacsurv.domain.ExamSlot;
import ma.bacsurv.domain.Teacher;
import ma.bacsurv.rules.Eligibility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Can this pool staff this operation at all?
 *
 * The binding limit is per slot, not per operation: a teacher can hold only
 * one duty at a time, so a slot needing 47 people cannot be run by a pool of
 * 45 however long the solver thinks about it. Checking first turns a wall of
 * unexplained hard violations into one clear sentence.
 */
public final class StaffingCheck {

    /** One slot the pool cannot cover. */
    public record Shortage(String slotId, int required, int available) {

        public int missing() {
            return required - available;
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

    public List<Shortage> check(ExamOperation operation, List<Teacher> pool, List<Duty> duties) {
        Map<String, List<Duty>> dutiesBySlot = duties.stream()
                .collect(Collectors.groupingBy(duty -> duty.slot().id()));

        List<Shortage> shortages = new ArrayList<>();
        for (ExamSlot slot : operation.slots()) {
            List<Duty> slotDuties = dutiesBySlot.getOrDefault(slot.id(), List.of());
            if (slotDuties.isEmpty()) continue;

            // a teacher counts as available for the slot if some duty of it is
            // one they could legally take
            long available = pool.stream()
                    .filter(teacher -> slotDuties.stream()
                            .anyMatch(duty -> eligibility.isEligible(teacher, duty)))
                    .count();

            if (available < slotDuties.size()) {
                shortages.add(new Shortage(slot.id(), slotDuties.size(), (int) available));
            }
        }
        return List.copyOf(shortages);
    }
}
