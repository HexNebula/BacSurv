package ma.bacsurv.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * The schedulable unit. Scope per role:
 *   SURVEILLANCE → slot + exam + room
 *   PERMANENCE   → slot + exam
 *   RESERVE      → slot only
 * assignedTeacher is the only mutable field — the decision variable.
 */
public final class Duty {

    private final String id;
    private final ExamSlot slot;
    private final Exam exam;   // null for RESERVE
    private final Room room;   // null unless SURVEILLANCE
    private final DutyRole role;
    private Teacher assignedTeacher;

    private Duty(String id, ExamSlot slot, Exam exam, Room room, DutyRole role) {
        this.id = Objects.requireNonNull(id);
        this.slot = Objects.requireNonNull(slot);
        this.exam = exam;
        this.room = room;
        this.role = Objects.requireNonNull(role);
    }

    public static Duty surveillance(String id, ExamSlot slot, Exam exam, Room room) {
        return new Duty(id, slot, Objects.requireNonNull(exam), Objects.requireNonNull(room),
                DutyRole.SURVEILLANCE);
    }

    public static Duty permanence(String id, ExamSlot slot, Exam exam) {
        return new Duty(id, slot, Objects.requireNonNull(exam), null, DutyRole.PERMANENCE);
    }

    public static Duty reserve(String id, ExamSlot slot) {
        return new Duty(id, slot, null, null, DutyRole.RESERVE);
    }

    public String id() { return id; }
    public ExamSlot slot() { return slot; }
    public Optional<Exam> exam() { return Optional.ofNullable(exam); }
    public Optional<Room> room() { return Optional.ofNullable(room); }
    public DutyRole role() { return role; }
    public Optional<Teacher> assignedTeacher() { return Optional.ofNullable(assignedTeacher); }

    public void assign(Teacher teacher) { this.assignedTeacher = teacher; }
    public void unassign() { this.assignedTeacher = null; }

    @Override public boolean equals(Object o) {
        return o instanceof Duty d && id.equals(d.id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return role + "[" + id + "]"
                + (exam != null ? " " + exam.subject().name() : "")
                + (room != null ? " @" + room.label() : "")
                + " " + slot.date() + " " + slot.startTime();
    }
}
