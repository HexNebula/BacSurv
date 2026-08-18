package ma.bacsurv.demo;

import ma.bacsurv.application.DutyGenerator;
import ma.bacsurv.application.ScheduleValidator;
import ma.bacsurv.application.ValidationReport;
import ma.bacsurv.domain.Duty;
import ma.bacsurv.domain.DutyRole;
import ma.bacsurv.domain.Teacher;
import ma.bacsurv.solver.TimefoldScheduler;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How does the solver behave at the size of a real center?
 *
 *   ScaleDemoMain [seconds] [teachers] [days] [rooms]
 *
 * Prints the problem size, how long solving took, what the independent
 * validator says, and how evenly the duties landed.
 */
public final class ScaleDemoMain {

    public static void main(String[] args) {
        int seconds = args.length > 0 ? Integer.parseInt(args[0]) : 30;
        var defaults = ScaleData.Size.realisticCenter();
        var size = new ScaleData.Size(
                args.length > 2 ? Integer.parseInt(args[2]) : defaults.days(),
                defaults.slotsPerDay(),
                args.length > 3 ? Integer.parseInt(args[3]) : defaults.rooms(),
                args.length > 1 ? Integer.parseInt(args[1]) : defaults.teachers(),
                defaults.seed());

        ScaleData data = new ScaleData(size);
        var operation = data.operation();
        List<Teacher> pool = data.pool();
        List<Duty> duties = new DutyGenerator().generate(operation);

        System.out.printf("""
                === PROBLEM SIZE ===
                  days                %d (%d sessions a day)
                  slots               %d
                  rooms               %d
                  exams               %d
                  duties              %d
                  teachers            %d
                  duties per teacher  %.1f
                  time limit          %ds
                %n""",
                size.days(), size.slotsPerDay(), operation.slots().size(), size.rooms(),
                operation.slots().stream().mapToInt(s -> s.exams().size()).sum(),
                duties.size(), pool.size(), duties.size() / (double) pool.size(), seconds);

        var shortages = ma.bacsurv.application.StaffingCheck.withDefaults()
                .check(operation, pool, duties);
        if (!shortages.isEmpty()) {
            System.out.println("=== CANNOT BE STAFFED ===");
            shortages.forEach(s -> System.out.printf(
                    "  slot %-4s needs %d, only %d teachers available (%d missing)%n",
                    s.slotId(), s.required(), s.available(), s.missing()));
            System.out.println("  no schedule exists — solving would be pointless.");
            return;
        }

        long startedAt = System.currentTimeMillis();
        var solution = new TimefoldScheduler(Duration.ofSeconds(seconds)).solve(duties, pool);
        long elapsed = System.currentTimeMillis() - startedAt;

        ValidationReport report = ScheduleValidator.withDefaults().validate(duties);
        long unfilled = duties.stream().filter(d -> d.assignedTeacher().isEmpty()).count();

        System.out.printf("""
                === RESULT ===
                  solved in           %.1fs
                  score               %s
                  feasible            %s
                  hard violations     %d
                  soft violations     %d
                  unfilled duties     %d
                %n""",
                elapsed / 1000.0, solution.getScore(), report.isFeasible() && unfilled == 0,
                report.hardViolations().size(), report.softViolations().size(), unfilled);

        report.hardViolations().stream().limit(5)
                .forEach(v -> System.out.println("  HARD " + v.rule() + ": " + v.detail()));
        // which quality rules are being traded away, not just how many
        new java.util.TreeMap<>(report.softViolations().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ma.bacsurv.application.Violation::rule,
                        java.util.stream.Collectors.counting())))
                .forEach((rule, count) -> System.out.printf("  soft %-22s %d%n", rule, count));

        printSpread(duties, pool);
    }

    private static void printSpread(List<Duty> duties, List<Teacher> pool) {
        Map<Teacher, Integer> totals = new HashMap<>();
        Map<Teacher, Map<DutyRole, Integer>> byRole = new HashMap<>();
        pool.forEach(t -> totals.put(t, 0));
        for (Duty duty : duties) {
            duty.assignedTeacher().ifPresent(t -> {
                totals.merge(t, 1, Integer::sum);
                byRole.computeIfAbsent(t, x -> new HashMap<>()).merge(duty.role(), 1, Integer::sum);
            });
        }
        var stats = totals.values().stream().mapToInt(Integer::intValue).summaryStatistics();
        long idle = totals.values().stream().filter(v -> v == 0).count();

        Map<Integer, Long> histogram = new java.util.TreeMap<>();
        totals.values().forEach(v -> histogram.merge(v, 1L, Long::sum));

        System.out.println("=== WORKLOAD SPREAD ===");
        System.out.printf("  min %d, max %d, average %.2f, idle teachers %d%n",
                stats.getMin(), stats.getMax(), stats.getAverage(), idle);
        histogram.forEach((dutiesPerTeacher, teachers) ->
                System.out.printf("  %2d duties : %s (%d)%n", dutiesPerTeacher,
                        "#".repeat((int) Math.min(teachers, 60)), teachers));

        for (DutyRole role : DutyRole.values()) {
            var roleStats = pool.stream()
                    .mapToInt(t -> byRole.getOrDefault(t, Map.of()).getOrDefault(role, 0))
                    .summaryStatistics();
            System.out.printf("  %-12s min %d, max %d, total %d%n",
                    role, roleStats.getMin(), roleStats.getMax(), roleStats.getSum());
        }
    }
}
