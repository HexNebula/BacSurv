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
                               OperationAssembler.Pool pool) {}

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
        OperationAssembler.Pool pool = assembler.poolFor(operation);

        Map<String, Long> teacherIdByDuty = new HashMap<>();
        rows.forEach(row -> {
            if (row.getTeacher() != null) teacherIdByDuty.put(row.getDutyId(), row.getTeacher().getId());
        });

        List<Duty> duties = new DutyGenerator().generate(domain);
        for (Duty duty : duties) {
            Long teacherId = teacherIdByDuty.get(duty.id());
            if (teacherId != null) {
                Teacher teacher = pool.teacherById().get(teacherId);
                if (teacher != null) duty.assign(teacher);
            }
        }
        return Optional.of(new Materialised(domain, duties, pool));
    }

    /** The schedule as clients read it: assignments, workload, validation summary. */
    @Transactional(readOnly = true)
    public Optional<ScheduleWriter.Result> result(long jobId) {
        return materialise(jobId).map(schedule -> {
            ValidationReport report = ScheduleValidator.withDefaults().validate(schedule.duties());
            return writer.build(schedule.operation().id(), schedule.duties(),
                    schedule.pool().teachers(), report);
        });
    }
}
