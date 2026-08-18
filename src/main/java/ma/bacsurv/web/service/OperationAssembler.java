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
import ma.bacsurv.web.persistence.JobWorkloadRepository;
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

    /** The domain teacher, plus the row it came from, so results can be stored back. */
    public record Pool(List<Teacher> teachers, Map<String, Long> teacherIdByMatricule) {}

    private final TeacherRepository teachers;
    private final JobWorkloadRepository workloads;

    public OperationAssembler(TeacherRepository teachers, JobWorkloadRepository workloads) {
        this.teachers = teachers;
        this.workloads = workloads;
    }

    public ExamOperation toDomain(OperationEntity operation) {
        List<ExamSlot> slots = operation.getSlots().stream().map(this::toDomain).toList();
        return new ExamOperation(operation.getReference(),
                OperationType.valueOf(operation.getType()), slots);
    }

    private ExamSlot toDomain(ExamSlotEntity slot) {
        List<Exam> exams = slot.getExams().stream().map(this::toDomain).toList();
        return new ExamSlot(slot.getReference(), slot.getDate(),
                slot.getStartTime(), slot.getEndTime(), slot.getOrdinalInDay(),
                exams, slot.getReserveCount());
    }

    private Exam toDomain(ExamEntity exam) {
        List<Room> rooms = exam.getRooms().stream()
                .map(room -> new Room(room.getReference(), room.getLabel()))
                .toList();
        return new Exam(exam.getReference(), new Subject(exam.getSubject()),
                new Stream(exam.getStream()), rooms,
                exam.getSurveillantsPerRoom(), exam.getPermanenceCount());
    }

    /** The center's pool, seeded with the workload of its past operations. */
    public Pool poolFor(OperationEntity operation) {
        Long centerId = operation.getCenter().getId();
        Map<Long, Map<DutyRole, Integer>> prior = priorWorkload(centerId, operation.getId());

        List<Teacher> pool = new java.util.ArrayList<>();
        Map<String, Long> idByMatricule = new HashMap<>();
        for (TeacherEntity entity : teachers.findPoolOfCenter(centerId)) {
            pool.add(toDomain(entity, prior.getOrDefault(entity.getId(), Map.of())));
            idByMatricule.put(entity.getMatricule(), entity.getId());
        }
        return new Pool(List.copyOf(pool), Map.copyOf(idByMatricule));
    }

    private Map<Long, Map<DutyRole, Integer>> priorWorkload(Long centerId, Long operationId) {
        Map<Long, Map<DutyRole, Integer>> prior = new HashMap<>();
        for (Object[] row : workloads.priorWorkloadOfCenter(centerId, operationId)) {
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
                entity.getGender() == null ? null : Gender.valueOf(entity.getGender()),
                unavailabilities,
                Set.of(TeacherQualification.forRole(DutyRole.SURVEILLANCE),
                        TeacherQualification.forRole(DutyRole.RESERVE),
                        TeacherQualification.permanenceFor(subject)),
                prior);
    }
}
