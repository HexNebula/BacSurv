package ma.bacsurv.web.service;

import ma.bacsurv.domain.DutyRole;
import ma.bacsurv.web.persistence.AssignmentRepository;
import ma.bacsurv.web.persistence.OperationEntity;
import ma.bacsurv.web.persistence.OperationRepository;
import ma.bacsurv.web.persistence.SchoolYearEntity;
import ma.bacsurv.web.persistence.SchoolYearRepository;
import ma.bacsurv.web.persistence.SolveJob;
import ma.bacsurv.web.persistence.SolveJobRepository;
import ma.bacsurv.web.persistence.TeacherEntity;
import ma.bacsurv.web.persistence.TeacherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A year as a record: who was here, what was sat, and what each person did.
 *
 * <p>The application's whole reason for holding history is that somebody will
 * eventually ask about it — "pourquoi j'ai eu trois surveillances de plus que
 * lui" — and the only satisfying answer is the year, in writing, with the
 * figures. That question is asked about a year that is over, by which time the
 * pool has changed and the person asking may not be in it any more.
 *
 * <p>Only settled sessions are counted. A draft was a trial; treating its
 * duties as work somebody did would put a number in front of a colleague that
 * he never actually served.
 */
@Service
public class ArchiveService {

    private final SchoolYearRepository years;
    private final TeacherRepository teachers;
    private final OperationRepository operations;
    private final SolveJobRepository jobs;
    private final AssignmentRepository assignments;

    public ArchiveService(SchoolYearRepository years, TeacherRepository teachers,
                          OperationRepository operations, SolveJobRepository jobs,
                          AssignmentRepository assignments) {
        this.years = years;
        this.teachers = teachers;
        this.operations = operations;
        this.jobs = jobs;
        this.assignments = assignments;
    }

    /** A teacher as a year holds them. */
    public record Member(String matricule, String name, String subject,
                         String establishment, String gender, boolean served) {}

    /**
     * The pool of one year, and the people of the centre who are not in it.
     *
     * <p>Both halves are needed by the same screen. September is taking three
     * people out of the carried-over list and putting two back in, and one of
     * those two may be somebody who left in 2025 and has returned — who is in
     * {@code former}, with everything he did still attached to his matricule.
     */
    public record YearPool(Long yearId, String label, List<Member> members, List<Member> former) {}

    @Transactional(readOnly = true)
    public YearPool poolOf(long schoolYearId) {
        SchoolYearEntity year = year(schoolYearId);
        List<TeacherEntity> members = teachers.findPoolOfYear(schoolYearId);
        Set<Long> memberIds = members.stream().map(TeacherEntity::getId).collect(Collectors.toSet());
        Map<Long, int[]> work = workOfYear(schoolYearId);

        List<Member> former = teachers.findPoolOfCenter(year.getCenter().getId()).stream()
                .filter(teacher -> !memberIds.contains(teacher.getId()))
                .map(teacher -> member(teacher, work))
                .toList();

        return new YearPool(year.getId(), year.getLabel(),
                members.stream().map(teacher -> member(teacher, work)).toList(), former);
    }

    /** One session of the year, as the record remembers it. */
    public record ArchivedSession(Long id, String reference, String type,
                                  LocalDate startsOn, LocalDate endsOn, String state,
                                  Long scheduleJobId, int dutyCount) {}

    /** What one teacher did over the year. */
    public record Tally(String matricule, String name, String subject,
                        int surveillance, int reserve, int permanence, int total) {}

    /**
     * The record of a year.
     *
     * <p>The per-session detail — who was in which room at which hour — is not
     * copied in here: each settled session carries the id of the solve it went
     * out with, and {@code GET /jobs/{id}/schedule} already returns exactly
     * that. Duplicating it would be a second version of the truth to keep in
     * step with the first.
     */
    public record Archive(Long yearId, String label, Long centerId, String centerName,
                          List<ArchivedSession> sessions, List<Tally> tally) {}

