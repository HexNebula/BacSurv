package ma.bacsurv.application;

import ma.bacsurv.domain.Duty;
import ma.bacsurv.domain.DutyRole;
import ma.bacsurv.domain.ExamSlot;
import ma.bacsurv.domain.Teacher;
import ma.bacsurv.rules.Eligibility;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Baseline scheduler: for each duty, pick the eligible teacher with the
 * lowest (role count, total count). Permanence assigned first — smallest
 * eligible pools. Not optimal by design; exists to exercise the domain
 * and to serve as the benchmark a real solver must beat.
 */
public final class GreedyScheduler {

    private final Eligibility eligibility;

    public GreedyScheduler(Eligibility eligibility) {
        this.eligibility = eligibility;
    }

    /** Assigns in place. Returns duties it could not fill. */
    public List<Duty> schedule(List<Duty> duties, List<Teacher> pool) {
        Map<Teacher, Map<DutyRole, Integer>> roleCounts = new HashMap<>();
        Map<ExamSlot, Set<Teacher>> busyPerSlot = new HashMap<>();

        List<Duty> ordered = duties.stream()
                .sorted(Comparator.comparing(d -> switch (d.role()) {
                    case PERMANENCE -> 0;
                    case SURVEILLANCE -> 1;
                    case RESERVE -> 2;
                }))
                .toList();

        for (Duty duty : ordered) {
            Set<Teacher> busy = busyPerSlot.computeIfAbsent(duty.slot(), s -> new HashSet<>());
            pool.stream()
                    .filter(t -> !busy.contains(t))
                    .filter(t -> eligibility.isEligible(t, duty))
                    .min(Comparator
                            .comparingInt((Teacher t) -> count(roleCounts, t, duty.role()))
                            .thenComparingInt(t -> total(roleCounts, t))
                            .thenComparing(Teacher::id))
                    .ifPresent(t -> {
                        duty.assign(t);
                        busy.add(t);
                        roleCounts.computeIfAbsent(t, x -> new HashMap<>())
                                .merge(duty.role(), 1, Integer::sum);
                    });
        }
        return duties.stream().filter(d -> d.assignedTeacher().isEmpty()).toList();
    }

    private int count(Map<Teacher, Map<DutyRole, Integer>> counts, Teacher t, DutyRole role) {
        return counts.getOrDefault(t, Map.of()).getOrDefault(role, 0);
    }

    private int total(Map<Teacher, Map<DutyRole, Integer>> counts, Teacher t) {
        return counts.getOrDefault(t, Map.of()).values().stream().mapToInt(Integer::intValue).sum();
    }
}
