package ma.bacsurv.web.api;

import ma.bacsurv.web.service.ArchiveService;
import ma.bacsurv.web.service.RefusedException;
import ma.bacsurv.web.service.SchoolYearService;
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
 * The school years of a centre, and the pool of each.
 *
 * <p>Opening a year carries the previous year's list into it, so September is
 * taking three people out and putting two in rather than retyping forty-five.
 */
@RestController
@RequestMapping("/api/centers/{id}/years")
public class SchoolYearApiController {

    private final SchoolYearService years;
    private final ArchiveService archive;
    private final MessageSource messages;

    public SchoolYearApiController(SchoolYearService years, ArchiveService archive,
                                   MessageSource messages) {
        this.years = years;
        this.archive = archive;
        this.messages = messages;
    }

    public record NewYear(String label) {}

    @GetMapping
    public List<SchoolYearService.YearView> years(@PathVariable long id) {
        return years.yearsOf(id);
    }

    @PostMapping
    public ResponseEntity<List<SchoolYearService.YearView>> open(@PathVariable long id,
                                                                 @RequestBody NewYear body) {
        years.open(id, body.label());
        return ResponseEntity.status(HttpStatus.CREATED).body(years.yearsOf(id));
    }

    /**
     * The pool of one year, and the people of the centre who are not in it.
     *
     * <p>Both halves at once, because September needs both: who is carried over
     * and can be taken out, and who could be put back — including somebody who
     * left two years ago and has returned.
     */
    @GetMapping("/{yearId}/teachers")
    public ArchiveService.YearPool pool(@PathVariable long id, @PathVariable long yearId) {
        return archive.poolOf(yearId);
    }

    /**
     * The record of a year: its sessions, and what each teacher did over it.
     *
     * <p>Counted from settled sessions only. Each settled session carries the
     * id of the solve it went out with, so the room-by-room detail comes from
     * {@code GET /jobs/{jobId}/schedule} rather than being copied in here.
     */
    @GetMapping("/{yearId}/archive")
    public ArchiveService.Archive archive(@PathVariable long id, @PathVariable long yearId) {
        return archive.of(yearId);
    }

    /** He has arrived, or he is back after a year elsewhere. */
    @PostMapping("/{yearId}/teachers/{matricule}")
    public List<SchoolYearService.YearView> add(@PathVariable long id, @PathVariable long yearId,
                                                @PathVariable String matricule) {
        years.addToYear(yearId, matricule);
        return years.yearsOf(id);
    }

    /**
     * He has left the establishment. Not a deletion: everything he did in the
     * years he was here stays exactly where it is.
     */
    @DeleteMapping("/{yearId}/teachers/{matricule}")
    public List<SchoolYearService.YearView> remove(@PathVariable long id, @PathVariable long yearId,
                                                   @PathVariable String matricule) {
        years.removeFromYear(yearId, matricule);
        return years.yearsOf(id);
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
