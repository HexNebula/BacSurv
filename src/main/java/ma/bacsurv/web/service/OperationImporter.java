package ma.bacsurv.web.service;

import ma.bacsurv.domain.Exam;
import ma.bacsurv.domain.ExamSlot;
import ma.bacsurv.domain.Room;
import ma.bacsurv.domain.Teacher;
import ma.bacsurv.io.InputMapper;
import ma.bacsurv.web.persistence.CenterEntity;
import ma.bacsurv.web.persistence.CenterRepository;
import ma.bacsurv.web.persistence.ExamEntity;
import ma.bacsurv.web.persistence.ExamSlotEntity;
import ma.bacsurv.web.persistence.OperationEntity;
import ma.bacsurv.web.persistence.OperationRepository;
import ma.bacsurv.web.persistence.RoomEntity;
import ma.bacsurv.web.persistence.RoomRepository;
import ma.bacsurv.web.persistence.TeacherEntity;
import ma.bacsurv.web.persistence.TeacherRepository;
import ma.bacsurv.web.persistence.UnavailabilityEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a parsed input file into stored center data.
 *
 * A center is matched by name and a teacher by matricule, so importing the
 * July operation of a center that already ran in June reuses the same
 * teacher rows — which is what lets past workload be carried forward.
 */
@Component
public class OperationImporter {

    private final CenterRepository centers;
    private final RoomRepository rooms;
    private final TeacherRepository teachers;
    private final OperationRepository operations;

    public OperationImporter(CenterRepository centers, RoomRepository rooms,
                             TeacherRepository teachers, OperationRepository operations) {
        this.centers = centers;
        this.rooms = rooms;
        this.teachers = teachers;
        this.operations = operations;
    }

    public OperationEntity importOperation(InputMapper.ParsedOperation parsed) {
        CenterEntity center = centers.findByName(parsed.centerName())
                .orElseGet(() -> centers.save(new CenterEntity(parsed.centerName())));

        Map<String, RoomEntity> roomsByReference = importRooms(center, parsed);
        importTeachers(center, parsed.teachers());

        OperationEntity operation = operations.save(new OperationEntity(
                center, parsed.operation().id(), parsed.operation().type().name()));

        for (ExamSlot slot : parsed.operation().slots()) {
            ExamSlotEntity slotEntity = new ExamSlotEntity(operation, slot.id(), slot.date(),
                    slot.startTime(), slot.endTime(), slot.ordinalInDay(), slot.reserveRequirement());
            operation.addSlot(slotEntity);
            for (Exam exam : slot.exams()) {
                List<RoomEntity> examRooms = exam.rooms().stream()
                        .map(Room::id).map(roomsByReference::get).toList();
                slotEntity.addExam(new ExamEntity(slotEntity, exam.id(),
                        exam.subject().name(), exam.stream().name(),
                        exam.surveillantsPerRoom(), exam.permanenceCount(), examRooms));
            }
        }
        return operations.save(operation);
    }

    private Map<String, RoomEntity> importRooms(CenterEntity center,
                                                InputMapper.ParsedOperation parsed) {
        Map<String, RoomEntity> existing = new HashMap<>();
        rooms.findByCenterIdOrderByReferenceAsc(center.getId())
                .forEach(room -> existing.put(room.getReference(), room));

        // rooms appear on exams, and the same room is used by several exams
        Map<String, Room> fromFile = new HashMap<>();
        parsed.operation().slots().forEach(slot -> slot.exams()
                .forEach(exam -> exam.rooms().forEach(room -> fromFile.put(room.id(), room))));

        fromFile.forEach((reference, room) -> {
            RoomEntity entity = existing.get(reference);
            if (entity == null) {
                existing.put(reference, rooms.save(new RoomEntity(center, reference, room.label())));
            } else {
                entity.setLabel(room.label());
            }
        });
        return existing;
    }

    private void importTeachers(CenterEntity center, List<Teacher> pool) {
        for (Teacher teacher : pool) {
            TeacherEntity entity = teachers
                    .findByCenterIdAndMatricule(center.getId(), teacher.matricule())
                    .map(existing -> {
                        existing.update(teacher.id(), teacher.name(), teacher.subject().name(),
                                teacher.establishment(),
                                teacher.gender().map(Enum::name).orElse(null));
                        return existing;
                    })
                    .orElseGet(() -> teachers.save(new TeacherEntity(center, teacher.id(),
                            teacher.matricule(), teacher.name(), teacher.subject().name(),
                            teacher.establishment(),
                            teacher.gender().map(Enum::name).orElse(null))));

            List<UnavailabilityEntity> unavailabilities = new ArrayList<>();
            teacher.unavailabilities().forEach(u -> unavailabilities.add(
                    new UnavailabilityEntity(entity, u.date(), u.start(), u.end())));
            entity.replaceUnavailabilities(unavailabilities);
            teachers.save(entity);
        }
    }
}
