package ma.bacsurv.web.ui;

import ma.bacsurv.web.service.ScheduleEditor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Changing one assignment by hand: choose someone, see what it would break,
 * then decide. Nothing is saved by the review step.
 */
@Controller
public class AssignmentController {

    private final ScheduleEditor editor;
    private final MessageSource messages;

    public AssignmentController(ScheduleEditor editor, MessageSource messages) {
        this.editor = editor;
        this.messages = messages;
    }

    private String say(String key, Object... args) {
        return messages.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    @GetMapping("/jobs/{id}/assignments/{dutyId}/edit")
    public String edit(@PathVariable long id, @PathVariable String dutyId, Model model) {
        model.addAttribute("jobId", id);
        model.addAttribute("duty", editor.duty(id, dutyId));
        model.addAttribute("candidates", editor.candidates(id));
        return "assignment-edit";
    }

    @PostMapping("/jobs/{id}/assignments/{dutyId}/review")
    public String review(@PathVariable long id, @PathVariable String dutyId,
                         @RequestParam(required = false) Long teacherId, Model model) {
        model.addAttribute("jobId", id);
        model.addAttribute("duty", editor.duty(id, dutyId));
        model.addAttribute("candidates", editor.candidates(id));
        model.addAttribute("review", editor.review(id, dutyId, teacherId));
        model.addAttribute("teacherId", teacherId);
        return "assignment-edit";
    }

    @PostMapping("/jobs/{id}/assignments/{dutyId}")
    public String apply(@PathVariable long id, @PathVariable String dutyId,
                        @RequestParam(required = false) Long teacherId,
                        @RequestParam(defaultValue = "false") boolean force,
                        RedirectAttributes flash) {
        try {
            editor.apply(id, dutyId, teacherId, force);
            flash.addFlashAttribute("message", say("change.applied"));
        } catch (ScheduleEditor.IllegalChangeException e) {
            flash.addFlashAttribute("error", say("change.illegal"));
        }
        return "redirect:/jobs/" + id;
    }

    @PostMapping("/jobs/{id}/assignments/{dutyId}/pin")
    public String pin(@PathVariable long id, @PathVariable String dutyId,
                      @RequestParam(defaultValue = "true") boolean pinned) {
        editor.pin(id, dutyId, pinned);
        return "redirect:/jobs/" + id;
    }
}
