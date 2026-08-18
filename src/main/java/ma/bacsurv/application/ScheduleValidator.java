package ma.bacsurv.application;

import ma.bacsurv.domain.Duty;
import ma.bacsurv.domain.DutyRole;
import ma.bacsurv.domain.ExamSlot;
import ma.bacsurv.domain.Room;
import ma.bacsurv.domain.Subject;
import ma.bacsurv.domain.Teacher;
import ma.bacsurv.rules.Eligibility;
import ma.bacsurv.rules.SchedulingPolicy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Independent check of a schedule against MODEL.md — no solver involved.
 * Hard: H1 coverage, H2 no overlap, H3 availability, H4 qualification,
 *       H5 subject conflict (when configured hard), H6 optional cap.
 * Soft (reported, never fail feasibility): repeated room, repeated pair,
 *       max consecutive days.
 */
public final class ScheduleValidator {

    private final Eligibility eligibility;
    private final Integer maxDutiesPerTeacher; // null = no configured cap
    private final int maxConsecutiveDays;

    public ScheduleValidator(Eligibility eligibility, Integer maxDutiesPerTeacher,
                             int maxConsecutiveDays) {
        this.eligibility = eligibility;
        this.maxDutiesPerTeacher = maxDutiesPerTeacher;
        this.maxConsecutiveDays = maxConsecutiveDays;
    }

    public static ScheduleValidator withDefaults() {
        return forPolicy(SchedulingPolicy.defaults());
    }

    /** Judges a schedule by the same rules the solver was given. */
    public static ScheduleValidator forPolicy(SchedulingPolicy policy) {
        return new ScheduleValidator(new Eligibility(policy.subjectConflict()), null,
                policy.maxConsecutiveWorkingDays());
    }

    public ValidationReport validate(List<Duty> duties) {
        List<Violation> violations = new ArrayList<>();

        checkCoverage(duties, violations);
        checkOverlap(duties, violations);
        checkAvailabilityQualificationConflict(duties, violations);
        checkCap(duties, violations);

        checkRepeatedRoom(duties, violations);
        checkRepeatedPair(duties, violations);
        checkConsecutiveDays(duties, violations);

        return new ValidationReport(violations, workload(duties));
    }

    // H1
    private void checkCoverage(List<Duty> duties, List<Violation> out) {
        duties.stream()
                .filter(d -> d.assignedTeacher().isEmpty())
                .forEach(d -> out.add(Violation.hard("H1-coverage", "unfilled duty " + d)));
    }

    // H2
    private void checkOverlap(List<Duty> duties, List<Violation> out) {
        Map<ExamSlot, Map<Teacher, Long>> perSlot = duties.stream()
                .filter(d -> d.assignedTeacher().isPresent())
                .collect(Collectors.groupingBy(Duty::slot,
                        Collectors.groupingBy(d -> d.assignedTeacher().get(),
                                Collectors.counting())));
        perSlot.forEach((slot, byTeacher) -> byTeacher.forEach((t, n) -> {
            if (n > 1) out.add(Violation.hard("H2-overlap",
                    t.name() + " has " + n + " duties in slot " + slot.id()));
        }));
    }

    // H3 + H4 + H5
    private void checkAvailabilityQualificationConflict(List<Duty> duties, List<Violation> out) {
        for (Duty d : duties) {
            Optional<Teacher> assigned = d.assignedTeacher();
            if (assigned.isEmpty()) continue;
            Teacher t = assigned.get();

            if (!t.isAvailable(d.slot()))
                out.add(Violation.hard("H3-availability",
                        t.name() + " unavailable for slot " + d.slot().id()));

            Subject examSubject = d.exam().map(e -> e.subject()).orElse(null);
            if (!t.isQualified(d.role(), examSubject))
                out.add(Violation.hard("H4-qualification",
                        t.name() + " not qualified for " + d));

            if (eligibility.isHardSubjectConflict(t, d))
                out.add(Violation.hard("H5-subject-conflict",
                        t.name() + " (" + t.subject().name() + ") on " + d));
        }
    }

