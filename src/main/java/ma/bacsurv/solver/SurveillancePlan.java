package ma.bacsurv.solver;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ma.bacsurv.domain.Teacher;

import java.util.List;

@PlanningSolution
public class SurveillancePlan {

    @PlanningEntityCollectionProperty
    private List<DutyAssignment> assignments;

    @ValueRangeProvider
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
