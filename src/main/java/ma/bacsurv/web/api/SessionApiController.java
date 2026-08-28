package ma.bacsurv.web.api;

import ma.bacsurv.web.service.CenterAdminService;
import ma.bacsurv.web.service.RefusedException;
import ma.bacsurv.web.service.SessionAdminService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The life of a session: what removing it would cost, settling it, reopening
 * it, and removing it.
 *
 * <p>The impact endpoint exists so a confirmation can say what is actually at
 * stake rather than "êtes-vous sûr ?". The screen asking the question does not
 * know how many teachers a distribution touched; the server does.
 */
@RestController
@RequestMapping("/api/sessions/{id}")
public class SessionApiController {

    private final SessionAdminService sessions;
    private final CenterAdminService centers;
    private final MessageSource messages;

    public SessionApiController(SessionAdminService sessions, CenterAdminService centers,
                                MessageSource messages) {
        this.sessions = sessions;
        this.centers = centers;
        this.messages = messages;
    }

    /** What deleting or reopening this session would cost. */
    @GetMapping("/impact")
    public SessionAdminService.Impact impact(@PathVariable long id) {
        return sessions.impact(id);
    }

    @PostMapping("/settle")
    public SessionAdminService.Impact settle(@PathVariable long id) {
        sessions.settle(id);
        return sessions.impact(id);
    }

    @PostMapping("/reopen")
    public SessionAdminService.Impact reopen(@PathVariable long id) {
        sessions.reopen(id);
        return sessions.impact(id);
    }

    /**
     * Removes the session. The centre comes back rather than nothing, because
     * the screen that asked has just lost the row it was showing and needs the
     * list it belongs to.
     */
    @DeleteMapping
    public CenterAdminService.CenterDetail delete(@PathVariable long id) {
        return centers.detail(sessions.delete(id));
    }

    /**
     * Service faults carry a message key, so the same refusal reads in French
     * or Arabic. A key with no translation is passed through as it is rather
     * than swallowed — a missing sentence should be visible, not silent.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> refused(IllegalArgumentException refusal) {
        String key = refusal.getMessage();
        Object[] args = refusal instanceof RefusedException named ? named.args() : null;
        String sentence = messages.getMessage("error." + key, args, key,
                LocaleContextHolder.getLocale());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", sentence, "code", key));
    }
}
