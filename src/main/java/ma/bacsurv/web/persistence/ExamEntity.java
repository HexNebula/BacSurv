package ma.bacsurv.web.persistence;

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
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/** One subject examination for one stream, held in a set of the center's rooms. */
@Entity
@Table(name = "exam")
public class ExamEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id", nullable = false)
    private ExamSlotEntity slot;

    @Column(nullable = false)
    private String reference;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String stream;

    @Column(name = "surveillants_per_room", nullable = false)
    private int surveillantsPerRoom;

    @Column(name = "permanence_count", nullable = false)
    private int permanenceCount;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "exam_room",
            joinColumns = @JoinColumn(name = "exam_id"),
            inverseJoinColumns = @JoinColumn(name = "room_id"))
    private List<RoomEntity> rooms = new ArrayList<>();

    protected ExamEntity() {}

    public ExamEntity(ExamSlotEntity slot, String reference, String subject, String stream,
                      int surveillantsPerRoom, int permanenceCount, List<RoomEntity> rooms) {
        this.slot = slot;
        this.reference = reference;
        this.subject = subject;
        this.stream = stream;
        this.surveillantsPerRoom = surveillantsPerRoom;
        this.permanenceCount = permanenceCount;
        this.rooms = new ArrayList<>(rooms);
    }

    /** Follows a filière that has been renamed: the exam remembers it by name. */
    public void rename(String stream) {
        this.stream = stream;
    }

    /** Follows a subject renamed in the centre's catalogue. */
    public void renameSubject(String subject) {
        this.subject = subject;
    }

    /** Rooms are set from the filière, which holds them for the whole session. */
    public void occupy(List<RoomEntity> replacements) {
        rooms.clear();
        rooms.addAll(replacements);
    }

    public Long getId() { return id; }
    public ExamSlotEntity getSlot() { return slot; }
    public String getReference() { return reference; }
    public String getSubject() { return subject; }
    public String getStream() { return stream; }
    public int getSurveillantsPerRoom() { return surveillantsPerRoom; }
    public int getPermanenceCount() { return permanenceCount; }
    public List<RoomEntity> getRooms() { return rooms; }
}
