package ma.bacsurv.web.service;

import ma.bacsurv.application.DutyGenerator;
import ma.bacsurv.application.ScheduleValidator;
import ma.bacsurv.application.StaffingCheck;
import ma.bacsurv.application.ValidationReport;
import ma.bacsurv.domain.Duty;
import ma.bacsurv.domain.DutyRole;
import ma.bacsurv.domain.ExamOperation;
import ma.bacsurv.domain.Teacher;
import ma.bacsurv.io.InputMapper;
import ma.bacsurv.io.ScheduleWriter;
import ma.bacsurv.rules.SchedulingPolicy;
import ma.bacsurv.solver.SolverSettings;
import ma.bacsurv.solver.TimefoldScheduler;
import ma.bacsurv.web.persistence.AssignmentEntity;
import ma.bacsurv.web.persistence.AssignmentRepository;
import ma.bacsurv.web.persistence.OperationEntity;
import ma.bacsurv.web.persistence.OperationFile;
import ma.bacsurv.web.persistence.OperationFileRepository;
import ma.bacsurv.web.persistence.OperationRepository;
import ma.bacsurv.web.persistence.SolveJob;
import ma.bacsurv.web.persistence.SolveJobRepository;
import ma.bacsurv.web.persistence.TeacherEntity;
import ma.bacsurv.web.persistence.TeacherRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Import an operation, submit a solve, poll for the result. Solving never
 * blocks a request thread: today it runs on a local executor, tomorrow the
 * same interface can hand the job to a worker process without touching callers.
 *
 * Entities stay inside this class; callers receive views, so no lazy
 * association is ever touched outside a transaction.
 */
@Service
public class SolveService {

    private static final Logger log = LoggerFactory.getLogger(SolveService.class);

    private final OperationFileRepository files;
    private final OperationRepository operations;
    private final TeacherRepository teachers;
    private final SolveJobRepository jobs;
    private final AssignmentRepository assignments;
    private final OperationImporter importer;
    private final OperationAssembler assembler;
    private final ScheduleService scheduleService;
    private final SolveService self;

    public SolveService(OperationFileRepository files, OperationRepository operations,
                        TeacherRepository teachers, SolveJobRepository jobs,
                        AssignmentRepository assignments, OperationImporter importer,
                        OperationAssembler assembler, ScheduleService scheduleService,
                        @Lazy SolveService self) {
        this.files = files;
        this.operations = operations;
        this.teachers = teachers;
        this.jobs = jobs;
        this.assignments = assignments;
        this.importer = importer;
        this.assembler = assembler;
        this.scheduleService = scheduleService;
        this.self = self; // proxy, so @Async/@Transactional apply to self-calls
    }

    /** Parses the file, then stores the center, its pool and the operation. */
    @Transactional
    public OperationView upload(String name, String content) {
        InputMapper.ParsedOperation parsed = new InputMapper().readJson(content);
        OperationFile file = files.save(new OperationFile(name, content));
        return OperationView.of(importer.importOperation(parsed), file.getId());
    }

