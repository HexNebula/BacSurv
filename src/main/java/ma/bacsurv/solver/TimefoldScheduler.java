package ma.bacsurv.solver;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import ma.bacsurv.domain.Duty;
import ma.bacsurv.domain.Teacher;
import ma.bacsurv.rules.SchedulingPolicy;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Adapter: domain duties in, solved assignments written back to the same
 * domain objects. The domain never sees Timefold.
 */
public final class TimefoldScheduler {

    private final SolverSettings settings;

    public TimefoldScheduler(Duration timeLimit) {
        this(new SolverSettings((int) Math.max(1, timeLimit.toSeconds()),
                (int) Math.max(1, Math.min(5, timeLimit.toSeconds()))));
    }

    public TimefoldScheduler(SolverSettings settings) {
        this.settings = settings;
    }

    public SurveillancePlan solve(List<Duty> duties, List<Teacher> pool) {
        return solve(duties, pool, Set.of());
    }

    /**
     * Duties named in {@code pinnedDutyIds} keep the teacher they already
     * carry: hand-made decisions survive a re-solve, and the rest of the
     * schedule is rebuilt around them.
     */
    public SurveillancePlan solve(List<Duty> duties, List<Teacher> pool,
                                  Set<String> pinnedDutyIds) {
        return solve(duties, pool, pinnedDutyIds, SchedulingPolicy.defaults());
    }

    public SurveillancePlan solve(List<Duty> duties, List<Teacher> pool,
                                  Set<String> pinnedDutyIds, SchedulingPolicy policy) {
        List<DutyAssignment> assignments = duties.stream()
                .map(duty -> new DutyAssignment(duty,
                        pinnedDutyIds.contains(duty.id()) && duty.assignedTeacher().isPresent()))
                .toList();
        SurveillancePlan problem = new SurveillancePlan(assignments, pool, policy);

        SolverConfig config = new SolverConfig()
                .withSolutionClass(SurveillancePlan.class)
                .withEntityClasses(DutyAssignment.class)
                .withConstraintProviderClass(SurveillanceConstraints.class)
                .withTerminationConfig(new TerminationConfig()
                        .withSpentLimit(settings.timeLimit())
                        .withUnimprovedSpentLimit(settings.unimprovedLimit()));

        SurveillancePlan solution = SolverFactory.<SurveillancePlan>create(config)
                .buildSolver()
                .solve(problem);

        for (DutyAssignment a : solution.getAssignments()) {
            a.getDuty().assign(a.getTeacher());
        }
        return solution;
    }
}
