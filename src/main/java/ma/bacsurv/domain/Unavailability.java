package ma.bacsurv.domain;

import java.time.LocalDate;
import java.time.LocalTime;

/** A period during which a teacher cannot work. Times null = whole day. */
public record Unavailability(LocalDate date, LocalTime start, LocalTime end) {

    public static Unavailability wholeDay(LocalDate date) {
        return new Unavailability(date, null, null);
    }

    public boolean covers(ExamSlot slot) {
        if (!date.equals(slot.date())) return false;
        if (start == null || end == null) return true;
        return start.isBefore(slot.endTime()) && slot.startTime().isBefore(end);
    }
}
