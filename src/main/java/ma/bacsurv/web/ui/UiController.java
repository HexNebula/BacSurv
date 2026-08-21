package ma.bacsurv.web.ui;

import ma.bacsurv.io.InputMapper;
import ma.bacsurv.io.ScheduleWriter;
import ma.bacsurv.web.service.InsufficientStaffException;
import ma.bacsurv.web.service.SolveService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Server-rendered pages. Thin: every action maps to a service call. */
@Controller
public class UiController {

    private final SolveService solveService;
    private final MessageSource messages;
    private final int defaultSeconds;

    public UiController(SolveService solveService, MessageSource messages,
                        @Value("${bacsurv.solver.default-seconds:30}") int defaultSeconds) {
        this.solveService = solveService;
        this.messages = messages;
        this.defaultSeconds = defaultSeconds;
    }

    /** Every user-facing string comes from the bundles — the UI is French and Arabic. */
    private String say(String key, Object... args) {
        return messages.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("operations", solveService.recentOperations());
        model.addAttribute("jobs", solveService.recentJobs());
        model.addAttribute("defaultSeconds", defaultSeconds);
        return "home";
    }

    @PostMapping("/operations")
    public String upload(@RequestParam("file") MultipartFile file, RedirectAttributes flash) {
        if (file.isEmpty()) {
            flash.addFlashAttribute("error", say("home.import.empty"));
            return "redirect:/";
        }
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            String name = file.getOriginalFilename() != null
                    ? file.getOriginalFilename() : "operation.json";
            solveService.upload(name, content);
            flash.addFlashAttribute("message", say("home.import.success", name));
        } catch (IOException e) {
            flash.addFlashAttribute("error", say("home.import.unreadable", e.getMessage()));
        } catch (InputMapper.InputException e) {
            flash.addFlashAttribute("error", say("home.import.invalid", e.getMessage()));
        }
        return "redirect:/";
    }

    @PostMapping("/operations/{id}/solve")
    public String solve(@PathVariable long id,
                        @RequestParam(defaultValue = "30") int seconds,
                        RedirectAttributes flash) {
        try {
            return "redirect:/jobs/" + solveService.submit(id, seconds).id();
        } catch (InsufficientStaffException e) {
            var worst = e.worst();
            // simultaneous épreuves need different wording: the number is what
            // the centre must field at one moment, not what one séance asks for
            flash.addFlashAttribute("error", say(
                    worst.isConcurrent() ? "solve.insufficient.concurrent" : "solve.insufficient",
                    worst.slotId(), worst.required(), worst.available(), e.shortages().size()));
            return "redirect:/";
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/";
        }
    }

    @GetMapping("/jobs/{id}")
    public String job(@PathVariable long id, Model model) {
        model.addAttribute("job", solveService.job(id));

        solveService.schedule(id).ifPresent(result -> {
            model.addAttribute("result", result);
            model.addAttribute("slots", groupBySlot(result));
            model.addAttribute("workload", result.workload().stream()
                    .sorted(Comparator.comparing(ScheduleWriter.WorkloadRow::teacherId))
                    .toList());
        });
        return "job";
    }

    /** Assignments grouped per slot, in chronological order, for the schedule table. */
    private Map<String, List<ScheduleWriter.AssignmentRow>> groupBySlot(ScheduleWriter.Result result) {
        return result.assignments().stream().collect(Collectors.groupingBy(
                a -> a.date() + " " + a.start() + "–" + a.end() + "  (" + a.slotId() + ")",
                LinkedHashMap::new,
                Collectors.toList()));
    }
}
