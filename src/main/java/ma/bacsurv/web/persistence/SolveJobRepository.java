package ma.bacsurv.web.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SolveJobRepository extends JpaRepository<SolveJob, Long> {

    /** Join fetch: the operation and its center are needed for every row. */
    @Query("""
            select j from SolveJob j
            join fetch j.operation o join fetch o.center
            order by j.createdAt desc
            """)
    List<SolveJob> findAllWithOperation();

    @Query("""
            select j from SolveJob j
            join fetch j.operation o join fetch o.center
            where j.id = :id
            """)
    Optional<SolveJob> findWithOperation(long id);

    /** Every solve of one session, newest first. */
    @Query("select j from SolveJob j where j.operation.id = :operationId order by j.id desc")
    List<SolveJob> ofOperation(Long operationId);
}
