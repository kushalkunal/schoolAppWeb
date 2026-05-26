package in.schoolapp.documents.entity;

import in.schoolapp.common.BaseEntity;
import in.schoolapp.documents.DocumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-school HTML/CSS override for a {@link DocumentType}. Absence of a row means the
 * default classpath template ships unchanged. Schools that want a custom letterhead /
 * watermark / logo placement upload a Thymeleaf template via the platform admin UI; the
 * variables exposed to the template are documented next to each {@code DocumentRequest}.
 */
@Entity
@Table(name = "document_templates")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class DocumentTemplate extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private DocumentType documentType;

    @Column(name = "html_template", nullable = false, columnDefinition = "text")
    private String htmlTemplate;

    @Column(name = "css_override", columnDefinition = "text")
    private String cssOverride;
}
