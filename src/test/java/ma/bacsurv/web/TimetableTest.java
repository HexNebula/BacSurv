package ma.bacsurv.web;

import ma.bacsurv.web.service.CenterAdminService;
import ma.bacsurv.web.service.RefusedException;
import ma.bacsurv.web.service.TimetableService;
import ma.bacsurv.web.service.TimetableService.Timetable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Entering a timetable the way a centre holds it: the filières and their rooms
 * once, then a subject and its hours per day. The shape under test is the real
 * 2BAC of an examination centre — three days, three filières, and two of them
 * sitting the same paper at the same hour for different lengths.
 */
@SpringBootTest
class TimetableTest {

    @Autowired TimetableService timetable;
    @Autowired CenterAdminService centers;

    private static final LocalDate DAY_ONE = LocalDate.of(2026, 6, 4);
    private static final LocalDate DAY_TWO = LocalDate.of(2026, 6, 5);

    /** A centre with rooms, and a session over three days. */
    private record Session(long centreId, long id) {}

    private Session session(int roomCount) {
        long centre = centers.createCenter("Lycée Grille " + System.nanoTime());
        centers.addRooms(centre, roomCount, "Salle");
        return new Session(centre, centers.createSession(centre, "Bac 2026", "NATIONAL_2BAC",
                DAY_ONE, LocalDate.of(2026, 6, 6)));
    }

    /** Rooms by their place in the list, counted the way a centre numbers them. */
    private List<Long> rooms(Session session, int from, int to) {
        return centers.detail(session.centreId()).rooms().subList(from - 1, to).stream()
                .map(CenterAdminService.RoomView::id).toList();
    }

    @Test
    void aFiliereHoldsItsRoomsForTheWholeSession() {
        Session session = session(10);
        long lettres = timetable.addStream(session.id(), "Lettres", rooms(session, 1, 1));

        var grid = timetable.timetable(session.id());
        assertEquals(1, grid.streams().size());
        assertEquals("Lettres", grid.streams().getFirst().name());
        assertEquals(1, grid.streams().getFirst().rooms().size());

        timetable.setExam(session.id(), lettres, "Philosophie", DAY_ONE,
                LocalTime.of(15, 0), LocalTime.of(18, 0));

        // the épreuve took the filière's rooms without being told them
        assertEquals(1, timetable.timetable(session.id()).exams().getFirst().roomCount());
    }

    /**
     * Two filières at the same hour for different lengths are two moments, not
     * one. Collapsing them is what let a teacher be booked twice over.
     */
    @Test
    void sameHourDifferentLengthMakesTwoSlots() {
        Session session = session(10);
        long lettres = timetable.addStream(session.id(), "Lettres", rooms(session, 1, 1));
        long humaines = timetable.addStream(session.id(), "Sciences Humaines", rooms(session, 2, 5));

        timetable.setExam(session.id(), lettres, "Philosophie", DAY_ONE,
                LocalTime.of(15, 0), LocalTime.of(18, 0));
        timetable.setExam(session.id(), humaines, "Philosophie", DAY_ONE,
                LocalTime.of(15, 0), LocalTime.of(17, 0));

        var exams = timetable.timetable(session.id()).exams();
        assertEquals(2, exams.size());
        assertNotEquals(exams.get(0).endTime(), exams.get(1).endTime());
    }

    /** The same moment shared by two filières is one slot holding two épreuves. */
    @Test
    void theSameMomentIsOneSlot() {
        Session session = session(10);
        long lettres = timetable.addStream(session.id(), "Lettres", rooms(session, 1, 1));
        long humaines = timetable.addStream(session.id(), "Sciences Humaines", rooms(session, 2, 5));

        timetable.setExam(session.id(), lettres, "Arabe", DAY_TWO,
                LocalTime.of(8, 0), LocalTime.of(11, 0));
        timetable.setExam(session.id(), humaines, "Arabe", DAY_TWO,
                LocalTime.of(8, 0), LocalTime.of(11, 0));

        var grid = timetable.timetable(session.id());
        assertEquals(2, grid.exams().size());
        assertEquals(1, grid.exams().stream()
                .map(e -> e.date() + " " + e.startTime() + " " + e.endTime()).distinct().count(),
                "one moment, so one slot");
    }

