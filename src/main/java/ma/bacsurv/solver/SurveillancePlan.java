package ma.bacsurv.solver;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ma.bacsurv.domain.Teacher;

import java.util.List;

@PlanningSolution
public class SurveillancePlan {

    @PlanningEntityCollectionProperty
    private List<DutyAssignment> assignments;

    /**
     * Also a fact collection, not only a value range: constraints must be able
     * to reason about a teacher who received nothing, and such a teacher never
     * shows up in the assignment stream.
     */
    @ValueRangeProvider
    @ProblemFactCollectionProperty
    private List<Teacher> teacherPool;

    @PlanningScore
    private HardSoftScore score;

    public SurveillancePlan() {}

    public SurveillancePlan(List<DutyAssignment> assignments, List<Teacher> teacherPool) {
        this.assignments = assignments;
        this.teacherPool = teacherPool;
    }

    public List<DutyAssignment> getAssignments() { return assignments; }
    public List<Teacher> getTeacherPool() { return teacherPool; }
    public HardSoftScore getScore() { return score; }
    public void setScore(HardSoftScore score) { this.score = score; }
}
