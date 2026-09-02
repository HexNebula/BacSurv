package ma.bacsurv.web;

import ma.bacsurv.domain.DutyRole;
import ma.bacsurv.domain.ExamSlot;
import ma.bacsurv.domain.Teacher;
import ma.bacsurv.web.persistence.AssignmentEntity;
import ma.bacsurv.web.persistence.AssignmentRepository;
import ma.bacsurv.web.persistence.ExamSlotEntity;
import ma.bacsurv.web.persistence.OperationRepository;
import ma.bacsurv.web.persistence.SolveJob;
import ma.bacsurv.web.persistence.SolveJobRepository;
import ma.bacsurv.web.persistence.TeacherEntity;
import ma.bacsurv.web.persistence.TeacherRepository;
import ma.bacsurv.web.service.CenterAdminService;
import ma.bacsurv.web.service.OperationAssembler;
import ma.bacsurv.web.service.ReadinessService;
import ma.bacsurv.web.service.RefusedException;
import ma.bacsurv.web.service.SessionAdminService;
import ma.bacsurv.web.service.SessionConflictService;
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
 * Two sessions of one centre running the same hours.
 *
 * <p>A centre that examines its own pupils and the candidats libres in the same
 * rattrapage runs two sessions over the same three mornings. Each is
 * distributed alone and neither can see the other, so both come out with no
 * violation at all — and put the same teacher in two rooms at eight o'clock,
 * and seat two filières behind the same door.
 *
 * <p>The application had every check it needed for one session and none for
 * two. What follows is the boundary: a session may be settled beside its
 * neighbours only if it can actually happen beside them.
 */
@SpringBootTest
class ConcurrentSessionsTest {

    @Autowired CenterAdminService centers;
    @Autowired SessionAdminService sessions;
    @Autowired TimetableService timetable;
    @Autowired TeacherAdminService teacherAdmin;
    @Autowired ReadinessService readiness;
    @Autowired SessionConflictService conflicts;
    @Autowired OperationAssembler assembler;
    @Autowired OperationRepository operations;
    @Autowired SolveJobRepository jobs;
    @Autowired AssignmentRepository assignments;
    @Autowired TeacherRepository teachers;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    /** Reading a session's slots walks a lazy collection, so it needs a transaction. */
    private <T> T inTransaction(java.util.function.Supplier<T> work) {
        return new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .execute(status -> work.get());
    }

    private static final LocalDate JULY = LocalDate.of(2026, 7, 2);
    private static final LocalTime MORNING = LocalTime.of(8, 0);
    private static final LocalTime NOON = LocalTime.of(11, 0);

    private long centre() {
        long id = centers.createCenter("ثانوية المتزامنة " + System.nanoTime());
        centers.addRooms(id, 6, "Salle");
        return id;
    }

    private List<Long> rooms(long centre) {
        return centers.detail(centre).rooms().stream()
                .map(CenterAdminService.RoomView::id).toList();
    }

    /** A session with one filière on the given rooms, sitting one épreuve. */
    private long session(long centre, String name, LocalDate from, LocalDate to,
                         List<Long> rooms) {
        long id = centers.createSession(centre, name, "NATIONAL_2BAC", from, to);
        long stream = timetable.addStream(id, "Lettres " + name, rooms);
        timetable.setExam(id, stream, "Philosophie", from, MORNING, NOON);
        return id;
    }

    /** A finished distribution, written directly: what is asserted is the state. */
    private long distribute(long sessionId, TeacherEntity... staff) {
        return inTransaction(() -> {
            SolveJob job = new SolveJob(operations.findById(sessionId).orElseThrow(), null, 10);
            job.markDone("{}", true, 0, 0, 0);
            SolveJob saved = jobs.save(job);

            ExamSlotEntity slot = operations.findById(sessionId).orElseThrow()
                    .getSlots().getFirst();
            int n = 1;
            for (TeacherEntity teacher : staff) {
                assignments.save(new AssignmentEntity(saved, "D" + n++, slot.getReference(),
                        "E1", "R1", DutyRole.SURVEILLANCE, teacher));
            }
            return saved.getId();
        });
    }

