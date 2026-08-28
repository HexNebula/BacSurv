package ma.bacsurv.web.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SchoolYearRepository extends JpaRepository<SchoolYearEntity, Long> {

    Optional<SchoolYearEntity> findByCenterIdAndLabel(Long centerId, String label);

    /** Newest first: the label sorts as text, and 2027-2028 follows 2026-2027. */
    @Query("select y from SchoolYearEntity y where y.center.id = :centerId order by y.label desc")
    List<SchoolYearEntity> ofCenter(Long centerId);
}
