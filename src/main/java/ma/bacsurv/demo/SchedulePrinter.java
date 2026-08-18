package ma.bacsurv.demo;

import ma.bacsurv.application.ValidationReport;
import ma.bacsurv.domain.Duty;
import ma.bacsurv.domain.DutyRole;
import ma.bacsurv.domain.ExamSlot;
import ma.bacsurv.domain.Room;
import ma.bacsurv.domain.Teacher;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class SchedulePrinter {

    static void printSchedule(List<Duty> duties) {
        duties.stream()
                .collect(Collectors.groupingBy(Duty::slot))
                .entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().id()))
                .forEach(e -> {
                    ExamSlot slot = e.getKey();
                    System.out.println();
                    System.out.println("=== " + slot.id() + "  " + slot.date()
                            + "  " + slot.startTime() + "–" + slot.endTime()
                            + "  (" + slot.halfDay() + ") ===");
                    e.getValue().stream()
                            .sorted(Comparator
                                    .comparing((Duty d) -> d.role().ordinal())
                                    .thenComparing(Duty::id))
                            .forEach(d -> System.out.printf("  %-13s %-35s %-10s %s%n",
                                    d.role(),
                                    d.exam().map(x -> x.subject().name()
                                            + " (" + x.stream().name() + ")").orElse("—"),
                                    d.room().map(Room::label).orElse("—"),
                                    d.assignedTeacher().map(Teacher::toString)
                                            .orElse("*** UNFILLED ***")));
                });
    }

    static void printWorkload(List<Duty> duties, List<Teacher> pool) {
        System.out.println();
        System.out.println("=== WORKLOAD (H = surveillance, R = reserve, P = permanence) ===");
        Map<Teacher, Map<DutyRole, Long>> load = duties.stream()
                .filter(d -> d.assignedTeacher().isPresent())
                .collect(Collectors.groupingBy(d -> d.assignedTeacher().get(),
                        Collectors.groupingBy(Duty::role, Collectors.counting())));
        pool.stream()
                .sorted(Comparator.comparingInt(t -> Integer.parseInt(t.id().substring(1))))
                .forEach(t -> {
                    Map<DutyRole, Long> m = load.getOrDefault(t, Map.of());
                    long h = m.getOrDefault(DutyRole.SURVEILLANCE, 0L);
                    long r = m.getOrDefault(DutyRole.RESERVE, 0L);
                    long p = m.getOrDefault(DutyRole.PERMANENCE, 0L);
                    System.out.printf("  %-4s %-12s %-22s H=%d R=%d P=%d total=%d%n",
                            t.id(), t.name(), t.subject().name(), h, r, p, h + r + p);
                });
    }

    static void printValidation(ValidationReport report, int unfilled) {
        System.out.println();
        System.out.println("=== VALIDATION ===");
        System.out.println("Feasible: " + report.isFeasible()
                + "  (hard: " + report.hardViolations().size()
                + ", soft: " + report.softViolations().size()
                + ", unfilled: " + unfilled + ")");
        report.hardViolations().forEach(v ->
                System.out.println("  HARD " + v.rule() + ": " + v.detail()));
        report.softViolations().forEach(v ->
                System.out.println("  soft " + v.rule() + ": " + v.detail()));
    }
}
