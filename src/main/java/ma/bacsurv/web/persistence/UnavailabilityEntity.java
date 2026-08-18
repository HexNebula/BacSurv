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
import java.time.LocalTime;

/** A period a teacher cannot work. Null times mean the whole day. */
@Entity
@Table(name = "teacher_unavailability")
public class UnavailabilityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private TeacherEntity teacher;

    @Column(name = "unavailable_on", nullable = false)
    private LocalDate date;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    protected UnavailabilityEntity() {}

    public UnavailabilityEntity(TeacherEntity teacher, LocalDate date,
                                LocalTime startTime, LocalTime endTime) {
        this.teacher = teacher;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public Long getId() { return id; }
    public LocalDate getDate() { return date; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
}
