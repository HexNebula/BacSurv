package ma.bacsurv.web.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomRepository extends JpaRepository<RoomEntity, Long> {

    List<RoomEntity> findByCenterIdOrderByReferenceAsc(Long centerId);
}
