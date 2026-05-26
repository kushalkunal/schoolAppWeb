package in.schoolapp.student.dto;

import in.schoolapp.student.entity.ParentRelation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Minimum-viable student payload per LLD §5.1 "Minimum Viable Student Record":
 * <ul>
 *   <li>{@code firstName} — required</li>
 *   <li>{@code sectionId} — required</li>
 *   <li>{@code parentPhone} — required (WhatsApp-capable mobile)</li>
 * </ul>
 * Everything else is progressive — a teacher/admin can fill in later without the record ever
 * blocking usage. Sibling detection: if a {@link in.schoolapp.student.entity.Parent} already
 * exists at this tenant with {@code parentPhone}, the new Student is linked to that same Parent
 * (no duplicate Parent record).
 */
public record CreateStudentRequest(
    @NotBlank @Size(max = 100) String firstName,
    @Size(max = 100) String lastName,
    @NotNull UUID sectionId,
    @NotBlank String parentPhone,
    @Size(max = 200) String parentName,
    @Size(max = 255) String parentEmail,
    ParentRelation parentRelation,
    @Size(max = 50) String admissionNumber,
    @Size(max = 10) String gender,
    LocalDate dateOfBirth,
    @Size(max = 5) String bloodGroup,
    String address
) {}
