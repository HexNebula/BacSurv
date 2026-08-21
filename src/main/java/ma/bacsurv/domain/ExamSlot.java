package ma.bacsurv.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Time container only — subjects live on the Exams inside it.
 * reserveRequirement is the configured truth; ReservePolicy merely suggests it.
 */
public record ExamSlot(String id, LocalDate date, LocalTime startTime, LocalTime endTime,
                       int ordinalInDay, List<Exam> exams, int reserveRequirement) {

    public ExamSlot {
        exams = List.copyOf(exams);
        if (!startTime.isBefore(endTime))
            throw new IllegalArgumentException("startTime must be before endTime");
        if (reserveRequirement < 0)
            throw new IllegalArgumentException("reserveRequirement must be >= 0");
    }

    /**
     * True when the two slots occupy a common moment, so no teacher can hold
     * a duty in both. Slots that merely touch — one ending as the other
     * starts — do not overlap.
     */
    public boolean overlaps(ExamSlot other) {
        return date.equals(other.date)
                && startTime.isBefore(other.endTime)
                && other.startTime.isBefore(endTime);
    }

    /** True when this slot is running at the given moment. */
    public boolean covers(LocalDate day, LocalTime time) {
        return date.equals(day) && !startTime.isAfter(time) && time.isBefore(endTime);
    }

    /** Derived — used only by the AM/PM balance objective. */
    public HalfDay halfDay() {
        return startTime.isBefore(LocalTime.NOON) ? HalfDay.AM : HalfDay.PM;
    }

    public int surveillanceDutyCount() {
        return exams.stream().mapToInt(Exam::surveillanceDutyCount).sum();
    }
}
