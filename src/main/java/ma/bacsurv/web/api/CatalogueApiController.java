package ma.bacsurv.web.api;

import ma.bacsurv.web.service.CatalogueService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import ma.bacsurv.web.service.RefusedException;
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

    /**
     * A subject sends a name; a filière sends the level it belongs to too.
     *
     * <p>{@code nameFr} is the French label. It is a {@code String} that may be
     * absent, and absent is not the same as blank: a screen that edits only the
     * Arabic name omits the field and the label it never showed is left alone,
     * while one that sends it empty is clearing it on purpose. That is what
     * {@code relabel} carries into the service.
     */
    public record NewEntry(String name, String nameFr, String level) {

        boolean sendsLabel() {
            return nameFr != null;
        }
    }

    @GetMapping("/subjects")
    public List<CatalogueService.Entry> subjects(@PathVariable long id) {
        return catalogue.subjectsOf(id);
    }

    @PostMapping("/subjects")
    public ResponseEntity<List<CatalogueService.Entry>> addSubject(@PathVariable long id,
                                                                   @RequestBody NewEntry body) {
        catalogue.addSubject(id, body.name(), body.nameFr());
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogue.subjectsOf(id));
    }

    @PostMapping("/subjects/{subjectId}")
    public List<CatalogueService.Entry> renameSubject(@PathVariable long id,
                                                      @PathVariable long subjectId,
                                                      @RequestBody NewEntry body) {
        catalogue.renameSubject(subjectId, body.name(), body.nameFr(), body.sendsLabel());
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
        catalogue.addStream(id, body.name(), body.nameFr(), body.level());
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogue.streamsOf(id));
    }

    @PostMapping("/streams/{streamId}")
    public List<CatalogueService.Entry> renameStream(@PathVariable long id,
                                                     @PathVariable long streamId,
                                                     @RequestBody NewEntry body) {
        catalogue.renameStream(streamId, body.name(), body.nameFr(), body.level(),
                body.sendsLabel());
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
        // a refusal that names particulars carries them: without the arguments
        // "« {0} » est arrêtée" reaches the screen with the braces still in it
        Object[] args = e instanceof RefusedException named ? named.args() : null;
        String sentence = messages.getMessage("error." + key, args, key,
                LocaleContextHolder.getLocale());
        return ResponseEntity.badRequest().body(Map.of("error", sentence, "code", key));
    }
}
