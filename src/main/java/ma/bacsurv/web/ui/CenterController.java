package ma.bacsurv.web.ui;

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
    private final MessageSource messages;

    public CenterController(TeacherImportService teacherImport, MessageSource messages) {
        this.teacherImport = teacherImport;
        this.messages = messages;
    }

    private String say(String key, Object... args) {
        return messages.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    @GetMapping("/centers")
    public String centers(Model model) {
        model.addAttribute("centers", teacherImport.centers());
        return "centers";
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
