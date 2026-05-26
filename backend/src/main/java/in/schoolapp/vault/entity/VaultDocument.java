package in.schoolapp.vault.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "student_vault_documents")
@Getter @Setter @NoArgsConstructor
public class VaultDocument {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false) private UUID id;
    @Column(name = "school_id", nullable = false, updatable = false) private UUID schoolId;
    @Column(name = "student_id", nullable = false) private UUID studentId;
    @Column(name = "doc_type", nullable = false, length = 50) private String docType;
    @Column(name = "file_name", nullable = false, length = 255) private String fileName;
    @Column(name = "file_url", nullable = false, columnDefinition = "TEXT") private String fileUrl;
    @Column(name = "mime_type", length = 120) private String mimeType;
    @Column(name = "size_bytes") private Long sizeBytes;
    @Column(name = "uploaded_by_id", nullable = false) private UUID uploadedById;
    @Column(name = "uploaded_at", nullable = false) private OffsetDateTime uploadedAt = OffsetDateTime.now();
    @Column(columnDefinition = "TEXT") private String notes;
}
