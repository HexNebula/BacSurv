package ma.bacsurv.io;

import ma.bacsurv.application.DutyGenerator;
import ma.bacsurv.application.ScheduleValidator;
import ma.bacsurv.domain.Duty;
import ma.bacsurv.solver.TimefoldScheduler;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression: the full pipeline (sample JSON -> duties -> Timefold ->
 * validator -> result JSON) must produce a feasible, fully filled schedule.
 * Uses the independent validator, not the solver's own score.
 */
class EndToEndSolveTest {

    @Test
    void sampleFileSolvesToFeasibleSchedule() throws Exception {
        var parsed = new InputMapper().read(Path.of("samples", "operation-sample.json"));
        List<Duty> duties = new DutyGenerator().generate(parsed.operation());

        new TimefoldScheduler(Duration.ofSeconds(10)).solve(duties, parsed.teachers());

        var report = ScheduleValidator.withDefaults().validate(duties);
        assertTrue(report.isFeasible(),
                "hard violations: " + report.hardViolations());
        assertEquals(0, duties.stream().filter(d -> d.assignedTeacher().isEmpty()).count(),
                "all duties must be filled");

        var writer = new ScheduleWriter();
        var result = writer.build(parsed.operation().id(), duties, parsed.teachers(), report);
        assertTrue(result.feasible());
        assertEquals(duties.size(), result.assignments().size());

        Path out = Files.createTempFile("bacsurv-schedule", ".json");
        try {
            writer.write(result, out);
            assertTrue(Files.size(out) > 0);
        } finally {
            Files.deleteIfExists(out);
        }
    }
}
