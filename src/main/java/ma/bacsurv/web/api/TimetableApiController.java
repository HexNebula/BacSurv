package ma.bacsurv.web.api;

import ma.bacsurv.web.service.TimetableService;
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
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * The timetable of one session: its filières, and the épreuves they sit.
 *
 * <p>Every call answers with the whole grid rather than with what it changed,
 * because the grid is what the screen draws and a timetable is small — a few
 * days by a few filières. One answer, one redraw, no chance of the two
 * drifting apart.
 */
@RestController
@RequestMapping("/api/sessions/{id}/timetable")
public class TimetableApiController {

    private final TimetableService timetable;
    private final MessageSource messages;

    public TimetableApiController(TimetableService timetable, MessageSource messages) {
        this.timetable = timetable;
        this.messages = messages;
    }

    public record NewStream(String name, List<Long> roomIds) {}
    public record NewExam(Long streamId, String subject,
                          LocalDate date, LocalTime startTime, LocalTime endTime) {}
    public record Copy(Long fromStreamId, Long toStreamId) {}

    @GetMapping
    public TimetableService.Timetable grid(@PathVariable long id) {
        return timetable.timetable(id);
    }

    @PostMapping("/streams")
    public ResponseEntity<TimetableService.Timetable> addStream(@PathVariable long id,
                                                                @RequestBody NewStream body) {
        timetable.addStream(id, body.name(), body.roomIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(timetable.timetable(id));
    }

    @PostMapping("/streams/{streamId}")
    public TimetableService.Timetable editStream(@PathVariable long id,
                                                 @PathVariable long streamId,
                                                 @RequestBody NewStream body) {
        timetable.editStream(streamId, body.name(), body.roomIds());
        return timetable.timetable(id);
    }

    @DeleteMapping("/streams/{streamId}")
    public TimetableService.Timetable removeStream(@PathVariable long id,
                                                   @PathVariable long streamId) {
        timetable.removeStream(streamId);
        return timetable.timetable(id);
    }

    @PostMapping("/exams")
    public TimetableService.Timetable setExam(@PathVariable long id, @RequestBody NewExam body) {
        timetable.setExam(id, body.streamId(), body.subject(),
                body.date(), body.startTime(), body.endTime());
        return timetable.timetable(id);
    }

    @DeleteMapping("/exams/{examId}")
    public TimetableService.Timetable removeExam(@PathVariable long id,
                                                 @PathVariable long examId) {
        timetable.removeExam(id, examId);
        return timetable.timetable(id);
    }

    @PostMapping("/copy")
    public TimetableService.Timetable copy(@PathVariable long id, @RequestBody Copy body) {
        timetable.copyStream(id, body.fromStreamId(), body.toStreamId());
        return timetable.timetable(id);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> refused(IllegalArgumentException e) {
        String key = String.valueOf(e.getMessage());
        // a refusal with particulars — which rooms, held by which filière —
        // carries them, and the bundle writes them into the sentence
        Object[] args = e instanceof ma.bacsurv.web.service.RefusedException refused
                ? refused.args() : null;
        String sentence;
        try {
            sentence = messages.getMessage("error." + key, args, LocaleContextHolder.getLocale());
        } catch (RuntimeException untranslated) {
            sentence = key;
        }
        return ResponseEntity.badRequest().body(Map.of("error", sentence, "code", key));
    }
}
