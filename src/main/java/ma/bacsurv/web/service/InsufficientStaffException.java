package ma.bacsurv.web.service;

import ma.bacsurv.application.StaffingCheck;

import java.util.List;

/**
 * The pool cannot cover some slot, so no schedule exists. Raised before
 * solving: the admin needs to add teachers or reduce the staffing
 * requirement, and no amount of solving time changes that.
 */
public class InsufficientStaffException extends RuntimeException {

    private final transient List<StaffingCheck.Shortage> shortages;

    public InsufficientStaffException(List<StaffingCheck.Shortage> shortages) {
        super(summarise(shortages));
        this.shortages = List.copyOf(shortages);
    }

    public List<StaffingCheck.Shortage> shortages() {
        return shortages;
    }

    /** First offending slot, which is the one the admin should look at. */
    public StaffingCheck.Shortage worst() {
        return shortages.stream()
                .max(java.util.Comparator.comparingInt(StaffingCheck.Shortage::missing))
                .orElseThrow();
    }

    private static String summarise(List<StaffingCheck.Shortage> shortages) {
        return shortages.stream()
                .map(s -> s.slotId() + " needs " + s.required() + ", has " + s.available())
                .collect(java.util.stream.Collectors.joining("; ",
                        "insufficient staff: ", ""));
    }
}