    private TeacherEntity teacher(long centre, String name) {
        String matricule = "M" + System.nanoTime();
        teacherAdmin.add(centre, new TeacherAdminService.Details(
                matricule, name, "Philosophie", null, null));
        return teachers.findByCenterIdAndMatricule(centre, matricule).orElseThrow();
    }

    // ---- rooms -------------------------------------------------------------

    /**
     * The refusal an administrator wants before the evening is spent: the room is
     * simply not available while the other session holds it.
     */
    @Test
    void aRoomHeldByAConcurrentSettledSessionCannotBeChosen() {
        long centre = centre();
        List<Long> all = rooms(centre);

        long first = session(centre, "Rattrapage scolarisés", JULY, JULY.plusDays(2),
                all.subList(0, 3));
        distribute(first);
        sessions.settle(first);

        long second = centers.createSession(centre, "Rattrapage libres", "NATIONAL_2BAC",
                JULY, JULY.plusDays(2));

        RefusedException refused = assertThrows(RefusedException.class,
                () -> timetable.addStream(second, "Sciences", all.subList(2, 5)));
        assertEquals("stream.rooms.otherSession", refused.getMessage());
    }

    /** Free rooms stay free: the guard is about the door, not about the session. */
    @Test
    void theRoomsTheOtherSessionDoesNotHoldRemainAvailable() {
        long centre = centre();
        List<Long> all = rooms(centre);

        long first = session(centre, "Rattrapage scolarisés", JULY, JULY.plusDays(2),
                all.subList(0, 3));
        distribute(first);
        sessions.settle(first);

        long second = centers.createSession(centre, "Rattrapage libres", "NATIONAL_2BAC",
                JULY, JULY.plusDays(2));

        assertDoesNotThrow(() -> timetable.addStream(second, "Sciences", all.subList(3, 6)));
    }

    /**
     * Sessions that do not share a day share nothing. The régionale of early
     * June and the nationale of late June use the same building and the same
     * thirteen rooms, one after the other.
     */
    @Test
    void aSessionOnOtherDaysMayReuseEveryRoom() {
        long centre = centre();
        List<Long> all = rooms(centre);

        long june = session(centre, "Régionale", LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 2), all);
        distribute(june);
        sessions.settle(june);

        long july = centers.createSession(centre, "Nationale", "NATIONAL_2BAC",
                JULY, JULY.plusDays(2));

