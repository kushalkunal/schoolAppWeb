package in.schoolapp.library.dto;

import in.schoolapp.library.entity.Book;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record BookDto(
    UUID id,
    @NotBlank String title,
    String author,
    String isbn,
    String publisher,
    String category,
    @Min(1) int totalCopies,
    int availableCopies
) {
    public static BookDto from(Book b) {
        return new BookDto(b.getId(), b.getTitle(), b.getAuthor(), b.getIsbn(), b.getPublisher(),
            b.getCategory(), b.getTotalCopies(), b.getAvailableCopies());
    }
}
