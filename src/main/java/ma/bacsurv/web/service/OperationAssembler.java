package ma.bacsurv.web.service;

import ma.bacsurv.domain.DutyRole;
import ma.bacsurv.domain.Exam;
import ma.bacsurv.domain.ExamOperation;
import ma.bacsurv.domain.ExamSlot;
import ma.bacsurv.domain.Gender;
import ma.bacsurv.domain.OperationType;
import ma.bacsurv.domain.Room;
import ma.bacsurv.domain.Stream;
import ma.bacsurv.domain.Subject;
import ma.bacsurv.domain.Teacher;
import ma.bacsurv.domain.TeacherQualification;
import ma.bacsurv.domain.Unavailability;
import ma.bacsurv.web.persistence.ExamEntity;
import ma.bacsurv.web.persistence.ExamSlotEntity;
import ma.bacsurv.rules.ReserveRequirement;
import ma.bacsurv.rules.SchedulingPolicy;
import ma.bacsurv.rules.StaffingPolicy;
import ma.bacsurv.solver.SolverSettings;
import ma.bacsurv.web.persistence.AssignmentRepository;
import ma.bacsurv.web.persistence.OperationConfigEntity;
import ma.bacsurv.web.persistence.OperationConfigRepository;
import ma.bacsurv.web.persistence.OperationEntity;
import ma.bacsurv.web.persistence.RoomEntity;
import ma.bacsurv.web.persistence.TeacherEntity;
import ma.bacsurv.web.persistence.TeacherRepository;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stored center data back into the framework-free domain the solver works on.
 *
 * Teachers come out carrying the duties earlier operations of the same center
 * already gave them, so fairness is computed over the whole year rather than
 * one operation at a time — no {@code prior} block to fill in by hand.
 */
@Component
public class OperationAssembler {

    /**
     * The domain teachers, plus the mapping back to the rows they came from,
     * so a solved schedule can be stored and re-read.
     */
    public record Pool(List<Teacher> teachers,
                       Map<String, Long> teacherIdByMatricule,
                       Map<Long, Teacher> teacherById) {}

    private final TeacherRepository teachers;
    private final AssignmentRepository assignments;
    private final OperationConfigRepository configs;

    public OperationAssembler(TeacherRepository teachers, AssignmentRepository assignments,
                              OperationConfigRepository configs) {
        this.teachers = teachers;
        this.assignments = assignments;
        this.configs = configs;
    }

    /** The operation as configured: staffing numbers come from its settings. */
    public ExamOperation toDomain(OperationEntity operation) {
        StaffingPolicy staffing = staffingOf(operation);
        List<ExamSlot> slots = operation.getSlots().stream()
                .map(slot -> toDomain(slot, staffing)).toList();
        return new ExamOperation(operation.getReference(),
                OperationType.valueOf(operation.getType()), slots);
    }

    /** Room overrides plus the operation's own defaults and reserve rule. */
    public StaffingPolicy staffingOf(OperationEntity operation) {
        Map<String, Integer> roomOverrides = new HashMap<>();
        operation.getSlots().forEach(slot -> slot.getExams().forEach(exam ->
                exam.getRooms().forEach(room -> {
                    if (room.getSurveillantsOverride() != null) {
                        roomOverrides.put(room.getReference(), room.getSurveillantsOverride());
                    }
                })));
        return configs.findById(operation.getId())
                .map(config -> config.staffing(roomOverrides))
                .orElseGet(() -> new StaffingPolicy(
                        StaffingPolicy.MINIMUM_SURVEILLANTS_PER_ROOM, roomOverrides,
                        ReserveRequirement.officialDefault()));
    }

    public SchedulingPolicy schedulingOf(OperationEntity operation) {
        return configs.findById(operation.getId())
                .map(OperationConfigEntity::scheduling)
                .orElseGet(SchedulingPolicy::defaults);
    }

    public SolverSettings solverSettingsOf(OperationEntity operation) {
        return configs.findById(operation.getId())
                .map(OperationConfigEntity::solver)
                .orElseGet(SolverSettings::defaults);
    }

