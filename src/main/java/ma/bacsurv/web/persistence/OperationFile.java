package ma.bacsurv.web.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** An uploaded operation input file, kept verbatim so a solve is reproducible. */
@Entity
@Table(name = "operation_file")
public class OperationFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected OperationFile() {}

    public OperationFile(String name, String content) {
        this.name = name;
        this.content = content;
        this.uploadedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getContent() { return content; }
    public Instant getUploadedAt() { return uploadedAt; }
}
