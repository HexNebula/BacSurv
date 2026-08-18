package ma.bacsurv.web;

import ma.bacsurv.web.persistence.AssignmentEntity;
import ma.bacsurv.web.persistence.AssignmentRepository;
import ma.bacsurv.web.service.JobView;
import ma.bacsurv.web.service.OperationView;
import ma.bacsurv.web.service.ScheduleEditor;
import ma.bacsurv.web.service.SolveService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A pinned assignment is a decision, not a suggestion: re-solving rebuilds the
 * schedule around it rather than overwriting it.
 */
@SpringBootTest
class PinnedAssignmentTest {

    @Autowired SolveService solveService;
    @Autowired ScheduleEditor editor;
    @Autowired AssignmentRepository assignments;

    private long solve(long operationId) {
        JobView job = solveService.submit(operationId, 8);
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(500))
                .until(() -> !solveService.job(job.id()).isRunning());
        assertEquals("DONE", solveService.job(job.id()).status().name());
        return job.id();
    }

    private OperationView operation(String centre) throws Exception {
        String sample = Files.readString(Path.of("samples", "operation-sample.json"))
                .replace("\"operation\": { \"id\": \"NAT-2026-JUIN\"",
                        "\"center\": { \"name\": \"" + centre + "\" },\n"
                                + "  \"operation\": { \"id\": \"OP-PIN-" + System.nanoTime() + "\"");
        return solveService.upload("pin.json", sample);
    }

    @Test
    void aPinnedAssignmentSurvivesTheNextSolve() throws Exception {
        OperationView operation = operation("Centre Pin");
        long first = solve(operation.id());

        // fix one duty on someone the solver did not choose for it
        AssignmentEntity duty = assignments.findOfJob(first).stream()
                .filter(a -> a.getRole().name().equals("SURVEILLANCE") && a.getTeacher() != null)
                .findFirst().orElseThrow();
        var candidate = editor.candidates(first).stream()
                .filter(c -> !c.id().equals(duty.getTeacher().getId()))
                .filter(c -> editor.review(first, duty.getDutyId(), c.id()).isLegal())
                .findFirst().orElseThrow();

        editor.apply(first, duty.getDutyId(), candidate.id(), false);
        editor.pin(first, duty.getDutyId(), true);

        long second = solve(operation.id());

        AssignmentEntity afterResolve = assignments
                .findByJobIdAndDutyId(second, duty.getDutyId()).orElseThrow();
        assertEquals(candidate.id(), afterResolve.getTeacher().getId(),
                "the pinned teacher stayed on their duty");
        assertTrue(afterResolve.isPinned(), "and the pin itself survived the re-solve");

        // the rest of the schedule is still a legal, complete schedule
        var result = solveService.schedule(second).orElseThrow();
        assertEquals(0, result.unfilled());
        assertEquals(0, result.hardViolations(), result.hardViolationDetails().toString());
    }

    @Test
    void withoutAPinTheSolverIsFreeToMoveTheDuty() throws Exception {
        OperationView operation = operation("Centre Sans Pin");
        long first = solve(operation.id());

        List<AssignmentEntity> before = assignments.findOfJob(first);
        long second = solve(operation.id());

        // nothing is pinned, so the second schedule owes nothing to the first
        assertTrue(assignments.findOfJob(second).stream().noneMatch(AssignmentEntity::isPinned));
        assertEquals(before.size(), assignments.findOfJob(second).size());
    }
}
