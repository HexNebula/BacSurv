package ma.bacsurv.web.service;

import ma.bacsurv.application.StaffingCheck;

import java.util.List;

/**
 * No schedule exists for this pool, so solving would only waste the wait.
 * Raised before the search starts: the administrator has to add teachers or
 * lower the staffing requirement, and no amount of solving time changes that.
 *
 * <p>Two reasons, and they call for different actions. Either an hour needs
 * more people than the centre has, or a particular duty has nobody qualified
 * to take it — usually a permanence whose subject has no specialist present.
 */
public class InsufficientStaffException extends RuntimeException {

    private final transient List<StaffingCheck.Shortage> shortages;
    private final transient List<StaffingCheck.Unfillable> unfillable;

    public InsufficientStaffException(List<StaffingCheck.Shortage> shortages,
                                      List<StaffingCheck.Unfillable> unfillable) {
        super(summarise(shortages, unfillable));
        this.shortages = List.copyOf(shortages);
        this.unfillable = List.copyOf(unfillable);
    }

    public List<StaffingCheck.Shortage> shortages() {
        return shortages;
    }

    public List<StaffingCheck.Unfillable> unfillable() {
        return unfillable;
    }

    /**
     * A missing specialist is reported ahead of a headcount: it is the more
     * specific fault, and often the only real one — a centre short of a
     * specialist is rarely short of people.
     */
    public boolean isMissingSpecialist() {
        return !unfillable.isEmpty();
    }

    public StaffingCheck.Unfillable firstUnfillable() {
        return unfillable.getFirst();
    }

    /** The worst offending hour, which is the one the admin should look at. */
    public StaffingCheck.Shortage worst() {
        return shortages.stream()
                .max(java.util.Comparator.comparingInt(StaffingCheck.Shortage::missing))
                .orElseThrow();
    }

    private static String summarise(List<StaffingCheck.Shortage> shortages,
                                    List<StaffingCheck.Unfillable> unfillable) {
        java.util.stream.Stream<String> reasons = java.util.stream.Stream.concat(
                unfillable.stream().map(u -> "no eligible teacher for " + u.role()
                        + (u.subject() == null ? "" : " " + u.subject())
                        + " in slot " + u.slotId()),
                shortages.stream().map(s -> s.slotId() + " needs " + s.required()
                        + ", has " + s.available()));
        return reasons.collect(java.util.stream.Collectors.joining("; ",
                "no schedule exists: ", ""));
    }
}
