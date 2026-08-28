package ma.bacsurv.web;

import ma.bacsurv.web.persistence.AssignmentRepository;
import ma.bacsurv.web.persistence.OperationRepository;
import ma.bacsurv.web.persistence.SchoolYearEntity;
import ma.bacsurv.web.persistence.SolveJob;
import ma.bacsurv.web.persistence.SolveJobRepository;
import ma.bacsurv.web.persistence.TeacherRepository;
import ma.bacsurv.web.service.CenterAdminService;
import ma.bacsurv.web.service.RefusedException;
import ma.bacsurv.web.service.SchoolYearService;
import ma.bacsurv.web.service.SessionAdminService;
import ma.bacsurv.web.service.TeacherAdminService;
import ma.bacsurv.web.service.TeacherAdminService.Details;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The school year: what a pool and a set of sessions belong to.
 *
 * <p>Two things depend on it. Who is given duties — a teacher who moved to
 * another school in July must not be asked to work in September, and must not
 * be deleted either, because the year he did serve is a record of what
 * happened. And how far fairness looks back — the counter starts at zero every
 * September, so nobody is compensated in 2028 for a duty done in 2026.
 */
@SpringBootTest
class SchoolYearTest {

    @Autowired SchoolYearService years;
    @Autowired CenterAdminService centers;
    @Autowired TeacherAdminService teacherAdmin;
    @Autowired SessionAdminService sessions;
    @Autowired TeacherRepository teachers;
    @Autowired OperationRepository operations;
    @Autowired SolveJobRepository jobs;
    @Autowired AssignmentRepository assignments;

    private long centre() {
        return centers.createCenter("Lycée Année " + System.nanoTime());
    }

    private long teacher(long centreId, String matricule) {
        teacherAdmin.add(centreId, new Details(matricule, "Enseignant " + matricule,
                "Arabe", null, "MALE"));
        return teachers.findByCenterIdAndMatricule(centreId, matricule).orElseThrow().getId();
    }

    /** A teacher is addressed by matricule, as everywhere else in the API. */
    private static final String LEAVER = "M1";

    /** The year turns over in September, not in January. */
    @Test
    void aJuneSessionBelongsToTheYearThatBeganTheAutumnBefore() {
        assertEquals("2026-2027", SchoolYearEntity.labelOf(LocalDate.of(2027, 6, 4)));
        assertEquals("2026-2027", SchoolYearEntity.labelOf(LocalDate.of(2026, 9, 1)));
        assertEquals("2025-2026", SchoolYearEntity.labelOf(LocalDate.of(2026, 8, 31)));
        assertEquals("2027-2028", SchoolYearEntity.labelOf(LocalDate.of(2027, 12, 25)));
    }

    /** A centre has a year from the moment it exists, or nothing can be entered. */
    @Test
    void aNewCentreAlreadyHasAYear() {
        long centre = centre();
        List<SchoolYearService.YearView> all = years.yearsOf(centre);
        assertEquals(1, all.size());
        assertTrue(all.getFirst().current());
    }

    /** The session's dates say which year it is in; nobody is asked to type it. */
    @Test
    void aSessionTakesTheYearOfItsDates() {
        long centre = centre();
        long session = centers.createSession(centre, "Bac", "NATIONAL_2BAC",
                LocalDate.of(2027, 6, 4), LocalDate.of(2027, 6, 6));

        assertEquals("2026-2027",
                operations.findWithYear(session).orElseThrow().getSchoolYear().getLabel());
    }

    /**
     * September is one editing session, not forty-five: the new year starts
     * from the previous year's list.
     */
    @Test
    void openingAYearCarriesThePreviousPool() {
        long centre = centre();
        String thisYear = years.current(centre).getLabel();
        teacher(centre, "M1");
        teacher(centre, "M2");

        int from = Integer.parseInt(thisYear.substring(0, 4)) + 1;
        long next = years.open(centre, from + "-" + (from + 1));

        assertEquals(2, teachers.findPoolOfYear(next).size(), "both carried over");
        assertEquals(2, teachers.findPoolOfYear(years.yearsOf(centre).getLast().id()).size(),
                "and the old year still has them");
    }

