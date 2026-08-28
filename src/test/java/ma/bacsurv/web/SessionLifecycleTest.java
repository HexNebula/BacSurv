package ma.bacsurv.web;

import ma.bacsurv.web.persistence.AssignmentEntity;
import ma.bacsurv.web.persistence.AssignmentRepository;
import ma.bacsurv.web.persistence.TeacherEntity;
import ma.bacsurv.web.persistence.TeacherRepository;
import ma.bacsurv.web.persistence.OperationRepository;
import ma.bacsurv.web.persistence.SolveJob;
import ma.bacsurv.web.persistence.SolveJobRepository;
import ma.bacsurv.web.persistence.StreamRepository;
import ma.bacsurv.web.service.CenterAdminService;
import ma.bacsurv.web.service.RefusedException;
import ma.bacsurv.web.service.SessionAdminService;
import ma.bacsurv.web.service.OperationConfigService;
import ma.bacsurv.web.service.ScheduleEditor;
import ma.bacsurv.web.service.SolveService;
import ma.bacsurv.web.service.TeacherAdminService;
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
    @Autowired ScheduleEditor editor;
    @Autowired SolveService solveService;
    @Autowired OperationConfigService config;
    @Autowired TeacherAdminService teacherAdmin;
    @Autowired OperationRepository operations;
    @Autowired StreamRepository streams;
    @Autowired SolveJobRepository jobs;
    @Autowired AssignmentRepository assignments;
    @Autowired TeacherRepository teachers;

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
     * A distribution with unstaffed duties must not become history.
     *
     * <p>Settling locks the planning and puts the duties in the queue. What is
     * locked has to be worth locking: an unfilled duty entered as history is
     * work nobody can have done, and it would settle the next session's queue
     * against a fiction.
     */
    @Test
    void abrokenDistributionCannotBeSettled() {
        Fixture fixture = typed();
        SolveJob job = new SolveJob(operations.findById(fixture.sessionId()).orElseThrow(), null, 10);
        job.markDone("{}", false, 2, 0, 3);
        jobs.save(job);

        RefusedException refused = assertThrows(RefusedException.class,
                () -> sessions.settle(fixture.sessionId()));
        assertEquals("session.settle.broken", refused.getMessage());
        assertEquals("2", refused.args()[0], "the refusal names the violations");
        assertEquals("3", refused.args()[1], "and the unstaffed duties");
        assertEquals("DRAFT", sessions.impact(fixture.sessionId()).state());
    }

    /**
     * A distribution solved before the timetable last moved answers a question
     * the session is no longer asking.
     */
    @Test
    void astaleDistributionCannotBeSettled() {
        Fixture fixture = typed();
        finishedSolve(fixture.sessionId());

        // an épreuve added after the solve: the paper on screen is out of date
        long stream = streams.ofOperation(fixture.sessionId()).getFirst().getId();
        timetable.setExam(fixture.sessionId(), stream, "Arabe", DAY,
                LocalTime.of(15, 0), LocalTime.of(18, 0));

        assertEquals("session.settle.stale", assertThrows(IllegalArgumentException.class,
                () -> sessions.settle(fixture.sessionId())).getMessage());
    }

    /**
     * A hand edit rewrites the very rows the queue counts and the convocations
     * were printed from. Locking the timetable and leaving this open would
     * guard the smaller door — reassigning a duty is the thing an administrator
     * does most often after distributing.
     */
    @Test
    void aSettledSessionsAssignmentsCannotBeEditedByHand() {
        Fixture fixture = typed();
        // the pool is completed before the solve: adding a teacher touches the
        // centre, and a solve older than its own inputs is refused as stale
        teacherAdmin.add(fixture.centreId(), new TeacherAdminService.Details(
                "H1", "Enseignant H1", "Philosophie", null, "MALE"));
        long jobId = finishedSolve(fixture.sessionId());
        TeacherEntity teacher = teachers
                .findByCenterIdAndMatricule(fixture.centreId(), "H1").orElseThrow();
        assignments.save(new AssignmentEntity(jobs.findById(jobId).orElseThrow(),
                "D1", "S1", "E1", null, ma.bacsurv.domain.DutyRole.SURVEILLANCE, teacher));
        sessions.settle(fixture.sessionId());

        assertEquals("session.settled.locked", assertThrows(RefusedException.class,
                () -> editor.apply(jobId, "D1", null, true)).getMessage());
        assertEquals("session.settled.locked", assertThrows(RefusedException.class,
                () -> editor.pin(jobId, "D1", true)).getMessage());

        // reopening lifts it, rather than requiring a new session
        sessions.reopen(fixture.sessionId());
        assertDoesNotThrow(() -> editor.pin(jobId, "D1", true));
    }

    /**
     * Re-solving a settled session would make a new job the newest, and the
     * paper in everybody's hands would quietly stop being what is held here.
     */
    @Test
    void aSettledSessionCannotBeReSolved() {
        Fixture fixture = typed();
        finishedSolve(fixture.sessionId());
        sessions.settle(fixture.sessionId());

        assertEquals("session.settled.locked", assertThrows(RefusedException.class,
                () -> solveService.submit(fixture.sessionId(), 5)).getMessage());
    }

    /** Its rules decide how many people each hour takes, so they are locked too. */
    @Test
    void aSettledSessionsRulesAreLocked() {
        Fixture fixture = typed();
        finishedSolve(fixture.sessionId());
        sessions.settle(fixture.sessionId());

        assertEquals("session.settled.locked", assertThrows(RefusedException.class,
                () -> config.save(fixture.sessionId(), 3, "PERCENTAGE", 0.1, 0,
                        2, "SOFT", 30, "SOFT", false, 10)).getMessage());
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
