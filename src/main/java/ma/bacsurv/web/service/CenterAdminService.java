package ma.bacsurv.web.service;

import ma.bacsurv.domain.OperationType;
import ma.bacsurv.web.persistence.CenterEntity;
import ma.bacsurv.web.persistence.CenterRepository;
import ma.bacsurv.web.persistence.OperationEntity;
import ma.bacsurv.web.persistence.OperationRepository;
import ma.bacsurv.web.persistence.RoomEntity;
import ma.bacsurv.web.persistence.RoomRepository;
import ma.bacsurv.web.persistence.TeacherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Setting a centre up: its name, its rooms, its sessions. Everything here is
 * typed once and reused for years — a centre does not gain a room between
 * June and July — so the screens are built for a first entry that is easy and
 * a later correction that is safe.
 */
@Service
public class CenterAdminService {

    private final CenterRepository centers;
    private final RoomRepository rooms;
    private final OperationRepository operations;
    private final TeacherRepository teachers;

    public CenterAdminService(CenterRepository centers, RoomRepository rooms,
                              OperationRepository operations, TeacherRepository teachers) {
        this.centers = centers;
        this.rooms = rooms;
        this.operations = operations;
        this.teachers = teachers;
    }

    /** A room as the administrator sees it. */
    public record RoomView(Long id, String reference, String label, Integer surveillants) {}

    /**
     * A session of a centre, with what it holds so far.
     *
     * <p>The state is DRAFT or SETTLED: a draft counts for nothing and may be
     * deleted, a settled session is the répartition that went out and is what
     * the privilege queue is built from.
     */
    public record SessionView(Long id, String reference, String type,
                              LocalDate startsOn, LocalDate endsOn, int slotCount,
                              String state) {}

    /**
     * How the establishment is identified on paper: the académie régionale, the
     * direction provinciale, the commune and its ministerial reference. Nothing
     * here reaches the solver — it is the head of a printed convocation.
     */
    public record CenterIdentity(String academy, String directorate, String commune,
                                 String ministerialReference) {}

    /** A centre and everything set up under it. */
    public record CenterDetail(Long id, String name, CenterIdentity identity, int teacherCount,
                               List<RoomView> rooms, List<SessionView> sessions) {}

    /**
     * R1, R2, … R13 — not R1, R10, R11, R2.
     *
     * <p>The database orders references as text, which puts the tenth room
     * second in a centre of thirteen. Nobody reads a room list that way, so the
     * number is compared as a number and the letters around it as text. A
     * reference with no digits keeps its alphabetical place.
     */
    private static final Comparator<RoomView> BY_REFERENCE =
            Comparator.comparing((RoomView room) -> lettersOf(room.reference()))
                    .thenComparingLong(room -> digitsOf(room.reference()))
                    .thenComparing(RoomView::reference);

    private static final Pattern TRAILING_NUMBER = Pattern.compile("^(.*?)(\\d+)$");

    private static String lettersOf(String reference) {
        if (reference == null) return "";
        Matcher matcher = TRAILING_NUMBER.matcher(reference);
        return matcher.matches() ? matcher.group(1) : reference;
    }

    /** Whatever has no trailing number sorts last, after the numbered rooms. */
    private static long digitsOf(String reference) {
        if (reference == null) return Long.MAX_VALUE;
        Matcher matcher = TRAILING_NUMBER.matcher(reference);
        if (!matcher.matches()) return Long.MAX_VALUE;
        try {
            return Long.parseLong(matcher.group(2));
        } catch (NumberFormatException tooLong) {
            return Long.MAX_VALUE;
        }
    }

    @Transactional(readOnly = true)
    public CenterDetail detail(long centerId) {
        CenterEntity center = centers.findById(centerId).orElseThrow(
                () -> new IllegalArgumentException("no center with id " + centerId));

        List<RoomView> roomViews = rooms.findByCenterIdOrderByReferenceAsc(centerId).stream()
                .map(room -> new RoomView(room.getId(), room.getReference(), room.getLabel(),
                        room.getSurveillantsOverride()))
                .sorted(BY_REFERENCE)
                .toList();

        List<SessionView> sessionViews = operations.findAllWithCenter().stream()
                .filter(operation -> operation.getCenter().getId().equals(centerId))
                .map(operation -> new SessionView(operation.getId(), operation.getReference(),
                        operation.getType(), operation.getStartsOn(), operation.getEndsOn(),
                        operation.getSlots().size(), operation.getState().name()))
                .toList();

        return new CenterDetail(center.getId(), center.getName(), identityOf(center),
                teachers.findPoolOfCenter(centerId).size(), roomViews, sessionViews);
    }

