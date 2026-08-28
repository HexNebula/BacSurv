package ma.bacsurv.web;

import ma.bacsurv.web.service.CenterAdminService;
import ma.bacsurv.web.service.ReadinessService;
import ma.bacsurv.web.service.ReadinessService.State;
import ma.bacsurv.web.service.TeacherAdminService;
import ma.bacsurv.web.service.TeacherAdminService.Details;
import ma.bacsurv.web.service.TimetableService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What is left to do, said before somebody presses solve rather than after.
 *
 * <p>The application already knew all of this; it only ever admitted it as a
 * refusal at the last moment, which is no use to an administrator deciding
 * which screen to open.
 */
@SpringBootTest
class ReadinessTest {

    @Autowired ReadinessService readiness;
    @Autowired CenterAdminService centers;
    @Autowired TeacherAdminService teacherAdmin;
    @Autowired TimetableService timetable;

    private static final LocalDate DAY = LocalDate.of(2026, 6, 4);

    private record Fixture(long centreId, long sessionId) {}

    private Fixture bare() {
        long centre = centers.createCenter("Lycée Étapes " + System.nanoTime());
        long session = centers.createSession(centre, "Bac", "NATIONAL_2BAC", DAY, DAY);
        return new Fixture(centre, session);
    }

    private State stateOf(long sessionId, String key) {
        return readiness.of(sessionId).steps().stream()
                .filter(step -> step.key().equals(key))
                .findFirst().orElseThrow().state();
    }

    @Test
    void anEmptySessionHasEverythingLeftToDo() {
        Fixture fixture = bare();
        var report = readiness.of(fixture.sessionId());

        // salles, enseignants, filières, emploi du temps, effectifs,
        // répartition, et l'arrêter — the last is what makes the duties count
        assertEquals(7, report.steps().size());
        assertTrue(report.steps().stream().noneMatch(step -> step.state() == State.READY),
                "nothing is set up yet: " + report.steps());
        assertEquals("rooms", report.next(), "rooms come first");
        assertEquals("settled", report.steps().getLast().key(), "and settling comes last");
    }

    @Test
    void theNextStepMovesAlongAsThingsAreDone() {
        Fixture fixture = bare();
        assertEquals("rooms", readiness.of(fixture.sessionId()).next());

        centers.addRooms(fixture.centreId(), 4, "Salle");
        assertEquals(State.READY, stateOf(fixture.sessionId(), "rooms"));
        assertEquals("teachers", readiness.of(fixture.sessionId()).next());

        teacherAdmin.add(fixture.centreId(), new Details("D800001", "Amina", "Anglais", null, "F"));
        assertEquals(State.READY, stateOf(fixture.sessionId(), "teachers"));
        assertEquals("filieres", readiness.of(fixture.sessionId()).next());
    }

    /** A filière with nowhere to sit is present but cannot hold an épreuve. */
    @Test
    void aFiliereWithoutRoomsNeedsChecking() {
        Fixture fixture = bare();
        centers.addRooms(fixture.centreId(), 4, "Salle");
        timetable.addStream(fixture.sessionId(), "Sans salle", List.of());

        assertEquals(State.CHECK, stateOf(fixture.sessionId(), "filieres"));
    }

    /** A filière that sits nothing is a column somebody forgot to fill. */
    @Test
    void aFiliereThatSitsNothingNeedsChecking() {
        Fixture fixture = bare();
        centers.addRooms(fixture.centreId(), 4, "Salle");
        List<Long> rooms = centers.detail(fixture.centreId()).rooms().stream()
                .map(CenterAdminService.RoomView::id).toList();

        long lettres = timetable.addStream(fixture.sessionId(), "Lettres", rooms.subList(0, 2));
        timetable.addStream(fixture.sessionId(), "Sciences", rooms.subList(2, 4));
        timetable.setExam(fixture.sessionId(), lettres, "Anglais", DAY,
                LocalTime.of(8, 0), LocalTime.of(10, 0));

        assertEquals(State.CHECK, stateOf(fixture.sessionId(), "timetable"),
                "Sciences sits nothing");
    }

    /**
     * Two rooms of two surveillants each need four people, plus a réserve and a
     * specialist. One teacher cannot do it, and that is worth knowing before
     * the solver is asked.
     */
    @Test
    void aPoolTooSmallForTheTimetableNeedsChecking() {
        Fixture fixture = bare();
        centers.addRooms(fixture.centreId(), 2, "Salle");
        teacherAdmin.add(fixture.centreId(), new Details("D800002", "Seul", "Anglais", null, "M"));

        List<Long> rooms = centers.detail(fixture.centreId()).rooms().stream()
                .map(CenterAdminService.RoomView::id).toList();
        long stream = timetable.addStream(fixture.sessionId(), "Lettres", rooms);
        timetable.setExam(fixture.sessionId(), stream, "Anglais", DAY,
                LocalTime.of(8, 0), LocalTime.of(10, 0));

        assertEquals(State.CHECK, stateOf(fixture.sessionId(), "staffing"));
    }

    /** With everything in place, only the distribution itself is left. */
    @Test
    void aSessionReadyToSolveSaysSo() {
        Fixture fixture = bare();
        centers.addRooms(fixture.centreId(), 2, "Salle");
        for (int i = 1; i <= 12; i++) {
            teacherAdmin.add(fixture.centreId(),
                    new Details("D81000" + i, "Enseignant " + i,
                            i <= 6 ? "Anglais" : "Mathématiques", null, "F"));
        }

        List<Long> rooms = centers.detail(fixture.centreId()).rooms().stream()
                .map(CenterAdminService.RoomView::id).toList();
        long stream = timetable.addStream(fixture.sessionId(), "Lettres", rooms);
        timetable.setExam(fixture.sessionId(), stream, "Anglais", DAY,
                LocalTime.of(8, 0), LocalTime.of(10, 0));

        var report = readiness.of(fixture.sessionId());
        assertEquals(State.READY, stateOf(fixture.sessionId(), "staffing"), report.steps().toString());
        assertEquals("distribution", report.next(),
                "everything but the distribution is done: " + report.steps());
    }

    /** Every step names the screen that fixes it. */
    @Test
    void everyStepPointsAtAScreen() {
        Fixture fixture = bare();
        readiness.of(fixture.sessionId()).steps().forEach(step ->
                assertTrue(List.of("center", "teachers", "schedule", "results").contains(step.screen()),
                        step.key() + " points nowhere useful: " + step.screen()));
    }
}
