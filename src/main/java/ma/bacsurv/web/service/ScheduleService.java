package ma.bacsurv.web.service;

import ma.bacsurv.application.DutyGenerator;
import ma.bacsurv.application.ScheduleValidator;
import ma.bacsurv.application.ValidationReport;
import ma.bacsurv.domain.Duty;
import ma.bacsurv.domain.ExamOperation;
import ma.bacsurv.domain.Teacher;
import ma.bacsurv.io.ScheduleWriter;
import ma.bacsurv.web.persistence.AssignmentEntity;
import ma.bacsurv.web.persistence.AssignmentRepository;
import ma.bacsurv.web.persistence.OperationEntity;
import ma.bacsurv.web.persistence.SolveJob;
import ma.bacsurv.web.persistence.SolveJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads a stored schedule back as the domain objects the rules understand.
 *
 * The duties themselves are regenerated from the operation — the generator is
 * deterministic, so ids line up — and the stored rows say who holds each one.
 * That makes the schedule something the validator can judge at any time, not
 * only at the moment the solver finished.
 */
@Service
public class ScheduleService {

    /** A schedule in domain form, with the pool it was built from. */
    public record Materialised(ExamOperation operation, List<Duty> duties,
                               OperationAssembler.Pool pool,
                               ma.bacsurv.rules.SchedulingPolicy policy) {}

    private final SolveJobRepository jobs;
    private final AssignmentRepository assignments;
    private final OperationAssembler assembler;
    private final ScheduleWriter writer = new ScheduleWriter();

    public ScheduleService(SolveJobRepository jobs, AssignmentRepository assignments,
                           OperationAssembler assembler) {
        this.jobs = jobs;
        this.assignments = assignments;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true)
    public Optional<Materialised> materialise(long jobId) {
        Optional<SolveJob> job = jobs.findWithOperation(jobId);
        if (job.isEmpty()) return Optional.empty();

        List<AssignmentEntity> rows = assignments.findOfJob(jobId);
        if (rows.isEmpty()) return Optional.empty();

        OperationEntity operation = job.get().getOperation();
        ExamOperation domain = assembler.toDomain(operation);
        // the people named on the stored rows are carried whether or not they
        // are still in the year's pool: somebody who has since left the
        // establishment still held the duties he held
        OperationAssembler.Pool pool = assembler.poolFor(operation,
                rows.stream().map(AssignmentEntity::getTeacher).toList());

        Map<String, Long> teacherIdByDuty = new HashMap<>();
        rows.forEach(row -> {
            if (row.getTeacher() != null) teacherIdByDuty.put(row.getDutyId(), row.getTeacher().getId());
        });

        List<Duty> duties = new DutyGenerator().generate(domain, assembler.staffingOf(operation));
        for (Duty duty : duties) {
            Long teacherId = teacherIdByDuty.get(duty.id());
            if (teacherId != null) {
                Teacher teacher = pool.teacherById().get(teacherId);
                if (teacher != null) duty.assign(teacher);
            }
        }
        return Optional.of(new Materialised(domain, duties, pool,
                assembler.schedulingOf(operation)));
    }

    /**
     * The schedule as clients read it: assignments, workload, validation summary.
     *
     * <p>The duties themselves are rebuilt from the session's live timetable —
     * only who holds each one, and which room it was printed against, are
     * stored. Where the row remembers a room, the row wins.
     *
     * <p>Today that changes nothing: the stored value is the room's reference,
     * which no screen can edit, and a settled session's timetable is locked.
     * It matters for a draft whose filière is given different rooms after a
     * solve — the stored schedule then still shows what was actually solved —
     * and it is what keeps this reading independent of the live centre if the
     * reference ever stops being immutable. What protects a distributed
     * répartition from a room disappearing under it is the refusal in
     * CenterAdminService#deleteRoom, not this.
     */
    @Transactional(readOnly = true)
    public Optional<ScheduleWriter.Result> result(long jobId) {
        Map<String, String> printedRooms = new HashMap<>();
        assignments.findOfJob(jobId).forEach(row -> {
            if (row.getRoomRef() != null) printedRooms.put(row.getDutyId(), row.getRoomRef());
        });

        return materialise(jobId).map(schedule -> {
            ValidationReport report = ScheduleValidator.forPolicy(schedule.policy())
                    .validate(schedule.duties());
            ScheduleWriter.Result built = writer.build(schedule.operation().id(),
                    schedule.duties(), schedule.pool().teachers(), report);
            return withPrintedRooms(built, printedRooms);
        });
    }

    /** Puts back the room label each duty actually went out under. */
    private static ScheduleWriter.Result withPrintedRooms(ScheduleWriter.Result built,
                                                          Map<String, String> printedRooms) {
        if (printedRooms.isEmpty()) return built;

        List<ScheduleWriter.AssignmentRow> rows = built.assignments().stream()
                .map(row -> {
                    String printed = printedRooms.get(row.dutyId());
                    return printed == null || printed.equals(row.roomId()) ? row
                            : new ScheduleWriter.AssignmentRow(row.dutyId(), row.slotId(),
                                    row.date(), row.start(), row.end(), row.role(), row.examId(),
                                    row.subject(), row.stream(), printed, row.teacherId(),
                                    row.teacherMatricule(), row.teacherName());
                })
                .toList();

        return new ScheduleWriter.Result(built.operationId(), built.feasible(),
                built.hardViolations(), built.softViolations(), built.unfilled(),
                built.hardViolationDetails(), rows, built.workload());
    }
}
