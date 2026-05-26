package in.schoolapp.student;

import in.schoolapp.school.repository.SchoolClassRepository;
import in.schoolapp.school.repository.SectionRepository;
import in.schoolapp.student.entity.Student;
import in.schoolapp.student.entity.StudentParentLink;
import in.schoolapp.student.repository.ParentRepository;
import in.schoolapp.student.repository.StudentEnrollmentRepository;
import in.schoolapp.student.repository.StudentParentLinkRepository;
import in.schoolapp.student.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamilyServiceTest {

    @Mock StudentRepository studentRepository;
    @Mock ParentRepository parentRepository;
    @Mock StudentParentLinkRepository linkRepository;
    @Mock StudentEnrollmentRepository enrollmentRepository;
    @Mock SectionRepository sectionRepository;
    @Mock SchoolClassRepository schoolClassRepository;

    @InjectMocks FamilyService familyService;

    private Student rohan;
    private Student neha;
    private Student orphan;
    private UUID parentId;

    @BeforeEach
    void setUp() {
        parentId = UUID.randomUUID();
        rohan = newStudent("Rohan");
        neha = newStudent("Neha");
        orphan = newStudent("Unlinked");
    }

    @Test
    void groupStudentsByPrimaryParent_bucketsSiblings() {
        // Rohan and Neha both have the same primary parent
        StudentParentLink rohanLink = newPrimaryLink(rohan.getId(), parentId);
        StudentParentLink nehaLink = newPrimaryLink(neha.getId(), parentId);

        when(linkRepository.findByStudentIdAndPrimaryTrue(rohan.getId()))
            .thenReturn(java.util.Optional.of(rohanLink));
        when(linkRepository.findByStudentIdAndPrimaryTrue(neha.getId()))
            .thenReturn(java.util.Optional.of(nehaLink));
        when(linkRepository.findByStudentIdAndPrimaryTrue(orphan.getId()))
            .thenReturn(java.util.Optional.empty());

        Map<UUID, List<Student>> grouped = familyService.groupStudentsByPrimaryParent(
            List.of(rohan, neha, orphan));

        assertThat(grouped).hasSize(1);
        assertThat(grouped.get(parentId)).containsExactlyInAnyOrder(rohan, neha);
    }

    @Test
    void groupStudentsByPrimaryParent_orphanedStudentsOmitted() {
        when(linkRepository.findByStudentIdAndPrimaryTrue(any()))
            .thenReturn(java.util.Optional.empty());

        Map<UUID, List<Student>> grouped = familyService.groupStudentsByPrimaryParent(
            List.of(orphan));

        assertThat(grouped).isEmpty();
    }

    private Student newStudent(String firstName) {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setFirstName(firstName);
        return s;
    }

    private StudentParentLink newPrimaryLink(UUID studentId, UUID parentId) {
        StudentParentLink l = new StudentParentLink();
        l.setId(UUID.randomUUID());
        l.setStudentId(studentId);
        l.setParentId(parentId);
        l.setPrimary(true);
        return l;
    }
}
