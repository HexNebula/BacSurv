package ma.bacsurv.io;

import java.util.List;
import java.util.Map;

/**
 * Raw shape of an operation input file, exactly as the JSON is written.
 * Dates/times stay strings here; InputMapper parses and validates them
 * into domain objects. Optional fields are null when absent.
 */
public record OperationInput(
        CenterDto center,
        OperationDto operation,
        DefaultsDto defaults,
        List<RoomDto> rooms,
        List<SlotDto> slots,
        List<TeacherDto> teachers) {

    /** Which center this operation belongs to — its teachers carry over between operations. */
    public record CenterDto(String name) {}

    public record OperationDto(String id, String type) {}

    /** Fallbacks applied wherever an exam/slot omits its own value. */
    public record DefaultsDto(
            Integer surveillantsPerRoom,
            Integer permanencePerExam,
            Double reservePercentage) {}

    public record RoomDto(String id, String label) {}

    public record SlotDto(
            String id,
            String date,
            String start,
            String end,
            List<ExamDto> exams,
            Integer reserveCount) {}

    public record ExamDto(
            String id,
            String subject,
            String stream,
            List<String> rooms,
            Integer surveillantsPerRoom,
            Integer permanenceCount) {}

    public record TeacherDto(
            String id,
            /** numéro de matricule / رقم التأجير — required, unique. */
            String matricule,
            String name,
            String subject,
            String establishment,
            String gender,
            List<UnavailabilityDto> unavailable,
            Map<String, Integer> prior) {}

    public record UnavailabilityDto(String date, String start, String end) {}
}
