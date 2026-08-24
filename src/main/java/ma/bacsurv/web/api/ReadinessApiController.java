package ma.bacsurv.web.api;

import ma.bacsurv.web.service.ReadinessService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** What is left to do before a session can be distributed. */
@RestController
@RequestMapping("/api/sessions/{id}")
public class ReadinessApiController {

    private final ReadinessService readiness;
    private final MessageSource messages;

    public ReadinessApiController(ReadinessService readiness, MessageSource messages) {
        this.readiness = readiness;
        this.messages = messages;
    }

    @GetMapping("/readiness")
    public ReadinessService.Readiness readiness(@PathVariable long id) {
        return readiness.of(id);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> refused(IllegalArgumentException e) {
        String key = String.valueOf(e.getMessage());
        String sentence;
        try {
            sentence = messages.getMessage("error." + key, null, LocaleContextHolder.getLocale());
        } catch (RuntimeException untranslated) {
            sentence = key;
        }
        return ResponseEntity.badRequest().body(Map.of("error", sentence, "code", key));
    }
}
