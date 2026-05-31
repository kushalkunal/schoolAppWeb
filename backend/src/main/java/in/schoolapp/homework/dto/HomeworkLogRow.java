package in.schoolapp.homework.dto;

import java.time.LocalDate;

/** One row of the admin homework log: who assigned what, to which class, on which day. */
public record HomeworkLogRow(
    LocalDate assignedOn,
    String teacherName,
    String sectionLabel,
    String subjectName,
    String title,
    LocalDate dueDate
) {}
