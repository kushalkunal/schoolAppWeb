package in.schoolapp.pdf;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test for {@link OpenHtmlToPdfRenderer}. We don't validate the PDF contents —
 * pdfbox itself is well-tested. We just confirm:
 *
 * <ul>
 *   <li>Trivial HTML renders to non-empty bytes.</li>
 *   <li>The PDF magic header is present.</li>
 *   <li>Realistic A4 document with CSS doesn't throw.</li>
 * </ul>
 */
class OpenHtmlToPdfRendererTest {

    private final OpenHtmlToPdfRenderer renderer = new OpenHtmlToPdfRenderer();

    @Test
    void rendersMinimalHtml() {
        byte[] pdf = renderer.render("<html><body><h1>Hello</h1></body></html>", "classpath:/");

        assertThat(pdf).isNotEmpty();
        // PDF files begin with the magic %PDF- header.
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void rendersStyledA4Document() {
        String html = """
            <!DOCTYPE html>
            <html>
            <head>
              <style>
                @page { size: A4; margin: 20mm; }
                body { font-family: Helvetica, sans-serif; font-size: 11pt; }
                h1 { color: #1e40af; }
                table { width: 100%; border-collapse: collapse; }
                td { padding: 6px; border-bottom: 1px solid #ddd; }
              </style>
            </head>
            <body>
              <h1>Sample receipt</h1>
              <table>
                <tr><td>Tuition fee</td><td>₹ 5,000</td></tr>
                <tr><td>Bus fee</td><td>₹ 1,500</td></tr>
              </table>
            </body>
            </html>
            """;

        byte[] pdf = renderer.render(html, "classpath:/");

        assertThat(pdf.length).isGreaterThan(500);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }
}
