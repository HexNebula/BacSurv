package ma.bacsurv.web;

import ma.bacsurv.domain.DutyRole;
import ma.bacsurv.web.persistence.AssignmentEntity;
import ma.bacsurv.web.persistence.AssignmentRepository;
import ma.bacsurv.web.persistence.OperationEntity;
import ma.bacsurv.web.persistence.OperationRepository;
import ma.bacsurv.web.persistence.SolveJob;
import ma.bacsurv.web.persistence.SolveJobRepository;
import ma.bacsurv.web.persistence.TeacherEntity;
import ma.bacsurv.web.persistence.TeacherRepository;
import ma.bacsurv.web.service.ArchiveService;
import ma.bacsurv.web.service.CenterAdminService;
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
 * A year as a record, for the question somebody eventually asks: "pourquoi
 * j'ai eu trois surveillances de plus que lui".
 *
 * <p>By the time it is asked the year is over, the pool has changed, and the
 * person asking may have left. So the archive is built from what was actually
 * handed out rather than from the pool as it stands today.
 */
@SpringBootTest
class ArchiveTest {

    @Autowired ArchiveService archive;
    @Autowired SchoolYearService years;
    @Autowired CenterAdminService centers;
    @Autowired TeacherAdminService teacherAdmin;
    @Autowired SessionAdminService sessions;
    @Autowired TeacherRepository teachers;
    @Autowired OperationRepository operations;
    @Autowired SolveJobRepository jobs;
    @Autowired AssignmentRepository assignments;
    @Autowired ma.bacsurv.web.service.OperationAssembler assembler;

    private static final LocalDate JUNE = LocalDate.of(2027, 6, 4);

    private record Fixture(long centreId, long yearId, long sessionId) {}

    /** A centre, a session in June 2027, and a pool of three. */
    private Fixture year() {
        long centre = centers.createCenter("Lycée Archive " + System.nanoTime());
        long session = centers.createSession(centre, "Bac 2027", "NATIONAL_2BAC", JUNE, JUNE);
        for (String matricule : List.of("A1", "A2", "A3")) {
            teacherAdmin.add(centre, new Details(matricule, "Enseignant " + matricule,
                    "Arabe", null, "MALE"));
        }
        long yearId = operations.findWithYear(session).orElseThrow().getSchoolYear().getId();
        return new Fixture(centre, yearId, session);
    }

    /** Duties as a finished, settled solve would have recorded them. */
    private void handOut(Fixture fixture, String matricule, DutyRole role, int times) {
        SolveJob job = jobs.ofOperation(fixture.sessionId()).stream().findFirst()
                .orElseGet(() -> {
                    SolveJob fresh = new SolveJob(
                            operations.findById(fixture.sessionId()).orElseThrow(), null, 10);
                    fresh.markDone("{}", true, 0, 0, 0);
                    return jobs.save(fresh);
                });
        TeacherEntity teacher = teachers
                .findByCenterIdAndMatricule(fixture.centreId(), matricule).orElseThrow();
        for (int i = 0; i < times; i++) {
            assignments.save(new AssignmentEntity(job,
                    role.name() + "-" + matricule + "-" + i,
                    "S1", "E1", null, role, teacher));
        }
    }

    @Test
    void theTallySaysWhatEachTeacherDid() {
        Fixture fixture = year();
        handOut(fixture, "A1", DutyRole.SURVEILLANCE, 5);
        handOut(fixture, "A1", DutyRole.PERMANENCE, 1);
        handOut(fixture, "A2", DutyRole.SURVEILLANCE, 2);
        sessions.settle(fixture.sessionId());

        ArchiveService.Archive record = archive.of(fixture.yearId());
        assertEquals("2026-2027", record.label());

        var byMatricule = record.tally().stream()
                .collect(java.util.stream.Collectors.toMap(ArchiveService.Tally::matricule, t -> t));
        assertEquals(5, byMatricule.get("A1").surveillance());
        assertEquals(1, byMatricule.get("A1").permanence());
        assertEquals(6, byMatricule.get("A1").total());
        assertEquals(2, byMatricule.get("A2").total());

        // the one who did nothing is in the record too: leaving him out is how
        // a fairness question gets the wrong answer
        assertTrue(byMatricule.containsKey("A3"));
        assertEquals(0, byMatricule.get("A3").total());

        // heaviest first, which is the order the question is asked in
        assertEquals("A1", record.tally().getFirst().matricule());
    }

