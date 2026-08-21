package ma.bacsurv.web.ui;

import ma.bacsurv.web.service.CenterAdminService;
import ma.bacsurv.web.service.TeacherImportService;
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

/** Centers and their teacher pool: list, import from a spreadsheet, confirm. */
@Controller
public class CenterController {

    private final TeacherImportService teacherImport;
    private final CenterAdminService admin;
    private final MessageSource messages;

    public CenterController(TeacherImportService teacherImport, CenterAdminService admin,
                            MessageSource messages) {
        this.teacherImport = teacherImport;
        this.admin = admin;
        this.messages = messages;
    }

    private String say(String key, Object... args) {
        return messages.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    /**
     * Service faults carry a message key rather than a sentence, so the same
     * fault reads in French or Arabic. Anything else is shown as it comes.
     */
    private String reason(IllegalArgumentException e) {
        String key = String.valueOf(e.getMessage());
        try {
            return messages.getMessage("error." + key, null, LocaleContextHolder.getLocale());
        } catch (RuntimeException unknown) {
            return key;
        }
    }

    @GetMapping("/centers")
    public String centers(Model model) {
        model.addAttribute("centers", teacherImport.centers());
        model.addAttribute("types", ma.bacsurv.domain.OperationType.values());
        return "centers";
    }

    @PostMapping("/centers")
    public String createCenter(@RequestParam String name, RedirectAttributes flash) {
        try {
            long id = admin.createCenter(name);
            flash.addFlashAttribute("message", say("center.created", name));
            return "redirect:/centers/" + id;
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", reason(e));
            return "redirect:/centers";
        }
    }

    /** Everything set up under one centre: rooms, sessions, the teacher pool. */
    @GetMapping("/centers/{id}")
    public String center(@PathVariable long id, Model model) {
        model.addAttribute("center", admin.detail(id));
        model.addAttribute("types", ma.bacsurv.domain.OperationType.values());
        return "center";
    }

    @PostMapping("/centers/{id}/rename")
    public String renameCenter(@PathVariable long id, @RequestParam String name,
                               RedirectAttributes flash) {
        try {
            admin.renameCenter(id, name);
            flash.addFlashAttribute("message", say("center.renamed", name));
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", reason(e));
        }
        return "redirect:/centers/" + id;
    }

    @PostMapping("/centers/{id}/rooms")
    public String addRooms(@PathVariable long id, @RequestParam int count,
                           @RequestParam(required = false) String prefix,
                           RedirectAttributes flash) {
        try {
            // an administrator working in Arabic and naming nothing should get
            // قاعة 1, not Salle 1 — the default follows the interface language
            String naming = prefix == null || prefix.isBlank() ? say("rooms.default.prefix") : prefix;
            flash.addFlashAttribute("message", say("rooms.added", admin.addRooms(id, count, naming)));
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", reason(e));
        }
        return "redirect:/centers/" + id;
    }

    @PostMapping("/centers/{id}/rooms/{roomId}")
    public String renameRoom(@PathVariable long id, @PathVariable long roomId,
                             @RequestParam String label,
                             @RequestParam(required = false) Integer surveillants,
                             RedirectAttributes flash) {
        try {
            admin.renameRoom(roomId, label, surveillants);
            flash.addFlashAttribute("message", say("room.saved", label));
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", reason(e));
        }
        return "redirect:/centers/" + id;
    }

    @PostMapping("/centers/{id}/rooms/{roomId}/delete")
    public String deleteRoom(@PathVariable long id, @PathVariable long roomId,
                             RedirectAttributes flash) {
        try {
            admin.deleteRoom(roomId);
            flash.addFlashAttribute("message", say("room.deleted"));
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", say("room.inUse"));
        }
        return "redirect:/centers/" + id;
    }

    @PostMapping("/centers/{id}/sessions")
    public String createSession(@PathVariable long id,
                                @RequestParam String reference, @RequestParam String type,
                                @RequestParam String startsOn, @RequestParam String endsOn,
                                RedirectAttributes flash) {
        try {
            admin.createSession(id, reference, type,
                    java.time.LocalDate.parse(startsOn), java.time.LocalDate.parse(endsOn));
            flash.addFlashAttribute("message", say("session.created", reference));
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", reason(e));
        } catch (java.time.format.DateTimeParseException e) {
            flash.addFlashAttribute("error", say("error.session.dates"));
        }
        return "redirect:/centers/" + id;
    }

    @GetMapping("/centers/{id}/teachers")
    public String pool(@PathVariable long id, Model model) {
        model.addAttribute("center", center(id));
        model.addAttribute("pool", teacherImport.pool(id));
        return "teachers";
    }

    /** Step one: read the file and show what it would do. Nothing is written. */
    @PostMapping("/centers/{id}/teachers/preview")
    public String preview(@PathVariable long id, @RequestParam("file") MultipartFile file,
                          Model model, RedirectAttributes flash) {
        if (file.isEmpty()) {
            flash.addFlashAttribute("error", say("home.import.empty"));
            return "redirect:/centers/" + id + "/teachers";
        }
        String csv;
        try {
            csv = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            flash.addFlashAttribute("error", say("home.import.unreadable", e.getMessage()));
            return "redirect:/centers/" + id + "/teachers";
        }
        model.addAttribute("center", center(id));
        model.addAttribute("preview", teacherImport.preview(id, csv));
        model.addAttribute("csv", csv); // carried into the confirm step
        return "teachers-preview";
    }

    /** Step two: the administrator has seen the changes and accepts them. */
    @PostMapping("/centers/{id}/teachers/apply")
    public String apply(@PathVariable long id, @RequestParam("csv") String csv,
                        RedirectAttributes flash) {
        var applied = teacherImport.apply(id, csv);
        flash.addFlashAttribute("message", say("teachers.imported",
                applied.created().size(), applied.updated().size(), applied.unchanged().size()));
        return "redirect:/centers/" + id + "/teachers";
    }

    private ma.bacsurv.web.service.CenterView center(long id) {
        return teacherImport.centers().stream()
                .filter(c -> c.id() == id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no center with id " + id));
    }
}
