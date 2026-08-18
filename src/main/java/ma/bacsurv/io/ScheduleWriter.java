package ma.bacsurv.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import ma.bacsurv.application.ValidationReport;
import ma.bacsurv.domain.Duty;
import ma.bacsurv.domain.DutyRole;
import ma.bacsurv.domain.Room;
import ma.bacsurv.domain.Teacher;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Solved schedule -> JSON result file (assignments + workload + validation). */
public final class ScheduleWriter {

    private final ObjectMapper json = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public record AssignmentRow(
            String dutyId, String slotId, String date, String start, String end,
            String role, String examId, String subject, String stream,
            String roomId, String teacherId, String teacherName) {}

    public record WorkloadRow(
            String teacherId, String name, String subject,
            int surveillance, int reserve, int permanence, int priorTotal, int total) {}

    public record Result(
            String operationId,
            boolean feasible,
            int hardViolations,
            int softViolations,
            int unfilled,
            List<String> hardViolationDetails,
            List<AssignmentRow> assignments,
            List<WorkloadRow> workload) {}

    public Result build(String operationId, List<Duty> duties, List<Teacher> pool,
                        ValidationReport report) {
        List<AssignmentRow> rows = duties.stream()
                .sorted(Comparator.comparing((Duty d) -> d.slot().date())
                        .thenComparing(d -> d.slot().startTime())
                        .thenComparing(Duty::id))
                .map(d -> new AssignmentRow(
                        d.id(), d.slot().id(),
                        d.slot().date().toString(),
                        d.slot().startTime().toString(), d.slot().endTime().toString(),
                        d.role().name(),
                        d.exam().map(e -> e.id()).orElse(null),
                        d.exam().map(e -> e.subject().name()).orElse(null),
                        d.exam().map(e -> e.stream().name()).orElse(null),
                        d.room().map(Room::id).orElse(null),
                        d.assignedTeacher().map(Teacher::id).orElse(null),
                        d.assignedTeacher().map(Teacher::name).orElse(null)))
                .toList();

        Map<Teacher, Map<DutyRole, Integer>> load = new LinkedHashMap<>();
        for (Duty d : duties) {
            d.assignedTeacher().ifPresent(t ->
                    load.computeIfAbsent(t, x -> new LinkedHashMap<>())
                            .merge(d.role(), 1, Integer::sum));
        }
        List<WorkloadRow> workload = pool.stream()
                .sorted(Comparator.comparing(Teacher::id))
                .map(t -> {
                    Map<DutyRole, Integer> m = load.getOrDefault(t, Map.of());
                    int s = m.getOrDefault(DutyRole.SURVEILLANCE, 0);
                    int r = m.getOrDefault(DutyRole.RESERVE, 0);
                    int p = m.getOrDefault(DutyRole.PERMANENCE, 0);
                    return new WorkloadRow(t.id(), t.name(), t.subject().name(),
                            s, r, p, t.priorTotal(), s + r + p);
                })
                .toList();

        long unfilled = duties.stream().filter(d -> d.assignedTeacher().isEmpty()).count();
        return new Result(operationId,
                report.isFeasible() && unfilled == 0,
                report.hardViolations().size(),
                report.softViolations().size(),
                (int) unfilled,
                report.hardViolations().stream()
                        .map(v -> v.rule() + ": " + v.detail()).toList(),
                rows, workload);
    }

    public void write(Result result, Path file) {
        try {
            json.writeValue(file.toFile(), result);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + file + ": " + e.getMessage(), e);
        }
    }

    public String toJson(Result result) {
        try {
            return json.writeValueAsString(result);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot serialise schedule: " + e.getMessage(), e);
        }
    }

    public Result parse(String content) {
        try {
            return json.readValue(content, Result.class);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read stored schedule: " + e.getMessage(), e);
        }
    }
}
