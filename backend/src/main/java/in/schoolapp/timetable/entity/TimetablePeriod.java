package in.schoolapp.timetable.entity;

import in.schoolapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

/**
 * One time slot in a school's day — e.g. "Period 1 · 08:30–09:15".
 * School-wide; sections share the periods catalog.
 */
@Entity
@Table(
    name = "timetable_periods",
    uniqueConstraints = @UniqueConstraint(name = "uq_tt_period_school_name",
        columnNames = {"school_id", "name"})
)
@Getter
@Setter
public class TimetablePeriod extends BaseEntity {

    @Column(nullable = false, length = 40)
    private String name;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_break", nullable = false)
    private boolean breakSlot;
}
