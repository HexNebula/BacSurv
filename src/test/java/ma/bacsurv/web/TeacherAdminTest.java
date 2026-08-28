package ma.bacsurv.web;

import ma.bacsurv.domain.DutyRole;
import ma.bacsurv.web.persistence.AssignmentEntity;
import ma.bacsurv.web.persistence.AssignmentRepository;
import ma.bacsurv.web.persistence.OperationRepository;
import ma.bacsurv.web.persistence.SolveJob;
import ma.bacsurv.web.persistence.SolveJobRepository;
import ma.bacsurv.web.persistence.TeacherEntity;
import ma.bacsurv.web.persistence.TeacherRepository;
import ma.bacsurv.web.service.CenterAdminService;
import ma.bacsurv.web.service.TeacherAdminService;
import ma.bacsurv.web.service.TeacherAdminService.Details;
import ma.bacsurv.web.service.TeacherImportService;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Keeping a pool once it has arrived: a teacher changes subject, somebody was
 * entered wrongly, somebody leaves. Re-importing forty-five rows to fix one of
 * them is what made the previous application unusable.
 */
@SpringBootTest
class TeacherAdminTest {

    @Autowired TeacherAdminService admin;
    @Autowired TeacherImportService pool;
    @Autowired CenterAdminService centers;
    @Autowired TeacherRepository teachers;
    @Autowired AssignmentRepository assignments;
    @Autowired ma.bacsurv.web.service.SessionAdminService sessions;
    @Autowired SolveJobRepository jobs;
    @Autowired OperationRepository operations;

    private long centre() {
        return centers.createCenter("Lycée Test " + System.nanoTime());
    }

    private static Details details(String matricule, String name, String subject) {
        return new Details(matricule, name, subject, "Lycée Ibn Sina", "F");
    }

    /**
     * A teacher who said in May that he is away on 5 June must not be given a
     * duty that day. The solver has always honoured unavailabilities; until now
     * nothing could record one, so the fact existed only in the administrator's
     * head and the distribution was quietly wrong.
     */
    @Test
    void absencesAreRecordedAndReadBack() {
        long centre = centre();
        admin.add(centre, details("D500100", "Youssef Berrada", "Physique et chimie"));

        admin.replaceAbsences(centre, "D500100", List.of(
                new TeacherAdminService.Absence(null, LocalDate.of(2026, 6, 5), null, null),
                new TeacherAdminService.Absence(null, LocalDate.of(2026, 6, 4),
                        LocalTime.of(14, 0), LocalTime.of(18, 0))));

        var absences = admin.absencesOf(centre, "D500100");
        assertEquals(2, absences.size());
        // the earliest day first: a list of dates is read as a calendar
        assertEquals(LocalDate.of(2026, 6, 4), absences.getFirst().date());
        assertEquals(LocalTime.of(14, 0), absences.getFirst().startTime());
        assertNull(absences.get(1).startTime(), "no hours means the whole day");

        assertEquals(2, pool.pool(centre).getFirst().absences(),
                "the pool has to show who cannot be given a duty");
    }

    /** Sending the whole list is what removes one: the screen states the truth. */
    @Test
    void replacingWithFewerRemovesTheRest() {
        long centre = centre();
        admin.add(centre, details("D500101", "Salma Idrissi", "Arabe"));
        admin.replaceAbsences(centre, "D500101", List.of(
                new TeacherAdminService.Absence(null, LocalDate.of(2026, 6, 5), null, null)));

        admin.replaceAbsences(centre, "D500101", List.of());

        assertTrue(admin.absencesOf(centre, "D500101").isEmpty());
    }

    /** Half a séance is not a séance: both hours, or neither. */
    @Test
    void halfStatedHoursAreRefused() {
        long centre = centre();
        admin.add(centre, details("D500102", "Karim Tazi", "SVT"));

        assertThrows(IllegalArgumentException.class,
                () -> admin.replaceAbsences(centre, "D500102", List.of(
                        new TeacherAdminService.Absence(null, LocalDate.of(2026, 6, 5),
                                LocalTime.of(8, 0), null))));

        assertThrows(IllegalArgumentException.class,
                () -> admin.replaceAbsences(centre, "D500102", List.of(
                        new TeacherAdminService.Absence(null, LocalDate.of(2026, 6, 5),
                                LocalTime.of(11, 0), LocalTime.of(8, 0)))),
                "an absence cannot end before it starts");
    }

    @Test
    void aTeacherCanBeAddedByHand() {
        long centre = centre();
        admin.add(centre, details("D500001", "Amina El Fassi", "Mathématiques"));

        var teachers = pool.pool(centre);
        assertEquals(1, teachers.size());
        assertEquals("D500001", teachers.getFirst().matricule());
        assertEquals("Mathématiques", teachers.getFirst().subject());
    }

    @Test
    void theSameMatriculeIsRefusedTwiceInOneCentre() {
        long centre = centre();
        admin.add(centre, details("D500002", "Youssef Benali", "Français"));

        var refused = assertThrows(IllegalArgumentException.class,
                () -> admin.add(centre, details("D500002", "Quelqu'un d'autre", "Anglais")));
        assertEquals("teacher.matricule.exists", refused.getMessage());
    }

