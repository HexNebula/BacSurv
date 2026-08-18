package ma.bacsurv.solver;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import ma.bacsurv.domain.Duty;
import ma.bacsurv.domain.DutyRole;
import ma.bacsurv.domain.Subject;
import ma.bacsurv.domain.Teacher;

import java.time.LocalDate;

/**
 * Solver-side wrapper around a domain Duty. Keeps Timefold annotations
 * out of the domain — the adapter maps back after solving.
 */
@PlanningEntity
public class DutyAssignment {

    @PlanningId
    private String id;
    private Duty duty;
    @PlanningVariable
    private Teacher teacher;

    public DutyAssignment() {} // Timefold needs a no-arg constructor

    public DutyAssignment(Duty duty) {
        this.id = duty.id();
        this.duty = duty;
        this.teacher = duty.assignedTeacher().orElse(null);
    }

    public String getId() { return id; }
    public Duty getDuty() { return duty; }
    public Teacher getTeacher() { return teacher; }
    public void setTeacher(Teacher teacher) { this.teacher = teacher; }

    // Convenience accessors for constraint streams
    public DutyRole role() { return duty.role(); }
    public String slotId() { return duty.slot().id(); }
    public LocalDate date() { return duty.slot().date(); }
    public boolean isSurveillance() { return duty.role() == DutyRole.SURVEILLANCE; }
    public Subject examSubject() { return duty.exam().map(e -> e.subject()).orElse(null); }

    /**
     * Join key for "two surveillants in the same physical room of the same
     * exam". Non-surveillance duties get a key unique to themselves so they
     * can never join each other.
     */
    public String roomKey() {
        return isSurveillance()
                ? slotId() + "/" + duty.exam().orElseThrow().id() + "/" + duty.room().orElseThrow().id()
                : "!" + id;
    }
}
