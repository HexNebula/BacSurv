package ma.bacsurv.web.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface OperationRepository extends JpaRepository<OperationEntity, Long> {

    @Query("select o from OperationEntity o join fetch o.center order by o.createdAt desc")
    List<OperationEntity> findAllWithCenter();

    @Query("select o from OperationEntity o join fetch o.center where o.id = :id")
    Optional<OperationEntity> findWithCenter(Long id);
}
