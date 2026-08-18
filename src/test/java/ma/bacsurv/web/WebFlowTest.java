package ma.bacsurv.web;

import ma.bacsurv.web.service.JobView;
import ma.bacsurv.web.service.OperationView;
import ma.bacsurv.web.service.SolveService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The web contract: upload -> solve -> poll -> schedule, plus the pages the
 * browser renders. Runs the real solver on the sample file with a short
 * time limit.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WebFlowTest {

    @Autowired MockMvc mvc;
    @Autowired SolveService solveService;

    private String sample() throws Exception {
        return Files.readString(Path.of("samples", "operation-sample.json"));
    }

    @Test
    void uploadSolvePollAndFetchSchedule() throws Exception {
        var file = new MockMultipartFile("file", "operation-sample.json",
                MediaType.APPLICATION_JSON_VALUE, sample().getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/operations").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value("NAT-2026-JUIN"))
                .andExpect(jsonPath("$.centerName").value("NAT-2026-JUIN"));

        OperationView operation = solveService.recentOperations().getFirst();

        mvc.perform(post("/api/operations/{id}/solve", operation.id()).param("seconds", "5"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"));

        JobView job = solveService.recentJobs().getFirst();

        // schedule is not available until the solver finishes
        mvc.perform(get("/api/jobs/{id}/schedule", job.id()))
                .andExpect(status().isConflict());

        await().atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> !solveService.job(job.id()).isRunning());

        JobView finished = solveService.job(job.id());
        assertEquals("DONE", finished.status().name(), "error: " + finished.error());
        assertTrue(finished.feasible(), "sample must produce a feasible schedule");
        assertEquals(0, finished.hardViolations());
        assertEquals(0, finished.unfilled());

        mvc.perform(get("/api/jobs/{id}/schedule", job.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feasible").value(true))
                .andExpect(jsonPath("$.assignments.length()").value(45))
                .andExpect(jsonPath("$.workload.length()").value(16));

        // the browser pages render the same data
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("NAT-2026-JUIN")));

        mvc.perform(get("/jobs/{id}", job.id()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SURVEILLANCE")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Charge de travail")));
    }

    @Test
    void rejectsInvalidOperationFile() throws Exception {
        var bad = new MockMultipartFile("file", "bad.json",
                MediaType.APPLICATION_JSON_VALUE,
                "{\"operation\":{\"id\":\"X\",\"type\":\"NATIONAL_2BAC\"}}".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/operations").file(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("slot")));
    }

    @Test
    void unknownJobIsRejected() throws Exception {
        mvc.perform(get("/api/jobs/{id}", 9999))
                .andExpect(status().isBadRequest());
    }
}
