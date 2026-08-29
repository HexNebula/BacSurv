package ma.bacsurv.web;

import ma.bacsurv.web.persistence.OperationRepository;
import ma.bacsurv.web.service.CenterAdminService;
import ma.bacsurv.web.service.RefusedException;
import ma.bacsurv.web.service.SessionAdminService;
import ma.bacsurv.web.service.TimetableService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A session could be created and destroyed but never corrected.
 *
 * <p>Everything else a centre owns can be: its name, a room, a subject, a
 * filière, a teacher. A session had settle, reopen and delete — so a mistyped
 * name meant deleting it and entering the whole timetable again, and for a
 * settled one it could not be done at all. Reopening was documented as the way
 * out of a wrong reference, and it never was: nothing downstream of it could
 * edit one.
 *
 * <p>The name and the dates are corrected differently on purpose. A name is a
 * label. Dates bound the planning grid and decide which school year the session
 * counts inside.
 */
@SpringBootTest
class SessionEditTest {

    @Autowired CenterAdminService centers;
    @Autowired SessionAdminService sessions;
    @Autowired TimetableService timetable;
    @Autowired OperationRepository operations;

    private static final LocalDate JUNE = LocalDate.of(2027, 6, 10);

    private long centre() {
        return centers.createCenter("ثانوية التصحيح " + System.nanoTime());
    }

    private long session(long centre, LocalDate from, LocalDate to) {
        return centers.createSession(centre, "Nationale 2026", "NATIONAL_2BAC", from, to);
    }

    @Test
    void aMistypedNameIsCorrected() {
        long centre = centre();
        long session = session(centre, JUNE, JUNE.plusDays(3));

        sessions.edit(session, "Nationale 2027", JUNE, JUNE.plusDays(3));

        assertEquals("Nationale 2027",
                operations.findById(session).orElseThrow().getReference());
    }

    /** The centre comes back, because the list showing the row is what redraws. */
    @Test
    void editingAnswersWithTheCentre() {
        long centre = centre();
        long session = session(centre, JUNE, JUNE.plusDays(3));

        assertEquals(centre, sessions.edit(session, "Nationale 2027", JUNE, JUNE.plusDays(3)));
    }

    @Test
    void theDatesMoveWhileTheSessionIsADraft() {
        long centre = centre();
        long session = session(centre, JUNE, JUNE.plusDays(3));

        sessions.edit(session, "Nationale 2027", JUNE.plusDays(2), JUNE.plusDays(5));

        var stored = operations.findById(session).orElseThrow();
        assertEquals(JUNE.plusDays(2), stored.getStartsOn());
        assertEquals(JUNE.plusDays(5), stored.getEndsOn());
    }

    /**
     * A name is printed, not computed from. Correcting one must not tell the
     * session its distribution is out of date — a settled session that became
     * stale could never be settled again without re-solving, which would hand
     * out a different répartition from the one already distributed.
     */
    @Test
    void correctingOnlyTheNameDoesNotMakeTheDistributionStale() {
        long centre = centre();
        long session = session(centre, JUNE, JUNE.plusDays(3));
        Instant before = operations.findById(session).orElseThrow().getChangedAt();

        sessions.edit(session, "Nationale 2027", JUNE, JUNE.plusDays(3));

        assertEquals(before, operations.findById(session).orElseThrow().getChangedAt());
    }

    @Test
    void movingTheDatesDoesMakeItStale() {
        long centre = centre();
        long session = session(centre, JUNE, JUNE.plusDays(3));
        Instant before = operations.findById(session).orElseThrow().getChangedAt();

        sessions.edit(session, "Nationale 2026", JUNE.plusDays(1), JUNE.plusDays(4));

        assertTrue(operations.findById(session).orElseThrow().getChangedAt().isAfter(before),
                "the grid the distribution was computed over has moved");
    }

    /**
     * The whole point of allowing this on a settled session: the wrong name is
     * on every convocation that went out, and that is when it most needs fixing.
     */
    @Test
    void aSettledSessionCanStillBeRenamed() {
        long centre = centre();
        long session = session(centre, JUNE, JUNE.plusDays(3));
        settle(session);

        sessions.edit(session, "Nationale 2027", JUNE, JUNE.plusDays(3));

        var stored = operations.findById(session).orElseThrow();
        assertEquals("Nationale 2027", stored.getReference());
        assertTrue(stored.isSettled(), "renaming is not a reason to unsettle it");
    }

    @Test
    void aSettledSessionRefusesToMove() {
        long centre = centre();
        long session = session(centre, JUNE, JUNE.plusDays(3));
        settle(session);

        var refusal = assertThrows(RefusedException.class,
                () -> sessions.edit(session, "Nationale 2026", JUNE.plusDays(1),
                        JUNE.plusDays(4)));
        assertEquals("session.dates.settled", refusal.getMessage());
    }

    /**
     * An épreuve outside the new range would disappear from the only grid it is
     * drawn on — neither visible nor removable. The refusal counts them and
     * names the first, so the administrator knows where to look.
     */
    @Test
    void datesThatWouldStrandAnEpreuveAreRefused() {
        long centre = centre();
        long session = session(centre, JUNE, JUNE.plusDays(3));
        centers.addRooms(centre, 2, "قاعة");
        List<Long> rooms = centers.detail(centre).rooms().stream()
                .map(CenterAdminService.RoomView::id).toList();
        long stream = timetable.addStream(session, "العلوم الرياضية", rooms);
        timetable.setExam(session, stream, "الرياضيات",
                JUNE.plusDays(3), LocalTime.of(8, 0), LocalTime.of(11, 0));

        var refusal = assertThrows(RefusedException.class,
                () -> sessions.edit(session, "Nationale 2026", JUNE, JUNE.plusDays(1)));
        assertEquals("session.dates.outsideSlots", refusal.getMessage());
        assertEquals(JUNE.plusDays(3).toString(), refusal.args()[1]);
    }

    /**
     * Fairness is counted inside a school year. Sliding a session into another
     * one would move duties between two queues at once, and no administrator
     * would recognise that as what he asked for.
     */
    @Test
    void datesThatWouldChangeTheSchoolYearAreRefused() {
        long centre = centre();
        long session = session(centre, JUNE, JUNE.plusDays(3));

        var refusal = assertThrows(RefusedException.class,
                () -> sessions.edit(session, "Nationale 2026",
                        LocalDate.of(2027, 10, 5), LocalDate.of(2027, 10, 8)));
        assertEquals("session.dates.otherYear", refusal.getMessage());
        assertEquals("2026-2027", refusal.args()[0]);
        assertEquals("2027-2028", refusal.args()[1]);
    }

    @Test
    void anEmptyNameIsRefused() {
        long centre = centre();
        long session = session(centre, JUNE, JUNE.plusDays(3));

        assertEquals("session.reference.required", assertThrows(IllegalArgumentException.class,
                () -> sessions.edit(session, "  ", JUNE, JUNE.plusDays(3))).getMessage());
    }

    @Test
    void reversedDatesAreRefused() {
        long centre = centre();
        long session = session(centre, JUNE, JUNE.plusDays(3));

        assertEquals("session.dates.reversed", assertThrows(IllegalArgumentException.class,
                () -> sessions.edit(session, "Nationale 2026", JUNE.plusDays(3), JUNE))
                .getMessage());
    }

    /** Settling needs a finished, clean solve; these tests only need the state. */
    private void settle(long sessionId) {
        var operation = operations.findById(sessionId).orElseThrow();
        operation.settle();
        operations.saveAndFlush(operation);
    }
}
