package ma.bacsurv;

import ma.bacsurv.domain.Exam;
import ma.bacsurv.domain.ExamOperation;
import ma.bacsurv.domain.ExamSlot;
import ma.bacsurv.domain.OperationType;
import ma.bacsurv.domain.Room;
import ma.bacsurv.domain.Stream;
import ma.bacsurv.domain.Subject;
import ma.bacsurv.domain.Teacher;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class TestFixtures {

    public static final Subject MATH = new Subject("Mathematics");
    public static final Subject FRENCH = new Subject("French");
    public static final Subject HG = new Subject("History-Geography");
    public static final Stream SCIENCES = new Stream("Sciences Expérimentales");
    public static final Stream ARTS = new Stream("Lettres");

    public static final Room R1 = new Room("R1", "Room 1");
    public static final Room R2 = new Room("R2", "Room 2");
    public static final Room R3 = new Room("R3", "Room 3");
    public static final Room R4 = new Room("R4", "Room 4");
    public static final Room R5 = new Room("R5", "Room 5");

    public static Teacher teacher(String id, Subject subject) {
        return Teacher.withDefaults(id, "M-" + id, "Teacher-" + id, subject, "Lycée Test");
    }

    /**
     * Monday 08:00–10:00, two exams in the same slot:
     *   HG / Sciences  → rooms 1–3 (6 surveillance duties, 1 permanence)
     *   French / Arts  → rooms 4–5 (4 surveillance duties, 1 permanence)
     * Reserve requirement 1 → 13 duties total.
     */
    public static ExamSlot multiSubjectSlot() {
        Exam hg = Exam.of("E-HG", HG, SCIENCES, List.of(R1, R2, R3));
        Exam fr = Exam.of("E-FR", FRENCH, ARTS, List.of(R4, R5));
        return new ExamSlot("SL1", LocalDate.of(2026, 6, 1),
                LocalTime.of(8, 0), LocalTime.of(10, 0), 1, List.of(hg, fr), 1);
    }

    /**
     * The normal case for the scientific streams: three streams, three exams,
     * one and the same subject at the same hour — so one permanence.
     */
    public static ExamSlot sameSubjectAcrossStreamsSlot() {
        Exam a = Exam.of("E-M1", MATH, SCIENCES, List.of(R1, R2));
        Exam b = Exam.of("E-M2", MATH, new Stream("Sciences Maths"), List.of(R3));
        Exam c = Exam.of("E-M3", MATH, new Stream("Sciences Agronomiques"), List.of(R4));
        return new ExamSlot("SL2", LocalDate.of(2026, 6, 1),
                LocalTime.of(8, 0), LocalTime.of(10, 0), 1, List.of(a, b, c), 1);
    }

    public static ExamOperation singleSlotOperation() {
        return new ExamOperation("OP1", OperationType.REGIONAL_1BAC,
                List.of(multiSubjectSlot()));
    }
}
