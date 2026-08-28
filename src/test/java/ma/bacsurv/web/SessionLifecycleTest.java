package ma.bacsurv.web;

import ma.bacsurv.web.persistence.AssignmentRepository;
import ma.bacsurv.web.persistence.OperationRepository;
import ma.bacsurv.web.persistence.SolveJob;
import ma.bacsurv.web.persistence.SolveJobRepository;
import ma.bacsurv.web.persistence.StreamRepository;
import ma.bacsurv.web.service.CenterAdminService;
import ma.bacsurv.web.service.RefusedException;
import ma.bacsurv.web.service.SessionAdminService;
import ma.bacsurv.web.service.TimetableService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Removing a session, and the state that makes removing it answerable.
 *
 * <p>A session that has been distributed is not data, it is a record of work
 * people did: the réserve and permanence turns it handed out are what puts
 * everybody in the queue for the next session. Deleting one used to be
 * impossible to do safely because nothing distinguished a session that happened
 * from one that had merely been solved once while its timetable was typed.
 */
@SpringBootTest
class SessionLifecycleTest {

    @Autowired SessionAdminService sessions;
    @Autowired CenterAdminService centers;
    @Autowired TimetableService timetable;
    @Autowired OperationRepository operations;
    @Autowired StreamRepository streams;
    @Autowired SolveJobRepository jobs;
    @Autowired AssignmentRepository assignments;

    private static final LocalDate DAY = LocalDate.of(2026, 6, 4);

    private record Fixture(long centreId, long sessionId) {}

    /** A centre with rooms and a session holding one filière and one épreuve. */
    private Fixture typed() {
        long centre = centers.createCenter("Lycée Cycle " + System.nanoTime());
        centers.addRooms(centre, 4, "Salle");
        long session = centers.createSession(centre, "Bac 2026", "NATIONAL_2BAC", DAY, DAY);

        List<Long> rooms = centers.detail(centre).rooms().stream()
                .map(CenterAdminService.RoomView::id).toList();
        long stream = timetable.addStream(session, "Lettres", rooms);
        timetable.setExam(session, stream, "Philosophie", DAY,
                LocalTime.of(8, 0), LocalTime.of(11, 0));
        return new Fixture(centre, session);
    }

    /** A finished solve, without running the solver: history is what is asserted. */
    private long finishedSolve(long sessionId) {
        SolveJob job = new SolveJob(operations.findById(sessionId).orElseThrow(), null, 10);
        job.markDone("{}", true, 0, 0, 0);
        return jobs.save(job).getId();
    }

    @Test
    void aNewSessionIsADraft() {
        Fixture fixture = typed();
        assertEquals("DRAFT", sessions.impact(fixture.sessionId()).state());
        assertTrue(sessions.impact(fixture.sessionId()).deletable());
    }

    /**
     * The whole point of the confirmation: it names what is about to be lost,
     * and the figures come from the server rather than from the screen's guess.
     */
    @Test
    void theImpactCountsWhatWouldBeLost() {
        Fixture fixture = typed();
        finishedSolve(fixture.sessionId());

        SessionAdminService.Impact impact = sessions.impact(fixture.sessionId());
        assertEquals("Bac 2026", impact.reference());
        assertEquals(1, impact.streamCount());
        assertEquals(1, impact.examCount());
        assertEquals(1, impact.solveCount());
    }

    /** Nothing of a draft is owed to anybody, so nothing stands in the way. */
    @Test
    void aDraftIsRemovedWithEverythingUnderIt() {
        Fixture fixture = typed();
        long jobId = finishedSolve(fixture.sessionId());

        assertEquals(fixture.centreId(), sessions.delete(fixture.sessionId()));

        assertTrue(operations.findById(fixture.sessionId()).isEmpty());
        assertTrue(streams.ofOperation(fixture.sessionId()).isEmpty(), "its filières went with it");
        assertTrue(jobs.findById(jobId).isEmpty(), "and its trial solves");
        assertEquals(1, centers.detail(fixture.centreId()).rooms().size() - 3,
                "the centre's rooms are not the session's to take");
    }