    /** The matricule is per centre, so the same person may serve two. */
    @Test
    void theSameMatriculeIsFineInAnotherCentre() {
        long first = centre();
        long second = centre();
        admin.add(first, details("D500003", "Karima Idrissi", "Physique et chimie"));

        assertDoesNotThrow(() -> admin.add(second, details("D500003", "Karima Idrissi", "Physique et chimie")));
        assertEquals(1, pool.pool(first).size());
        assertEquals(1, pool.pool(second).size());
    }

    @Test
    void editingChangesTheDescriptionButNotTheIdentity() {
        long centre = centre();
        admin.add(centre, details("D500004", "Hakim Alaoui", "Histoire et géographie"));

        admin.edit(centre, "D500004",
                new Details("D500004", "Hakim Alaoui", "Éducation islamique", null, "M"));

        var teacher = pool.pool(centre).getFirst();
        assertEquals("D500004", teacher.matricule(), "the matricule is the identity, untouched");
        assertEquals("Éducation islamique", teacher.subject());
        assertNull(teacher.establishment(), "a blank établissement clears it rather than storing \"\"");
    }

    @Test
    void aTeacherWithNoHistoryCanBeRemoved() {
        long centre = centre();
        admin.add(centre, details("D500005", "Nadia Tazi", "SVT"));

        admin.remove(centre, "D500005");
        assertTrue(pool.pool(centre).isEmpty());
    }

    @Test
    void refusalsNameTheFieldThatIsMissing() {
        long centre = centre();

        assertEquals("teacher.matricule.required", assertThrows(IllegalArgumentException.class,
                () -> admin.add(centre, details("  ", "Sans matricule", "Anglais"))).getMessage());
        assertEquals("teacher.name.required", assertThrows(IllegalArgumentException.class,
                () -> admin.add(centre, details("D500006", "", "Anglais"))).getMessage());
        assertEquals("teacher.subject.required", assertThrows(IllegalArgumentException.class,
                () -> admin.add(centre, details("D500007", "Sans matière", " "))).getMessage());
    }

    /**
     * The whole point of the matricule is that a past session still counts.
     * Deleting somebody who has already served would take their réserve and
     * permanence turns out of the queue with them, and the next session would
     * hand a privilege to a colleague who has already had one.
     */
    @Test
    void aTeacherWhoHasAlreadyServedCannotBeRemoved() {
        long centre = centre();
        admin.add(centre, details("D500008", "Rachid Bennani", "Anglais"));
        TeacherEntity teacher = teachers.findByCenterIdAndMatricule(centre, "D500008").orElseThrow();

        // a duty of a session that was arrêtée: that is what "served" means
        long sessionId = centers.createSession(centre, "Bac 2026", "NATIONAL_2BAC",
                LocalDate.of(2026, 6, 4), LocalDate.of(2026, 6, 6));
        SolveJob job = new SolveJob(operations.findById(sessionId).orElseThrow(), null, 5);
        job.markDone("{}", true, 0, 0, 0);
        jobs.save(job);
        assignments.save(new AssignmentEntity(job, "S1-E1-R1-SURV-1", "S1", "E1", "R1",
                DutyRole.SURVEILLANCE, teacher));
        sessions.settle(sessionId);

        var refused = assertThrows(IllegalArgumentException.class,
                () -> admin.remove(centre, "D500008"));
        assertEquals("teacher.hasHistory", refused.getMessage());
        assertEquals(1, pool.pool(centre).size(), "the refusal must leave the pool untouched");
    }

    /**
     * A trial is not service.
     *
     * <p>A session may be solved a dozen times while its timetable is being
     * typed, and none of it is work anybody did. Somebody entered by mistake and
     * swept into one of those solves must stay deletable — counting it would
     * tell the administrator that a person he has never met has already served,
     * and leave the wrong name in the pool for good.
     */
    @Test
    void aTeacherWhoOnlyAppearsInATrialCanStillBeRemoved() {
        long centre = centre();
        admin.add(centre, details("D500009", "Saisi par erreur", "Anglais"));
        TeacherEntity teacher = teachers.findByCenterIdAndMatricule(centre, "D500009").orElseThrow();

        long sessionId = centers.createSession(centre, "Essai", "NATIONAL_2BAC",
                LocalDate.of(2026, 6, 4), LocalDate.of(2026, 6, 6));
        SolveJob job = new SolveJob(operations.findById(sessionId).orElseThrow(), null, 5);
        job.markDone("{}", true, 0, 0, 0);
        jobs.save(job);
        assignments.save(new AssignmentEntity(job, "S1-E1-R1-SURV-1", "S1", "E1", "R1",
                DutyRole.SURVEILLANCE, teacher));
        // the session is never arrêtée, so nothing here is history

        assertDoesNotThrow(() -> admin.remove(centre, "D500009"));
        assertEquals(0, pool.pool(centre).size());
    }

    @Test
    void editingSomebodyWhoIsNotInThePoolIsRefused() {
        long centre = centre();
        var refused = assertThrows(IllegalArgumentException.class,
                () -> admin.edit(centre, "D999999", details("D999999", "Fantôme", "Anglais")));
        assertEquals("teacher.unknown", refused.getMessage());
    }
}
