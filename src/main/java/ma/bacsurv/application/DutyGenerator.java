package ma.bacsurv.application;

import ma.bacsurv.domain.Duty;
import ma.bacsurv.domain.Exam;
import ma.bacsurv.domain.ExamOperation;
import ma.bacsurv.domain.ExamSlot;
import ma.bacsurv.domain.Room;
import ma.bacsurv.domain.Subject;
import ma.bacsurv.rules.StaffingPolicy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
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
            for (int i = 1; i <= slot.reserveRequirement(); i++) {
                duties.add(Duty.reserve(slot.id() + "-R" + i, slot));
            }
        }
        duties.addAll(permanenceDuties(operation));
        return duties;
    }

    /**
     * Permanence answers questions about a subject, so it is staffed once per
     * subject being examined — not once per exam, and not once per slot.
     *
     * <p>Per <em>séance</em>: everything starting at the same hour of the same
     * day is one moment in the centre, whatever time each paper ends. Two
     * filières sitting philosophy from 15:00, one until 17:00 and one until
     * 18:00, are two slots — they have to be, réserve and surveillance are
     * counted against the hours a room is actually occupied — but they are one
     * question the specialist answers, and he answers it once.
     *
     * <p>Grouping by slot instead was a real shortage on paper that did not
     * exist in the building: a centre with three philosophers was asked for two
     * of them at the same hour, and when a concurrent session held the other
     * two the distribution had nowhere to go and seated somebody unqualified.
     *
     * <p>The duty is anchored to the longest of the slots it covers, because
     * that is how late the specialist has to stay. Anchoring it to the first
     * would let the scheduler believe he is free while a paper of his own
     * subject is still being written.
     */
    private List<Duty> permanenceDuties(ExamOperation operation) {
        record Seance(LocalDate date, LocalTime startTime, Subject subject) {}

        Map<Seance, List<ExamSlot>> slotsBySeance = new LinkedHashMap<>();
        for (ExamSlot slot : operation.slots()) {
            for (Exam exam : slot.exams()) {
                slotsBySeance.computeIfAbsent(
                        new Seance(slot.date(), slot.startTime(), exam.subject()),
                        seance -> new ArrayList<>()).add(slot);
            }
        }

        List<Duty> duties = new ArrayList<>();
        slotsBySeance.forEach((seance, slots) -> {
            ExamSlot longest = slots.stream()
                    .max(Comparator.comparing(ExamSlot::endTime)).orElseThrow();
            List<Exam> examined = slots.stream().distinct()
                    .flatMap(slot -> slot.exams().stream())
                    .filter(exam -> exam.subject().equals(seance.subject()))
                    .toList();
            Exam representative = examined.stream()
                    .filter(exam -> longest.exams().contains(exam))
                    .findFirst().orElse(examined.getFirst());

            int count = examined.stream().mapToInt(Exam::permanenceCount).max().orElse(0);
            for (int i = 1; i <= count; i++) {
                duties.add(Duty.permanence(
                        longest.id() + "-" + representative.id() + "-P" + i,
                        longest, representative));
            }
        });
        return duties;
    }
}
