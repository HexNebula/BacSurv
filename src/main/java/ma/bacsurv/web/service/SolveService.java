package ma.bacsurv.web.service;

import ma.bacsurv.application.DutyGenerator;
import ma.bacsurv.application.ScheduleValidator;
import ma.bacsurv.application.ValidationReport;
import ma.bacsurv.domain.Duty;
import ma.bacsurv.io.InputMapper;
import ma.bacsurv.io.ScheduleWriter;
import ma.bacsurv.solver.TimefoldScheduler;
import ma.bacsurv.web.persistence.OperationFile;
import ma.bacsurv.web.persistence.OperationFileRepository;
import ma.bacsurv.web.persistence.SolveJob;
import ma.bacsurv.web.persistence.SolveJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Submit a solve, get a job id, poll for the result. Solving never blocks a
 * request thread: today it runs on a local executor, tomorrow the same
 * interface can hand the job to a worker process without touching callers.
 *
 * Entities stay inside this class; callers receive views, so no lazy
 * association is ever touched outside a transaction.
 */
@Service
public class SolveService {

    private static final Logger log = LoggerFactory.getLogger(SolveService.class);

    private final OperationFileRepository files;
    private final SolveJobRepository jobs;
    private final SolveService self;

    public SolveService(OperationFileRepository files, SolveJobRepository jobs,
                        @Lazy SolveService self) {
        this.files = files;
        this.jobs = jobs;
        this.self = self; // proxy, so @Async/@Transactional apply to self-calls
    }

    /** Stores the file after checking it parses, so bad input fails immediately. */
    @Transactional
    public OperationView upload(String name, String content) {
        new InputMapper().readJson(content); // validation only — throws InputException
        return OperationView.of(files.save(new OperationFile(name, content)));
    }

    @Transactional
    public JobView submit(long operationFileId, int timeLimitSeconds) {
        OperationFile file = files.findById(operationFileId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "no operation file with id " + operationFileId));
        SolveJob job = jobs.saveAndFlush(new SolveJob(file, timeLimitSeconds));
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
            var parsed = new InputMapper().readJson(input.content());
            List<Duty> duties = new DutyGenerator().generate(parsed.operation());
            new TimefoldScheduler(Duration.ofSeconds(input.seconds()))
                    .solve(duties, parsed.teachers());

            ValidationReport report = ScheduleValidator.withDefaults().validate(duties);
            ScheduleWriter writer = new ScheduleWriter();
            ScheduleWriter.Result result = writer.build(
                    parsed.operation().id(), duties, parsed.teachers(), report);

            self.complete(jobId, writer.toJson(result), result);
        } catch (RuntimeException e) {
            log.error("solve job {} failed", jobId, e);
            self.fail(jobId, e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    /** Everything the solver needs, read out before the transaction closes. */
    public record SolveInput(String content, int seconds) {}

    @Transactional
    public SolveInput start(long jobId) {
        SolveJob job = jobs.findWithFile(jobId)
                .orElseThrow(() -> new IllegalArgumentException("no job with id " + jobId));
        job.markRunning();
        return new SolveInput(job.getOperationFile().getContent(), job.getTimeLimitSeconds());
    }

    @Transactional
    public void complete(long jobId, String resultJson, ScheduleWriter.Result result) {
        jobs.findById(jobId).ifPresent(job -> job.markDone(resultJson, result.feasible(),
                result.hardViolations(), result.softViolations(), result.unfilled()));
    }

    @Transactional
    public void fail(long jobId, String message) {
        jobs.findById(jobId).ifPresent(job -> job.markFailed(message));
    }

    @Transactional(readOnly = true)
    public JobView job(long jobId) {
        return jobs.findWithFile(jobId).map(JobView::of)
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
        return jobs.findAllWithFile().stream().map(JobView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<OperationView> recentFiles() {
        return files.findAllByOrderByUploadedAtDesc().stream().map(OperationView::of).toList();
    }
}
