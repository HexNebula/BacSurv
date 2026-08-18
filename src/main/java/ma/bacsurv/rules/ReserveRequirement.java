package ma.bacsurv.rules;

import ma.bacsurv.domain.ExamSlot;

/**
 * How many reservists a session needs.
 *
 * The official wording is an upper-bound framework around a tenth of the
 * surveillance staff, not a formula every session must reproduce, so a centre
 * states either a rate or a plain count. Rates always round up: half a
 * reservist is a whole person.
 */
public record ReserveRequirement(Mode mode, double percentage, int fixedCount) {

    public enum Mode { PERCENTAGE, FIXED_COUNT }

    public ReserveRequirement {
        if (mode == Mode.PERCENTAGE && (percentage < 0 || percentage > 1)) {
            throw new IllegalArgumentException("percentage must be between 0 and 1, was " + percentage);
        }
        if (mode == Mode.FIXED_COUNT && fixedCount < 0) {
            throw new IllegalArgumentException("fixedCount must be >= 0, was " + fixedCount);
        }
    }

    public static ReserveRequirement percentage(double rate) {
        return new ReserveRequirement(Mode.PERCENTAGE, rate, 0);
    }

    public static ReserveRequirement fixed(int count) {
        return new ReserveRequirement(Mode.FIXED_COUNT, 0, count);
    }

    /** The official default: a tenth of the slot's surveillance duties, rounded up. */
    public static ReserveRequirement officialDefault() {
        return percentage(0.10);
    }

    public int requiredFor(ExamSlot slot) {
        return switch (mode) {
            case PERCENTAGE -> (int) Math.ceil(slot.surveillanceDutyCount() * percentage);
            case FIXED_COUNT -> fixedCount;
        };
    }
}