    @Test
    void aFiliereSitsOneSubjectAtATime() {
        Session session = session(10);
        long lettres = timetable.addStream(session.id(), "Lettres", rooms(session, 1, 1));

        timetable.setExam(session.id(), lettres, "Philosophie", DAY_ONE,
                LocalTime.of(15, 0), LocalTime.of(18, 0));
        timetable.setExam(session.id(), lettres, "Arabe", DAY_ONE,
                LocalTime.of(15, 0), LocalTime.of(18, 0));

        var exams = timetable.timetable(session.id()).exams();
        assertEquals(1, exams.size(), "the second replaces the first rather than doubling it");
        assertEquals("Arabe", exams.getFirst().subject());
    }

    /**
     * Sciences Expérimentales sits exactly what Sciences Mathématiques sits.
     * Copying is the difference between one action and a dozen.
     */
    @Test
    void oneFiliereCanBeCopiedOntoAnother() {
        Session session = session(13);
        long maths = timetable.addStream(session.id(), "Sciences Mathématiques", rooms(session, 13, 13));
        long experimentales = timetable.addStream(session.id(), "Sciences Expérimentales",
                rooms(session, 6, 12));

        timetable.setExam(session.id(), maths, "Mathématiques", DAY_ONE,
                LocalTime.of(8, 0), LocalTime.of(11, 0));
        timetable.setExam(session.id(), maths, "Physique et chimie", DAY_TWO,
                LocalTime.of(15, 0), LocalTime.of(17, 0));

        assertEquals(2, timetable.copyStream(session.id(), maths, experimentales));

        var grid = timetable.timetable(session.id());
        assertEquals(4, grid.exams().size());
        var copied = grid.exams().stream()
                .filter(e -> e.streamId().equals(experimentales)).toList();
        assertEquals(2, copied.size());
        // the copy takes the receiving filière's own rooms, not the source's
        assertTrue(copied.stream().allMatch(e -> e.roomCount() == 7));
    }

    @Test
    void renamingAFiliereCarriesItsEpreuves() {
        Session session = session(10);
        long stream = timetable.addStream(session.id(), "Lettres", rooms(session, 1, 1));
        timetable.setExam(session.id(), stream, "Philosophie", DAY_ONE,
                LocalTime.of(15, 0), LocalTime.of(18, 0));

        timetable.editStream(stream, "Lettres et Sciences Humaines", rooms(session, 1, 2));

        var grid = timetable.timetable(session.id());
        assertEquals("Lettres et Sciences Humaines", grid.streams().getFirst().name());
        assertEquals(stream, grid.exams().getFirst().streamId(),
                "the épreuve still belongs to the filière it was entered under");
        assertEquals(2, grid.exams().getFirst().roomCount(), "and follows it to its new rooms");
    }

    @Test
    void removingAFiliereTakesItsEpreuvesAndLeavesNoEmptySlot() {
        Session session = session(10);
        long lettres = timetable.addStream(session.id(), "Lettres", rooms(session, 1, 1));
        long humaines = timetable.addStream(session.id(), "Sciences Humaines", rooms(session, 2, 5));
        timetable.setExam(session.id(), lettres, "Philosophie", DAY_ONE,
                LocalTime.of(15, 0), LocalTime.of(18, 0));
        timetable.setExam(session.id(), humaines, "Arabe", DAY_TWO,
                LocalTime.of(8, 0), LocalTime.of(11, 0));

        timetable.removeStream(lettres);

        var grid = timetable.timetable(session.id());
        assertEquals(1, grid.streams().size());
        assertEquals(1, grid.exams().size());
        assertEquals("Arabe", grid.exams().getFirst().subject());
    }

