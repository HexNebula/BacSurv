package ma.bacsurv.web.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * One school year of a centre: 2026-2027.
 *
 * <p>The year is what a pool and a set of sessions belong to. Fairness is
 * counted inside it and never across it — the counter starts at zero every
 * September, because balancing a teacher's week against work he did two years
 * ago settles an argument nobody is having.
 *
 * <p>It is also what makes a departure expressible. A teacher who moves to
 * another school is not deleted, which would take his turns out of the year he
 * actually served; he is simply not in the pool of the years that follow.
 */
@Entity
@Table(name = "school_year")
public class SchoolYearEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "center_id", nullable = false)
    private CenterEntity center;

    /** 2026-2027 — sortable as text, which is what orders the years. */
    @Column(nullable = false, length = 20)
    private String label;

    protected SchoolYearEntity() {}

    public SchoolYearEntity(CenterEntity center, String label) {
        this.center = center;
        this.label = label;
    }

    /**
     * The school year a date falls in.
     *
     * <p>It turns over in September, not in January: the épreuves of June 2027
     * belong to 2026-2027, the year that began the previous autumn. Getting
     * this backwards would file a whole session under the wrong year and split
     * a pool in two.
     */
    public static String labelOf(LocalDate date) {
        int year = date.getYear();
        int start = date.getMonthValue() >= 9 ? year : year - 1;
        return start + "-" + (start + 1);
    }

    public Long getId() { return id; }
    public CenterEntity getCenter() { return center; }
    public String getLabel() { return label; }
}
