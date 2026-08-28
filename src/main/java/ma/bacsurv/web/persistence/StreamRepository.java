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

    /**
     * Is this room held by a filière of a session that has gone out?
     *
     * <p>Not a question about its name — a label is the centre's own vocabulary
     * and may change. Removing the room is what does damage: the duties of a
     * stored distribution are rebuilt from the live timetable, so a room that
     * is gone produces fewer duties, and the rows for it drop out of the
     * schedule without a word.
     */
    @Query("""
            select count(s) from StreamEntity s join s.rooms r
            where r.id = :roomId
              and s.operation.state
                  = ma.bacsurv.web.persistence.OperationEntity$State.SETTLED
            """)
    long countSettledUses(Long roomId);
}
