package ma.bacsurv.cli;

import ma.bacsurv.application.DutyGenerator;
import ma.bacsurv.application.ScheduleValidator;
import ma.bacsurv.application.StaffingCheck;
import ma.bacsurv.application.ValidationReport;
import ma.bacsurv.domain.Duty;
import ma.bacsurv.io.InputMapper;
import ma.bacsurv.io.ScheduleWriter;
import ma.bacsurv.solver.TimefoldScheduler;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * bacsurv solve: operation JSON in, schedule JSON out.
 *
 *   SolveMain <input.json> [-o output.json] [--seconds N]
 *
 * Exit codes: 0 = feasible schedule written, 1 = solved but infeasible
 * (hard violations or unfilled duties remain), 2 = bad input file / usage.
 */
public final class SolveMain {

    public static void main(String[] args) {
        Path input = null, output = null;
        int seconds = 30;
        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "-o", "--output" -> output = Path.of(args[++i]);
                    case "--seconds" -> seconds = Integer.parseInt(args[++i]);
                    default -> {
                        if (input != null) throw new IllegalArgumentException(
                                "unexpected argument: " + args[i]);
                        input = Path.of(args[i]);
                    }
                }
            }
            if (input == null) throw new IllegalArgumentException("no input file given");
        } catch (RuntimeException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("Usage: SolveMain <input.json> [-o output.json] [--seconds N]");
            System.exit(2);
            return;
        }
        if (output == null) {
            String name = input.getFileName().toString().replaceFirst("\\.json$", "");
            output = input.resolveSibling(name + ".schedule.json");
        }

        InputMapper.ParsedOperation parsed;
        try {
            parsed = new InputMapper().read(input);
        } catch (RuntimeException e) {
            System.err.println("Input error: " + e.getMessage());
            System.exit(2);
            return;
        }

        List<Duty> duties = new DutyGenerator().generate(parsed.operation());
        System.out.printf("Operation %s: %d slots, %d duties, %d teachers. Solving (max %ds)...%n",
                parsed.operation().id(), parsed.operation().slots().size(),
                duties.size(), parsed.teachers().size(), seconds);

        // No pool can be in two rooms at once, so an impossible hour is worth
        // saying plainly instead of letting the solver return a wall of hard
        // violations that nobody can act on.
        List<StaffingCheck.Shortage> shortages =
                StaffingCheck.withDefaults().check(parsed.operation(), parsed.teachers(), duties);
        if (!shortages.isEmpty()) {
            System.err.println("Insufficient staff — no schedule exists:");
            for (StaffingCheck.Shortage shortage : shortages) {
                System.err.printf("  %s %s  %s needs %d, %d available (%d missing)%n",
                        shortage.date(), shortage.at(),
                        shortage.isConcurrent()
                                ? "simultaneous " + shortage.slotId()
                                : "slot " + shortage.slotId(),
                        shortage.required(), shortage.available(), shortage.missing());
            }
            System.exit(1);
            return;
        }

        new TimefoldScheduler(Duration.ofSeconds(seconds)).solve(duties, parsed.teachers());

        ValidationReport report = ScheduleValidator.withDefaults().validate(duties);
        ScheduleWriter writer = new ScheduleWriter();
        ScheduleWriter.Result result = writer.build(
                parsed.operation().id(), duties, parsed.teachers(), report);
        writer.write(result, output);

        System.out.printf("Done. feasible=%s hard=%d soft=%d unfilled=%d%n",
                result.feasible(), result.hardViolations(),
                result.softViolations(), result.unfilled());
        result.hardViolationDetails().forEach(v -> System.out.println("  HARD " + v));
        System.out.println("Schedule written to " + output);
        System.exit(result.feasible() ? 0 : 1);
    }
}
