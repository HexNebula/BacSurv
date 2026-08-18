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

    /** Derived — used only by the AM/PM balance objective. */
    public HalfDay halfDay() {
        return startTime.isBefore(LocalTime.NOON) ? HalfDay.AM : HalfDay.PM;
    }

    public int surveillanceDutyCount() {
        return exams.stream().mapToInt(Exam::surveillanceDutyCount).sum();
    }
}
