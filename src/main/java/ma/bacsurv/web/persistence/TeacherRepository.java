package ma.bacsurv.web.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TeacherRepository extends JpaRepository<TeacherEntity, Long> {

    /**
     * A matricule identifies the teacher within a center's pool. It is not
     * global: the same person may serve another center, with its own history.
     */
    Optional<TeacherEntity> findByCenterIdAndMatricule(Long centerId, String matricule);

    /**
     * Every teacher the centre has ever held, past members included.
     *
     * <p>Right for the questions that are genuinely about the centre — the
     * subject catalogue, the backup, finding somebody by matricule. Wrong for
     * anything that hands out work: use findPoolOfYear for that.
     */
    @Query("""
            select distinct t from TeacherEntity t
            left join fetch t.unavailabilities
            where t.center.id = :centerId
            order by t.reference
            """)
    List<TeacherEntity> findPoolOfCenter(Long centerId);

    /**
     * The pool of one school year: who is actually to be given duties.
     *
     * <p>This is what the solver and every count on a screen must use. A
     * teacher who moved to another school in July is still in the centre's
     * register — his duties of last year belong to last year — but he is not
     * in this year's pool and must not be asked to work.
     */
    @Query("""
            select distinct t from TeacherEntity t
            left join fetch t.unavailabilities
            join t.schoolYears y
            where y.id = :schoolYearId
            order by t.reference
            """)
    List<TeacherEntity> findPoolOfYear(Long schoolYearId);
}
