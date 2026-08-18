package ma.bacsurv.web.service;

import ma.bacsurv.web.persistence.OperationFile;

import java.time.Instant;

/** What callers see of a stored operation file. Entities never leave the service. */
public record OperationView(Long id, String name, Instant uploadedAt) {

    static OperationView of(OperationFile file) {
        return new OperationView(file.getId(), file.getName(), file.getUploadedAt());
    }
}
