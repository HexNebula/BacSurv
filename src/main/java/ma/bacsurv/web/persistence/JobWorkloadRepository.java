package ma.bacsurv.web.persistence;

import ma.bacsurv.domain.DutyRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface JobWorkloadRepository extends JpaRepository<JobWorkload, Long> {

    /**
     * Duties already given to each teacher of a center by earlier finished
     * jobs, excluding the operation being solved. Only the newest finished
     * job of each past operation counts, so re-solving an operation does not
     * inflate its history.
     */
    @Query("""
            select w.teacher.id, w.role, sum(w.dutyCount)
            from JobWorkload w
            where w.teacher.center.id = :centerId
              and w.job.operation.id <> :excludedOperationId
              and w.job.id in (
                  select max(j.id) from SolveJob j
                  where j.status = ma.bacsurv.web.persistence.SolveJob$Status.DONE
                  group by j.operation.id)
            group by w.teacher.id, w.role
            """)
    List<Object[]> priorWorkloadOfCenter(Long centerId, Long excludedOperationId);

    List<JobWorkload> findByJobId(Long jobId);

    void deleteByJobId(Long jobId);

    /** Convenience for tests and reports: what one job gave one teacher. */
    default int dutiesOf(Long jobId, Long teacherId, DutyRole role) {
        return findByJobId(jobId).stream()
                .filter(w -> w.getTeacher().getId().equals(teacherId) && w.getRole() == role)
                .mapToInt(JobWorkload::getDutyCount)
                .sum();
    }
}
