package in.schoolapp.student.dto;

import in.schoolapp.student.entity.Student;

import java.time.LocalDate;
import java.util.UUID;

public record StudentResponse(
    UUID id,
    String firstName,
    String lastName,
    String displayName,
    String admissionNumber,
    String gender,
    LocalDate dateOfBirth,
    String bloodGroup,
    String photoUrl,
    boolean active
) {
    public static StudentResponse from(Student s) {
        return new StudentResponse(
            s.getId(),
            s.getFirstName(),
            s.getLastName(),
            s.displayName(),
            s.getAdmissionNumber(),
            s.getGender(),
            s.getDateOfBirth(),
            s.getBloodGroup(),
            s.getPhotoUrl(),
            s.isActive()
        );
    }
}
