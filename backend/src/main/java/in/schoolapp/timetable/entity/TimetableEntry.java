package in.schoolapp.timetable.entity;

import in.schoolapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Subject + teacher assigned to a (section × day-of-week × period). Subject and teacher are
 * nullable to model free periods.
 */
@Entity
@Table(
    name = "timetable_entries",
    uniqueConstraints = @UniqueConstraint(name = "uq_tt_entry_section_day_period",
        columnNames = {"section_id", "day_of_week", "period_id"})
)
@Getter
@Setter
public class TimetableEntry extends BaseEntity {

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "period_id", nullable = false)
    private UUID periodId;

    /** 1=Monday … 7=Sunday (ISO-8601). */
    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "subject_id")
    private UUID subjectId;

    @Column(name = "teacher_id")
    private UUID teacherId;

    @Column(columnDefinition = "TEXT")
    private String note;
}
