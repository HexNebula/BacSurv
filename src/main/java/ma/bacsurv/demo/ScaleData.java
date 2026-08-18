package ma.bacsurv.demo;

import ma.bacsurv.application.ReservePolicy;
import ma.bacsurv.domain.Exam;
import ma.bacsurv.domain.ExamOperation;
import ma.bacsurv.domain.ExamSlot;
import ma.bacsurv.domain.Gender;
import ma.bacsurv.domain.OperationType;
import ma.bacsurv.domain.Room;
import ma.bacsurv.domain.Stream;
import ma.bacsurv.domain.Subject;
import ma.bacsurv.domain.Teacher;
import ma.bacsurv.domain.Unavailability;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * A center at the size a real one runs at, generated deterministically:
 * several exam days, two sessions a day, streams sitting different subjects
 * in parallel, and a pool large enough to cover it.
 *
 * The demo data used elsewhere is deliberately tiny; this exists to find out
 * how the solver behaves when the problem is the size of an actual centre.
 */
public final class ScaleData {

    public record Size(int days, int slotsPerDay, int rooms, int teachers, int seed) {

        public static Size realisticCenter() {
            return new Size(6, 2, 20, 100, 42);
        }
    }

    private static final List<Subject> SUBJECTS = List.of(
            new Subject("Mathématiques"), new Subject("Physique-Chimie"),
            new Subject("SVT"), new Subject("Français"), new Subject("Arabe"),
            new Subject("Histoire-Géographie"), new Subject("Anglais"),
            new Subject("Philosophie"));

    private static final List<Stream> STREAMS = List.of(
            new Stream("Sciences Expérimentales"), new Stream("Sciences Maths"),
            new Stream("Lettres"));

    private final Size size;
    private final List<Room> rooms;

    public ScaleData(Size size) {
        this.size = size;
        this.rooms = new ArrayList<>();
        for (int i = 1; i <= size.rooms(); i++) {
            rooms.add(new Room("R" + i, "Salle " + i));
        }
    }

    public ExamOperation operation() {
        ReservePolicy policy = ReservePolicy.officialDefault();
        List<ExamSlot> slots = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 6, 8);
        int examCounter = 1;

        for (int day = 0; day < size.days(); day++) {
            for (int session = 0; session < size.slotsPerDay(); session++) {
                String id = "S" + (day * size.slotsPerDay() + session + 1);
                LocalTime from = session == 0 ? LocalTime.of(8, 0) : LocalTime.of(15, 0);

                // the two scientific streams sit the same paper at the same
                // hour, the literature stream sits another one — so this slot
                // holds three exams but only two subjects, hence two permanences
                List<Exam> exams = new ArrayList<>();
                int roomsPerStream = size.rooms() / STREAMS.size();
                Subject scientific = SUBJECTS.get((day * 2 + session) % SUBJECTS.size());
                Subject literary = SUBJECTS.get((day * 2 + session + 3) % SUBJECTS.size());
                for (int s = 0; s < STREAMS.size(); s++) {
                    List<Room> block = rooms.subList(s * roomsPerStream,
                            s == STREAMS.size() - 1 ? size.rooms() : (s + 1) * roomsPerStream);
                    Stream stream = STREAMS.get(s);
                    Subject subject = stream.name().startsWith("Lettres") ? literary : scientific;
                    exams.add(Exam.of("E" + examCounter++, subject, stream, block));
                }

                ExamSlot withoutReserve = new ExamSlot(id, start.plusDays(day), from,
                        from.plusHours(2), session + 1, exams, 0);
                slots.add(new ExamSlot(id, start.plusDays(day), from, from.plusHours(2),
                        session + 1, exams, policy.suggest(withoutReserve)));
            }
        }
        return new ExamOperation("NAT-2026-SCALE", OperationType.NATIONAL_2BAC, slots);
    }

    /** A pool spread over the subjects, with a realistic sprinkling of absences. */
    public List<Teacher> pool() {
        Random random = new Random(size.seed());
        LocalDate start = LocalDate.of(2026, 6, 8);
        List<Teacher> pool = new ArrayList<>();

        for (int i = 1; i <= size.teachers(); i++) {
            Subject subject = SUBJECTS.get(i % SUBJECTS.size());
            Set<Unavailability> unavailabilities = Set.of();
            if (random.nextInt(10) == 0) { // one teacher in ten is away for a day
                unavailabilities = Set.of(
                        Unavailability.wholeDay(start.plusDays(random.nextInt(size.days()))));
            }
            Teacher teacher = Teacher.withDefaults("T" + i, "D%06d".formatted(200000 + i),
                    "Prof " + i, subject, "Lycée " + (i % 5 + 1));
            pool.add(new Teacher(teacher.id(), teacher.matricule(), teacher.name(),
                    subject, teacher.establishment(),
                    i % 2 == 0 ? Gender.FEMALE : Gender.MALE,
                    unavailabilities, teacher.qualifications()));
        }
        return pool;
    }
}
