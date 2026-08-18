package ma.bacsurv.demo;

import ma.bacsurv.application.DutyGenerator;
import ma.bacsurv.application.GreedyScheduler;
import ma.bacsurv.application.ScheduleValidator;
import ma.bacsurv.application.ValidationReport;
import ma.bacsurv.domain.Duty;
import ma.bacsurv.domain.Teacher;
import ma.bacsurv.rules.Eligibility;
import ma.bacsurv.solver.SurveillancePlan;
import ma.bacsurv.solver.TimefoldScheduler;

import java.time.Duration;
import java.util.List;

/** Greedy vs Timefold on the same dataset. */
public final class SolverDemoMain {

    public static void main(String[] args) {
        List<Teacher> pool = DemoData.pool();
        ScheduleValidator validator = ScheduleValidator.withDefaults();

        // --- greedy baseline ---
        List<Duty> greedyDuties = new DutyGenerator().generate(DemoData.operation());
        new GreedyScheduler(Eligibility.withDefaults()).schedule(greedyDuties, pool);
        ValidationReport greedyReport = validator.validate(greedyDuties);

        // --- Timefold ---
        List<Duty> solverDuties = new DutyGenerator().generate(DemoData.operation());
        SurveillancePlan solution = new TimefoldScheduler(Duration.ofSeconds(10))
                .solve(solverDuties, pool);
        ValidationReport solverReport = validator.validate(solverDuties);

        SchedulePrinter.printSchedule(solverDuties);
        SchedulePrinter.printWorkload(solverDuties, pool);
        SchedulePrinter.printValidation(solverReport, 0);

        System.out.println();
        System.out.println("=== GREEDY vs TIMEFOLD ===");
        System.out.printf("  greedy   : feasible=%s hard=%d soft=%d%n",
                greedyReport.isFeasible(),
                greedyReport.hardViolations().size(),
                greedyReport.softViolations().size());
        System.out.printf("  timefold : feasible=%s hard=%d soft=%d  (score %s)%n",
                solverReport.isFeasible(),
                solverReport.hardViolations().size(),
                solverReport.softViolations().size(),
                solution.getScore());
    }
}
