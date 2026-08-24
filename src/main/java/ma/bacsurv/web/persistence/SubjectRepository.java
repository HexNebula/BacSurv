package ma.bacsurv.web.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubjectRepository extends JpaRepository<SubjectEntity, Long> {

    List<SubjectEntity> findByCenterIdOrderByNameAsc(Long centerId);

    Optional<SubjectEntity> findByCenterIdAndName(Long centerId, String name);
}
