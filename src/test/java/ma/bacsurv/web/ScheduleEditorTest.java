package ma.bacsurv.web;

import ma.bacsurv.io.ScheduleWriter;
import ma.bacsurv.web.persistence.AssignmentEntity;
import ma.bacsurv.web.persistence.AssignmentRepository;
import ma.bacsurv.web.persistence.TeacherEntity;
import ma.bacsurv.web.persistence.TeacherRepository;
import ma.bacsurv.web.service.JobView;
import ma.bacsurv.web.service.OperationView;
import ma.bacsurv.web.service.ScheduleEditor;
import ma.bacsurv.web.service.SolveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Hand-made changes: the administrator decides, but is told what the decision
 * costs — which rule it breaks, and which preferences it worsens.
 */
@SpringBootTest
class ScheduleEditorTest {

    @Autowired SolveService solveService;
    @Autowired ScheduleEditor editor;
    @Autowired AssignmentRepository assignments;
    @Autowired TeacherRepository teachers;

    private long jobId;
    private long centerId;

    @BeforeEach
    void solveOnce() throws Exception {
        String sample = Files.readString(Path.of("samples", "operation-sample.json"))
                .replace("\"operation\": { \"id\": \"NAT-2026-JUIN\"",
                        "\"center\": { \"name\": \"Centre Edition\" },\n"
                                + "  \"operation\": { \"id\": \"OP-EDIT-" + System.nanoTime() + "\"");
        OperationView operation = solveService.upload("edit.json", sample);
        JobView job = solveService.submit(operation.id(), 8);
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(500))
                .until(() -> !solveService.job(job.id()).isRunning());
        jobId = job.id();
        centerId = teachers.findByCenterIdAndMatricule(
                        centerIdOf(), "D100001").map(t -> t.getCenter().getId()).orElseThrow();
    }

    /** The centre created by this test's import, found through any of its teachers. */
    private Long centerIdOf() {
        return assignments.findOfJob(jobId).stream()
                .map(AssignmentEntity::getTeacher)
                .filter(java.util.Objects::nonNull)
                .map(t -> t.getCenter().getId())
                .findFirst().orElseThrow();
    }

    private TeacherEntity teacher(String matricule) {
        return teachers.findByCenterIdAndMatricule(centerId, matricule).orElseThrow();
    }

    private List<AssignmentEntity> surveillanceOf(String matricule) {
        return assignments.findOfJob(jobId).stream()
                .filter(a -> a.getTeacher() != null
                        && a.getTeacher().getMatricule().equals(matricule))
                .toList();
    }

    @Test
    void refusesToPutSomeoneInTwoPlacesAtOnce() {
        AssignmentEntity duty = assignments.findOfJob(jobId).stream()
                .filter(a -> a.getTeacher() != null).findFirst().orElseThrow();
        // whoever else works that same slot cannot also take this duty
        AssignmentEntity sameSlot = assignments.findOfJob(jobId).stream()
                .filter(a -> a.getSlotRef().equals(duty.getSlotRef()))
                .filter(a -> a.getTeacher() != null
                        && !a.getTeacher().getId().equals(duty.getTeacher().getId()))
                .findFirst().orElseThrow();

        var review = editor.review(jobId, duty.getDutyId(), sameSlot.getTeacher().getId());

        assertFalse(review.isLegal());
        assertTrue(review.breaches().stream().anyMatch(b -> b.code().equals("change.breach.busy")),
                review.breaches().toString());

        assertThrows(ScheduleEditor.IllegalChangeException.class,
                () -> editor.apply(jobId, duty.getDutyId(), sameSlot.getTeacher().getId(), false));
    }

    @Test
    void refusesAnAbsentTeacher() {
        // D100008 is declared unavailable for the whole of 1 June in the sample
        AssignmentEntity firstOfJune = assignments.findOfJob(jobId).stream()
                .filter(a -> a.getSlotRef().equals("S1"))
                .findFirst().orElseThrow();

        var review = editor.review(jobId, firstOfJune.getDutyId(), teacher("D100008").getId());

        assertTrue(review.breaches().stream()
                        .anyMatch(b -> b.code().equals("change.breach.unavailable")),
                review.breaches().toString());
    }

    @Test
    void refusesSurveillanceOfOnesOwnSubject() {
        // D100001 teaches Mathématiques; E1 is the Maths paper
        AssignmentEntity mathsRoom = assignments.findOfJob(jobId).stream()
                .filter(a -> "E1".equals(a.getExamRef()) && a.getRoomRef() != null)
                .findFirst().orElseThrow();

        var review = editor.review(jobId, mathsRoom.getDutyId(), teacher("D100001").getId());

        assertTrue(review.breaches().stream()
                        .anyMatch(b -> b.code().equals("change.breach.ownSubject")),
                review.breaches().toString());
    }

    @Test
    void refusesPermanenceByANonSpecialist() {
        AssignmentEntity permanence = assignments.findOfJob(jobId).stream()
                .filter(a -> a.getRole().name().equals("PERMANENCE")
                        && "E1".equals(a.getExamRef()))
                .findFirst().orElseThrow();

        // a French teacher cannot answer for the Maths paper
        var review = editor.review(jobId, permanence.getDutyId(), teacher("D100005").getId());

        assertTrue(review.breaches().stream()
                        .anyMatch(b -> b.code().equals("change.breach.notSpecialist")),
                review.breaches().toString());
    }

    @Test
    void acceptsALegalSwapAndReportsWhatItCosts() {
        // free the duty first so a second teacher can legally take it
        AssignmentEntity reserve = assignments.findOfJob(jobId).stream()
                .filter(a -> a.getRole().name().equals("RESERVE"))
                .findFirst().orElseThrow();
        String dutyId = reserve.getDutyId();
        editor.apply(jobId, dutyId, null, false);

        TeacherEntity candidate = teachers.findPoolOfCenter(centerId).stream()
                .filter(t -> surveillanceOf(t.getMatricule()).stream()
                        .noneMatch(a -> a.getSlotRef().equals(reserve.getSlotRef())))
                .findFirst().orElseThrow();

        var review = editor.review(jobId, dutyId, candidate.getId());
        assertTrue(review.isLegal(), review.breaches().toString());
        assertEquals(review.newHolderDutiesBefore() + 1, review.newHolderDutiesAfter(),
                "the review says what the change does to their load");

        editor.apply(jobId, dutyId, candidate.getId(), false);

        ScheduleWriter.Result after = solveService.schedule(jobId).orElseThrow();
        assertTrue(after.assignments().stream()
                        .anyMatch(a -> a.dutyId().equals(dutyId)
                                && candidate.getMatricule().equals(a.teacherMatricule())),
                "the stored schedule reflects the change");
    }

    @Test
    void anInsistedChangeIsSavedButStillShowsAsAViolation() {
        AssignmentEntity duty = assignments.findOfJob(jobId).stream()
                .filter(a -> a.getTeacher() != null).findFirst().orElseThrow();
        AssignmentEntity sameSlot = assignments.findOfJob(jobId).stream()
                .filter(a -> a.getSlotRef().equals(duty.getSlotRef()))
                .filter(a -> a.getTeacher() != null
                        && !a.getTeacher().getId().equals(duty.getTeacher().getId()))
                .findFirst().orElseThrow();

        editor.apply(jobId, duty.getDutyId(), sameSlot.getTeacher().getId(), true);

        ScheduleWriter.Result after = solveService.schedule(jobId).orElseThrow();
        assertFalse(after.feasible(), "the schedule is no longer legal, and says so");
        assertTrue(after.hardViolations() > 0);
    }

    @Test
    void pinningIsRemembered() {
        AssignmentEntity duty = assignments.findOfJob(jobId).stream()
                .filter(a -> a.getTeacher() != null).findFirst().orElseThrow();

        editor.pin(jobId, duty.getDutyId(), true);

        assertTrue(assignments.findByJobIdAndDutyId(jobId, duty.getDutyId())
                .orElseThrow().isPinned());
    }
}
