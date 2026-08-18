package ma.bacsurv.application;

import ma.bacsurv.domain.ExamSlot;

/**
 * Suggests a reserveRequirement for a slot. The configured value on the
 * slot is the truth — this only proposes. Official wording is a ceiling
 * ("up to 10% of surveillance personnel"), not a mandate.
 */
public sealed interface ReservePolicy {

    int suggest(ExamSlot slot);

    record FixedCount(int count) implements ReservePolicy {
        public int suggest(ExamSlot slot) { return count; }
    }

    /** e.g. 10% of all surveillance duties in the slot, rounded up. */
    record PercentageOfSurveillance(double percentage) implements ReservePolicy {
        public int suggest(ExamSlot slot) {
            return (int) Math.ceil(slot.surveillanceDutyCount() * percentage);
        }
    }

    static ReservePolicy officialDefault() {
        return new PercentageOfSurveillance(0.10);
    }
}
