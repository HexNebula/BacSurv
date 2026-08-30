package ma.bacsurv.web.service;

import ma.bacsurv.web.persistence.CenterStreamEntity;
import ma.bacsurv.web.persistence.ExamEntity;
import ma.bacsurv.web.persistence.ExamSlotEntity;
import ma.bacsurv.web.persistence.OperationEntity;
import ma.bacsurv.web.persistence.OperationRepository;
import ma.bacsurv.web.persistence.RoomEntity;
import ma.bacsurv.web.persistence.RoomRepository;
import ma.bacsurv.web.persistence.StreamEntity;
import ma.bacsurv.web.persistence.StreamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Building a session's timetable by hand.
 *
 * <p>Until now a timetable could only arrive as a JSON file, which is fine for
 * a machine and useless for the person who actually holds the paper. This is
 * the same thing entered the way a centre thinks about it: the filières and
 * their rooms once, then a subject and its hours per day.
 *
 * <p>Two ideas carry most of the work. A <em>slot</em> is a moment, so two
 * filières sitting at the same hour for different lengths are two slots and
 * the solver sees them as overlapping rather than as one. And the counts —
 * surveillants per room, réserve — are left at zero, which means "whatever the
 * session's settings say" rather than "none": the policy decides, and changing
 * it later does not require touching every épreuve.
 */
@Service
public class TimetableService {

    private final OperationRepository operations;
    private final StreamRepository streams;
    private final RoomRepository rooms;
    private final CatalogueService catalogue;
    private final SessionConflictService conflicts;

    public TimetableService(OperationRepository operations, StreamRepository streams,
                            RoomRepository rooms, CatalogueService catalogue,
                            SessionConflictService conflicts) {
        this.operations = operations;
        this.streams = streams;
        this.rooms = rooms;
        this.catalogue = catalogue;
        this.conflicts = conflicts;
    }

    public record RoomRef(Long id, String reference, String label) {}

    /** A filière of the session and the rooms it holds throughout. */
    public record StreamView(Long id, String name, List<RoomRef> rooms) {}

    /** One subject sat by one filière, at one moment. */
    public record ExamView(Long id, Long streamId, String subject,
                           LocalDate date, LocalTime startTime, LocalTime endTime,
                           int roomCount) {}

    /**
     * The whole grid: the days across, the filières down, and the épreuves
     * that fill it. Days come from the session's declared dates when it has
     * them, so an empty timetable still has columns to fill.
     */
    public record Timetable(Long operationId, String reference,
                            Long centerId, String centerName,
                            List<LocalDate> days, List<StreamView> streams,
                            List<ExamView> exams,
                            List<SessionConflictService.RoomClash> roomsTaken) {}

    @Transactional(readOnly = true)
    public Timetable timetable(long operationId) {
        OperationEntity operation = operation(operationId);

        List<ExamView> exams = new ArrayList<>();
        Set<LocalDate> days = new TreeSet<>(operation.days());
        List<StreamView> streamViews = streams.ofOperation(operationId).stream()
                .map(TimetableService::view).toList();

        for (ExamSlotEntity slot : operation.getSlots()) {
            days.add(slot.getDate());
            for (ExamEntity exam : slot.getExams()) {
                Long streamId = streamViews.stream()
                        .filter(stream -> stream.name().equals(exam.getStream()))
                        .map(StreamView::id).findFirst().orElse(null);
                exams.add(new ExamView(exam.getId(), streamId, exam.getSubject(),
                        slot.getDate(), slot.getStartTime(), slot.getEndTime(),
                        exam.getRooms().size()));
            }
        }

        // the rooms a settled neighbour already holds these same days: the
        // picker greys them out rather than letting one be chosen and refused
        return new Timetable(operation.getId(), operation.getReference(),
                operation.getCenter().getId(), operation.getCenter().getName(),
                List.copyOf(days), streamViews, List.copyOf(exams),
                List.copyOf(conflicts.roomsTakenAround(operation).values()));
    }

