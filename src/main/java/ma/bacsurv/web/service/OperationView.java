package ma.bacsurv.web.service;

import ma.bacsurv.web.persistence.OperationEntity;

import java.time.Instant;

/**
 * What callers see of a stored operation. Entities never leave the service.
 *
 * <p>{@code level} is here and not worked out from {@code type} by whoever
 * reads this. It is the list the session picker draws from, so a screen that
 * knows only the chosen session had to go and fetch the centre to find out
 * which year it examines — and the alternative, deriving it from the type, is
 * the second copy of a rule that was wrong about the candidats libres'
 * regional rattrapage.
 */
public record OperationView(Long id, String reference,
                            Long centerId, String centerName,
                            String type, String level,
                            Instant createdAt, Long sourceFileId) {

    static OperationView of(OperationEntity operation, Long sourceFileId) {
        return new OperationView(operation.getId(), operation.getReference(),
                operation.getCenter().getId(), operation.getCenter().getName(),
                operation.getType(), operation.getLevel(),
                operation.getCreatedAt(), sourceFileId);
    }
}
