package ma.bacsurv.web.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A teacher of the center's pool. The matricule (رقم التأجير) is the real
 * identity: it is what lets the same person be recognised from one operation
 * to the next, and so what makes cumulative fairness possible.
 */
@Entity
@Table(name = "teacher")
public class TeacherEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "center_id", nullable = false)
    private CenterEntity center;

    @Column(nullable = false)
    private String reference;

    @Column(nullable = false)
    private String matricule;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String subject;

    private String establishment;

    private String gender;

    @OneToMany(mappedBy = "teacher", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<UnavailabilityEntity> unavailabilities = new ArrayList<>();

    /**
     * The school years this teacher was in the pool for.
     *
     * <p>Presence is all or nothing for a year: nobody leaves between the
     * régionale and the rattrapage, so a teacher is either asked to work a
     * whole year or not at all. Leaving the establishment is expressed by not
     * being a member of the years that follow — the row itself stays, with the
     * matricule that identifies him and everything he did still attached.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "teacher_school_year",
            joinColumns = @JoinColumn(name = "teacher_id"),
            inverseJoinColumns = @JoinColumn(name = "school_year_id"))
    private Set<SchoolYearEntity> schoolYears = new LinkedHashSet<>();

    protected TeacherEntity() {}

    public TeacherEntity(CenterEntity center, String reference, String matricule,
                         String name, String subject, String establishment, String gender) {
        this.center = center;
        this.reference = reference;
        this.matricule = matricule;
        this.name = name;
        this.subject = subject;
        this.establishment = establishment;
        this.gender = gender;
    }

    /** Refreshes the descriptive fields when a teacher is imported again. */
    public void update(String reference, String name, String subject,
                       String establishment, String gender) {
        this.reference = reference;
        this.name = name;
        this.subject = subject;
        this.establishment = establishment;
        this.gender = gender;
    }

    public void replaceUnavailabilities(List<UnavailabilityEntity> replacements) {
        unavailabilities.clear();
        unavailabilities.addAll(replacements);
    }

    public Long getId() { return id; }
    public CenterEntity getCenter() { return center; }
    public String getReference() { return reference; }
    public String getMatricule() { return matricule; }
    public String getName() { return name; }
    public String getSubject() { return subject; }
    public String getEstablishment() { return establishment; }
    public String getGender() { return gender; }
    public List<UnavailabilityEntity> getUnavailabilities() { return unavailabilities; }

    public Set<SchoolYearEntity> getSchoolYears() { return schoolYears; }

    /** Puts this teacher in a year's pool. Joining a year twice is not an event. */
    public void joinYear(SchoolYearEntity year) { schoolYears.add(year); }

    /**
     * Takes this teacher out of a year's pool. Nothing he did is touched: the
     * duties of a year he did serve belong to that year and stay in it.
     */
    public void leaveYear(SchoolYearEntity year) { schoolYears.remove(year); }

    public boolean isInYear(SchoolYearEntity year) { return schoolYears.contains(year); }
}