        assertDoesNotThrow(() -> timetable.addStream(july, "Lettres", all));
    }

    /** A draft is still being typed, and may never go out. It holds nothing. */
    @Test
    void aDraftNeighbourDoesNotReserveItsRooms() {
        long centre = centre();
        List<Long> all = rooms(centre);

        session(centre, "Rattrapage scolarisés", JULY, JULY.plusDays(2), all.subList(0, 3));

        long second = centers.createSession(centre, "Rattrapage libres", "NATIONAL_2BAC",
                JULY, JULY.plusDays(2));

        assertDoesNotThrow(() -> timetable.addStream(second, "Sciences", all.subList(0, 3)));
    }

    // ---- teachers ----------------------------------------------------------

    /**
     * The bug itself. Both distributions are individually perfect — no
     * violation, nothing unfilled — and together they put one man in two rooms
     * at eight o'clock. Nothing but settling can catch it, because until the
     * first session is settled there is nothing to compare against.
     */
    @Test
    void aTeacherAlreadyStandingSomewhereBlocksTheSecondSettle() {
        long centre = centre();
        List<Long> all = rooms(centre);
        TeacherEntity shared = teacher(centre, "براد يوسف");

        long first = session(centre, "Rattrapage scolarisés", JULY, JULY.plusDays(2),
                all.subList(0, 3));
        distribute(first, shared);
        sessions.settle(first);

        long second = session(centre, "Rattrapage libres", JULY, JULY.plusDays(2),
                all.subList(3, 6));
        distribute(second, shared);

        RefusedException refused = assertThrows(RefusedException.class,
                () -> sessions.settle(second));
        assertEquals("session.settle.teachersBusy", refused.getMessage());
        assertFalse(operations.findById(second).orElseThrow().isSettled());
    }

    /** Different people at the same hour is not a collision. It is a centre working. */
    @Test
    void twoSessionsStaffedByDifferentPeopleBothSettle() {
        long centre = centre();
        List<Long> all = rooms(centre);

        long first = session(centre, "Rattrapage scolarisés", JULY, JULY.plusDays(2),
                all.subList(0, 3));
        distribute(first, teacher(centre, "الأول"));
        sessions.settle(first);

        long second = session(centre, "Rattrapage libres", JULY, JULY.plusDays(2),
                all.subList(3, 6));
        distribute(second, teacher(centre, "الثاني"));

        assertDoesNotThrow(() -> sessions.settle(second));
        assertTrue(operations.findById(second).orElseThrow().isSettled());
    }

    /**
     * Order does not decide who is refused, which is the property that makes
     * this a guarantee: whichever of the two is settled second is stopped, so
     * no sequence of steps reaches the broken state.
     */
    @Test
    void whicheverIsSettledSecondIsTheOneRefused() {
        long centre = centre();
        List<Long> all = rooms(centre);
        TeacherEntity shared = teacher(centre, "لهند عبد الرزاق");

        long libres = session(centre, "Rattrapage libres", JULY, JULY.plusDays(2),
                all.subList(0, 3));
        long scolarises = session(centre, "Rattrapage scolarisés", JULY, JULY.plusDays(2),
                all.subList(3, 6));
        distribute(libres, shared);
        distribute(scolarises, shared);

        sessions.settle(libres);

        assertThrows(RefusedException.class, () -> sessions.settle(scolarises));
    }

    /** Consecutive, not simultaneous: a duty ending at 11:00 frees the man at 11:00. */
    @Test
    void anHourThatOnlyTouchesTheNextIsNotAClash() {
        long centre = centre();
        List<Long> all = rooms(centre);
        TeacherEntity shared = teacher(centre, "قسو سميرة");

        long morning = session(centre, "Matin", JULY, JULY, all.subList(0, 3));
        distribute(morning, shared);
        sessions.settle(morning);

        long afternoon = centers.createSession(centre, "Après-midi", "NATIONAL_2BAC", JULY, JULY);
        long stream = timetable.addStream(afternoon, "Sciences", all.subList(3, 6));
        timetable.setExam(afternoon, stream, "Philosophie", JULY, NOON, LocalTime.of(13, 0));
        distribute(afternoon, shared);

        assertDoesNotThrow(() -> sessions.settle(afternoon));
    }

    // ---- what the next distribution is told --------------------------------

    /**
     * The sign before the wall.
     *
     * <p>Settling refuses a distribution that collides, but on its own it
     * leaves the administrator pressing the same button again with nothing
     * changed: the solver never learned why. Once the neighbouring session is
     * arrêtée, the people it holds arrive in the pool as unavailable for those
     * exact hours — the same shape as a declared absence — so the next
     * distribution does not offer them at all.
     */
    @Test
    void aTeacherHeldByASettledNeighbourIsUnavailableToTheNextDistribution() {
        long centre = centre();
        List<Long> all = rooms(centre);
        TeacherEntity shared = teacher(centre, "براد يوسف");

        long first = session(centre, "Rattrapage scolarisés", JULY, JULY.plusDays(2),
                all.subList(0, 3));
        distribute(first, shared);
        sessions.settle(first);

        long second = session(centre, "Rattrapage libres", JULY, JULY.plusDays(2),
                all.subList(3, 6));

        Teacher asSeen = inTransaction(() -> assembler
                .poolFor(operations.findById(second).orElseThrow())
                .teacherById().get(shared.getId()));

        ExamSlot theHour = inTransaction(() -> assembler
                .toDomain(operations.findById(second).orElseThrow()).slots().getFirst());

        assertFalse(asSeen.isAvailable(theHour),
                "he is standing in the other session at that hour");
    }

    /** And nobody else is touched: only the hours actually held are taken. */
    @Test
    void theRestOfThePoolStaysAvailable() {
        long centre = centre();
        List<Long> all = rooms(centre);
        TeacherEntity busy = teacher(centre, "المشغول");
        TeacherEntity free = teacher(centre, "الحر");

        long first = session(centre, "Rattrapage scolarisés", JULY, JULY.plusDays(2),
                all.subList(0, 3));
        distribute(first, busy);
        sessions.settle(first);

        long second = session(centre, "Rattrapage libres", JULY, JULY.plusDays(2),
                all.subList(3, 6));

        ExamSlot theHour = inTransaction(() -> assembler
                .toDomain(operations.findById(second).orElseThrow()).slots().getFirst());
        OperationAssembler.Pool pool = inTransaction(() -> assembler
                .poolFor(operations.findById(second).orElseThrow()));

        assertFalse(pool.teacherById().get(busy.getId()).isAvailable(theHour));
        assertTrue(pool.teacherById().get(free.getId()).isAvailable(theHour));
    }

    /** A draft neighbour commits nobody: its people are still free to be given work. */
    @Test
    void aDraftNeighbourDoesNotHoldItsTeachers() {
        long centre = centre();
        List<Long> all = rooms(centre);
        TeacherEntity shared = teacher(centre, "غير مصادق");

        long first = session(centre, "Rattrapage scolarisés", JULY, JULY.plusDays(2),
                all.subList(0, 3));
        distribute(first, shared);
        // deliberately not settled

        long second = session(centre, "Rattrapage libres", JULY, JULY.plusDays(2),
                all.subList(3, 6));

        ExamSlot theHour = inTransaction(() -> assembler
                .toDomain(operations.findById(second).orElseThrow()).slots().getFirst());
        Teacher asSeen = inTransaction(() -> assembler
                .poolFor(operations.findById(second).orElseThrow())
                .teacherById().get(shared.getId()));

        assertTrue(asSeen.isAvailable(theHour));
    }

    /**
     * Reopening drops a session out of the settled set, so while it is a draft
     * it holds nobody. The question is whether the one that was settled in the
     * meantime is left protected — and it is, because settling is checked every
     * time, including the second time.
     */
    @Test
    void reSettlingAReopenedSessionIsCheckedAgain() {
        long centre = centre();
        List<Long> all = rooms(centre);
        TeacherEntity shared = teacher(centre, "المشترك");

        long libres = session(centre, "Rattrapage libres", JULY, JULY.plusDays(2),
                all.subList(0, 3));
        distribute(libres, shared);
        sessions.settle(libres);

        // the second session is distributed onto the same man deliberately:
        // this is the state a reopen would leave behind
        long scolarises = session(centre, "Rattrapage scolarisés", JULY, JULY.plusDays(2),
                all.subList(3, 6));
        distribute(scolarises, shared);
        assertThrows(RefusedException.class, () -> sessions.settle(scolarises));

        // reopen the first, and the second may now be settled: nothing holds him
        sessions.reopen(libres);
        assertDoesNotThrow(() -> sessions.settle(scolarises));

        // the first one back: it collides with what went out in the meantime
        RefusedException refused = assertThrows(RefusedException.class,
                () -> sessions.settle(libres));
        assertEquals("session.settle.teachersBusy", refused.getMessage());
    }

    // ---- what the screen says before the click -----------------------------

    /**
     * Settling refuses on this, so readiness has to say it first. A refusal at
     * the last click, on a screen that had reported everything ready, is the
     * kind of thing that makes an administrator stop trusting the guide.
     */
    @Test
    void readinessWarnsBeforeTheRefusalArrives() {
        long centre = centre();
        List<Long> all = rooms(centre);
        TeacherEntity shared = teacher(centre, "صادقي عائشة");

        long first = session(centre, "Rattrapage scolarisés", JULY, JULY.plusDays(2),
                all.subList(0, 3));
        distribute(first, shared);
        sessions.settle(first);

        long second = session(centre, "Rattrapage libres", JULY, JULY.plusDays(2),
                all.subList(3, 6));
        distribute(second, shared);

        ReadinessService.Step step = readiness.of(second).steps().stream()
                .filter(s -> s.key().equals("concurrent")).findFirst().orElseThrow();

        assertEquals(ReadinessService.State.CHECK, step.state());
        assertEquals("concurrent.teachers", step.detail());
    }

    /** And it does not offer the act it knows would be refused. */
    @Test
    void readinessDoesNotOfferASettleThatWouldBeRefused() {
        long centre = centre();
        List<Long> all = rooms(centre);
        TeacherEntity shared = teacher(centre, "جميل محمد");

        long first = session(centre, "Rattrapage scolarisés", JULY, JULY.plusDays(2),
                all.subList(0, 3));
        distribute(first, shared);
        sessions.settle(first);

        long second = session(centre, "Rattrapage libres", JULY, JULY.plusDays(2),
                all.subList(3, 6));
        distribute(second, shared);

        ReadinessService.Step settled = readiness.of(second).steps().stream()
                .filter(s -> s.key().equals("settled")).findFirst().orElseThrow();

        assertEquals("settled.waiting", settled.detail());
    }

    /**
     * A centre running one session at a time never sees the step at all. Most
     * centres, most of the year — and a line saying nothing else is happening
     * is one more thing for him to read past.
     */
    @Test
    void aSessionWithNoNeighbourHasNoSuchStep() {
        long centre = centre();
        long alone = session(centre, "Nationale", JULY, JULY.plusDays(2), rooms(centre));
        distribute(alone, teacher(centre, "وحيد"));

        assertTrue(readiness.of(alone).steps().stream()
                        .noneMatch(s -> s.key().equals("concurrent")),
                "nothing else runs these days");
    }

    /** With a neighbour that shares nothing, the step appears and reassures. */
    @Test
    void aNeighbourSharingNothingReadsClear() {
        long centre = centre();
        List<Long> all = rooms(centre);

        long first = session(centre, "Rattrapage scolarisés", JULY, JULY.plusDays(2),
                all.subList(0, 3));
        distribute(first, teacher(centre, "الأول"));
        sessions.settle(first);

        long second = session(centre, "Rattrapage libres", JULY, JULY.plusDays(2),
                all.subList(3, 6));
        distribute(second, teacher(centre, "الثاني"));

        ReadinessService.Step step = readiness.of(second).steps().stream()
                .filter(s -> s.key().equals("concurrent")).findFirst().orElseThrow();

        assertEquals(ReadinessService.State.READY, step.state());
        assertEquals("concurrent.clear", step.detail());
        assertEquals(List.of("Rattrapage scolarisés"), step.args());
    }

    /** The report names what collides, because that is what has to be moved. */
    @Test
    void theConflictNamesTheTeacherAndTheSession() {
        long centre = centre();
        List<Long> all = rooms(centre);
        TeacherEntity shared = teacher(centre, "الفاسي أمينة");

        long first = session(centre, "Rattrapage scolarisés", JULY, JULY.plusDays(2),
                all.subList(0, 3));
        distribute(first, shared);
        sessions.settle(first);

        long second = session(centre, "Rattrapage libres", JULY, JULY.plusDays(2),
                all.subList(3, 6));
        distribute(second, shared);

        SessionConflictService.Conflicts found = conflicts.of(second);

        assertEquals(1, found.teachers().size());
        assertEquals("الفاسي أمينة", found.teachers().getFirst().teacher());
        assertEquals(JULY, found.teachers().getFirst().date());
        assertEquals(List.of("Rattrapage scolarisés"), found.sessions());
    }
}