    @Transactional
    public Long addStream(long operationId, String name, List<Long> roomIds) {
        OperationEntity operation = editable(operationId);
        String cleaned = required(name, "stream.name");

        streams.findByOperationIdAndName(operationId, cleaned).ifPresent(existing -> {
            throw new IllegalArgumentException("stream.exists");
        });

        List<RoomEntity> chosen = roomsOf(roomIds);
        refuseRoomsHeldElsewhere(operation, null, chosen);

        int ordinal = streams.ofOperation(operationId).size();
        // a filière declared in a 2BAC session belongs to the 2BAC list, and
        // the session says which year sits it rather than its type implying it
        catalogue.rememberStream(operation.getCenter().getId(), cleaned,
                operation.getLevel());
        operation.touch();
        return streams.save(new StreamEntity(operation, cleaned, ordinal, chosen)).getId();
    }

    /**
     * Renaming a filière carries its épreuves with it, because the exam rows
     * remember the stream by name. Missing that would leave a session whose
     * épreuves belong to a filière that no longer exists.
     */
    @Transactional
    public void editStream(long streamId, String name, List<Long> roomIds) {
        StreamEntity stream = stream(streamId);
        SessionAdminService.mustBeEditable(stream.getOperation());
        String cleaned = required(name, "stream.name");

        streams.findByOperationIdAndName(stream.getOperation().getId(), cleaned)
                .filter(other -> !other.getId().equals(streamId))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("stream.exists");
                });

        String previous = stream.getName();
        List<RoomEntity> occupied = roomsOf(roomIds);
        refuseRoomsHeldElsewhere(stream.getOperation(), streamId, occupied);
        stream.rename(cleaned);
        stream.occupy(occupied);

        for (ExamEntity exam : examsOfStream(stream.getOperation(), previous)) {
            exam.rename(cleaned);
            exam.occupy(occupied);
        }
        stream.getOperation().touch();
    }

    @Transactional
    public void removeStream(long streamId) {
        StreamEntity stream = stream(streamId);
        OperationEntity operation = stream.getOperation();
        SessionAdminService.mustBeEditable(operation);
        for (ExamEntity exam : examsOfStream(operation, stream.getName())) {
            exam.getSlot().getExams().remove(exam);
        }
        dropEmptySlots(operation);
        operation.touch();
        streams.delete(stream);
    }

    /**
     * States that a filière sits a subject on a day, between two hours.
     *
     * <p>The slot is found by its moment rather than created blindly: when
     * Lettres and Sciences Humaines both start at 15:00 and finish at 18:00,
     * that is one slot holding two épreuves, and the réserve is computed once
     * for it.
     */
    @Transactional
    public Long setExam(long operationId, long streamId, String subject,
                        LocalDate date, LocalTime startTime, LocalTime endTime) {
        OperationEntity operation = editable(operationId);
        StreamEntity stream = stream(streamId);
        String cleaned = required(subject, "exam.subject");

        if (date == null) throw new IllegalArgumentException("exam.date");
        if (startTime == null || endTime == null) throw new IllegalArgumentException("exam.hours");
        if (!startTime.isBefore(endTime)) throw new IllegalArgumentException("exam.hours.reversed");
        if (stream.getRooms().isEmpty()) throw new IllegalArgumentException("stream.rooms.none");

        // a filière sits one subject at a time: replace rather than double-book
        examsOfStream(operation, stream.getName()).stream()
                .filter(exam -> exam.getSlot().getDate().equals(date))
                .filter(exam -> exam.getSlot().getStartTime().equals(startTime))
                .forEach(exam -> exam.getSlot().getExams().remove(exam));

        catalogue.rememberSubject(operation.getCenter().getId(), cleaned);
        operation.touch();
        ExamSlotEntity slot = slotAt(operation, date, startTime, endTime);
        ExamEntity exam = new ExamEntity(slot, nextExamReference(operation), cleaned,
                stream.getName(), 0, 1, stream.getRooms());
        slot.addExam(exam);
        operations.save(operation);
        return exam.getId();
    }

    @Transactional
    public void removeExam(long operationId, long examId) {
        OperationEntity operation = editable(operationId);
        for (ExamSlotEntity slot : operation.getSlots()) {
            if (slot.getExams().removeIf(exam -> exam.getId().equals(examId))) {
                dropEmptySlots(operation);
                operation.touch();
                return;
            }
        }
        throw new IllegalArgumentException("exam.unknown");
    }

    /**
     * Copies one filière's whole timetable onto another.
     *
     * <p>Real sessions repeat themselves: Sciences Expérimentales sits exactly
     * what Sciences Mathématiques sits, Sciences Humaines exactly what Lettres
     * sits. Retyping it is where mistakes come from.
     */
    @Transactional
    public int copyStream(long operationId, long fromStreamId, long toStreamId) {
        if (fromStreamId == toStreamId) throw new IllegalArgumentException("stream.copy.same");
        OperationEntity operation = editable(operationId);
        StreamEntity from = stream(fromStreamId);
        StreamEntity to = stream(toStreamId);
        if (to.getRooms().isEmpty()) throw new IllegalArgumentException("stream.rooms.none");

        record Moment(String subject, LocalDate date, LocalTime start, LocalTime end) {}
        List<Moment> source = examsOfStream(operation, from.getName()).stream()
                .map(exam -> new Moment(exam.getSubject(), exam.getSlot().getDate(),
                        exam.getSlot().getStartTime(), exam.getSlot().getEndTime()))
                .toList();

        for (Moment moment : source) {
            setExam(operationId, toStreamId, moment.subject(), moment.date(),
                    moment.start(), moment.end());
        }
        return source.size();
    }

    /** The slot for this exact moment, reused when it already exists. */
    private ExamSlotEntity slotAt(OperationEntity operation, LocalDate date,
                                  LocalTime startTime, LocalTime endTime) {
        Optional<ExamSlotEntity> existing = operation.getSlots().stream()
                .filter(slot -> slot.getDate().equals(date))
                .filter(slot -> slot.getStartTime().equals(startTime))
                .filter(slot -> slot.getEndTime().equals(endTime))
                .findFirst();
        if (existing.isPresent()) return existing.get();

        // ordinal within the day, so morning stays before afternoon
        int ordinal = (int) operation.getSlots().stream()
                .filter(slot -> slot.getDate().equals(date))
                .filter(slot -> slot.getStartTime().isBefore(startTime))
                .count() + 1;

        // zero réserve, not stated: the session's rule decides how many
        ExamSlotEntity slot = new ExamSlotEntity(operation, nextSlotReference(operation),
                date, startTime, endTime, ordinal, 0);
        operation.addSlot(slot);
        renumberDay(operation, date);
        return slot;
    }

    /** Ordinals follow the clock, so a slot inserted earlier takes its place. */
    private static void renumberDay(OperationEntity operation, LocalDate date) {
        List<ExamSlotEntity> ofDay = operation.getSlots().stream()
                .filter(slot -> slot.getDate().equals(date))
                .sorted(Comparator.comparing(ExamSlotEntity::getStartTime))
                .toList();
        for (int index = 0; index < ofDay.size(); index++) {
            ofDay.get(index).setOrdinalInDay(index + 1);
        }
    }

    private static void dropEmptySlots(OperationEntity operation) {
        operation.getSlots().removeIf(slot -> slot.getExams().isEmpty());
    }

    private static List<ExamEntity> examsOfStream(OperationEntity operation, String streamName) {
        return operation.getSlots().stream()
                .flatMap(slot -> slot.getExams().stream())
                .filter(exam -> exam.getStream().equals(streamName))
                .toList();
    }

    private String nextSlotReference(OperationEntity operation) {
        return "S" + (highest(operation.getSlots().stream()
                .map(ExamSlotEntity::getReference).toList(), "S") + 1);
    }

    private String nextExamReference(OperationEntity operation) {
        List<String> used = operation.getSlots().stream()
                .flatMap(slot -> slot.getExams().stream())
                .map(ExamEntity::getReference).toList();
        return "E" + (highest(used, "E") + 1);
    }

    /** Highest number already used behind a prefix, so references never repeat. */
    private static int highest(List<String> references, String prefix) {
        int top = 0;
        for (String reference : references) {
            if (reference == null || !reference.startsWith(prefix)) continue;
            try {
                top = Math.max(top, Integer.parseInt(reference.substring(prefix.length())));
            } catch (NumberFormatException notNumbered) {
                // a reference from an imported file may be named anything
            }
        }
        return top;
    }

    /**
     * A room seats one filière for the whole session.
     *
     * <p>Candidates of two filières do not sit in the same room, and the
     * application counted on it without ever saying so: a room given to both
     * produces two épreuves at the same hour behind one door, each asking for
     * its own surveillants. Nothing refused it, so a centre could enter it and
     * only discover the double-booking on the printed list.
     *
     * <p>The refusal names the rooms and the filière already holding them,
     * because "these rooms are taken" leaves the administrator comparing two
     * screens to find out which.
     */
    private void refuseRoomsHeldElsewhere(OperationEntity operation, Long selfStreamId,
                                          List<RoomEntity> chosen) {
        if (chosen.isEmpty()) return;
        Set<Long> wanted = chosen.stream().map(RoomEntity::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        for (StreamEntity other : streams.ofOperation(operation.getId())) {
            if (selfStreamId != null && other.getId().equals(selfStreamId)) continue;
            List<String> clash = other.getRooms().stream()
                    .filter(room -> wanted.contains(room.getId()))
                    .map(RoomEntity::getLabel)
                    .toList();
            if (!clash.isEmpty()) {
                throw new RefusedException("stream.rooms.taken",
                        String.join(", ", clash), other.getName());
            }
        }

        // and the same door cannot open on another session running these days
        Map<Long, SessionConflictService.RoomClash> busy = conflicts.roomsTakenAround(operation);
        for (Long roomId : wanted) {
            SessionConflictService.RoomClash held = busy.get(roomId);
            if (held != null) {
                throw new RefusedException("stream.rooms.otherSession",
                        held.room(), held.session(), held.heldBy());
            }
        }
    }

    private List<RoomEntity> roomsOf(List<Long> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) return List.of();
        // a set, so the same room listed twice does not double a filière's need
        Set<Long> unique = new LinkedHashSet<>(roomIds);
        List<RoomEntity> found = rooms.findAllById(unique);
        if (found.size() != unique.size()) throw new IllegalArgumentException("room.unknown");
        return found;
    }

    private static StreamView view(StreamEntity stream) {
        return new StreamView(stream.getId(), stream.getName(),
                stream.getRooms().stream()
                        .map(room -> new RoomRef(room.getId(), room.getReference(), room.getLabel()))
                        .toList());
    }

    private OperationEntity operation(long operationId) {
        return operations.findById(operationId)
                .orElseThrow(() -> new IllegalArgumentException("session.unknown"));
    }

    /**
     * The session, refused if it is settled.
     *
     * <p>A settled session's planning is the planning the convocations were
     * printed from. Letting an épreuve move under it would leave the paper in
     * everyone's hands describing a session the application no longer holds,
     * and the difference would surface on the morning of the exam. Reopening
     * the session says what that costs and then allows it.
     */
    private OperationEntity editable(long operationId) {
        OperationEntity operation = operation(operationId);
        SessionAdminService.mustBeEditable(operation);
        return operation;
    }

    private StreamEntity stream(long streamId) {
        return streams.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("stream.unknown"));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + ".required");
        return value.trim();
    }
}
