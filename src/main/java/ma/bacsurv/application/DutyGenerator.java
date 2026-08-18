package ma.bacsurv.application;

import ma.bacsurv.domain.Duty;
import ma.bacsurv.domain.Exam;
import ma.bacsurv.domain.ExamOperation;
import ma.bacsurv.domain.ExamSlot;
import ma.bacsurv.domain.Room;
import ma.bacsurv.domain.Subject;
import ma.bacsurv.rules.StaffingPolicy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * operation → slots → exams → duties. Deterministic ids so regeneration
 * is stable: {slotId}-{examId}-{room}-S{n} / -P{n} / {slotId}-R{n}.
 */
public final class DutyGenerator {

    public List<Duty> generate(ExamOperation operation) {
        return generate(operation, StaffingPolicy.defaults());
    }

    /**
     * Staffing comes from the centre's policy, which lets one large room take
     * more surveillants than the rest without changing the exam itself.
     */
    public List<Duty> generate(ExamOperation operation, StaffingPolicy staffing) {
        List<Duty> duties = new ArrayList<>();
        for (ExamSlot slot : operation.slots()) {
            for (Exam exam : slot.exams()) {
                for (Room room : exam.rooms()) {
                    for (int i = 1; i <= staffing.surveillantsFor(exam, room.id()); i++) {
                        duties.add(Duty.surveillance(
                                slot.id() + "-" + exam.id() + "-" + room.id() + "-S" + i,
                                slot, exam, room));
                    }
                }
            }
            duties.addAll(permanenceDuties(slot));
            for (int i = 1; i <= slot.reserveRequirement(); i++) {
                duties.add(Duty.reserve(slot.id() + "-R" + i, slot));
            }
        }
        return duties;
    }

    /**
     * Permanence answers questions about a subject, so it is staffed once per
     * subject being examined — not once per exam. Streams sitting the same
     * subject at the same hour share one specialist; a stream sitting a
     * different subject needs its own.
     */
    private List<Duty> permanenceDuties(ExamSlot slot) {
        Map<Subject, List<Exam>> examsBySubject = slot.exams().stream()
                .collect(Collectors.groupingBy(Exam::subject, LinkedHashMap::new,
                        Collectors.toList()));

        List<Duty> duties = new ArrayList<>();
        examsBySubject.forEach((subject, exams) -> {
            Exam representative = exams.getFirst();
            int count = exams.stream().mapToInt(Exam::permanenceCount).max().orElse(0);
            for (int i = 1; i <= count; i++) {
                duties.add(Duty.permanence(
                        slot.id() + "-" + representative.id() + "-P" + i, slot, representative));
            }
        });
        return duties;
    }
}
