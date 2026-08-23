package ma.bacsurv.web.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StreamRepository extends JpaRepository<StreamEntity, Long> {

    @Query("""
            select distinct s from StreamEntity s
            left join fetch s.rooms
            where s.operation.id = :operationId
            order by s.ordinal
            """)
    List<StreamEntity> ofOperation(Long operationId);

    Optional<StreamEntity> findByOperationIdAndName(Long operationId, String name);
}
