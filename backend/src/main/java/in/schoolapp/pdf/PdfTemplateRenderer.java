package in.schoolapp.pdf;

/**
 * HTML → PDF renderer. The default impl uses openhtmltopdf, but the indirection lets us
 * swap to:
 *
 * <ul>
 *   <li>headless Chromium (richer CSS, web fonts, JS) — via Playwright service</li>
 *   <li>Cloudconvert / DocRaptor (paid, hosted)</li>
 *   <li>AWS Textract pipeline (when the input is already a PDF and we just stamp)</li>
 * </ul>
 *
 * Pick at runtime via {@code app.pdf.provider=<key>}. Per-tenant overrides flow through
 * {@code TenantProviderConfig} on the {@code DOCUMENT} concern, same pattern as WhatsApp /
 * Email / Storage. <strong>Never hard-code a provider</strong>: every concrete renderer is
 * wrapped in {@code @ConditionalOnProperty}.
 */
public interface PdfTemplateRenderer {

    /**
     * Render the given HTML body to a PDF byte stream. {@code baseUri} is used as the
     * resource root for relative URLs (CSS, images) inside the HTML — typically a
     * {@code classpath:/} URL when rendering from a packaged template.
     *
     * @return raw PDF bytes, ready to upload to storage or stream as a response
     */
    byte[] render(String html, String baseUri);
}
