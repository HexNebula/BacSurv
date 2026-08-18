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

    @Query("""
            select distinct t from TeacherEntity t
            left join fetch t.unavailabilities
            where t.center.id = :centerId
            order by t.reference
            """)
    List<TeacherEntity> findPoolOfCenter(Long centerId);
}
