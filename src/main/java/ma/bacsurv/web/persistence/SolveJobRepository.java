package ma.bacsurv.web.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SolveJobRepository extends JpaRepository<SolveJob, Long> {

    /** Join fetch: the file is needed for every row, so avoid one query per job. */
    @Query("select j from SolveJob j join fetch j.operationFile order by j.createdAt desc")
    List<SolveJob> findAllWithFile();

    @Query("select j from SolveJob j join fetch j.operationFile where j.id = :id")
    Optional<SolveJob> findWithFile(long id);
}
