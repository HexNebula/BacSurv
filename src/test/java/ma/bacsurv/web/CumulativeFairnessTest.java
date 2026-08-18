package ma.bacsurv.web;

import ma.bacsurv.io.ScheduleWriter;
import ma.bacsurv.web.service.JobView;
import ma.bacsurv.web.service.OperationView;
import ma.bacsurv.web.service.SolveService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Two operations of the same center, solved one after the other. The second
 * must read what the first handed out — teachers loaded in June are relieved
 * in July — without anyone typing a prior workload into a file.
 */
@SpringBootTest
class CumulativeFairnessTest {

    @Autowired SolveService solveService;

    @Test
    void secondOperationCompensatesTheFirst() throws Exception {
        String sample = Files.readString(Path.of("samples", "operation-sample.json"));
        String center = "Centre Ibn Batouta";

        var june = solveService.upload("juin.json", withCenterAndReference(sample, center, "JUIN"));
        ScheduleWriter.Result juneResult = solveAndRead(june);

        var july = solveService.upload("juillet.json",
                withCenterAndReference(sample, center, "JUILLET"));
        ScheduleWriter.Result julyResult = solveAndRead(july);

        // both operations belong to the same center, so the pool is the same rows
        assertEquals(center, june.centerName());
        assertEquals(center, july.centerName());

        Map<String, ScheduleWriter.WorkloadRow> juneByMatricule = byMatricule(juneResult);
        Map<String, ScheduleWriter.WorkloadRow> julyByMatricule = byMatricule(julyResult);
        assertEquals(juneByMatricule.keySet(), julyByMatricule.keySet());

        // July starts from June's totals rather than from zero
        julyByMatricule.forEach((matricule, july2) ->
                assertEquals(juneByMatricule.get(matricule).total(), july2.priorTotal(),
                        "prior workload of " + matricule + " must be what June gave"));

        // nobody is left out: hiding a teacher is the cheapest way to look
        // balanced, so the absence of idle teachers is what makes it honest
        assertTrue(juneResult.workload().stream().allMatch(row -> row.total() > 0),
                "no teacher of the pool may be left without duties");

        // the year's totals stay close together; 90 duties over 16 teachers is
        // 5.6 each, and only the teacher unavailable for a whole day sits lower
        var yearTotals = juneByMatricule.values().stream()
                .mapToInt(row -> row.total() + julyByMatricule.get(row.matricule()).total())
                .summaryStatistics();
        assertTrue(yearTotals.getMax() - yearTotals.getMin() <= 2,
                "cumulative spread should stay tight, was "
                        + yearTotals.getMin() + ".." + yearTotals.getMax());
    }

    private ScheduleWriter.Result solveAndRead(OperationView operation) {
        JobView job = solveService.submit(operation.id(), 10);
        await().atMost(Duration.ofSeconds(90))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> !solveService.job(job.id()).isRunning());

        JobView finished = solveService.job(job.id());
        assertEquals("DONE", finished.status().name(), "error: " + finished.error());
        assertTrue(finished.feasible(), "schedule must be feasible");
        return solveService.schedule(job.id()).orElseThrow();
    }

    private static Map<String, ScheduleWriter.WorkloadRow> byMatricule(ScheduleWriter.Result r) {
        return r.workload().stream().collect(java.util.stream.Collectors.toMap(
                ScheduleWriter.WorkloadRow::matricule, Function.identity()));
    }

    /** Same center and same teachers, a different operation reference. */
    private static String withCenterAndReference(String sample, String center, String reference) {
        return sample.replace(
                "\"operation\": { \"id\": \"NAT-2026-JUIN\", \"type\": \"NATIONAL_2BAC\" }",
                "\"center\": { \"name\": \"" + center + "\" },\n"
                        + "  \"operation\": { \"id\": \"" + reference
                        + "\", \"type\": \"NATIONAL_2BAC\" }");
    }

    @Test
    void teachersAreReusedAcrossOperationsOfTheSameCenter() throws Exception {
        String sample = Files.readString(Path.of("samples", "operation-sample.json"));
        String center = "Centre Al Massira";

        solveService.upload("a.json", withCenterAndReference(sample, center, "OP-A"));
        solveService.upload("b.json", withCenterAndReference(sample, center, "OP-B"));

        List<OperationView> operations = solveService.recentOperations().stream()
                .filter(o -> center.equals(o.centerName())).toList();
        assertEquals(2, operations.size(), "both operations belong to the same center");
    }
}
