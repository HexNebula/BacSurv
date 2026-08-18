package ma.bacsurv.demo;

import ma.bacsurv.application.DutyGenerator;
import ma.bacsurv.application.GreedyScheduler;
import ma.bacsurv.application.ScheduleValidator;
import ma.bacsurv.application.ValidationReport;
import ma.bacsurv.domain.Duty;
import ma.bacsurv.domain.Teacher;
import ma.bacsurv.rules.Eligibility;

import java.util.List;

/** Greedy baseline on the demo dataset. */
public final class DemoMain {

    public static void main(String[] args) {
        List<Teacher> pool = DemoData.pool();
        List<Duty> duties = new DutyGenerator().generate(DemoData.operation());
        List<Duty> unfilled = new GreedyScheduler(Eligibility.withDefaults())
                .schedule(duties, pool);

        SchedulePrinter.printSchedule(duties);
        SchedulePrinter.printWorkload(duties, pool);
        ValidationReport report = ScheduleValidator.withDefaults().validate(duties);
        SchedulePrinter.printValidation(report, unfilled.size());
    }
}
