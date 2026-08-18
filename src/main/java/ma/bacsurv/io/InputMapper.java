package ma.bacsurv.io;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.bacsurv.application.ReservePolicy;
import ma.bacsurv.domain.Exam;
import ma.bacsurv.domain.ExamOperation;
import ma.bacsurv.domain.ExamSlot;
import ma.bacsurv.domain.DutyRole;
import ma.bacsurv.domain.Gender;
import ma.bacsurv.domain.OperationType;
import ma.bacsurv.domain.Room;
import ma.bacsurv.domain.Subject;
import ma.bacsurv.domain.Stream;
import ma.bacsurv.domain.Teacher;
import ma.bacsurv.domain.TeacherQualification;
import ma.bacsurv.domain.Unavailability;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JSON file -> (ExamOperation, teacher pool). Strict: unknown JSON fields,
 * dangling room references, duplicate ids and malformed dates all fail
 * with a message naming the offending element, so an admin can fix the file.
 */
public final class InputMapper {

    private final ObjectMapper json = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    public record ParsedOperation(ExamOperation operation, List<Teacher> teachers) {}

    public ParsedOperation read(Path file) {
        OperationInput input;
        try {
            input = json.readValue(file.toFile(), OperationInput.class);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file + ": " + e.getMessage(), e);
        }
        return map(input);
    }

    /** Same as {@link #read(Path)} but from an in-memory JSON string. */
    public ParsedOperation readJson(String content) {
        try {
            return map(json.readValue(content, OperationInput.class));
        } catch (IOException e) {
            throw new InputException("invalid JSON: " + e.getMessage());
        }
    }

    public ParsedOperation map(OperationInput in) {
        require(in.operation() != null, "operation section is required");
        require(in.slots() != null && !in.slots().isEmpty(), "at least one slot is required");
        require(in.teachers() != null && !in.teachers().isEmpty(), "at least one teacher is required");

        OperationType type = parseEnum(OperationType.class, in.operation().type(), "operation.type");
        Map<String, Room> rooms = mapRooms(in.rooms());

        int defaultSurveillants = in.defaults() != null && in.defaults().surveillantsPerRoom() != null
                ? in.defaults().surveillantsPerRoom() : Exam.MIN_SURVEILLANTS_PER_ROOM;
        int defaultPermanence = in.defaults() != null && in.defaults().permanencePerExam() != null
                ? in.defaults().permanencePerExam() : 1;
        ReservePolicy reservePolicy = in.defaults() != null && in.defaults().reservePercentage() != null
                ? new ReservePolicy.PercentageOfSurveillance(in.defaults().reservePercentage())
                : ReservePolicy.officialDefault();

        List<ExamSlot> slots = mapSlots(in.slots(), rooms,
                defaultSurveillants, defaultPermanence, reservePolicy);
        List<Teacher> teachers = mapTeachers(in.teachers());

        return new ParsedOperation(
                new ExamOperation(in.operation().id(), type, slots), teachers);
    }

    private Map<String, Room> mapRooms(List<OperationInput.RoomDto> dtos) {
        require(dtos != null && !dtos.isEmpty(), "at least one room is required");
        Map<String, Room> rooms = new HashMap<>();
        for (var dto : dtos) {
            require(rooms.put(dto.id(), new Room(dto.id(),
                    dto.label() != null ? dto.label() : dto.id())) == null,
                    "duplicate room id: " + dto.id());
        }
        return rooms;
    }

    private List<ExamSlot> mapSlots(List<OperationInput.SlotDto> dtos, Map<String, Room> rooms,
                                    int defaultSurveillants, int defaultPermanence,
                                    ReservePolicy reservePolicy) {
        Set<String> slotIds = new HashSet<>();
        Set<String> examIds = new HashSet<>();
        List<ExamSlot> slots = new ArrayList<>();
        for (var dto : dtos) {
            require(slotIds.add(dto.id()), "duplicate slot id: " + dto.id());
            require(dto.exams() != null && !dto.exams().isEmpty(),
                    "slot " + dto.id() + " has no exams");
            LocalDate date = parseDate(dto.date(), "slot " + dto.id());
            LocalTime start = parseTime(dto.start(), "slot " + dto.id() + " start");
            LocalTime end = parseTime(dto.end(), "slot " + dto.id() + " end");

            List<Exam> exams = new ArrayList<>();
            for (var e : dto.exams()) {
                require(examIds.add(e.id()), "duplicate exam id: " + e.id());
                List<Room> examRooms = new ArrayList<>();
                for (String roomId : e.rooms()) {
                    Room room = rooms.get(roomId);
                    require(room != null, "exam " + e.id() + " references unknown room: " + roomId);
                    require(examRooms.stream().noneMatch(r -> r.id().equals(roomId)),
                            "exam " + e.id() + " lists room twice: " + roomId);
                    examRooms.add(room);
                }
                exams.add(new Exam(e.id(), new Subject(e.subject()), new Stream(e.stream()),
                        examRooms,
                        e.surveillantsPerRoom() != null ? e.surveillantsPerRoom() : defaultSurveillants,
                        e.permanenceCount() != null ? e.permanenceCount() : defaultPermanence));
            }

            // reserveCount in the file is the configured truth; policy only fills gaps
            ExamSlot provisional = new ExamSlot(dto.id(), date, start, end, 0, exams, 0);
            int reserve = dto.reserveCount() != null
                    ? dto.reserveCount() : reservePolicy.suggest(provisional);
            slots.add(new ExamSlot(dto.id(), date, start, end, 0, exams, reserve));
        }
        return withOrdinals(slots);
    }

    /** ordinalInDay derived from start-time order within each date. */
    private List<ExamSlot> withOrdinals(List<ExamSlot> slots) {
        Map<LocalDate, Integer> counters = new HashMap<>();
        return slots.stream()
                .sorted(Comparator.comparing(ExamSlot::date).thenComparing(ExamSlot::startTime))
                .map(s -> new ExamSlot(s.id(), s.date(), s.startTime(), s.endTime(),
                        counters.merge(s.date(), 1, Integer::sum),
                        s.exams(), s.reserveRequirement()))
                .toList();
    }

    private List<Teacher> mapTeachers(List<OperationInput.TeacherDto> dtos) {
        Set<String> ids = new HashSet<>();
        Set<String> matricules = new HashSet<>();
        List<Teacher> teachers = new ArrayList<>();
        for (var dto : dtos) {
            require(ids.add(dto.id()), "duplicate teacher id: " + dto.id());
            require(dto.subject() != null, "teacher " + dto.id() + " has no subject");
            require(dto.matricule() != null && !dto.matricule().isBlank(),
                    "teacher " + dto.id() + " has no matricule");
            require(matricules.add(dto.matricule().trim()),
                    "duplicate matricule: " + dto.matricule());

            Gender gender = dto.gender() == null ? null
                    : parseEnum(Gender.class, dto.gender(), "teacher " + dto.id() + " gender");

            Set<Unavailability> unavailabilities = new HashSet<>();
            if (dto.unavailable() != null) {
                for (var u : dto.unavailable()) {
                    LocalDate date = parseDate(u.date(), "teacher " + dto.id() + " unavailability");
                    require((u.start() == null) == (u.end() == null),
                            "teacher " + dto.id() + " unavailability on " + u.date()
                                    + " must set both start and end, or neither");
                    unavailabilities.add(u.start() == null
                            ? Unavailability.wholeDay(date)
                            : new Unavailability(date,
                                    parseTime(u.start(), "unavailability start"),
                                    parseTime(u.end(), "unavailability end")));
                }
            }

            Subject subject = new Subject(dto.subject());
            Map<DutyRole, Integer> prior = new EnumMap<>(DutyRole.class);
            if (dto.prior() != null) {
                dto.prior().forEach((role, count) -> {
                    require(count != null && count >= 0,
                            "teacher " + dto.id() + " prior." + role + " must be >= 0");
                    prior.put(parseEnum(DutyRole.class, role,
                            "teacher " + dto.id() + " prior role"), count);
                });
            }

            teachers.add(new Teacher(dto.id(), dto.matricule().trim(),
                    dto.name() != null ? dto.name() : dto.id(),
                    subject, dto.establishment(), gender,
                    unavailabilities,
                    Set.of(TeacherQualification.forRole(DutyRole.SURVEILLANCE),
                            TeacherQualification.forRole(DutyRole.RESERVE),
                            TeacherQualification.permanenceFor(subject)),
                    prior));
        }
        return teachers;
    }

    private static LocalDate parseDate(String value, String context) {
        require(value != null, context + ": date is required");
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new InputException(context + ": bad date '" + value + "' (expected yyyy-MM-dd)");
        }
    }

    private static LocalTime parseTime(String value, String context) {
        require(value != null, context + ": time is required");
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new InputException(context + ": bad time '" + value + "' (expected HH:mm)");
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String context) {
        require(value != null, context + " is required");
        try {
            return Enum.valueOf(type, value.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new InputException(context + ": unknown value '" + value + "' (expected one of "
                    + List.of(type.getEnumConstants()) + ")");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new InputException(message);
    }

    /** Any problem with the input file the admin must fix. */
    public static final class InputException extends RuntimeException {
        InputException(String message) { super(message); }
    }
}