    /** A settled session is history; deleting it would rewrite the queue. */
    @Test
    void aSettledSessionRefusesToBeDeleted() {
        Fixture fixture = typed();
        finishedSolve(fixture.sessionId());
        sessions.settle(fixture.sessionId());

        RefusedException refused = assertThrows(RefusedException.class,
                () -> sessions.delete(fixture.sessionId()));
        assertEquals("session.settled", refused.getMessage());
        assertEquals("Bac 2026", refused.args()[0], "the refusal names the session");

        assertTrue(operations.findById(fixture.sessionId()).isPresent(), "and it is still there");
        assertFalse(sessions.impact(fixture.sessionId()).deletable());
    }

    /** Settling means "this répartition went out"; there has to be one. */
    @Test
    void thereMustBeSomethingToSettle() {
        Fixture fixture = typed();
        assertEquals("session.settle.noDistribution", assertThrows(IllegalArgumentException.class,
                () -> sessions.settle(fixture.sessionId())).getMessage());
    }

    /** A wrong date should not be permanent: reopening returns it to a draft. */
    @Test
    void reopeningReturnsItToADraftAndToBeingDeletable() {
        Fixture fixture = typed();
        finishedSolve(fixture.sessionId());
        sessions.settle(fixture.sessionId());

        sessions.reopen(fixture.sessionId());

        assertEquals("DRAFT", sessions.impact(fixture.sessionId()).state());
        assertDoesNotThrow(() -> sessions.delete(fixture.sessionId()));
    }

    /**
     * The planning of a settled session is the planning the convocations were
     * printed from. Moving an épreuve under it would leave the paper in
     * everybody's hands describing a session the application no longer holds.
     */
    @Test
    void aSettledSessionsPlanningIsLocked() {
        Fixture fixture = typed();
        finishedSolve(fixture.sessionId());
        sessions.settle(fixture.sessionId());

        long stream = streams.ofOperation(fixture.sessionId()).getFirst().getId();
        List<Long> rooms = centers.detail(fixture.centreId()).rooms().stream()
                .map(CenterAdminService.RoomView::id).toList();

        assertEquals("session.settled.locked", assertThrows(RefusedException.class,
                () -> timetable.setExam(fixture.sessionId(), stream, "Arabe", DAY,
                        LocalTime.of(15, 0), LocalTime.of(18, 0))).getMessage());
        assertThrows(RefusedException.class,
                () -> timetable.addStream(fixture.sessionId(), "Sciences", rooms));
        assertThrows(RefusedException.class, () -> timetable.removeStream(stream));

        // reading it is not editing it: the grid must still draw
        assertEquals(1, timetable.timetable(fixture.sessionId()).exams().size());

        // and reopening lifts the lock rather than requiring a new session
        sessions.reopen(fixture.sessionId());
        assertDoesNotThrow(() -> timetable.setExam(fixture.sessionId(), stream, "Arabe", DAY,
                LocalTime.of(15, 0), LocalTime.of(18, 0)));
    }

    /**
     * The bug the state was introduced for, in its own right.
     *
     * <p>Cumulative fairness reads the newest finished solve of every other
     * session of the centre. Before the state existed, a trial solve of the
     * nationale counted as duties served when the régionale was distributed,
     * and turns were repaid to people who had done nothing.
     */
    @Test
    void aTrialSolveIsNotHistory() {
        long centre = centers.createCenter("Lycée Essai " + System.nanoTime());
        long trial = centers.createSession(centre, "Essai", "NATIONAL_2BAC", DAY, DAY);
        long real = centers.createSession(centre, "Régionale", "REGIONAL_1BAC", DAY, DAY);
        finishedSolve(trial);

        // both sit in June, so both are in the same school year: what keeps the
        // trial out of the régionale's history is the state, not the year
        Long year = operations.findWithYear(real).orElseThrow().getSchoolYear().getId();
        assertEquals(year, operations.findWithYear(trial).orElseThrow().getSchoolYear().getId());

        // the query the assembler asks when it builds the pool for the régionale
        assertTrue(assignments.priorWorkloadOfYear(year, real).isEmpty(),
                "a session nobody settled is not work anybody did");
        assertTrue(assignments.privilegeTurnsOfYear(year, real).isEmpty());
    }
}
