package ma.bacsurv.web.service;

import ma.bacsurv.web.persistence.OperationEntity;

import java.time.Instant;

/** What callers see of a stored operation. Entities never leave the service. */
public record OperationView(Long id, String reference,
                            Long centerId, String centerName,
                            String type, Instant createdAt, Long sourceFileId) {

    static OperationView of(OperationEntity operation, Long sourceFileId) {
        return new OperationView(operation.getId(), operation.getReference(),
                operation.getCenter().getId(), operation.getCenter().getName(),
                operation.getType(), operation.getCreatedAt(), sourceFileId);
    }
}
