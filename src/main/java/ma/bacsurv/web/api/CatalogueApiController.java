package ma.bacsurv.web.api;

import ma.bacsurv.web.service.CatalogueService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * A centre's own lists: the subjects it examines, the filières it runs.
 *
 * <p>Each call answers with the whole list, counts included, because that is
 * what the screen draws and both lists are short.
 */
@RestController
@RequestMapping("/api/centers/{id}")
public class CatalogueApiController {

    private final CatalogueService catalogue;
    private final MessageSource messages;

    public CatalogueApiController(CatalogueService catalogue, MessageSource messages) {
        this.catalogue = catalogue;
        this.messages = messages;
    }

    public record NewEntry(String name) {}

    @GetMapping("/subjects")
    public List<CatalogueService.Entry> subjects(@PathVariable long id) {
        return catalogue.subjectsOf(id);
    }

    @PostMapping("/subjects")
    public ResponseEntity<List<CatalogueService.Entry>> addSubject(@PathVariable long id,
                                                                   @RequestBody NewEntry body) {
        catalogue.addSubject(id, body.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogue.subjectsOf(id));
    }

    @PostMapping("/subjects/{subjectId}")
    public List<CatalogueService.Entry> renameSubject(@PathVariable long id,
                                                      @PathVariable long subjectId,
                                                      @RequestBody NewEntry body) {
        catalogue.renameSubject(subjectId, body.name());
        return catalogue.subjectsOf(id);
    }

    @DeleteMapping("/subjects/{subjectId}")
    public List<CatalogueService.Entry> removeSubject(@PathVariable long id,
                                                      @PathVariable long subjectId) {
        catalogue.removeSubject(subjectId);
        return catalogue.subjectsOf(id);
    }

    @GetMapping("/streams")
    public List<CatalogueService.Entry> streams(@PathVariable long id) {
        return catalogue.streamsOf(id);
    }

    @PostMapping("/streams")
    public ResponseEntity<List<CatalogueService.Entry>> addStream(@PathVariable long id,
                                                                  @RequestBody NewEntry body) {
        catalogue.addStream(id, body.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogue.streamsOf(id));
    }

    @PostMapping("/streams/{streamId}")
    public List<CatalogueService.Entry> renameStream(@PathVariable long id,
                                                     @PathVariable long streamId,
                                                     @RequestBody NewEntry body) {
        catalogue.renameStream(streamId, body.name());
        return catalogue.streamsOf(id);
    }

    @DeleteMapping("/streams/{streamId}")
    public List<CatalogueService.Entry> removeStream(@PathVariable long id,
                                                     @PathVariable long streamId) {
        catalogue.removeStream(streamId);
        return catalogue.streamsOf(id);
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
