package ma.bacsurv.web;

import ma.bacsurv.application.DutyGenerator;
import ma.bacsurv.domain.DutyRole;
import ma.bacsurv.domain.ExamOperation;
import ma.bacsurv.domain.ExamSlot;
import ma.bacsurv.rules.ConstraintStrength;
import ma.bacsurv.web.persistence.OperationRepository;
import ma.bacsurv.web.service.OperationAssembler;
import ma.bacsurv.web.service.OperationConfigService;
import ma.bacsurv.web.service.OperationView;
import ma.bacsurv.web.service.SolveService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Configuration is only worth having if it changes the work. Each setting is
 * checked by what it does to the duties or the rules, not by reading it back.
 */
@SpringBootTest
class OperationConfigTest {

    @Autowired SolveService solveService;
    @Autowired OperationConfigService configs;
    @Autowired ma.bacsurv.web.service.CenterAdminService centers;
    @Autowired OperationAssembler assembler;
    @Autowired OperationRepository operations;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    /** Assembling an operation touches lazy collections, so it needs a transaction. */
    private <T> T inTransaction(java.util.function.Supplier<T> work) {
        return new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .execute(status -> work.get());
    }

    private OperationView imported(String centre) throws Exception {
        String sample = Files.readString(Path.of("samples", "operation-sample.json"))
                .replace("\"operation\": { \"id\": \"NAT-2026-JUIN\"",
                        "\"center\": { \"name\": \"" + centre + "\" },\n"
                                + "  \"operation\": { \"id\": \"OP-CFG-" + System.nanoTime() + "\"");
        return solveService.upload("cfg.json", sample);
    }

    private ExamOperation domain(long operationId) {
        return inTransaction(() ->
                assembler.toDomain(operations.findWithCenter(operationId).orElseThrow()));
    }

    private static ExamSlot slot(ExamOperation operation, String reference) {
        return operation.slots().stream()
                .filter(s -> s.id().equals(reference)).findFirst().orElseThrow();
    }

    /** Duties as the operation would really be staffed, policy included. */
    private long count(long operationId, DutyRole role) {
        return inTransaction(() -> {
            var entity = operations.findWithCenter(operationId).orElseThrow();
            return new DutyGenerator()
                    .generate(assembler.toDomain(entity), assembler.staffingOf(entity)).stream()
                    .filter(duty -> duty.role() == role).count();
        });
    }

    @Test
    void defaultsFollowTheOfficialRules() throws Exception {
        var operation = imported("Centre Cfg Defaults");
        var settings = configs.settings(operation.id());

        assertEquals(2, settings.defaultSurveillantsPerRoom());
        assertEquals("PERCENTAGE", settings.reserveMode());
        assertEquals(0.10, settings.reservePercentage(), 0.0001);
        assertEquals(3, settings.maxConsecutiveDays());
        assertEquals("SOFT", settings.consecutiveDaysStrength(), "unreachable as a hard rule");
        assertEquals(0, settings.minGapMinutes(), "no rest is imposed unless asked for");
        assertEquals("HARD", settings.ownSubjectStrength());
    }

    @Test
    void aFixedReserveCountReplacesThePercentage() throws Exception {
        var operation = imported("Centre Cfg Reserve");
        // 10% of 8 surveillance duties rounds up to 1
        assertEquals(1, slot(domain(operation.id()), "S1").reserveRequirement());

        configs.save(operation.id(), 2, "FIXED_COUNT", 0.10, 3,
                3, "SOFT", 0, "HARD", false, 30);

        assertEquals(3, slot(domain(operation.id()), "S1").reserveRequirement());
        assertEquals(2, slot(domain(operation.id()), "S4").reserveRequirement(),
                "a count stated in the file still wins");
    }

    @Test
    void raisingTheDefaultAddsSurveillantsEverywhere() throws Exception {
        var operation = imported("Centre Cfg Surveillants");
        long before = count(operation.id(), DutyRole.SURVEILLANCE);

        configs.save(operation.id(), 3, "PERCENTAGE", 0.10, 0,
                3, "SOFT", 0, "HARD", false, 30);

        // 16 room-sittings across the operation, one more surveillant each
        assertEquals(before + 16, count(operation.id(), DutyRole.SURVEILLANCE));
    }

    @Test
    void oneLargeRoomCanTakeMoreThanTheOthers() throws Exception {
        var operation = imported("Centre Cfg Room");
        long before = count(operation.id(), DutyRole.SURVEILLANCE);

        var room = configs.settings(operation.id()).rooms().stream()
                .filter(r -> r.reference().equals("R1")).findFirst().orElseThrow();
        // a room's own figure belongs to the room, so it is written where the
        // room is edited — there is no second door through the session's rules
        centers.renameRoom(room.id(), room.label(), 4);

        // R1 is used in all four slots: two extra surveillants each time
        assertEquals(before + 8, count(operation.id(), DutyRole.SURVEILLANCE));
    }

    @Test
    void aRoomMayNeverGoBelowTheOfficialMinimum() throws Exception {
        var operation = imported("Centre Cfg Minimum");
        var room = configs.settings(operation.id()).rooms().getFirst();

        assertEquals("room.surveillants.tooFew",
                assertThrows(IllegalArgumentException.class,
                        () -> centers.renameRoom(room.id(), room.label(), 1)).getMessage());
        // and the session's own default is refused as a key too, so the screen
        // answers in the administrator's language rather than the domain's
        assertEquals("settings.surveillants.tooFew",
                assertThrows(IllegalArgumentException.class,
                        () -> configs.save(operation.id(), 1, "PERCENTAGE", 0.10, 0,
                                3, "SOFT", 0, "HARD", false, 30)).getMessage());
    }

    @Test
    void anAcademyCanPromoteTheConsecutiveDaysLimitToARule() throws Exception {
        var operation = imported("Centre Cfg Consecutive");
        configs.save(operation.id(), 2, "PERCENTAGE", 0.10, 0,
                5, "HARD", 30, "HARD", false, 45);

        var policy = inTransaction(() -> assembler.schedulingOf(
                operations.findWithCenter(operation.id()).orElseThrow()));

        assertEquals(5, policy.maxConsecutiveWorkingDays());
        assertEquals(ConstraintStrength.HARD, policy.consecutiveDaysStrength());
        assertTrue(policy.consecutiveDaysIsHard());
        assertEquals(30, policy.minimumGapBetweenDutiesMinutes());
        assertTrue(policy.enforcesGap());

        assertEquals(45, inTransaction(() -> assembler.solverSettingsOf(
                operations.findWithCenter(operation.id()).orElseThrow())).timeLimitSeconds());
    }
}
