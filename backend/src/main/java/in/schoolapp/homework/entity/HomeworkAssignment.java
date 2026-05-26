package in.schoolapp.homework.entity;

import in.schoolapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

import java.util.UUID;

@Entity
@Table(name = "homework_assignments")
@Getter
@Setter
public class HomeworkAssignment extends BaseEntity {

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "subject_id")
    private UUID subjectId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "attachment_url", columnDefinition = "TEXT")
    private String attachmentUrl;

    @Column(name = "due_date")
    private LocalDate dueDate;

    // `created_by_id` is inherited from BaseEntity.createdByStaffId — JPA auditing fills it.
}