    // H6 (only if a real administrative cap is configured)
    private void checkCap(List<Duty> duties, List<Violation> out) {
        if (maxDutiesPerTeacher == null) return;
        countByTeacher(duties).forEach((t, n) -> {
            if (n > maxDutiesPerTeacher) out.add(Violation.hard("H6-cap",
                    t.name() + " has " + n + " duties, cap is " + maxDutiesPerTeacher));
        });
    }

    // AvoidRepeatedRoomAssignment (soft)
    private void checkRepeatedRoom(List<Duty> duties, List<Violation> out) {
        Map<Teacher, Map<Room, Long>> byTeacherRoom = duties.stream()
                .filter(d -> d.role() == DutyRole.SURVEILLANCE && d.assignedTeacher().isPresent())
                .collect(Collectors.groupingBy(d -> d.assignedTeacher().get(),
                        Collectors.groupingBy(d -> d.room().get(), Collectors.counting())));
        byTeacherRoom.forEach((t, rooms) -> rooms.forEach((room, n) -> {
            if (n > 1) out.add(Violation.soft("S-repeated-room",
                    t.name() + " in room " + room.label() + " " + n + " times"));
        }));
    }

    // AvoidRepeatedPair (soft) — unordered, pairwise within a room of one exam
    private void checkRepeatedPair(List<Duty> duties, List<Violation> out) {
        record RoomKey(ExamSlot slot, String examId, Room room) {}
        Map<RoomKey, List<Teacher>> teams = duties.stream()
                .filter(d -> d.role() == DutyRole.SURVEILLANCE && d.assignedTeacher().isPresent())
                .collect(Collectors.groupingBy(
                        d -> new RoomKey(d.slot(), d.exam().get().id(), d.room().get()),
                        Collectors.mapping(d -> d.assignedTeacher().get(), Collectors.toList())));

        Map<Set<Teacher>, Integer> pairCounts = new HashMap<>();
        for (List<Teacher> team : teams.values()) {
            for (int i = 0; i < team.size(); i++)
                for (int j = i + 1; j < team.size(); j++) {
                    // same teacher twice in one room is an H2 overlap, not a pair
                    if (team.get(i).equals(team.get(j))) continue;
                    pairCounts.merge(Set.of(team.get(i), team.get(j)), 1, Integer::sum);
                }
        }
        pairCounts.forEach((pair, n) -> {
            if (n > 1) out.add(Violation.soft("S-repeated-pair",
                    pair.stream().map(Teacher::name).collect(Collectors.joining(" + "))
                            + " paired " + n + " times"));
        });
    }

    // Max consecutive days (soft)
    private void checkConsecutiveDays(List<Duty> duties, List<Violation> out) {
        Map<Teacher, TreeSet<LocalDate>> daysByTeacher = duties.stream()
                .filter(d -> d.assignedTeacher().isPresent())
                .collect(Collectors.groupingBy(d -> d.assignedTeacher().get(),
                        Collectors.mapping(d -> d.slot().date(),
                                Collectors.toCollection(TreeSet::new))));
        daysByTeacher.forEach((t, days) -> {
            int run = 0;
            LocalDate prev = null;
            int longest = 0;
            for (LocalDate day : days) {
                run = (prev != null && prev.plusDays(1).equals(day)) ? run + 1 : 1;
                longest = Math.max(longest, run);
                prev = day;
            }
            if (longest > maxConsecutiveDays) out.add(Violation.soft("S-consecutive-days",
                    t.name() + " works " + longest + " consecutive days (max "
                            + maxConsecutiveDays + ")"));
        });
    }

    private Map<Teacher, Long> countByTeacher(List<Duty> duties) {
        return duties.stream()
                .filter(d -> d.assignedTeacher().isPresent())
                .collect(Collectors.groupingBy(d -> d.assignedTeacher().get(),
                        Collectors.counting()));
    }

    /** Per-role workload — all roles count equal (same pay, one unit each). */
    private Map<Teacher, Map<DutyRole, Long>> workload(List<Duty> duties) {
        Map<Teacher, Map<DutyRole, Long>> result = new HashMap<>();
        for (Duty d : duties) {
            d.assignedTeacher().ifPresent(t ->
                    result.computeIfAbsent(t, x -> new HashMap<>())
                          .merge(d.role(), 1L, Long::sum));
        }
        return result;
    }
}