    @Test
    void refusalsExplainWhatIsWrong() {
        Session session = session(10);
        long stream = timetable.addStream(session.id(), "Lettres", rooms(session, 1, 1));

        assertEquals("stream.exists", assertThrows(IllegalArgumentException.class,
                () -> timetable.addStream(session.id(), "Lettres", rooms(session, 2, 3)))
                .getMessage());

        assertEquals("exam.hours.reversed", assertThrows(IllegalArgumentException.class,
                () -> timetable.setExam(session.id(), stream, "Philosophie", DAY_ONE,
                        LocalTime.of(18, 0), LocalTime.of(15, 0))).getMessage());

        assertEquals("exam.subject.required", assertThrows(IllegalArgumentException.class,
                () -> timetable.setExam(session.id(), stream, "  ", DAY_ONE,
                        LocalTime.of(8, 0), LocalTime.of(11, 0))).getMessage());

        // a filière with nowhere to sit cannot hold an épreuve
        long roomless = timetable.addStream(session.id(), "Sans salle", List.of());
        assertEquals("stream.rooms.none", assertThrows(IllegalArgumentException.class,
                () -> timetable.setExam(session.id(), roomless, "Anglais", DAY_ONE,
                        LocalTime.of(8, 0), LocalTime.of(10, 0))).getMessage());
    }

    /** An empty session still offers its declared days as columns to fill. */
    @Test
    void theGridHasItsDaysBeforeAnyEpreuveExists() {
        Session session = session(10);
        Timetable grid = timetable.timetable(session.id());

        assertEquals(3, grid.days().size(), "4, 5 and 6 June: " + grid.days());
        assertTrue(grid.exams().isEmpty());
    }

    /**
     * A room seats one filière for the whole session. Nothing refused a second
     * claim on it, so a centre could give salles 1 to 3 to Sciences and salles
     * 1 to 5 to Lettres: two épreuves behind one door, each asking for its own
     * surveillants, discovered on the printed list.
     */
    @Test
    void aRoomCannotBeGivenToTwoFilieres() {
        Session session = session(8);
        timetable.addStream(session.id(), "Sciences mathématiques", rooms(session, 1, 3));

        var refused = assertThrows(RefusedException.class,
                () -> timetable.addStream(session.id(), "Lettres", rooms(session, 1, 5)));

        assertEquals("stream.rooms.taken", refused.getMessage());
        assertEquals("Salle 1, Salle 2, Salle 3", refused.args()[0],
                "the refusal names the rooms, not just the fact");
        assertEquals("Sciences mathématiques", refused.args()[1],
                "and who is holding them");

        assertEquals(1, timetable.timetable(session.id()).streams().size(),
                "the refused filière was not created");
    }

    /** The free ones are still free: the refusal is about the overlap alone. */
    @Test
    void filieresMayShareASessionWithoutSharingARoom() {
        Session session = session(8);
        timetable.addStream(session.id(), "Sciences mathématiques", rooms(session, 1, 3));
        timetable.addStream(session.id(), "Lettres", rooms(session, 4, 8));

        assertEquals(2, timetable.timetable(session.id()).streams().size());
    }

    /** Editing a filière may keep its own rooms; they are not "taken" by it. */
    @Test
    void aFiliereKeepsItsOwnRoomsWhenEdited() {
        Session session = session(8);
        long lettres = timetable.addStream(session.id(), "Lettres", rooms(session, 1, 3));

        timetable.editStream(lettres, "Lettres", rooms(session, 1, 4));

        assertEquals(4, timetable.timetable(session.id()).streams().getFirst().rooms().size());
    }

    @Test
    void editingCannotTakeARoomFromAnotherFiliere() {
        Session session = session(8);
        timetable.addStream(session.id(), "Sciences mathématiques", rooms(session, 1, 3));
        long lettres = timetable.addStream(session.id(), "Lettres", rooms(session, 4, 8));

        assertThrows(RefusedException.class,
                () -> timetable.editStream(lettres, "Lettres", rooms(session, 3, 8)));
    }
}
