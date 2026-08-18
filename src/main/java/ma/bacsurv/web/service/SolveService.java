package ma.bacsurv.web.service;

import ma.bacsurv.application.DutyGenerator;
import ma.bacsurv.application.ScheduleValidator;
import ma.bacsurv.application.ValidationReport;
import ma.bacsurv.domain.Duty;
import ma.bacsurv.domain.DutyRole;
import ma.bacsurv.domain.ExamOperation;
import ma.bacsurv.domain.Teacher;
import ma.bacsurv.io.InputMapper;
import ma.bacsurv.io.ScheduleWriter;
import ma.bacsurv.solver.TimefoldScheduler;
import ma.bacsurv.web.persistence.JobWorkload;
import ma.bacsurv.web.persistence.JobWorkloadRepository;
import ma.bacsurv.web.persistence.OperationEntity;
import ma.bacsurv.web.persistence.OperationFile;
import ma.bacsurv.web.persistence.OperationFileRepository;
import ma.bacsurv.web.persistence.OperationRepository;
import ma.bacsurv.web.persistence.SolveJob;
import ma.bacsurv.web.persistence.SolveJobRepository;
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
    private final JobWorkloadRepository workloads;
    private final OperationImporter importer;
    private final OperationAssembler assembler;
    private final SolveService self;

    public SolveService(OperationFileRepository files, OperationRepository operations,
                        TeacherRepository teachers, SolveJobRepository jobs,
                        JobWorkloadRepository workloads, OperationImporter importer,
                        OperationAssembler assembler, @Lazy SolveService self) {
        this.files = files;
        this.operations = operations;
        this.teachers = teachers;
        this.jobs = jobs;
        this.workloads = workloads;
        this.importer = importer;
        this.assembler = assembler;
        this.self = self; // proxy, so @Async/@Transactional apply to self-calls
    }

    /** Parses the file, then stores the center, its pool and the operation. */
    @Transactional
    public OperationView upload(String name, String content) {
        InputMapper.ParsedOperation parsed = new InputMapper().readJson(content);
        OperationFile file = files.save(new OperationFile(name, content));
        return OperationView.of(importer.importOperation(parsed), file.getId());
    }

    @Transactional
    public JobView submit(long operationId, int timeLimitSeconds) {
        OperationEntity operation = operations.findWithCenter(operationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "no operation with id " + operationId));
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
            List<Duty> duties = new DutyGenerator().generate(input.operation());
            new TimefoldScheduler(Duration.ofSeconds(input.seconds()))
                    .solve(duties, input.pool().teachers());

            ValidationReport report = ScheduleValidator.withDefaults().validate(duties);
            ScheduleWriter writer = new ScheduleWriter();
            ScheduleWriter.Result result = writer.build(
                    input.operation().id(), duties, input.pool().teachers(), report);

            self.complete(jobId, writer.toJson(result), result,
                    countDuties(duties), input.pool().teacherIdByMatricule());
        } catch (RuntimeException e) {
            log.error("solve job {} failed", jobId, e);
            self.fail(jobId, e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    /** Everything the solver needs, read out before the transaction closes. */
    public record SolveInput(ExamOperation operation, OperationAssembler.Pool pool, int seconds) {}

    @Transactional
    public SolveInput start(long jobId) {
        SolveJob job = jobs.findWithOperation(jobId)
                .orElseThrow(() -> new IllegalArgumentException("no job with id " + jobId));
        job.markRunning();
        OperationEntity operation = job.getOperation();
        return new SolveInput(assembler.toDomain(operation), assembler.poolFor(operation),
                job.getTimeLimitSeconds());
    }

    /** Duties given per teacher matricule and role — the year's fairness ledger. */
    private static Map<String, Map<DutyRole, Integer>> countDuties(List<Duty> duties) {
        Map<String, Map<DutyRole, Integer>> counts = new HashMap<>();
        for (Duty duty : duties) {
            duty.assignedTeacher().map(Teacher::matricule).ifPresent(matricule ->
                    counts.computeIfAbsent(matricule, m -> new HashMap<>())
                            .merge(duty.role(), 1, Integer::sum));
        }
        return counts;
    }

    @Transactional
    public void complete(long jobId, String resultJson, ScheduleWriter.Result result,
                         Map<String, Map<DutyRole, Integer>> dutiesByMatricule,
                         Map<String, Long> teacherIdByMatricule) {
        jobs.findById(jobId).ifPresent(job -> {
            job.markDone(resultJson, result.feasible(), result.hardViolations(),
                    result.softViolations(), result.unfilled());
            workloads.deleteByJobId(jobId); // a re-solve replaces its own history
            dutiesByMatricule.forEach((matricule, byRole) -> {
                Long teacherId = teacherIdByMatricule.get(matricule);
                if (teacherId == null) return;
                teachers.findById(teacherId).ifPresent(teacher ->
                        byRole.forEach((role, count) ->
                                workloads.save(new JobWorkload(job, teacher, role, count))));
            });
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

    /** The stored schedule of a finished job, empty while it is still running. */
    @Transactional(readOnly = true)
    public Optional<ScheduleWriter.Result> schedule(long jobId) {
        return jobs.findById(jobId)
                .map(SolveJob::getResult)
                .map(new ScheduleWriter()::parse);
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