    @Transactional
    public Long createCenter(String name) {
        String cleaned = required(name, "center.name");
        centers.findByName(cleaned).ifPresent(existing -> {
            throw new IllegalArgumentException("center.exists");
        });
        return centers.save(new CenterEntity(cleaned)).getId();
    }

    @Transactional
    public void renameCenter(long centerId, String name) {
        CenterEntity center = centers.findById(centerId).orElseThrow(
                () -> new IllegalArgumentException("no center with id " + centerId));
        center.setName(required(name, "center.name"));
    }

    /**
     * The centre's administrative identity, as the ministry writes it.
     *
     * <p>The name is required because a centre without one cannot be printed on
     * anything. The four identifiers are not: an administrator setting up in
     * June should not be stopped by a reference they have to go and look up, so
     * a blank field is stored as nothing rather than as an empty string.
     */
    @Transactional
    public void editCenter(long centerId, String name, CenterIdentity identity) {
        CenterEntity center = centers.findById(centerId).orElseThrow(
                () -> new IllegalArgumentException("no center with id " + centerId));
        center.setName(required(name, "center.name"));
        if (identity == null) return;
        center.setAcademy(trimmed(identity.academy()));
        center.setDirectorate(trimmed(identity.directorate()));
        center.setCommune(trimmed(identity.commune()));
        center.setMinisterialReference(trimmed(identity.ministerialReference()));
    }

    private static CenterIdentity identityOf(CenterEntity center) {
        return new CenterIdentity(center.getAcademy(), center.getDirectorate(),
                center.getCommune(), center.getMinisterialReference());
    }

    /** An untyped field is absent, not present and empty. */
    private static String trimmed(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * Adds {@code count} rooms numbered after the ones already there.
     *
     * <p>A centre with thirteen identical rooms should not be thirteen forms.
     * The exceptions — a hall, a library — are renamed afterwards, which is
     * the rare case and the one worth spending clicks on.
     */
    @Transactional
    public int addRooms(long centerId, int count, String labelPrefix) {
        if (count < 1) throw new IllegalArgumentException("rooms.count.invalid");
        if (count > 200) throw new IllegalArgumentException("rooms.count.tooMany");

        CenterEntity center = centers.findById(centerId).orElseThrow(
                () -> new IllegalArgumentException("no center with id " + centerId));

        List<RoomEntity> existing = rooms.findByCenterIdOrderByReferenceAsc(centerId);
        int next = existing.stream()
                .map(RoomEntity::getReference)
                .filter(reference -> reference.matches("R\\d+"))
                .mapToInt(reference -> Integer.parseInt(reference.substring(1)))
                .max().orElse(0) + 1;

        String prefix = labelPrefix == null || labelPrefix.isBlank() ? "Salle" : labelPrefix.trim();
        for (int i = 0; i < count; i++) {
            int number = next + i;
            rooms.save(new RoomEntity(center, "R" + number, prefix + " " + number));
        }
        center.touch();
        return count;
    }

    @Transactional
    public void renameRoom(long roomId, String label, Integer surveillants) {
        RoomEntity room = rooms.findById(roomId).orElseThrow(
                () -> new IllegalArgumentException("no room with id " + roomId));
        room.setLabel(required(label, "room.label"));
        // below the official floor is not a centre's decision to make. The
        // figure comes from the rules rather than a literal here: this is the
        // only place a room's own number is written, and a floor spelled out
        // twice is a floor that eventually differs from itself.
        if (surveillants != null
                && surveillants < ma.bacsurv.rules.StaffingPolicy.MINIMUM_SURVEILLANTS_PER_ROOM)
            throw new IllegalArgumentException("room.surveillants.tooFew");
        room.setSurveillantsOverride(surveillants);
        room.getCenter().touch();
    }

    @Transactional
    public void deleteRoom(long roomId) {
        rooms.findById(roomId).map(RoomEntity::getCenter).ifPresent(CenterEntity::touch);
        rooms.deleteById(roomId);
    }

    @Transactional
    public Long createSession(long centerId, String reference, String type,
                              LocalDate startsOn, LocalDate endsOn) {
        CenterEntity center = centers.findById(centerId).orElseThrow(
                () -> new IllegalArgumentException("no center with id " + centerId));

        String cleaned = required(reference, "session.reference");
        OperationType parsed;
        try {
            parsed = OperationType.valueOf(type);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("session.type.invalid");
        }
        if (startsOn == null || endsOn == null) throw new IllegalArgumentException("session.dates");
        if (endsOn.isBefore(startsOn)) throw new IllegalArgumentException("session.dates.reversed");

        return operations.save(new OperationEntity(center, cleaned, parsed.name(),
                startsOn, endsOn)).getId();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + ".required");
        return value.trim();
    }
}
