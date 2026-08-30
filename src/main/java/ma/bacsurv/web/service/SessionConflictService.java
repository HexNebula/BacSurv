package ma.bacsurv.web.service;

import ma.bacsurv.web.persistence.AssignmentEntity;
import ma.bacsurv.web.persistence.AssignmentRepository;
import ma.bacsurv.web.persistence.ExamSlotEntity;
import ma.bacsurv.web.persistence.OperationEntity;
import ma.bacsurv.web.persistence.OperationRepository;
import ma.bacsurv.web.persistence.RoomEntity;
import ma.bacsurv.web.persistence.SolveJob;
import ma.bacsurv.web.persistence.SolveJobRepository;
import ma.bacsurv.web.persistence.StreamEntity;
import ma.bacsurv.web.persistence.StreamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What two sessions of one centre would ask of the same people and the same
 * rooms at the same hour.
 *
 * <p>Every other check in the application asks whether one session is
 * coherent. This one asks whether it is coherent <em>with the ones already
 * settled</em>, which is a different question and the only one nobody was
 * asking. A centre that runs the rattrapage of its own pupils and the
 * rattrapage of the candidats libres on the same three mornings has two
 * sessions, one building and one staff; each is distributed alone, neither can
 * see the other, and both come out perfect. The administrator then prints two
 * lists that put the same teacher in two rooms at eight o'clock.
 *
 * <p>Two kinds of collision, and they are found in different places.
 *
 * <p>A <b>room</b> is held by a filière for the whole session — that rule
 * already exists inside a session, and this extends it across sessions whose
 * days touch. It is a fact about the timetable, visible as soon as the rooms
 * are chosen, before any distribution exists.
 *
 * <p>A <b>teacher</b> is held only for the hours of a duty, so the collision
 * exists only once both sessions have been distributed, and only where the
 * hours genuinely overlap. Two sessions sharing a day but not an hour share
 * nothing.
 *
 * <p>Both are compared against settled sessions only. A draft is still being
 * typed and may never go out; refusing today's work because of a trial would
 * stop the administrator on a session that does not exist yet.
 */
@Service
public class SessionConflictService {

    private final OperationRepository operations;
    private final AssignmentRepository assignments;
    private final StreamRepository streams;
    private final SolveJobRepository jobs;

    public SessionConflictService(OperationRepository operations, AssignmentRepository assignments,
                                  StreamRepository streams, SolveJobRepository jobs) {
        this.operations = operations;
        this.assignments = assignments;
        this.streams = streams;
        this.jobs = jobs;
    }

    /** A room this session wants that a concurrent settled session already holds. */
    public record RoomClash(Long roomId, String room, String heldBy, String session) {}

    /** A teacher this session's distribution puts where another one already has them. */
    public record TeacherClash(String teacher, LocalDate date, LocalTime at, String session) {}

    /**
     * @param neighbours the settled sessions running these same days, whether
     *                   or not anything is shared with them. Empty means the
     *                   session runs alone, which is the ordinary case and the
     *                   one where none of this is worth a word on screen.
     */
    public record Conflicts(List<String> neighbours, List<RoomClash> rooms,
                            List<TeacherClash> teachers) {

        public Conflicts {
            neighbours = List.copyOf(neighbours);
            rooms = List.copyOf(rooms);
            teachers = List.copyOf(teachers);
        }

        public static Conflicts none() { return new Conflicts(List.of(), List.of(), List.of()); }

        public boolean isEmpty() { return rooms.isEmpty() && teachers.isEmpty(); }

        /** Nothing else runs these days at all — not merely nothing shared. */
        public boolean alone() { return neighbours.isEmpty(); }

        public int count() { return rooms.size() + teachers.size(); }

        /** The sessions collided with, named once each, in the order found. */
        public List<String> sessions() {
            List<String> named = new ArrayList<>();
            rooms.stream().map(RoomClash::session).filter(s -> !named.contains(s))
                    .forEach(named::add);
            teachers.stream().map(TeacherClash::session).filter(s -> !named.contains(s))
                    .forEach(named::add);
            return List.copyOf(named);
        }
    }