    /** Hours the pool cannot cover, empty when the operation can be staffed. */
    @Transactional(readOnly = true)
    public List<StaffingCheck.Shortage> staffingShortages(long operationId) {
        OperationEntity operation = operations.findWithCenter(operationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "no operation with id " + operationId));
        ExamOperation domain = assembler.toDomain(operation);
        return StaffingCheck.forPolicy(assembler.schedulingOf(operation)).check(domain,
                assembler.poolFor(operation).teachers(),
                new DutyGenerator().generate(domain, assembler.staffingOf(operation)));
    }

    /** Duties nobody in the pool may take — usually a subject with no specialist. */
    @Transactional(readOnly = true)
    public List<StaffingCheck.Unfillable> unfillableDuties(long operationId) {
        OperationEntity operation = operations.findWithCenter(operationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "no operation with id " + operationId));
        ExamOperation domain = assembler.toDomain(operation);
        return StaffingCheck.forPolicy(assembler.schedulingOf(operation)).unfillable(
                assembler.poolFor(operation).teachers(),
                new DutyGenerator().generate(domain, assembler.staffingOf(operation)));
    }

    @Transactional
    public JobView submit(long operationId, int timeLimitSeconds) {
        OperationEntity operation = operations.findWithCenter(operationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "no operation with id " + operationId));

        // refuse impossible work up front rather than after a long solve
        List<StaffingCheck.Shortage> shortages = staffingShortages(operationId);
        List<StaffingCheck.Unfillable> unfillable = unfillableDuties(operationId);
        if (!shortages.isEmpty() || !unfillable.isEmpty())
            throw new InsufficientStaffException(shortages, unfillable);

        SolveJob job = jobs.saveAndFlush(new SolveJob(operation, null, timeLimitSeconds));
        JobView view = JobView.of(job);
        // start only once the row is committed, otherwise the solver thread
        // may look for a job the database does not show yet
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override public void afterCommit() { self.run(view.id()); }
                });
        return view;
    }

    /** Runs on the solver pool; each database touch has its own transaction. */
    @Async("solverExecutor")
    public void run(long jobId) {
        SolveInput input;
        try {
            input = self.start(jobId);
        } catch (RuntimeException e) {
            log.error("solve job {} could not be started", jobId, e);
            return;
        }

        try {
            List<Duty> duties = new DutyGenerator().generate(input.operation(), input.staffing());
            applyPins(duties, input);
            new TimefoldScheduler(input.solverSettings())
                    .solve(duties, input.pool().teachers(), input.pinned().keySet(),
                            input.policy());

            ValidationReport report = ScheduleValidator.forPolicy(input.policy()).validate(duties);
            ScheduleWriter writer = new ScheduleWriter();
            ScheduleWriter.Result result = writer.build(
                    input.operation().id(), duties, input.pool().teachers(), report);

            self.complete(jobId, writer.toJson(result), result, duties,
                    input.pool().teacherIdByMatricule(), input.pinned().keySet());
        } catch (RuntimeException e) {
            log.error("solve job {} failed", jobId, e);
            self.fail(jobId, e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    /**
     * Everything the solver needs, read out before the transaction closes.
     * {@code pinned} maps a duty to the teacher an administrator fixed on it.
     */
    public record SolveInput(ExamOperation operation, OperationAssembler.Pool pool,
                             Map<String, Long> pinned, ma.bacsurv.rules.StaffingPolicy staffing,
                             SchedulingPolicy policy, SolverSettings solverSettings) {}

    @Transactional
    public SolveInput start(long jobId) {
        SolveJob job = jobs.findWithOperation(jobId)
                .orElseThrow(() -> new IllegalArgumentException("no job with id " + jobId));
        job.markRunning();
        OperationEntity operation = job.getOperation();

        Map<String, Long> pinned = new HashMap<>();
        assignments.pinnedOfOperation(operation.getId())
                .forEach(row -> pinned.put(row.getDutyId(), row.getTeacher().getId()));

        // the job's own time limit wins: it is what the administrator asked for
        SolverSettings settings = SolverSettings.ofSeconds(job.getTimeLimitSeconds());

        return new SolveInput(assembler.toDomain(operation), assembler.poolFor(operation),
                pinned, assembler.staffingOf(operation), assembler.schedulingOf(operation),
                settings);
    }

    /** Places the pinned teachers before solving; the solver then holds them there. */
    private static void applyPins(List<Duty> duties, SolveInput input) {
        for (Duty duty : duties) {
            Long teacherId = input.pinned().get(duty.id());
            if (teacherId == null) continue;
            Teacher teacher = input.pool().teacherById().get(teacherId);
            if (teacher != null) duty.assign(teacher);
        }
    }

    /** Stores the solved schedule as rows: one per duty, editable afterwards. */
    @Transactional
    public void complete(long jobId, String resultJson, ScheduleWriter.Result result,
                         List<Duty> duties, Map<String, Long> teacherIdByMatricule,
                         java.util.Set<String> pinnedDutyIds) {
        jobs.findById(jobId).ifPresent(job -> {
            job.markDone(resultJson, result.feasible(), result.hardViolations(),
                    result.softViolations(), result.unfilled());

            assignments.deleteByJobId(jobId); // a re-solve replaces its own schedule
            for (Duty duty : duties) {
                TeacherEntity holder = duty.assignedTeacher()
                        .map(Teacher::matricule)
                        .map(teacherIdByMatricule::get)
                        .flatMap(teachers::findById)
                        .orElse(null);
                AssignmentEntity row = new AssignmentEntity(job, duty.id(), duty.slot().id(),
                        duty.exam().map(e -> e.id()).orElse(null),
                        duty.room().map(r -> r.id()).orElse(null),
                        duty.role(), holder);
                // a pin belongs to the decision, not to one solve of it
                row.setPinned(pinnedDutyIds.contains(duty.id()));
                assignments.save(row);
            }
        });
    }

    @Transactional
    public void fail(long jobId, String message) {
        jobs.findById(jobId).ifPresent(job -> job.markFailed(message));
    }

    @Transactional(readOnly = true)
    public JobView job(long jobId) {
        return jobs.findWithOperation(jobId).map(JobView::of)
                .orElseThrow(() -> new IllegalArgumentException("no job with id " + jobId));
    }

    /**
     * The stored schedule of a finished job, empty while it is still running.
     * Rebuilt from the assignment rows, so it reflects any manual change.
     */
    @Transactional(readOnly = true)
    public Optional<ScheduleWriter.Result> schedule(long jobId) {
        return scheduleService.result(jobId);
    }

    @Transactional(readOnly = true)
    public List<JobView> recentJobs() {
        return jobs.findAllWithOperation().stream().map(JobView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<OperationView> recentOperations() {
        return operations.findAllWithCenter().stream()
                .map(operation -> OperationView.of(operation, null))
                .toList();
    }
}
