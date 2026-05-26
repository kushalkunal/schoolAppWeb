package in.schoolapp.pdf;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Default {@link PdfTemplateRenderer}. Uses openhtmltopdf-pdfbox — pure Java, no native
 * dependency, works in every JVM. Trade-off: CSS support is roughly CSS 2.1 + a subset of
 * CSS 3. Anything fancier (flexbox, modern fonts) needs the Chromium-based provider.
 *
 * <p>Activated by default (matchIfMissing) or explicitly via {@code app.pdf.provider=OPENHTML}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.pdf.provider", havingValue = "OPENHTML", matchIfMissing = true)
public class OpenHtmlToPdfRenderer implements PdfTemplateRenderer {

    @Override
    public byte[] render(String html, String baseUri) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, baseUri);
            builder.toStream(out);
            builder.run();
            byte[] bytes = out.toByteArray();
            log.debug("PDF rendered: {} bytes from HTML of {} chars", bytes.length, html.length());
            return bytes;
        } catch (IOException e) {
            throw new AppException(ErrorCode.INTERNAL_ERROR,
                "PDF rendering failed: " + e.getMessage(), e);
        }
    }
}
