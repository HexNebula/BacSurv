package ma.bacsurv.web.service;

import ma.bacsurv.web.persistence.CenterEntity;
import ma.bacsurv.web.persistence.CenterRepository;
import ma.bacsurv.web.persistence.CenterStreamEntity;
import ma.bacsurv.web.persistence.CenterStreamRepository;
import ma.bacsurv.web.persistence.ExamEntity;
import ma.bacsurv.web.persistence.ExamSlotEntity;
import ma.bacsurv.web.persistence.OperationEntity;
import ma.bacsurv.web.persistence.OperationRepository;
import ma.bacsurv.web.persistence.StreamEntity;
import ma.bacsurv.web.persistence.StreamRepository;
import ma.bacsurv.web.persistence.SubjectEntity;
import ma.bacsurv.web.persistence.SubjectRepository;
import ma.bacsurv.web.persistence.TeacherEntity;
import ma.bacsurv.web.persistence.TeacherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The two lists a centre owns: the subjects it examines, the filières it runs.
 *
 * <p>Neither can be derived. The teacher pool is not the subject list — a
 * centre may examine a paper nobody there teaches, and may employ a teacher of
 * a subject it never examines. A session is not the filière list — the same
 * centre runs different filières in June and in July. Two centres in one city
 * differ in both, which is why this is something the administrator edits rather
 * than something the application infers.
 *
 * <p>Teachers and épreuves store the <em>name</em>, because that is what the
 * solver matches on. So renaming an entry rewrites every row that used it: the
 * catalogue and the data it names cannot be allowed to drift apart. Removing an
 * entry still in use is refused instead, since there is no correct name to put
 * in its place.
 */
@Service
public class CatalogueService {

    private final CenterRepository centers;
    private final SubjectRepository subjects;
    private final CenterStreamRepository streams;
    private final TeacherRepository teachers;
    private final OperationRepository operations;
    private final StreamRepository sessionStreams;

    public CatalogueService(CenterRepository centers, SubjectRepository subjects,
                            CenterStreamRepository streams, TeacherRepository teachers,
                            OperationRepository operations, StreamRepository sessionStreams) {
        this.centers = centers;
        this.subjects = subjects;
        this.streams = streams;
        this.teachers = teachers;
        this.operations = operations;
        this.sessionStreams = sessionStreams;
    }

    /**
     * An entry and what already depends on it, so the screen can say so.
     *
     * <p>{@code level} is the filière's — {@code BAC1} or {@code BAC2}. A
     * subject has none: a paper is a paper whichever year sits it.
     */
    public record Entry(Long id, String name, String level, int usedByTeachers, int usedByExams) {

        public boolean isUsed() {
            return usedByTeachers > 0 || usedByExams > 0;
        }
    }

    // ---------------------------------------------------------------- subjects

    @Transactional(readOnly = true)
    public List<Entry> subjectsOf(long centerId) {
        List<ExamEntity> exams = examsOf(centerId);
        List<TeacherEntity> pool = teachers.findPoolOfCenter(centerId);

        return subjects.findByCenterIdOrderByNameAsc(centerId).stream()
                .map(subject -> new Entry(subject.getId(), subject.getName(), null,
                        (int) pool.stream()
                                .filter(t -> subject.getName().equals(t.getSubject())).count(),
                        (int) exams.stream()
                                .filter(e -> subject.getName().equals(e.getSubject())).count()))
                .toList();
    }

    @Transactional
    public Long addSubject(long centerId, String name) {
        CenterEntity center = center(centerId);
        String cleaned = required(name, "subject.name");
        subjects.findByCenterIdAndName(centerId, cleaned).ifPresent(existing -> {
            throw new IllegalArgumentException("subject.exists");
        });
        return subjects.save(new SubjectEntity(center, cleaned)).getId();
    }

    @Transactional
    public void renameSubject(long subjectId, String name) {
        SubjectEntity subject = subjects.findById(subjectId)
                .orElseThrow(() -> new IllegalArgumentException("subject.unknown"));
        long centerId = subject.getCenter().getId();
        String cleaned = required(name, "subject.name");
        if (cleaned.equals(subject.getName())) return;

        subjects.findByCenterIdAndName(centerId, cleaned).ifPresent(other -> {
            throw new IllegalArgumentException("subject.exists");
        });

        String previous = subject.getName();
        subject.rename(cleaned);

        // the rows that named it follow, or the solver stops matching them
        teachers.findPoolOfCenter(centerId).stream()
                .filter(teacher -> previous.equals(teacher.getSubject()))
                .forEach(teacher -> teacher.update(teacher.getReference(), teacher.getName(),
                        cleaned, teacher.getEstablishment(), teacher.getGender()));

        examsOf(centerId).stream()
                .filter(exam -> previous.equals(exam.getSubject()))
                .forEach(exam -> exam.renameSubject(cleaned));
    }

    @Transactional
    public void removeSubject(long subjectId) {
        SubjectEntity subject = subjects.findById(subjectId)
                .orElseThrow(() -> new IllegalArgumentException("subject.unknown"));
        Entry entry = subjectsOf(subject.getCenter().getId()).stream()
                .filter(candidate -> candidate.id().equals(subjectId))
                .findFirst().orElseThrow();
        if (entry.isUsed()) throw new IllegalArgumentException("subject.inUse");
        subjects.delete(subject);
    }