    /**
     * Everything this session collides with. Rooms always; teachers only where
     * a distribution exists to collide — an undistributed session occupies
     * nobody's morning yet.
     */
    @Transactional(readOnly = true)
    public Conflicts of(long sessionId) {
        return of(operations.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("session.unknown")));
    }

    /**
     * The entity overload, for callers already holding one inside their own
     * transaction — settling, and the readiness guide. A session loaded outside
     * one has a detached slot collection and cannot be read here; those callers
     * want {@link #of(long)}.
     */
    @Transactional(readOnly = true)
    public Conflicts of(OperationEntity session) {
        List<OperationEntity> concurrent = concurrentWith(session);
        if (concurrent.isEmpty()) return Conflicts.none();

        return new Conflicts(concurrent.stream().map(OperationEntity::getReference).toList(),
                roomClashes(session, concurrent), teacherClashes(session));
    }

    /**
     * The rooms a concurrent settled session is already sitting in, by room id,
     * each naming the filière and the session holding it.
     *
     * <p>Used while the timetable is being typed, so the refusal arrives at the
     * moment the room is chosen rather than at the end of the evening.
     */
    @Transactional(readOnly = true)
    public Map<Long, RoomClash> roomsTakenAround(OperationEntity session) {
        Map<Long, RoomClash> taken = new LinkedHashMap<>();
        for (OperationEntity other : concurrentWith(session)) {
            for (StreamEntity stream : streams.ofOperation(other.getId())) {
                for (RoomEntity room : stream.getRooms()) {
                    taken.putIfAbsent(room.getId(), new RoomClash(room.getId(), room.getLabel(),
                            stream.getName(), other.getReference()));
                }
            }
        }
        return taken;
    }

    private List<OperationEntity> concurrentWith(OperationEntity session) {
        if (session.getStartsOn() == null || session.getEndsOn() == null) return List.of();
        return operations.settledOverlapping(session.getCenter().getId(), session.getId(),
                session.getStartsOn(), session.getEndsOn());
    }

    private List<RoomClash> roomClashes(OperationEntity session,
                                        List<OperationEntity> concurrent) {
        Map<Long, RoomClash> taken = new LinkedHashMap<>();
        for (OperationEntity other : concurrent) {
            for (StreamEntity stream : streams.ofOperation(other.getId())) {
                for (RoomEntity room : stream.getRooms()) {
                    taken.putIfAbsent(room.getId(), new RoomClash(room.getId(), room.getLabel(),
                            stream.getName(), other.getReference()));
                }
            }
        }

        List<RoomClash> clashes = new ArrayList<>();
        for (StreamEntity mine : streams.ofOperation(session.getId())) {
            for (RoomEntity room : mine.getRooms()) {
                RoomClash held = taken.get(room.getId());
                if (held != null && clashes.stream().noneMatch(c -> c.roomId().equals(room.getId()))) {
                    clashes.add(held);
                }
            }
        }
        return clashes;
    }

    /**
     * The moments this session's newest distribution puts somebody where a
     * settled session already has them.
     *
     * <p>Its newest distribution whatever its state, because this is asked of a
     * draft on its way to being settled — the plan about to go out is the one
     * that has to be true.
     */
    private List<TeacherClash> teacherClashes(OperationEntity session) {
        SolveJob newest = jobs.ofOperation(session.getId()).stream()
                .filter(job -> job.getStatus() == SolveJob.Status.DONE)
                .max(Comparator.comparing(SolveJob::getId))
                .orElse(null);
        if (newest == null) return List.of();

        Map<String, ExamSlotEntity> slotByRef = new LinkedHashMap<>();
        for (ExamSlotEntity slot : session.getSlots()) {
            slotByRef.put(slot.getReference(), slot);
        }

        List<Object[]> elsewhere = assignments.settledOccupancy(session.getCenter().getId(),
                session.getId(), session.getStartsOn(), session.getEndsOn());
        if (elsewhere.isEmpty()) return List.of();

        List<TeacherClash> clashes = new ArrayList<>();
        for (AssignmentEntity mine : assignments.findOfJob(newest.getId())) {
            if (mine.getTeacher() == null) continue;
            ExamSlotEntity slot = slotByRef.get(mine.getSlotRef());
            if (slot == null || slot.getDate() == null) continue;

            for (Object[] row : elsewhere) {
                Long teacherId = (Long) row[0];
                if (!teacherId.equals(mine.getTeacher().getId())) continue;
                if (!slot.getDate().equals(row[2])) continue;
                if (!overlaps(slot.getStartTime(), slot.getEndTime(),
                        (LocalTime) row[3], (LocalTime) row[4])) continue;

                TeacherClash clash = new TeacherClash((String) row[1], (LocalDate) row[2],
                        slot.getStartTime(), (String) row[6]);
                if (!clashes.contains(clash)) clashes.add(clash);
            }
        }
        clashes.sort(Comparator.comparing(TeacherClash::date)
                .thenComparing(TeacherClash::at)
                .thenComparing(TeacherClash::teacher));
        return clashes;
    }

    /**
     * Half-open on both ends: a duty ending at 11:00 and one starting at 11:00
     * are consecutive, not simultaneous. The same reading as
     * {@link ma.bacsurv.domain.Unavailability#covers}.
     */
    private static boolean overlaps(LocalTime start, LocalTime end,
                                    LocalTime otherStart, LocalTime otherEnd) {
        if (start == null || end == null || otherStart == null || otherEnd == null) return false;
        return start.isBefore(otherEnd) && otherStart.isBefore(end);
    }
}
