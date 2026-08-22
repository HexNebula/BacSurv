package ma.bacsurv.web.api;

import ma.bacsurv.web.service.TeacherImportService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    public TeacherApiController(TeacherImportService teachers) {
        this.teachers = teachers;
    }

    /** The file's text, carried from the preview into the confirmation. */
    public record Spreadsheet(String csv) {}

    @GetMapping
    public List<TeacherImportService.Change> pool(@PathVariable long id) {
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> refused(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
    }
}
