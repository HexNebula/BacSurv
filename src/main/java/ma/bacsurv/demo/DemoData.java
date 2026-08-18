package ma.bacsurv.demo;

import ma.bacsurv.application.ReservePolicy;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Fake but realistic 1BAC regional operation: 2 days × 2 slots,
 * two streams sitting different subjects in the same slot,
 * 10 rooms, 30 teachers.
 */
public final class DemoData {

    public static final Subject MATH = new Subject("Mathématiques");
    public static final Subject PC = new Subject("Physique-Chimie");
    public static final Subject SVT = new Subject("SVT");
    public static final Subject FR = new Subject("Français");
    public static final Subject HG = new Subject("Histoire-Géographie");
    public static final Subject AR = new Subject("Arabe");
    public static final Subject EN = new Subject("Anglais");
    public static final Subject PHILO = new Subject("Philosophie");

    public static final Stream SC = new Stream("Sciences Expérimentales");
    public static final Stream LET = new Stream("Lettres");

    public static ExamOperation operation() {
        List<Room> rooms = new ArrayList<>();
        for (int i = 1; i <= 10; i++) rooms.add(new Room("R" + i, "Salle " + i));
        List<Room> scRooms = rooms.subList(0, 6);
        List<Room> letRooms = rooms.subList(6, 10);

        ReservePolicy policy = ReservePolicy.officialDefault();
        LocalDate d1 = LocalDate.of(2026, 6, 1);
        LocalDate d2 = LocalDate.of(2026, 6, 2);

        List<ExamSlot> slots = List.of(
                slot("SL1", d1, 8, 1, List.of(
                        Exam.of("E1", HG, SC, scRooms), Exam.of("E2", FR, LET, letRooms)), policy),
                slot("SL2", d1, 15, 2, List.of(
                        Exam.of("E3", FR, SC, scRooms), Exam.of("E4", HG, LET, letRooms)), policy),
                slot("SL3", d2, 8, 1, List.of(
                        Exam.of("E5", MATH, SC, scRooms), Exam.of("E6", AR, LET, letRooms)), policy),
                slot("SL4", d2, 15, 2, List.of(
                        Exam.of("E7", SVT, SC, scRooms), Exam.of("E8", PHILO, LET, letRooms)), policy));

        return new ExamOperation("REG-2026", OperationType.REGIONAL_1BAC, slots);
    }

    public static List<Teacher> pool() {
        record Quota(Subject subject, String prefix, int count) {}
        List<Quota> quotas = List.of(
                new Quota(MATH, "Mat", 5), new Quota(PC, "Phy", 4), new Quota(SVT, "Svt", 4),
                new Quota(FR, "Fra", 4), new Quota(HG, "Hgo", 3), new Quota(AR, "Ara", 4),
                new Quota(EN, "Ang", 3), new Quota(PHILO, "Phi", 3));
        List<Teacher> pool = new ArrayList<>();
        int n = 1;
        for (Quota q : quotas)
            for (int i = 0; i < q.count(); i++)
                pool.add(Teacher.withDefaults("T" + n++, "Prof-" + q.prefix() + i,
                        q.subject(), "Lycée Al Massira"));
        return pool;
    }

    private static ExamSlot slot(String id, LocalDate date, int startHour, int ordinal,
                                 List<Exam> exams, ReservePolicy policy) {
        ExamSlot withoutReserve = new ExamSlot(id, date,
                LocalTime.of(startHour, 0), LocalTime.of(startHour + 2, 0),
                ordinal, exams, 0);
        return new ExamSlot(id, date,
                LocalTime.of(startHour, 0), LocalTime.of(startHour + 2, 0),
                ordinal, exams, policy.suggest(withoutReserve));
    }
}
