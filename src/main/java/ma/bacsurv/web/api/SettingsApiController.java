package ma.bacsurv.web.api;

import ma.bacsurv.web.service.OperationConfigService;
import org.springframework.http.ResponseEntity;
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

    public SettingsApiController(OperationConfigService configs) {
        this.configs = configs;
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

    /** Null clears the override, putting the room back on the session default. */
    public record RoomStaffingEdit(Integer surveillants) {}

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

    @PostMapping("/rooms/{roomId}")
    public OperationConfigService.Settings room(@PathVariable long id, @PathVariable long roomId,
                                                @RequestBody RoomStaffingEdit body) {
        configs.setRoomStaffing(roomId, body.surveillants());
        return configs.settings(id);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> refused(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
    }
}
