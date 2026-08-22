package ma.bacsurv.web.api;

import ma.bacsurv.web.service.CenterAdminService;
import ma.bacsurv.web.service.CenterView;
import ma.bacsurv.web.service.TeacherImportService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Setting a centre up, over HTTP: its name, its rooms, its sessions.
 *
 * <p>Faults come back as a sentence in the caller's language rather than a
 * code — the interface shows what the server says, so "the official minimum is
 * two surveillants per room" has to be written here and not invented there.
 */
@RestController
@RequestMapping("/api/centers")
public class CenterApiController {

    private final CenterAdminService admin;
    private final TeacherImportService teachers;
    private final MessageSource messages;

    public CenterApiController(CenterAdminService admin, TeacherImportService teachers,
                               MessageSource messages) {
        this.admin = admin;
        this.teachers = teachers;
        this.messages = messages;
    }

    public record NewCenter(String name) {}
    public record NewRooms(int count, String prefix) {}
    public record RoomEdit(String label, Integer surveillants) {}
    public record NewSession(String reference, String type, LocalDate startsOn, LocalDate endsOn) {}

    @GetMapping
    public List<CenterView> list() {
        return teachers.centers();
    }

    @GetMapping("/{id}")
    public CenterAdminService.CenterDetail detail(@PathVariable long id) {
        return admin.detail(id);
    }

    @PostMapping
    public ResponseEntity<CenterAdminService.CenterDetail> create(@RequestBody NewCenter body) {
        long id = admin.createCenter(body.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(admin.detail(id));
    }

    @PostMapping("/{id}/name")
    public CenterAdminService.CenterDetail rename(@PathVariable long id,
                                                  @RequestBody NewCenter body) {
        admin.renameCenter(id, body.name());
        return admin.detail(id);
    }

    @PostMapping("/{id}/rooms")
    public CenterAdminService.CenterDetail addRooms(@PathVariable long id,
                                                    @RequestBody NewRooms body) {
        // an administrator working in Arabic and naming nothing gets قاعة 1
        String naming = body.prefix() == null || body.prefix().isBlank()
                ? say("rooms.default.prefix") : body.prefix();
        admin.addRooms(id, body.count(), naming);
        return admin.detail(id);
    }

    @PostMapping("/{id}/rooms/{roomId}")
    public CenterAdminService.CenterDetail editRoom(@PathVariable long id,
                                                    @PathVariable long roomId,
                                                    @RequestBody RoomEdit body) {
        admin.renameRoom(roomId, body.label(), body.surveillants());
        return admin.detail(id);
    }

    @DeleteMapping("/{id}/rooms/{roomId}")
    public CenterAdminService.CenterDetail deleteRoom(@PathVariable long id,
                                                      @PathVariable long roomId) {
        admin.deleteRoom(roomId);
        return admin.detail(id);
    }

    @PostMapping("/{id}/sessions")
    public ResponseEntity<CenterAdminService.CenterDetail> createSession(
            @PathVariable long id, @RequestBody NewSession body) {
        admin.createSession(id, body.reference(), body.type(), body.startsOn(), body.endsOn());
        return ResponseEntity.status(HttpStatus.CREATED).body(admin.detail(id));
    }

    /**
     * Service faults carry a message key, so the same refusal reads in French
     * or Arabic. A key with no translation is passed through as it is rather
     * than swallowed — a missing sentence should be visible, not silent.
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

    private String say(String key) {
        return messages.getMessage(key, null, LocaleContextHolder.getLocale());
    }
}