    /** A trial is not work anybody did, so it is not in the record. */
    @Test
    void adraftSessionContributesNothing() {
        Fixture fixture = year();
        handOut(fixture, "A1", DutyRole.SURVEILLANCE, 5);

        ArchiveService.Archive record = archive.of(fixture.yearId());
        assertTrue(record.tally().stream().allMatch(t -> t.total() == 0),
                "the session was never arrêtée: " + record.tally());
        assertEquals("DRAFT", record.sessions().getFirst().state());
        assertNull(record.sessions().getFirst().scheduleJobId());
    }

    /**
     * The detail of who was where is not copied into the archive: the settled
     * session names the solve it went out with, and that is already readable.
     */
    @Test
    void aSettledSessionPointsAtTheDistributionThatWentOut() {
        Fixture fixture = year();
        handOut(fixture, "A1", DutyRole.SURVEILLANCE, 3);
        sessions.settle(fixture.sessionId());

        ArchiveService.ArchivedSession session = archive.of(fixture.yearId()).sessions().getFirst();
        assertEquals("SETTLED", session.state());
        assertNotNull(session.scheduleJobId());
        assertEquals(3, session.dutyCount());
        assertEquals(3, assignments.findOfJob(session.scheduleJobId()).size(),
                "and the id really does lead to the duties");
    }

    /**
     * Somebody who left is still in the record of the year he served — which is
     * the whole reason a departure is membership rather than deletion.
     */
    @Test
    void aTeacherWhoLeftIsStillInTheYearHeServed() {
        Fixture fixture = year();
        handOut(fixture, "A1", DutyRole.SURVEILLANCE, 4);
        sessions.settle(fixture.sessionId());

        long next = years.open(fixture.centreId(), "2027-2028");
        years.removeFromYear(next, "A1");

        var record = archive.of(fixture.yearId());
        assertTrue(record.tally().stream().anyMatch(
                t -> t.matricule().equals("A1") && t.total() == 4),
                "his year is still his year");

        // and he is offered back: the screen that opens a year needs to find him
        ArchiveService.YearPool pool = archive.poolOf(next);
        assertTrue(pool.former().stream().anyMatch(m -> m.matricule().equals("A1")));
        assertFalse(pool.members().stream().anyMatch(m -> m.matricule().equals("A1")));
        assertEquals(2, pool.members().size(), "the two who stayed were untouched");
    }

    /**
     * The tally and the session it came from must agree about the same
     * afternoon.
     *
     * <p>A schedule is rebuilt from the live timetable, with only the holder of
     * each duty read from storage. Resolve that holder against the year's pool
     * alone and a teacher who has since left comes back as nobody — so the
     * archive would say Ahmed did four surveillances while the session he did
     * them in showed four empty rows.
     */
    @Test
    void aDepartedTeacherStillHoldsTheDutiesHeHeld() {
        Fixture fixture = year();
        handOut(fixture, "A1", DutyRole.SURVEILLANCE, 2);
        sessions.settle(fixture.sessionId());

        // taken out of the very year he served — reachable by hand, and the
        // case where the schedule and the tally can disagree
        years.removeFromYear(fixture.yearId(), "A1");

        // fetched through the query that loads absences with it, since the
        // assembler reads them and this test is outside a transaction
        TeacherEntity departed = teachers.findPoolOfCenter(fixture.centreId()).stream()
                .filter(t -> t.getMatricule().equals("A1")).findFirst().orElseThrow();
        OperationEntity operation = operations.findWithYear(fixture.sessionId()).orElseThrow();

        assertTrue(teachers.findPoolOfYear(fixture.yearId()).stream()
                        .noneMatch(t -> t.getId().equals(departed.getId())),
                "he is out of the pool, so he is offered no new work");
        assertNull(assembler.poolFor(operation).teacherById().get(departed.getId()),
                "the solver's pool does not know him");
        assertNotNull(assembler.poolFor(operation, List.of(departed))
                        .teacherById().get(departed.getId()),
                "but reading back a distribution does: he held those duties");

        // and the year tally says the same
        assertEquals(2, archive.of(fixture.yearId()).tally().stream()
                .filter(t -> t.matricule().equals("A1")).findFirst().orElseThrow().total());
    }

    /** The pool of a year says who actually served, not only who was listed. */
    @Test
    void thePoolSaysWhoServed() {
        Fixture fixture = year();
        handOut(fixture, "A1", DutyRole.RESERVE, 1);
        sessions.settle(fixture.sessionId());

        ArchiveService.YearPool pool = archive.poolOf(fixture.yearId());
        assertEquals(3, pool.members().size());
        assertTrue(pool.members().stream()
                .filter(m -> m.matricule().equals("A1")).findFirst().orElseThrow().served());
        assertFalse(pool.members().stream()
                .filter(m -> m.matricule().equals("A2")).findFirst().orElseThrow().served());
    }
}
