package ma.bacsurv.application;

import ma.bacsurv.domain.Duty;
import ma.bacsurv.domain.Exam;
import ma.bacsurv.domain.ExamOperation;
import ma.bacsurv.domain.ExamSlot;
import ma.bacsurv.domain.Room;

import java.util.ArrayList;
import java.util.List;

/**
 * operation → slots → exams → duties. Deterministic ids so regeneration
 * is stable: {slotId}-{examId}-{room}-S{n} / -P{n} / {slotId}-R{n}.
 */
public final class DutyGenerator {

    public List<Duty> generate(ExamOperation operation) {
        List<Duty> duties = new ArrayList<>();
        for (ExamSlot slot : operation.slots()) {
            for (Exam exam : slot.exams()) {
                for (Room room : exam.rooms()) {
                    for (int i = 1; i <= exam.surveillantsPerRoom(); i++) {
                        duties.add(Duty.surveillance(
                                slot.id() + "-" + exam.id() + "-" + room.id() + "-S" + i,
                                slot, exam, room));
                    }
                }
                for (int i = 1; i <= exam.permanenceCount(); i++) {
                    duties.add(Duty.permanence(
                            slot.id() + "-" + exam.id() + "-P" + i, slot, exam));
                }
            }
            for (int i = 1; i <= slot.reserveRequirement(); i++) {
                duties.add(Duty.reserve(slot.id() + "-R" + i, slot));
            }
        }
        return duties;
    }
}
