package in.schoolapp.timetable.entity;

import in.schoolapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Records a one-day substitute teacher covering an absent colleague.
 * A substitution for (date, sectionId, periodId) is unique — only one substitute can be
 * assigned per slot per day.
 */
@Entity
@Table(
    name = "timetable_substitutions",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_tt_sub_section_period_date",
        columnNames = {"section_id", "period_id", "date"})
)
@Getter
@Setter
public class TimetableSubstitution extends BaseEntity {

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "period_id", nullable = false)
    private UUID periodId;

    @Column(nullable = false)
    private LocalDate date;

    /** The teacher who is absent (from the regular timetable entry). */
    @Column(name = "absent_teacher_id")
    private UUID absentTeacherId;

    /** The substitute who will cover this slot. */
    @Column(name = "substitute_teacher_id", nullable = false)
    private UUID substituteTeacherId;

    @Column(columnDefinition = "TEXT")
    private String reason;
}
