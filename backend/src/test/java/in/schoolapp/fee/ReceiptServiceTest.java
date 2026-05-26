package in.schoolapp.fee;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptServiceTest {

    @Test
    void formatAmount_twoDecimalPlaces() {
        assertThat(ReceiptService.formatAmount(0L)).isEqualTo("0.00");
        assertThat(ReceiptService.formatAmount(100L)).isEqualTo("1.00");
        assertThat(ReceiptService.formatAmount(150L)).isEqualTo("1.50");
        assertThat(ReceiptService.formatAmount(99L)).isEqualTo("0.99");
        assertThat(ReceiptService.formatAmount(450000L)).isEqualTo("4,500.00");
    }

    @Test
    void formatAmount_largeValuesUseGroupingSeparators() {
        assertThat(ReceiptService.formatAmount(10_000_000L)).isEqualTo("100,000.00");
        assertThat(ReceiptService.formatAmount(123_456_789L)).isEqualTo("1,234,567.89");
    }

    @Test
    void formatReceiptNumber_zeroPaddedSixDigits() {
        assertThat(ReceiptService.formatReceiptNumber(1, 2026)).isEqualTo("REC-2026-000001");
        assertThat(ReceiptService.formatReceiptNumber(1847, 2026)).isEqualTo("REC-2026-001847");
        assertThat(ReceiptService.formatReceiptNumber(999_999, 2026)).isEqualTo("REC-2026-999999");
    }
}
