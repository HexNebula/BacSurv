package ma.bacsurv.web.api;

import ma.bacsurv.web.service.TeacherAdminService;
import ma.bacsurv.web.service.TeacherImportService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * The teacher pool of a centre, and the spreadsheet it comes from.
 *
 * <p>Two steps on purpose. The first reads the file and answers what it would
 * change — added, modified, already correct, and the rows that could not be
 * read with their line numbers. Nothing is written until the second call, so
 * an administrator can look at a bad row before it becomes a bad record.
 */
@RestController
@RequestMapping("/api/centers/{id}/teachers")
public class TeacherApiController {

    private final TeacherImportService teachers;
    private final TeacherAdminService admin;
    private final MessageSource messages;

    public TeacherApiController(TeacherImportService teachers, TeacherAdminService admin,
                                MessageSource messages) {
        this.teachers = teachers;
        this.admin = admin;
        this.messages = messages;
    }

    /** The file's text, carried from the preview into the confirmation. */
    public record Spreadsheet(String csv) {}

    @GetMapping
    public List<TeacherImportService.Change> pool(@PathVariable long id) {
        return teachers.pool(id);
    }

    /**
     * One teacher, added by hand. {@code /preview} and {@code /apply} are
     * literal segments, so they are matched before the matricule below —
     * a pool would have to contain a teacher numbered "preview" for the two
     * to collide.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<TeacherImportService.Change>> add(
            @PathVariable long id, @RequestBody TeacherAdminService.Details body) {
        admin.add(id, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(teachers.pool(id));
    }

    /** The days a teacher is known to be away, told in advance. */
    @GetMapping("/{matricule}/absences")
    public List<TeacherAdminService.Absence> absences(@PathVariable long id,
                                                      @PathVariable String matricule) {
        return admin.absencesOf(id, matricule);
    }

    /**
     * Replaces the whole list. The screen sends what it holds rather than
     * additions and removals, so nothing depends on it having kept track of
     * which row was which.
     */
    @PostMapping("/{matricule}/absences")
    public List<TeacherAdminService.Absence> replaceAbsences(
            @PathVariable long id, @PathVariable String matricule,
            @RequestBody List<TeacherAdminService.Absence> body) {
        admin.replaceAbsences(id, matricule, body);
        return admin.absencesOf(id, matricule);
    }

    @PostMapping(value = "/{matricule}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public List<TeacherImportService.Change> edit(@PathVariable long id,
                                                  @PathVariable String matricule,
                                                  @RequestBody TeacherAdminService.Details body) {
        admin.edit(id, matricule, body);
        return teachers.pool(id);
    }

    @DeleteMapping("/{matricule}")
    public List<TeacherImportService.Change> remove(@PathVariable long id,
                                                    @PathVariable String matricule) {
        admin.remove(id, matricule);
        return teachers.pool(id);
    }

    /** Step one: read and report. Nothing is written. */
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TeacherImportService.Preview previewFile(@PathVariable long id,
                                                    @RequestParam("file") MultipartFile file)
            throws IOException {
        return teachers.preview(id, text(file));
    }

    @PostMapping(value = "/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    public TeacherImportService.Preview preview(@PathVariable long id,
                                                @RequestBody Spreadsheet body) {
        return teachers.preview(id, body.csv());
    }

    /** Step two: the administrator has seen the changes and accepts them. */
    @PostMapping(value = "/apply", consumes = MediaType.APPLICATION_JSON_VALUE)
    public TeacherImportService.Preview apply(@PathVariable long id,
                                              @RequestBody Spreadsheet body) {
        return teachers.apply(id, body.csv());
    }

    private static String text(MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new IllegalArgumentException("teachers.file.empty");
        return new String(file.getBytes(), StandardCharsets.UTF_8);
    }

    /**
     * The same contract the rest of the API uses: a sentence in the caller's
     * language plus the key behind it, so the interface can show what the
     * server says instead of inventing its own wording.
     */
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
