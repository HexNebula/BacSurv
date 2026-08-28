package ma.bacsurv.web.api;

import ma.bacsurv.web.service.OperationConfigService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import ma.bacsurv.web.service.RefusedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The rules a centre may change for one session, in three groups that are
 * deliberately kept apart: how many people staff a room and the réserve, the
 * scheduling policy the académie imposes, and how long the search may run —
 * raising the last is not a change of procedure.
 */
@RestController
@RequestMapping("/api/operations/{id}/settings")
public class SettingsApiController {

    private final OperationConfigService configs;
    private final MessageSource messages;

    public SettingsApiController(OperationConfigService configs, MessageSource messages) {
        this.configs = configs;
        this.messages = messages;
    }

    public record SettingsEdit(
            int defaultSurveillantsPerRoom,
            String reserveMode,
            double reservePercentage,
            int reserveFixedCount,
            int maxConsecutiveDays,
            String consecutiveDaysStrength,
            int minGapMinutes,
            String ownSubjectStrength,
            boolean forbidOwnSubjectReserve,
            int solveSeconds) {}

    @GetMapping
    public OperationConfigService.Settings settings(@PathVariable long id) {
        return configs.settings(id);
    }

    @PostMapping
    public OperationConfigService.Settings save(@PathVariable long id,
                                                @RequestBody SettingsEdit body) {
        configs.save(id, body.defaultSurveillantsPerRoom(), body.reserveMode(),
                body.reservePercentage(), body.reserveFixedCount(), body.maxConsecutiveDays(),
                body.consecutiveDaysStrength(), body.minGapMinutes(), body.ownSubjectStrength(),
                body.forbidOwnSubjectReserve(), body.solveSeconds());
        return configs.settings(id);
    }

    /**
     * A refusal is answered as a sentence the administrator can read, the same
     * way every other screen answers one. What arrives here is a message key;
     * anything else is a fault in the code rather than in what was typed, and
     * is passed through untranslated so it stays visible instead of silent.
     */
    /**
     * Service faults carry a message key, so the same refusal reads in French
     * or Arabic. Refusals that name particulars carry them too: without the
     * arguments, "« {0} » est arrêtée" reaches the screen with the braces still
     * in it.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> refused(IllegalArgumentException e) {
        String key = String.valueOf(e.getMessage());
        Object[] args = e instanceof RefusedException named ? named.args() : null;
        String sentence = messages.getMessage("error." + key, args, key,
                LocaleContextHolder.getLocale());
        return ResponseEntity.badRequest().body(Map.of("error", sentence, "code", key));
    }
}
