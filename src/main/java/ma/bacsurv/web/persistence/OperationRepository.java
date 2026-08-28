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

    /**
     * The session with its school year loaded. The year is fetched lazily, so
     * reading it from a session that has since closed fails — which is a
     * runtime fault rather than a compile one, and so worth a query of its own
     * for the callers that read the year outside a transaction.
     */
    @Query("select o from OperationEntity o join fetch o.schoolYear where o.id = :id")
    Optional<OperationEntity> findWithYear(Long id);
}