    @Transactional(readOnly = true)
    public Archive of(long schoolYearId) {
        SchoolYearEntity year = year(schoolYearId);
        Map<Long, int[]> work = workOfYear(schoolYearId);

        List<ArchivedSession> sessions = operations.findAllWithCenter().stream()
                .filter(operation -> operation.getSchoolYear().getId().equals(schoolYearId))
                .sorted(Comparator.comparing(
                        (OperationEntity operation) -> operation.getStartsOn(),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::archived)
                .toList();

        // everybody who did anything, plus everybody who was in the pool: a
        // teacher who served nothing is a fact about the year too, and leaving
        // him out is how a fairness question gets the wrong answer
        List<TeacherEntity> people = new ArrayList<>(teachers.findPoolOfYear(schoolYearId));
        Set<Long> listed = people.stream().map(TeacherEntity::getId).collect(Collectors.toSet());
        teachers.findPoolOfCenter(year.getCenter().getId()).stream()
                .filter(teacher -> work.containsKey(teacher.getId()))
                .filter(teacher -> !listed.contains(teacher.getId()))
                .forEach(people::add);

        List<Tally> tally = people.stream()
                .map(teacher -> {
                    int[] counts = work.getOrDefault(teacher.getId(), new int[3]);
                    return new Tally(teacher.getMatricule(), teacher.getName(),
                            teacher.getSubject(), counts[0], counts[1], counts[2],
                            counts[0] + counts[1] + counts[2]);
                })
                .sorted(Comparator.comparingInt(Tally::total).reversed()
                        .thenComparing(Tally::name))
                .toList();

        return new Archive(year.getId(), year.getLabel(), year.getCenter().getId(),
                year.getCenter().getName(), sessions, tally);
    }

    private ArchivedSession archived(OperationEntity operation) {
        SolveJob settled = operation.isSettled() ? newestDone(operation.getId()) : null;
        int duties = settled == null ? 0 : assignments.findOfJob(settled.getId()).size();

        return new ArchivedSession(operation.getId(), operation.getReference(),
                operation.getType(), operation.getStartsOn(), operation.getEndsOn(),
                operation.getState().name(),
                settled == null ? null : settled.getId(), duties);
    }

    private SolveJob newestDone(long operationId) {
        return jobs.ofOperation(operationId).stream()
                .filter(job -> job.getStatus() == SolveJob.Status.DONE)
                .max(Comparator.comparing(SolveJob::getId))
                .orElse(null);
    }

    /**
     * Surveillance, réserve and permanence per teacher over the whole year.
     *
     * <p>Read through the same query the fairness rule uses, with no session
     * excluded, so the archive cannot disagree with the reason a distribution
     * came out the way it did. An archive that told a different story from the
     * solver would be worse than none.
     */
    private Map<Long, int[]> workOfYear(long schoolYearId) {
        Map<Long, int[]> work = new HashMap<>();
        for (Object[] row : assignments.priorWorkloadOfYear(schoolYearId, -1L)) {
            Long teacherId = (Long) row[0];
            DutyRole role = (DutyRole) row[1];
            int count = ((Number) row[2]).intValue();
            int[] counts = work.computeIfAbsent(teacherId, id -> new int[3]);
            counts[switch (role) {
                case SURVEILLANCE -> 0;
                case RESERVE -> 1;
                case PERMANENCE -> 2;
            }] += count;
        }
        return work;
    }

    private static Member member(TeacherEntity teacher, Map<Long, int[]> work) {
        int[] counts = work.get(teacher.getId());
        return new Member(teacher.getMatricule(), teacher.getName(), teacher.getSubject(),
                teacher.getEstablishment(), teacher.getGender(),
                counts != null && counts[0] + counts[1] + counts[2] > 0);
    }

    private SchoolYearEntity year(long id) {
        return years.findById(id).orElseThrow(() -> new IllegalArgumentException("year.unknown"));
    }
}
