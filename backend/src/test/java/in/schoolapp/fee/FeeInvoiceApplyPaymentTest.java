package in.schoolapp.fee;

import in.schoolapp.fee.entity.FeeInvoice;
import in.schoolapp.fee.entity.InvoiceStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeeInvoiceApplyPaymentTest {

    @Test
    void applyPayment_partialLeavesStatusPartial() {
        FeeInvoice inv = newInvoice(450000L);  // ₹4,500
        inv.applyPayment(200000L);             // ₹2,000

        assertThat(inv.getAmountPaidPaise()).isEqualTo(200000L);
        assertThat(inv.balancePaise()).isEqualTo(250000L);
        assertThat(inv.getStatus()).isEqualTo(InvoiceStatus.PARTIAL);
    }

    @Test
    void applyPayment_fullBalanceMarksPaid() {
        FeeInvoice inv = newInvoice(450000L);
        inv.applyPayment(450000L);

        assertThat(inv.balancePaise()).isZero();
        assertThat(inv.getStatus()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void applyPayment_accumulatesAcrossCalls() {
        FeeInvoice inv = newInvoice(450000L);
        inv.applyPayment(100000L);   // 1k paid, 3.5k balance, PARTIAL
        inv.applyPayment(350000L);   // full, PAID

        assertThat(inv.getAmountPaidPaise()).isEqualTo(450000L);
        assertThat(inv.getStatus()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void applyPayment_zeroOrNegativeIsNoOp() {
        FeeInvoice inv = newInvoice(450000L);
        inv.applyPayment(0L);
        inv.applyPayment(-100L);

        assertThat(inv.getAmountPaidPaise()).isZero();
        assertThat(inv.getStatus()).isEqualTo(InvoiceStatus.PENDING);
    }

    private FeeInvoice newInvoice(long amountDuePaise) {
        FeeInvoice inv = new FeeInvoice();
        inv.setAmountDuePaise(amountDuePaise);
        inv.setStatus(InvoiceStatus.PENDING);
        return inv;
    }
}
