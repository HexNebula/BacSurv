package ma.bacsurv.web.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CenterStreamRepository extends JpaRepository<CenterStreamEntity, Long> {

    List<CenterStreamEntity> findByCenterIdOrderByNameAsc(Long centerId);

    Optional<CenterStreamEntity> findByCenterIdAndName(Long centerId, String name);

    /** A name belongs to one level, so both are needed to find the one meant. */
    Optional<CenterStreamEntity> findByCenterIdAndNameAndLevel(Long centerId, String name,
                                                               String level);
}
