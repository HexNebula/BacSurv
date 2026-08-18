package ma.bacsurv.web.ui;

import ma.bacsurv.web.service.OperationConfigService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** The settings a centre may change for one operation. */
@Controller
public class SettingsController {

    private final OperationConfigService configs;
    private final MessageSource messages;

    public SettingsController(OperationConfigService configs, MessageSource messages) {
        this.configs = configs;
        this.messages = messages;
    }

    private String say(String key, Object... args) {
        return messages.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    @GetMapping("/operations/{id}/settings")
    public String settings(@PathVariable long id, Model model) {
        model.addAttribute("settings", configs.settings(id));
        return "settings";
    }

    @PostMapping("/operations/{id}/settings")
    public String save(@PathVariable long id,
                       @RequestParam int defaultSurveillantsPerRoom,
                       @RequestParam String reserveMode,
                       @RequestParam double reservePercentage,
                       @RequestParam int reserveFixedCount,
                       @RequestParam int maxConsecutiveDays,
                       @RequestParam String consecutiveDaysStrength,
                       @RequestParam int minGapMinutes,
                       @RequestParam String ownSubjectStrength,
                       @RequestParam(defaultValue = "false") boolean forbidOwnSubjectReserve,
                       @RequestParam int solveSeconds,
                       RedirectAttributes flash) {
        try {
            configs.save(id, defaultSurveillantsPerRoom, reserveMode, reservePercentage,
                    reserveFixedCount, maxConsecutiveDays, consecutiveDaysStrength, minGapMinutes,
                    ownSubjectStrength, forbidOwnSubjectReserve, solveSeconds);
            flash.addFlashAttribute("message", say("settings.saved"));
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/operations/" + id + "/settings";
    }

    @PostMapping("/operations/{id}/settings/rooms/{roomId}")
    public String room(@PathVariable long id, @PathVariable long roomId,
                       @RequestParam(required = false) Integer surveillants,
                       RedirectAttributes flash) {
        try {
            configs.setRoomStaffing(roomId, surveillants);
            flash.addFlashAttribute("message", say("settings.saved"));
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/operations/" + id + "/settings";
    }
}
