package ma.bacsurv.web;

import ma.bacsurv.web.persistence.OperationRepository;
import ma.bacsurv.web.persistence.SolveJob;
import ma.bacsurv.web.persistence.SolveJobRepository;
import ma.bacsurv.web.service.CenterAdminService;
import ma.bacsurv.web.service.JobView;
import ma.bacsurv.web.service.ReadinessService;
import ma.bacsurv.web.service.ReadinessService.State;
import ma.bacsurv.web.service.SolveService;
import ma.bacsurv.web.service.TeacherAdminService;
import ma.bacsurv.web.service.TeacherAdminService.Absence;
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
 * A distribution computed before the last change looks exactly like a fresh
 * one. That is how a répartition gets handed out that no longer accounts for
 * an absence recorded yesterday, so the application has to know the difference.
 */
@SpringBootTest
class StaleDistributionTest {

    @Autowired SolveService solveService;
    @Autowired ReadinessService readiness;
    @Autowired CenterAdminService centers;
    @Autowired TeacherAdminService teacherAdmin;
    @Autowired TimetableService timetable;
    @Autowired SolveJobRepository jobs;
    @Autowired OperationRepository operations;
    @Autowired ma.bacsurv.web.service.OperationConfigService configs;

    private static final LocalDate DAY = LocalDate.of(2026, 6, 4);

    private record Fixture(long centreId, long sessionId) {}

    /** A centre with a session that has been distributed once. */
    private Fixture solved(String name) {
        long centre = centers.createCenter(name + System.nanoTime());
        long session = centers.createSession(centre, "Bac", "NATIONAL_2BAC", DAY, DAY);
        centers.addRooms(centre, 2, "Salle");
        for (int i = 1; i <= 12; i++) {
            teacherAdmin.add(centre, new Details("S90000" + i, "Enseignant " + i,
                    "Anglais", null, "F"));
        }
        List<Long> rooms = centers.detail(centre).rooms().stream()
                .map(CenterAdminService.RoomView::id).toList();
        long stream = timetable.addStream(session, "Lettres", rooms);
        timetable.setExam(session, stream, "Anglais", DAY,
                LocalTime.of(8, 0), LocalTime.of(10, 0));

        SolveJob job = jobs.save(new SolveJob(operations.findById(session).orElseThrow(),
                null, 30));
        job.markDone("{}", true, 0, 0, 0);
        jobs.save(job);
        return new Fixture(centre, session);
    }

    private JobView jobOf(long sessionId) {
        return solveService.recentJobs().stream()
                .filter(view -> view.operationId().equals(sessionId))
                .findFirst().orElseThrow();
    }

    private State distributionState(long sessionId) {
        return readiness.of(sessionId).steps().stream()
                .filter(step -> step.key().equals("distribution"))
                .findFirst().orElseThrow().state();
    }

    @Test
    void aDistributionNothingHasTouchedIsCurrent() {
        Fixture fixture = solved("Lycée Frais ");

        assertFalse(jobOf(fixture.sessionId()).stale());
        assertEquals(State.READY, distributionState(fixture.sessionId()));
    }

    /** The session's own inputs: an épreuve added after the solve. */
    @Test
    void changingTheTimetableDatesTheDistribution() {
        Fixture fixture = solved("Lycée Planning ");

        long stream = timetable.timetable(fixture.sessionId()).streams().getFirst().id();
        timetable.setExam(fixture.sessionId(), stream, "Anglais", DAY,
                LocalTime.of(15, 0), LocalTime.of(17, 0));

        assertTrue(jobOf(fixture.sessionId()).stale(),
                "an épreuve nobody was assigned to is not the same distribution");
        assertEquals(State.CHECK, distributionState(fixture.sessionId()));
    }

    /**
     * The centre's, not the session's: an absence belongs to the pool and
     * invalidates every session solved before it was recorded.
     */
    @Test
    void recordingAnAbsenceDatesTheDistribution() {
        Fixture fixture = solved("Lycée Absence ");

        teacherAdmin.replaceAbsences(fixture.centreId(), "S900001",
                List.of(new Absence(null, DAY, null, null)));

        assertTrue(jobOf(fixture.sessionId()).stale(),
                "somebody who is away may be holding a duty in what is on screen");
    }

    @Test
    void changingTheRulesDatesTheDistribution() {
        Fixture fixture = solved("Lycée Règles ");

        // one more surveillant per room is duties nobody has been given
        configs.save(fixture.sessionId(), 3, "PERCENTAGE", 0.10, 0,
                3, "SOFT", 0, "HARD", false, 30);

        assertTrue(jobOf(fixture.sessionId()).stale());
    }

    /** Renaming the centre changes nothing a solver reads. */
    @Test
    void thePaperIdentityDoesNotDateAnything() {
        Fixture fixture = solved("Lycée Papier ");

        centers.editCenter(fixture.centreId(), "Lycée Papier renommé",
                new CenterAdminService.CenterIdentity("AREF", "Direction", "Commune", "REF"));

        assertFalse(jobOf(fixture.sessionId()).stale(),
                "an académie printed on a convocation is not part of the distribution");
    }
}
