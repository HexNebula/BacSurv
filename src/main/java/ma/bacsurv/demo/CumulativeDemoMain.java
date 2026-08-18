package ma.bacsurv.demo;

import ma.bacsurv.application.DutyGenerator;
import ma.bacsurv.domain.Duty;
import ma.bacsurv.domain.DutyRole;
import ma.bacsurv.domain.Teacher;
import ma.bacsurv.solver.TimefoldScheduler;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Cumulative fairness across two operations: solve June, carry each
 * teacher's workload as prior into July, solve July, show totals.
 * Expectation: July compensates June — heavily loaded teachers get less.
 */
public final class CumulativeDemoMain {

    public static void main(String[] args) {
        List<Teacher> pool = DemoData.pool();

        // --- operation 1: June ---
        List<Duty> june = new DutyGenerator().generate(DemoData.operation());
        new TimefoldScheduler(Duration.ofSeconds(10)).solve(june, pool);
        Map<Teacher, Map<DutyRole, Integer>> juneLoad = load(june);

        // --- carry history into the pool ---
        List<Teacher> poolWithHistory = pool.stream()
                .map(t -> t.withPriorWorkload(juneLoad.getOrDefault(t, Map.of())))
                .toList();

        // --- operation 2: July rattrapage (same shape for the demo) ---
        List<Duty> july = new DutyGenerator().generate(DemoData.operation());
        new TimefoldScheduler(Duration.ofSeconds(10)).solve(july, poolWithHistory);
        Map<Teacher, Map<DutyRole, Integer>> julyLoad = load(july);

        System.out.println();
        System.out.println("=== CUMULATIVE WORKLOAD (June + July) ===");
        System.out.printf("  %-4s %-12s %-22s %5s %5s %6s%n",
                "id", "name", "subject", "June", "July", "Total");
        pool.stream()
                .sorted(Comparator.comparingInt(t -> Integer.parseInt(t.id().substring(1))))
                .forEach(t -> {
                    int a = total(juneLoad.get(t));
                    Teacher tJuly = poolWithHistory.stream()
                            .filter(x -> x.equals(t)).findFirst().orElseThrow();
                    int b = total(julyLoad.get(tJuly));
                    System.out.printf("  %-4s %-12s %-22s %5d %5d %6d%n",
                            t.id(), t.name(), t.subject().name(), a, b, a + b);
                });

        var totals = pool.stream().map(t -> {
            Teacher tJuly = poolWithHistory.stream()
                    .filter(x -> x.equals(t)).findFirst().orElseThrow();
            return total(juneLoad.get(t)) + total(julyLoad.get(tJuly));
        }).sorted().toList();
        System.out.println();
        System.out.println("  Total spread: min=" + totals.getFirst()
                + " max=" + totals.getLast());
    }

    private static Map<Teacher, Map<DutyRole, Integer>> load(List<Duty> duties) {
        Map<Teacher, Map<DutyRole, Integer>> result = new HashMap<>();
        for (Duty d : duties) {
            d.assignedTeacher().ifPresent(t ->
                    result.computeIfAbsent(t, x -> new HashMap<>())
                            .merge(d.role(), 1, Integer::sum));
        }
        return result;
    }

    private static int total(Map<DutyRole, Integer> m) {
        return m == null ? 0 : m.values().stream().mapToInt(Integer::intValue).sum();
    }
}