    /**
     * The act the whole model exists for: somebody leaves, and nothing he did
     * is destroyed.
     */
    @Test
    void aTeacherWhoLeavesKeepsEverythingHeDid() {
        long centre = centre();
        String thisYear = years.current(centre).getLabel();
        long leaver = teacher(centre, "M1");
        teacher(centre, "M2");
        long served = years.current(centre).getId();

        int from = Integer.parseInt(thisYear.substring(0, 4)) + 1;
        long next = years.open(centre, from + "-" + (from + 1));
        years.removeFromYear(next, LEAVER);

        assertEquals(1, teachers.findPoolOfYear(next).size(), "he is not asked to work again");
        assertEquals(2, teachers.findPoolOfYear(served).size(),
                "but the year he served still holds him");
        assertTrue(teachers.findById(leaver).isPresent(), "and his row is untouched");
    }

    /** He comes back two years later, and is the same person. */
    @Test
    void aTeacherWhoReturnsIsTheSamePerson() {
        long centre = centre();
        String thisYear = years.current(centre).getLabel();
        long person = teacher(centre, "M1");

        int from = Integer.parseInt(thisYear.substring(0, 4)) + 1;
        long next = years.open(centre, from + "-" + (from + 1));
        years.removeFromYear(next, "M1");
        years.addToYear(next, "M1");

        assertEquals(1, teachers.findPoolOfYear(next).size());
        assertEquals(person, teachers.findPoolOfYear(next).getFirst().getId(),
                "the matricule is the identity, so it is the same row");
    }

    /**
     * The bug this was built for. Fairness stops at September: a settled
     * session of last year must not seed this year's counters.
     */
    @Test
    void fairnessDoesNotReachBackPastSeptember() {
        long centre = centre();
        long lastYear = centers.createSession(centre, "Bac 2027", "NATIONAL_2BAC",
                LocalDate.of(2027, 6, 4), LocalDate.of(2027, 6, 6));
        long thisYear = centers.createSession(centre, "Bac 2028", "NATIONAL_2BAC",
                LocalDate.of(2028, 6, 4), LocalDate.of(2028, 6, 6));

        SolveJob job = new SolveJob(operations.findById(lastYear).orElseThrow(), null, 10);
        job.markDone("{}", true, 0, 0, 0);
        jobs.save(job);
        sessions.settle(lastYear);

        // the two sessions are in different years, so neither sees the other
        Long lastYearId = operations.findWithYear(lastYear).orElseThrow().getSchoolYear().getId();
        Long thisYearId = operations.findWithYear(thisYear).orElseThrow().getSchoolYear().getId();
        assertNotEquals(lastYearId, thisYearId, "2026-2027 and 2027-2028");

        assertTrue(assignments.priorWorkloadOfYear(thisYearId, thisYear).isEmpty(),
                "last year's duties are not this year's history");
        assertTrue(assignments.privilegeTurnsOfYear(thisYearId, thisYear).isEmpty(),
                "and last year's turns do not settle this year's queue");
    }

    /** The label is what a whole year of work is filed under; a typo is costly. */
    @Test
    void theLabelIsChecked() {
        long centre = centre();
        assertEquals("year.label.format", assertThrows(IllegalArgumentException.class,
                () -> years.open(centre, "2026")).getMessage());
        assertEquals("year.label.span", assertThrows(IllegalArgumentException.class,
                () -> years.open(centre, "2026-2028")).getMessage());
        assertEquals("year.label.required", assertThrows(IllegalArgumentException.class,
                () -> years.open(centre, "  ")).getMessage());

        years.open(centre, "2030-2031");
        assertEquals("year.exists", assertThrows(RefusedException.class,
                () -> years.open(centre, "2030-2031")).getMessage());
    }
}