    // ---------------------------------------------------------------- filières

    @Transactional(readOnly = true)
    public List<Entry> streamsOf(long centerId) {
        List<ExamEntity> exams = examsOf(centerId);
        List<Long> operationIds = operationsOf(centerId).stream()
                .map(OperationEntity::getId).toList();

        return streams.findByCenterIdOrderByNameAsc(centerId).stream()
                .map(stream -> new Entry(stream.getId(), stream.getName(), stream.getLevel(),
                        // a filière is "used" by the sessions that declared it
                        (int) operationIds.stream()
                                .flatMap(id -> sessionStreams.ofOperation(id).stream())
                                .filter(declared -> stream.getName().equals(declared.getName()))
                                .count(),
                        (int) exams.stream()
                                .filter(e -> stream.getName().equals(e.getStream())).count()))
                .toList();
    }

    /**
     * A filière of the centre, at one level.
     *
     * <p>The level is not optional. A filière with none would be offered when
     * planning either year, which is the situation this list exists to stop.
     */
    @Transactional
    public Long addStream(long centerId, String name, String level) {
        CenterEntity center = center(centerId);
        String cleaned = required(name, "stream.name");
        String cleanedLevel = level(level);
        streams.findByCenterIdAndNameAndLevel(centerId, cleaned, cleanedLevel)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("stream.listed");
                });
        return streams.save(new CenterStreamEntity(center, cleaned, cleanedLevel)).getId();
    }

    @Transactional
    public void renameStream(long streamId, String name, String level) {
        CenterStreamEntity stream = streams.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("stream.unknown"));
        long centerId = stream.getCenter().getId();
        String cleaned = required(name, "stream.name");
        // the level may be corrected on its own: a filière listed at the wrong
        // year is the mistake the backfill was always going to leave behind
        String cleanedLevel = level == null ? stream.getLevel() : level(level);
        boolean sameName = cleaned.equals(stream.getName());
        if (sameName && cleanedLevel.equals(stream.getLevel())) return;

        streams.findByCenterIdAndNameAndLevel(centerId, cleaned, cleanedLevel)
                .filter(other -> !other.getId().equals(streamId))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("stream.listed");
                });

        stream.setLevel(cleanedLevel);
        if (sameName) return;

        String previous = stream.getName();
        stream.rename(cleaned);

        for (OperationEntity operation : operationsOf(centerId)) {
            sessionStreams.ofOperation(operation.getId()).stream()
                    .filter(declared -> previous.equals(declared.getName()))
                    .forEach(declared -> declared.rename(cleaned));
        }
        examsOf(centerId).stream()
                .filter(exam -> previous.equals(exam.getStream()))
                .forEach(exam -> exam.rename(cleaned));
    }

    @Transactional
    public void removeStream(long streamId) {
        CenterStreamEntity stream = streams.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("stream.unknown"));
        Entry entry = streamsOf(stream.getCenter().getId()).stream()
                .filter(candidate -> candidate.id().equals(streamId))
                .findFirst().orElseThrow();
        if (entry.isUsed()) throw new IllegalArgumentException("stream.inUse");
        streams.delete(stream);
    }

    // ------------------------------------------------------------------ shared

    /**
     * Records a name the centre has not listed yet.
     *
     * <p>An import must not fail because a spreadsheet mentions a subject the
     * catalogue is missing — the administrator would have to leave, type it in,
     * and come back. The entry is created quietly and shows up in the centre's
     * list, where it can be corrected or removed.
     */
    @Transactional
    public void rememberSubject(long centerId, String name) {
        if (name == null || name.isBlank()) return;
        String cleaned = name.trim();
        if (subjects.findByCenterIdAndName(centerId, cleaned).isPresent()) return;
        subjects.save(new SubjectEntity(center(centerId), cleaned));
    }

    /** Same idea for a filière named while building a session's timetable. */
    @Transactional
    public void rememberStream(long centerId, String name, String level) {
        if (name == null || name.isBlank()) return;
        String cleaned = name.trim();
        String cleanedLevel = level(level);
        if (streams.findByCenterIdAndNameAndLevel(centerId, cleaned, cleanedLevel).isPresent()) {
            return;
        }
        streams.save(new CenterStreamEntity(center(centerId), cleaned, cleanedLevel));
    }

    /** Only the two levels exist; anything else is a caller's mistake. */
    private static String level(String value) {
        String cleaned = value == null ? "" : value.trim();
        if (!cleaned.equals("BAC1") && !cleaned.equals("BAC2")) {
            throw new IllegalArgumentException("stream.level");
        }
        return cleaned;
    }

    private List<ExamEntity> examsOf(long centerId) {
        return operationsOf(centerId).stream()
                .flatMap(operation -> operation.getSlots().stream())
                .flatMap(slot -> slot.getExams().stream())
                .toList();
    }

    private List<OperationEntity> operationsOf(long centerId) {
        return operations.findAllWithCenter().stream()
                .filter(operation -> operation.getCenter().getId().equals(centerId))
                .toList();
    }

    private CenterEntity center(long centerId) {
        return centers.findById(centerId)
                .orElseThrow(() -> new IllegalArgumentException("center.unknown"));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + ".required");
        return value.trim();
    }
}
