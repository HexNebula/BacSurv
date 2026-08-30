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

    /**
     * The settled sessions of the same centre whose days touch this one's.
     *
     * <p>A centre is one building with one staff. Two sessions running the same
     * hours are not two independent problems: a teacher standing in a room for
     * one of them cannot stand in a room for the other, and a room seating one
     * cannot seat the other. Each is solved alone and neither can see it, so
     * the overlap has to be found outside the solver.
     *
     * <p>Settled only, and for the same reason the fairness queries say so: a
     * draft is still being typed. Refusing today's work because of a trial that
     * may be abandoned tomorrow would block the administrator on a session that
     * does not exist yet.
     */
    @Query("""
            select o from OperationEntity o
            where o.center.id = :centerId
              and o.id <> :excludedOperationId
              and o.state = ma.bacsurv.web.persistence.OperationEntity$State.SETTLED
              and o.startsOn <= :endsOn
              and o.endsOn >= :startsOn
            order by o.startsOn
            """)
    List<OperationEntity> settledOverlapping(Long centerId, Long excludedOperationId,
                                             java.time.LocalDate startsOn,
                                             java.time.LocalDate endsOn);
}
