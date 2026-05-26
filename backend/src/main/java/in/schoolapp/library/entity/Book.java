package in.schoolapp.library.entity;

import in.schoolapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "library_books",
    uniqueConstraints = @UniqueConstraint(name = "uq_library_book_school_isbn",
        columnNames = {"school_id", "isbn"}))
@Getter
@Setter
public class Book extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 255)
    private String author;

    @Column(length = 20)
    private String isbn;

    @Column(length = 255)
    private String publisher;

    @Column(length = 80)
    private String category;

    @Column(name = "total_copies", nullable = false)
    private int totalCopies = 1;

    @Column(name = "available_copies", nullable = false)
    private int availableCopies = 1;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
