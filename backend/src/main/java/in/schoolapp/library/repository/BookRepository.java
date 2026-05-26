package in.schoolapp.library.repository;

import in.schoolapp.library.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BookRepository extends JpaRepository<Book, UUID> {
    List<Book> findBySchoolIdAndActiveTrueOrderByTitleAsc(UUID schoolId);
}
