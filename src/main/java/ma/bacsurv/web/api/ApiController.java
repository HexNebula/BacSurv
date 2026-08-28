package ma.bacsurv.web.api;

import ma.bacsurv.application.StaffingCheck;
import ma.bacsurv.io.InputMapper;
import ma.bacsurv.web.service.InsufficientStaffException;
import ma.bacsurv.web.service.ScheduleEditor;
import ma.bacsurv.io.ScheduleWriter;
import ma.bacsurv.web.service.JobView;
import ma.bacsurv.web.service.OperationView;
import ma.bacsurv.web.service.SolveService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import ma.bacsurv.web.service.RefusedException;
import org.springframework.context.i18n.LocaleContextHolder;
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
    private final ScheduleEditor editor;
    private final MessageSource messages;

    public ApiController(SolveService solveService, ScheduleEditor editor,
                         MessageSource messages) {
        this.solveService = solveService;
        this.editor = editor;
        this.messages = messages;
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

    /**
     * Why this operation could not be scheduled: hours the pool cannot cover,
     * and duties nobody is qualified to take. Both empty means it can be run.
     */
    @GetMapping("/operations/{id}/staffing")
    public Map<String, Object> staffing(@PathVariable long id) {
        return Map.of(
                "shortages", solveService.staffingShortages(id),
                "unfillable", solveService.unfillableDuties(id));
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

    /**
     * Who could take a duty instead: the pool this schedule was solved from.
     *
     * <p>The teacher list a screen already has is keyed by matricule, and a
     * reassignment is made by id, so a picker cannot be built without this.
     */
    @GetMapping("/jobs/{id}/candidates")
    public List<ScheduleEditor.Candidate> candidates(@PathVariable long id) {
        return editor.candidates(id);
    }

    /** One duty as it stands: what it is, who holds it, whether it is pinned. */
    @GetMapping("/jobs/{id}/duties/{dutyId}")
    public ScheduleEditor.DutyView duty(@PathVariable long id, @PathVariable String dutyId) {
        return editor.duty(id, dutyId);
    }

    /** What a reassignment would break, without saving it. */
    @PostMapping("/jobs/{id}/assignments/{dutyId}/review")
    public ScheduleEditor.ChangeReview review(@PathVariable long id, @PathVariable String dutyId,
                                              @RequestParam(required = false) Long teacherId) {
        return editor.review(id, dutyId, teacherId);
    }

    /** Saves a reassignment; 409 with the reasons when it breaks a rule. */
    @PostMapping("/jobs/{id}/assignments/{dutyId}")
    public ScheduleEditor.ChangeReview reassign(@PathVariable long id, @PathVariable String dutyId,
                                                @RequestParam(required = false) Long teacherId,
                                                @RequestParam(defaultValue = "false") boolean force) {
        return editor.apply(id, dutyId, teacherId, force);
    }

    /** Pinning keeps a hand-made decision through the next solve. */
    @PostMapping("/jobs/{id}/assignments/{dutyId}/pin")
    public ResponseEntity<Void> pin(@PathVariable long id, @PathVariable String dutyId,
                                    @RequestParam(defaultValue = "true") boolean pinned) {
        editor.pin(id, dutyId, pinned);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(ScheduleEditor.IllegalChangeException.class)
    public ResponseEntity<ScheduleEditor.ChangeReview> illegalChange(
            ScheduleEditor.IllegalChangeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.review());
    }

    /**
     * Service faults carry a message key, so the same refusal reads in French
     * or Arabic — the same contract as every other controller.
     *
     * <p>This used to hand the key straight to the screen, so refusing a solve
     * on a settled session put the words "session.settled.locked" in front of
     * an administrator. A message that has no translation still passes through
     * unchanged: a parse fault names a line of somebody's file and is more use
     * verbatim than swallowed.
     */
    @ExceptionHandler({InputMapper.InputException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> badInput(RuntimeException e) {
        String key = String.valueOf(e.getMessage());
        Object[] args = e instanceof RefusedException named ? named.args() : null;
        String sentence = messages.getMessage("error." + key, args, key,
                LocaleContextHolder.getLocale());
        return ResponseEntity.badRequest().body(Map.of("error", sentence, "code", key));
    }

    /** 409: the request is well formed, the pool simply cannot cover the work. */
    @ExceptionHandler(InsufficientStaffException.class)
    public ResponseEntity<Map<String, Object>> insufficientStaff(InsufficientStaffException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", explain(e),
                "shortages", e.shortages(),
                "unfillable", e.unfillable()));
    }

    /**
     * Why the pool cannot do the work, in the administrator's language.
     *
     * <p>The exception's own message is written in English inside the code,
     * which is right for a log and wrong for the one person who has to act on
     * it. One fault is named — the first that has to be fixed — and the rest
     * are counted: a session missing a specialist is short at every hour that
     * subject is examined, and listing eleven sentences saying the same thing
     * hides the single decision behind them.
     */
    private String explain(InsufficientStaffException e) {
        // a missing specialist comes first only when that is really the fault.
        // A centre short of people also has duties nobody can take — everyone
        // eligible is already in a room — and answering "nobody is available"
        // there hides the figure that fixes it: the hour needs ten and there
        // are two.
        StaffingCheck.Unfillable specialist = e.unfillable().stream()
                .filter(duty -> duty.needsSpecialist() && duty.subject() != null)
                .findFirst().orElse(null);

        if (specialist != null) {
            return with(say("error.staff.specialist", specialist.subject(),
                            day(specialist.date()), hour(specialist.at())),
                    e.unfillable().size() - 1);
        }
        if (!e.shortages().isEmpty()) {
            StaffingCheck.Shortage worst = e.worst();
            return with(say("error.staff.shortage", day(worst.date()), hour(worst.at()),
                            String.valueOf(worst.required()), String.valueOf(worst.available())),
                    e.shortages().size() - 1);
        }
        StaffingCheck.Unfillable duty = e.firstUnfillable();
        return with(say("error.staff.unfillable", day(duty.date()), hour(duty.at())),
                e.unfillable().size() - 1);
    }

    /** The fault that was named, and how many more of its own kind there are. */
    private String with(String sentence, int others) {
        return others <= 0 ? sentence
                : sentence + " " + say("error.staff.more", String.valueOf(others));
    }

    /**
     * Counts and dates are passed as text on purpose: MessageFormat would
     * render a number in the locale's own digits, and Arabic pages here are
     * read with Latin ones.
     */
    private String say(String key, String... args) {
        return messages.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    private static String day(java.time.LocalDate date) {
        return date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private static String hour(java.time.LocalTime at) {
        return at.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }
}
