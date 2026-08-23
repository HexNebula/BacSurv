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

/**
 * A filière of one session, and the rooms it sits in for the whole of it.
 *
 * <p>The rooms are held here rather than on each épreuve because that is how a
 * centre allocates them: Lettres has salle 1 for the three days, Sciences
 * physiques has salles 6 à 10. Each {@link ExamEntity} still carries its own
 * rooms, since that is what the solver reads, but they are filled from this
 * list — so moving a filière to different rooms is one change, not one per
 * subject.
 */
@Entity
@Table(name = "operation_stream")
public class StreamEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operation_id", nullable = false)
    private OperationEntity operation;

    @Column(nullable = false, length = 120)
    private String name;

    /** The order the administrator put them in, kept for the grid's rows. */
    @Column(nullable = false)
    private int ordinal;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "operation_stream_room",
            joinColumns = @JoinColumn(name = "stream_id"),
            inverseJoinColumns = @JoinColumn(name = "room_id"))
    private List<RoomEntity> rooms = new ArrayList<>();

    protected StreamEntity() {}

    public StreamEntity(OperationEntity operation, String name, int ordinal,
                        List<RoomEntity> rooms) {
        this.operation = operation;
        this.name = name;
        this.ordinal = ordinal;
        this.rooms = new ArrayList<>(rooms);
    }

    public void rename(String name) {
        this.name = name;
    }

    public void occupy(List<RoomEntity> replacements) {
        rooms.clear();
        rooms.addAll(replacements);
    }

    public Long getId() { return id; }
    public OperationEntity getOperation() { return operation; }
    public String getName() { return name; }
    public int getOrdinal() { return ordinal; }
    public List<RoomEntity> getRooms() { return rooms; }
}
