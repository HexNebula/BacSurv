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

    /**
     * The name as the administration writes it — Arabic, because that is the
     * language of the lists a centre works from.
     */
    @Column(nullable = false)
    private String name;

    /**
     * The same name in French, for the documents that are written in it.
     *
     * <p>Display only. Nothing compares it and nothing joins on it, so a
     * teacher without one simply prints in Arabic.
     */
    @Column(name = "name_fr", length = 200)
    private String nameFr;

    @Column(nullable = false)
    private String subject;

    private String establishment;

    /**
     * السلك — the corps: ثانوي تأهيلي, ثانوي إعدادي, ابتدائي.
     *
     * <p>Printed on the official list, never read by a rule. A centre short of
     * staff borrows from elsewhere, and the list has to say which corps each
     * borrowed person came from; {@code establishment} says which school, and
     * the same corps at a different school is a real case, so neither field
     * stands in for the other.
     */
    @Column(length = 60)
    private String corps;

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
        this(center, reference, matricule, name, null, subject, establishment, null, gender);
    }

    public TeacherEntity(CenterEntity center, String reference, String matricule,
                         String name, String nameFr, String subject, String establishment,
                         String corps, String gender) {
        this.center = center;
        this.reference = reference;
        this.matricule = matricule;
        this.name = name;
        this.nameFr = nameFr;
        this.subject = subject;
        this.establishment = establishment;
        this.corps = corps;
        this.gender = gender;
    }

    /** Refreshes the descriptive fields when a teacher is imported again. */
    public void update(String reference, String name, String subject,
                       String establishment, String gender) {
        update(reference, name, nameFr, subject, establishment, corps, gender);
    }

    public void update(String reference, String name, String nameFr, String subject,
                       String establishment, String corps, String gender) {
        this.reference = reference;
        this.name = name;
        this.nameFr = nameFr;
        this.subject = subject;
        this.establishment = establishment;
        this.corps = corps;
        this.gender = gender;
    }

    /**
     * Whether a change would alter what a distribution should be.
     *
     * <p>Only three of these fields reach the solver: the subject it matches
     * against an épreuve, the gender the mixed-pair preference reads, and the
     * days the teacher is away. A name, an établissement or a corps is printed
     * and nothing more, so correcting one must not tell every session that its
     * distribution is out of date — an administrator filling in French names
     * would be told to re-solve the year.
     */
    public boolean differsWhereItMatters(String subject, String gender) {
        return !java.util.Objects.equals(this.subject, subject)
                || !java.util.Objects.equals(this.gender, gender);
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
    public String getNameFr() { return nameFr; }
    public String getSubject() { return subject; }
    public String getCorps() { return corps; }
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
