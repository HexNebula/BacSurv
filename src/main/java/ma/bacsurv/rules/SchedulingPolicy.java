package ma.bacsurv.rules;

/**
 * Local scheduling choices — preferences a centre sets for itself, not rules
 * the Ministry imposes.
 *
 * Both limits here are soft by default and deliberately so. The scale test
 * showed that with eleven duties over twelve exam days, working more than
 * three days in a row is arithmetically unavoidable: as a hard rule it would
 * make a perfectly ordinary centre unschedulable. An académie that really does
 * impose a maximum can promote it to hard.
 */
public record SchedulingPolicy(int maxConsecutiveWorkingDays,
                               ConstraintStrength consecutiveDaysStrength,
                               int consecutiveDaysWeight,
                               int minimumGapBetweenDutiesMinutes,
                               int minimumGapWeight,
                               SubjectConflictConfig subjectConflict) {

    public SchedulingPolicy {
        if (maxConsecutiveWorkingDays < 1) {
            throw new IllegalArgumentException(
                    "maxConsecutiveWorkingDays must be at least 1, was " + maxConsecutiveWorkingDays);
        }
        if (minimumGapBetweenDutiesMinutes < 0) {
            throw new IllegalArgumentException("minimumGapBetweenDutiesMinutes must be >= 0, was "
                    + minimumGapBetweenDutiesMinutes);
        }
    }

    /**
     * No enforced rest between two duties of the same day: nothing in the
     * confirmed procedure requires one, so a centre opts in when it wants the
     * time for people to move, sign and be briefed.
     */
    public static SchedulingPolicy defaults() {
        return new SchedulingPolicy(3, ConstraintStrength.SOFT, 20,
                0, 30, SubjectConflictConfig.defaults());
    }

    public boolean enforcesGap() {
        return minimumGapBetweenDutiesMinutes > 0;
    }

    public boolean consecutiveDaysIsHard() {
        return consecutiveDaysStrength == ConstraintStrength.HARD;
    }

    public SchedulingPolicy withMaxConsecutiveWorkingDays(int days, ConstraintStrength strength) {
        return new SchedulingPolicy(days, strength, consecutiveDaysWeight,
                minimumGapBetweenDutiesMinutes, minimumGapWeight, subjectConflict);
    }

    public SchedulingPolicy withMinimumGap(int minutes) {
        return new SchedulingPolicy(maxConsecutiveWorkingDays, consecutiveDaysStrength,
                consecutiveDaysWeight, minutes, minimumGapWeight, subjectConflict);
    }

    public SchedulingPolicy withSubjectConflict(SubjectConflictConfig config) {
        return new SchedulingPolicy(maxConsecutiveWorkingDays, consecutiveDaysStrength,
                consecutiveDaysWeight, minimumGapBetweenDutiesMinutes, minimumGapWeight, config);
    }
}
