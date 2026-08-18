package ma.bacsurv.web.api;

import ma.bacsurv.io.InputMapper;
import ma.bacsurv.io.ScheduleWriter;
import ma.bacsurv.web.service.JobView;
import ma.bacsurv.web.service.OperationView;
import ma.bacsurv.web.service.SolveService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * The contract every client uses — the browser pages included. Keeping the
 * UI on this API means a different front end later is a new skin, not a
 * rewrite.
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final SolveService solveService;

    public ApiController(SolveService solveService) {
        this.solveService = solveService;
    }

    @GetMapping("/operations")
    public List<OperationView> operations() {
        return solveService.recentOperations();
    }

    /** Upload an operation input file (multipart) — rejected if it does not parse. */
    @PostMapping(value = "/operations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OperationView> upload(@RequestParam("file") MultipartFile file) {
        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String name = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "operation.json";
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(solveService.upload(name, content));
    }

    /** Same thing for API clients that just POST the JSON body. */
    @PostMapping(value = "/operations", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OperationView> uploadJson(
            @RequestBody String body,
            @RequestParam(defaultValue = "operation.json") String name) {
        return ResponseEntity.status(HttpStatus.CREATED).body(solveService.upload(name, body));
    }

    @PostMapping("/operations/{id}/solve")
    public ResponseEntity<JobView> solve(@PathVariable long id,
                                         @RequestParam(defaultValue = "30") int seconds) {
        return ResponseEntity.accepted().body(solveService.submit(id, seconds));
    }

    @GetMapping("/jobs")
    public List<JobView> jobs() {
        return solveService.recentJobs();
    }

    @GetMapping("/jobs/{id}")
    public JobView job(@PathVariable long id) {
        return solveService.job(id);
    }

    /** The solved schedule itself — 409 while the job is still running. */
    @GetMapping("/jobs/{id}/schedule")
    public ResponseEntity<ScheduleWriter.Result> schedule(@PathVariable long id) {
        solveService.job(id); // 400 if the job does not exist at all
        return solveService.schedule(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT).build());
    }

    @ExceptionHandler({InputMapper.InputException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> badInput(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
    }
}
