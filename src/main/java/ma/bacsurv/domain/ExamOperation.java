package ma.bacsurv.domain;

import java.util.List;

/** One independent scheduling run (regional, national, or rattrapage). */
public record ExamOperation(String id, OperationType type, List<ExamSlot> slots) {
    public ExamOperation {
        slots = List.copyOf(slots);
    }
}
