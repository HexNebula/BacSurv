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

    /**
     * The pool of this session's school year, seeded with what the year's
     * earlier sessions handed out.
     *
     * <p>The year is the boundary in both directions. Only its members are
     * given duties, so a teacher who moved to another school in July is not
     * asked to work in September; and only its own sessions count as history,
     * so nobody is compensated in June 2028 for a duty done in June 2026.
     */
    public Pool poolFor(OperationEntity operation) {
        return poolFor(operation, List.of());
    }

    /**
     * The same pool, plus people who must be recognisable even though they are
     * no longer in it.
     *
     * <p>Reading back a distribution is not the same act as solving one. A
     * teacher who moved to another school in July must not be given work in
     * September — so he is out of the pool — but the sessions he actually
     * served still name him, and rendering those duties as unheld would erase
     * him from his own week. The archive already counts him; the schedule has
     * to show him too, or the two disagree about the same afternoon.
     *
     * <p>The extras are carried for identification only. They are appended
     * after the pool, so nothing that reads the pool as "who can be given work"
     * — the solver, the candidate list — is offered somebody who has left.
     */
    public Pool poolFor(OperationEntity operation, java.util.Collection<TeacherEntity> also) {
        Long yearId = operation.getSchoolYear().getId();
        Map<Long, Map<DutyRole, Integer>> prior = priorWorkload(yearId, operation.getId());
        Map<Long, Set<Unavailability>> busy = heldByConcurrentSessions(operation);
        List<TeacherEntity> entities = new java.util.ArrayList<>(teachers.findPoolOfYear(yearId));
        java.util.Set<Long> known = entities.stream()
                .map(TeacherEntity::getId).collect(java.util.stream.Collectors.toSet());
        also.stream().filter(extra -> extra != null && known.add(extra.getId()))
                .forEach(entities::add);
        Map<Long, Map<DutyRole, Integer>> carry =
                privilegeCarry(yearId, operation.getId(), entities);

        List<Teacher> pool = new java.util.ArrayList<>();
        Map<String, Long> idByMatricule = new HashMap<>();
        Map<Long, Teacher> byId = new HashMap<>();
        for (TeacherEntity entity : entities) {
            Teacher teacher = toDomain(entity, prior.getOrDefault(entity.getId(), Map.of()),
                    busy.getOrDefault(entity.getId(), Set.of()))
                    .withPrivilegeCarry(carry.getOrDefault(entity.getId(), Map.of()));
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
     * <p>Once per queue. Réserve and permanence are counted apart, so a
     * specialist who took three permanences is not thereby at the back of the
     * réserve queue — permanence is asked of him, not offered.
     *
     * <p>The floor is taken over the whole current pool, so a teacher who
     * missed the earlier sessions counts as zero and goes to the front —
     * which is right, they have not had their turn. Somebody who joined the
     * establishment this year arrives at zero for the same reason.
     */
    private Map<Long, Map<DutyRole, Integer>> privilegeCarry(Long schoolYearId, Long operationId,
                                                            List<TeacherEntity> pool) {
        Map<DutyRole, Map<Long, Integer>> taken = new EnumMap<>(DutyRole.class);
        for (Object[] row : assignments.privilegeTurnsOfYear(schoolYearId, operationId)) {
            taken.computeIfAbsent((DutyRole) row[1], role -> new HashMap<>())
                    .merge((Long) row[0], ((Number) row[2]).intValue(), Integer::sum);
        }

        List<Long> poolIds = pool.stream().map(TeacherEntity::getId).toList();
        Map<Long, Map<DutyRole, Integer>> carry = new HashMap<>();
        taken.forEach((role, counts) ->
                carryFrom(counts, poolIds).forEach((teacherId, extra) ->
                        carry.computeIfAbsent(teacherId, id -> new EnumMap<>(DutyRole.class))
                                .put(role, extra)));
        return Map.copyOf(carry);
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

    private Map<Long, Map<DutyRole, Integer>> priorWorkload(Long schoolYearId, Long operationId) {
        Map<Long, Map<DutyRole, Integer>> prior = new HashMap<>();
        for (Object[] row : assignments.priorWorkloadOfYear(schoolYearId, operationId)) {
            Long teacherId = (Long) row[0];
            DutyRole role = (DutyRole) row[1];
            int count = ((Number) row[2]).intValue();
            prior.computeIfAbsent(teacherId, id -> new EnumMap<>(DutyRole.class))
                    .merge(role, count, Integer::sum);
        }
        return prior;
    }

    /**
     * The hours each teacher is already standing somewhere, because another
     * session of the same centre runs these days and has been settled.
     *
     * <p>Carried as unavailability rather than as a constraint of its own, and
     * that is the whole of the design: a teacher held by the rattrapage of the
     * scolarisés at eight o'clock is, from the point of view of the libres
     * being distributed, exactly as unavailable as one who declared an absence.
     * The application already refuses to give work to an unavailable teacher in
     * five places — eligibility, the solver's hard constraints, the validator,
     * the hand-edit screen and the staffing check — so saying it once here says
     * it in all five.
     *
     * <p>This does not make the collision impossible; settling is what does
     * that, and it does it whatever order the administrator works in. What this
     * removes is the loop: without it he presses distribute, is refused at
     * arrêter, presses distribute again and is refused again, with nothing on
     * any screen saying why. With it the first distribution simply avoids them.
     *
     * <p>Settled sessions only — a draft is still being typed, and its people
     * are not committed to anything.
     */
    private Map<Long, Set<Unavailability>> heldByConcurrentSessions(OperationEntity operation) {
        if (operation.getStartsOn() == null || operation.getEndsOn() == null) return Map.of();

        Map<Long, Set<Unavailability>> busy = new HashMap<>();
        for (Object[] row : assignments.settledOccupancy(operation.getCenter().getId(),
                operation.getId(), operation.getStartsOn(), operation.getEndsOn())) {
            busy.computeIfAbsent((Long) row[0], id -> new java.util.HashSet<>())
                    .add(new Unavailability((java.time.LocalDate) row[2],
                            (java.time.LocalTime) row[3], (java.time.LocalTime) row[4]));
        }
        return busy;
    }

    private Teacher toDomain(TeacherEntity entity, Map<DutyRole, Integer> prior,
                             Set<Unavailability> alreadyStanding) {
        Subject subject = new Subject(entity.getSubject());
        Set<Unavailability> unavailabilities = new java.util.HashSet<>(alreadyStanding);
        entity.getUnavailabilities().stream()
                .map(u -> new Unavailability(u.getDate(), u.getStartTime(), u.getEndTime()))
                .forEach(unavailabilities::add);

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