    /**
     * An unreadable gender is treated as unstated rather than fatal: it is a
     * descriptive field, and no constraint depends on it, so a bad row from an
     * older import must not stop a centre distributing its surveillance.
     */
    private static Gender readGender(String stored) {
        if (stored == null || stored.isBlank()) return null;
        try {
            return Gender.valueOf(stored.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unreadable) {
            return null;
        }
    }

    private ExamSlot toDomain(ExamSlotEntity slot, StaffingPolicy staffing) {
        List<Exam> exams = slot.getExams().stream()
                .map(exam -> toDomain(exam, staffing)).toList();

        // a count stated in the file stands; otherwise the reserve rule decides
        ExamSlot withoutReserve = new ExamSlot(slot.getReference(), slot.getDate(),
                slot.getStartTime(), slot.getEndTime(), slot.getOrdinalInDay(), exams, 0);
        int reserve = slot.isReserveExplicit()
                ? slot.getReserveCount()
                : staffing.reserve().requiredFor(withoutReserve);

        return new ExamSlot(slot.getReference(), slot.getDate(),
                slot.getStartTime(), slot.getEndTime(), slot.getOrdinalInDay(), exams, reserve);
    }

    private Exam toDomain(ExamEntity exam, StaffingPolicy staffing) {
        List<Room> rooms = exam.getRooms().stream()
                .map(room -> new Room(room.getReference(), room.getLabel()))
                .toList();
        return new Exam(exam.getReference(), new Subject(exam.getSubject()),
                new Stream(exam.getStream()), rooms,
                Math.max(exam.getSurveillantsPerRoom(), staffing.defaultSurveillantsPerRoom()),
                exam.getPermanenceCount());
    }

    /** The center's pool, seeded with the workload of its past operations. */
    public Pool poolFor(OperationEntity operation) {
        Long centerId = operation.getCenter().getId();
        Map<Long, Map<DutyRole, Integer>> prior = priorWorkload(centerId, operation.getId());
        List<TeacherEntity> entities = teachers.findPoolOfCenter(centerId);
        Map<Long, Integer> carry = privilegeCarry(centerId, operation.getId(), entities);

        List<Teacher> pool = new java.util.ArrayList<>();
        Map<String, Long> idByMatricule = new HashMap<>();
        Map<Long, Teacher> byId = new HashMap<>();
        for (TeacherEntity entity : entities) {
            Teacher teacher = toDomain(entity, prior.getOrDefault(entity.getId(), Map.of()))
                    .withPrivilegeCarry(carry.getOrDefault(entity.getId(), 0));
            pool.add(teacher);
            idByMatricule.put(entity.getMatricule(), entity.getId());
            byId.put(entity.getId(), teacher);
        }
        return new Pool(List.copyOf(pool), Map.copyOf(idByMatricule), Map.copyOf(byId));
    }

    /**
     * How many privilege turns each teacher has taken beyond the colleague
     * who has had the fewest.
     *
     * <p>Not a count of turns, a place in a queue. The subtraction is what
     * keeps it small: when a round completes everyone rises together and the
     * floor rises with them, so the numbers fall back to zero on their own.
     * It only grows where somebody genuinely got ahead — the sole specialist
     * of a subject, who has to take that permanence whoever else is waiting.
     * Session sizes never enter into it, since what is counted is turns per
     * teacher and never the size of a session.
     *
     * <p>The floor is taken over the whole current pool, so a teacher who
     * missed the earlier sessions counts as zero and goes to the front —
     * which is right, they have not had their turn.
     */
    private Map<Long, Integer> privilegeCarry(Long centerId, Long operationId,
                                              List<TeacherEntity> pool) {
        Map<Long, Integer> taken = new HashMap<>();
        for (Object[] row : assignments.privilegeTurnsOfCenter(centerId, operationId)) {
            taken.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return carryFrom(taken, pool.stream().map(TeacherEntity::getId).toList());
    }

    /** The arithmetic of the rule above, kept separate so it can be read and tested alone. */
    static Map<Long, Integer> carryFrom(Map<Long, Integer> taken, List<Long> poolIds) {
        if (taken.isEmpty() || poolIds.isEmpty()) return Map.of();

        int floor = poolIds.stream().mapToInt(id -> taken.getOrDefault(id, 0)).min().orElse(0);

        Map<Long, Integer> carry = new HashMap<>();
        for (Long id : poolIds) {
            int extra = taken.getOrDefault(id, 0) - floor;
            if (extra > 0) carry.put(id, extra);
        }
        return Map.copyOf(carry);
    }

    private Map<Long, Map<DutyRole, Integer>> priorWorkload(Long centerId, Long operationId) {
        Map<Long, Map<DutyRole, Integer>> prior = new HashMap<>();
        for (Object[] row : assignments.priorWorkloadOfCenter(centerId, operationId)) {
            Long teacherId = (Long) row[0];
            DutyRole role = (DutyRole) row[1];
            int count = ((Number) row[2]).intValue();
            prior.computeIfAbsent(teacherId, id -> new EnumMap<>(DutyRole.class))
                    .merge(role, count, Integer::sum);
        }
        return prior;
    }

    private Teacher toDomain(TeacherEntity entity, Map<DutyRole, Integer> prior) {
        Subject subject = new Subject(entity.getSubject());
        Set<Unavailability> unavailabilities = entity.getUnavailabilities().stream()
                .map(u -> new Unavailability(u.getDate(), u.getStartTime(), u.getEndTime()))
                .collect(java.util.stream.Collectors.toSet());

        return new Teacher(entity.getReference(), entity.getMatricule(), entity.getName(),
                subject, entity.getEstablishment(),
                readGender(entity.getGender()),
                unavailabilities,
                Set.of(TeacherQualification.forRole(DutyRole.SURVEILLANCE),
                        TeacherQualification.forRole(DutyRole.RESERVE),
                        TeacherQualification.permanenceFor(subject)),
                prior);
    }
}
