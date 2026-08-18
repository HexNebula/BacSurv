package ma.bacsurv.solver;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import ma.bacsurv.domain.Duty;
import ma.bacsurv.domain.Teacher;

import java.time.Duration;
import java.util.List;

/**
 * Adapter: domain duties in, solved assignments written back to the same
 * domain objects. The domain never sees Timefold.
 */
public final class TimefoldScheduler {

    private final Duration timeLimit;

    public TimefoldScheduler(Duration timeLimit) {
        this.timeLimit = timeLimit;
    }

    public SurveillancePlan solve(List<Duty> duties, List<Teacher> pool) {
        List<DutyAssignment> assignments = duties.stream().map(DutyAssignment::new).toList();
        SurveillancePlan problem = new SurveillancePlan(assignments, pool);

        SolverConfig config = new SolverConfig()
                .withSolutionClass(SurveillancePlan.class)
                .withEntityClasses(DutyAssignment.class)
                .withConstraintProviderClass(SurveillanceConstraints.class)
                .withTerminationConfig(new TerminationConfig()
                        .withSpentLimit(timeLimit)
                        .withUnimprovedSpentLimit(Duration.ofSeconds(5)));

        SurveillancePlan solution = SolverFactory.<SurveillancePlan>create(config)
                .buildSolver()
                .solve(problem);

        for (DutyAssignment a : solution.getAssignments()) {
            a.getDuty().assign(a.getTeacher());
        }
        return solution;
    }
}
