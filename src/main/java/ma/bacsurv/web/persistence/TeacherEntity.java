package ma.bacsurv.web.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

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
}
