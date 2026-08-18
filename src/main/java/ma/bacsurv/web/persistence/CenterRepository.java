package ma.bacsurv.web.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CenterRepository extends JpaRepository<CenterEntity, Long> {

    Optional<CenterEntity> findByName(String name);

    List<CenterEntity> findAllByOrderByNameAsc();
}
