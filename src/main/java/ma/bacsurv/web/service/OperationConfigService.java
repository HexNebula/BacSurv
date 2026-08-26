package ma.bacsurv.web.service;

import ma.bacsurv.rules.ReserveRequirement;
import ma.bacsurv.web.persistence.OperationConfigEntity;
import ma.bacsurv.web.persistence.OperationConfigRepository;
import ma.bacsurv.web.persistence.OperationEntity;
import ma.bacsurv.web.persistence.OperationRepository;
import ma.bacsurv.web.persistence.RoomEntity;
import ma.bacsurv.web.persistence.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reads and writes what a centre configured for an operation.
 *
 * The three groups stay distinct on purpose: staffing rules say how many
 * people the work needs, scheduling policy says what the centre prefers, and
 * the solver setting is a technical knob that changes no exam rule at all.
 */
@Service
public class OperationConfigService {

    /** One room and how many surveillants it takes, when it differs. */
    public record RoomStaffing(Long id, String reference, String label, Integer surveillants) {}

    public record Settings(Long operationId, String operationReference, String centerName,
                           int defaultSurveillantsPerRoom, int minimumSurveillantsPerRoom,
                           String reserveMode, double reservePercentage, int reserveFixedCount,
                           int maxConsecutiveDays, String consecutiveDaysStrength,
                           int minGapMinutes, String ownSubjectStrength,
                           boolean forbidOwnSubjectReserve, int solveSeconds,
                           List<RoomStaffing> rooms) {}

    private final OperationRepository operations;
    private final OperationConfigRepository configs;
    private final RoomRepository rooms;

    public OperationConfigService(OperationRepository operations,
                                  OperationConfigRepository configs, RoomRepository rooms) {
        this.operations = operations;
        this.configs = configs;
        this.rooms = rooms;
    }

    @Transactional(readOnly = true)
    public Settings settings(long operationId) {
        OperationEntity operation = operation(operationId);
        OperationConfigEntity config = configs.findById(operationId)
                .orElseGet(() -> new OperationConfigEntity(operationId));

        List<RoomStaffing> roomStaffing = rooms
                .findByCenterIdOrderByReferenceAsc(operation.getCenter().getId()).stream()
                .map(room -> new RoomStaffing(room.getId(), room.getReference(),
                        room.getLabel(), room.getSurveillantsOverride()))
                .toList();

        return new Settings(operationId, operation.getReference(),
                operation.getCenter().getName(),
                config.getDefaultSurveillantsPerRoom(),
                ma.bacsurv.rules.StaffingPolicy.MINIMUM_SURVEILLANTS_PER_ROOM,
                config.getReserveMode(), config.getReservePercentage(),
                config.getReserveFixedCount(), config.getMaxConsecutiveDays(),
                config.getConsecutiveDaysStrength(), config.getMinGapMinutes(),
                config.getOwnSubjectStrength(), config.isForbidOwnSubjectReserve(),
                config.getSolveSeconds(), roomStaffing);
    }

    @Transactional
    public void save(long operationId, int defaultSurveillantsPerRoom, String reserveMode,
                     double reservePercentage, int reserveFixedCount, int maxConsecutiveDays,
                     String consecutiveDaysStrength, int minGapMinutes, String ownSubjectStrength,
                     boolean forbidOwnSubjectReserve, int solveSeconds) {
        OperationEntity target = operation(operationId); // fails fast when unknown
        check(defaultSurveillantsPerRoom, reserveMode, reservePercentage, reserveFixedCount,
                maxConsecutiveDays, minGapMinutes);
        OperationConfigEntity config = configs.findById(operationId)
                .orElseGet(() -> new OperationConfigEntity(operationId));
        config.apply(defaultSurveillantsPerRoom, reserveMode, reservePercentage, reserveFixedCount,
                maxConsecutiveDays, consecutiveDaysStrength, minGapMinutes, ownSubjectStrength,
                forbidOwnSubjectReserve, solveSeconds);
        configs.save(config);
        // the rules decide how many people each hour takes: a distribution
        // solved under the old ones is no longer the answer
        target.touch();
    }

    /**
     * The same limits the rule records enforce, refused here first.
     *
     * <p>The records guard the domain and say so in English — right for a
     * programming mistake, wrong for a number somebody typed into a form. What
     * an administrator can actually reach from the screen is checked here, as
     * message keys, so the answer arrives in their language; the records stay
     * as the last line behind it.
     */
    private void check(int defaultSurveillantsPerRoom, String reserveMode,
                       double reservePercentage, int reserveFixedCount,
                       int maxConsecutiveDays, int minGapMinutes) {
        if (defaultSurveillantsPerRoom < ma.bacsurv.rules.StaffingPolicy.MINIMUM_SURVEILLANTS_PER_ROOM)
            throw new IllegalArgumentException("settings.surveillants.tooFew");
        if ("PERCENTAGE".equals(reserveMode) && (reservePercentage < 0 || reservePercentage > 1))
            throw new IllegalArgumentException("settings.reserve.percentage");
        if ("FIXED_COUNT".equals(reserveMode) && reserveFixedCount < 0)
            throw new IllegalArgumentException("settings.reserve.count");
        if (maxConsecutiveDays < 1)
            throw new IllegalArgumentException("settings.consecutive.tooFew");
        if (minGapMinutes < 0)
            throw new IllegalArgumentException("settings.gap.negative");
    }

    /*
     * A room's own number of surveillants used to be settable here too, under
     * an operation's URL. It never belonged: the figure is stored on the room,
     * which belongs to the centre, so setting it "for this session" quietly
     * changed every session of that centre — the ones already distributed
     * included. It also duplicated the floor check, in another language, and
     * the two copies had already drifted apart. CenterAdminService.renameRoom
     * is now the only thing that writes it.
     */

    private OperationEntity operation(long operationId) {
        return operations.findWithCenter(operationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "no operation with id " + operationId));
    }
}
