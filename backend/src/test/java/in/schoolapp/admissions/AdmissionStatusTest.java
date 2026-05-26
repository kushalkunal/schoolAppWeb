package in.schoolapp.admissions;

import in.schoolapp.admissions.entity.AdmissionStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdmissionStatusTest {

    @Test
    void happyPathFlow() {
        // ENQUIRY → APP → TEST_SCHED → TEST_COMP → OFFERED → ACCEPTED → ENROLLED
        assertThat(AdmissionStatus.ENQUIRY.canTransitionTo(AdmissionStatus.APPLICATION_SUBMITTED)).isTrue();
        assertThat(AdmissionStatus.APPLICATION_SUBMITTED.canTransitionTo(AdmissionStatus.TEST_SCHEDULED)).isTrue();
        assertThat(AdmissionStatus.TEST_SCHEDULED.canTransitionTo(AdmissionStatus.TEST_COMPLETED)).isTrue();
        assertThat(AdmissionStatus.TEST_COMPLETED.canTransitionTo(AdmissionStatus.OFFERED)).isTrue();
        assertThat(AdmissionStatus.OFFERED.canTransitionTo(AdmissionStatus.ACCEPTED)).isTrue();
        assertThat(AdmissionStatus.ACCEPTED.canTransitionTo(AdmissionStatus.ENROLLED)).isTrue();
    }

    @Test
    void schoolsThatSkipEntranceTestCanGoStraightToOffer() {
        assertThat(AdmissionStatus.APPLICATION_SUBMITTED.canTransitionTo(AdmissionStatus.OFFERED)).isTrue();
    }

    @Test
    void terminalStatesAreSticky() {
        for (var s : new AdmissionStatus[]{
                AdmissionStatus.ENROLLED,
                AdmissionStatus.DECLINED,
                AdmissionStatus.WITHDRAWN,
                AdmissionStatus.REJECTED}) {
            for (var next : AdmissionStatus.values()) {
                assertThat(s.canTransitionTo(next))
                    .as("Terminal %s should not transition to %s", s, next)
                    .isFalse();
            }
        }
    }

    @Test
    void rejectionAvailableAtEveryNonTerminalStage() {
        assertThat(AdmissionStatus.ENQUIRY.canTransitionTo(AdmissionStatus.REJECTED)).isTrue();
        assertThat(AdmissionStatus.APPLICATION_SUBMITTED.canTransitionTo(AdmissionStatus.REJECTED)).isTrue();
        assertThat(AdmissionStatus.TEST_SCHEDULED.canTransitionTo(AdmissionStatus.REJECTED)).isTrue();
        assertThat(AdmissionStatus.TEST_COMPLETED.canTransitionTo(AdmissionStatus.REJECTED)).isTrue();
        assertThat(AdmissionStatus.OFFERED.canTransitionTo(AdmissionStatus.REJECTED)).isTrue();
    }

    @Test
    void cannotJumpFromEnquiryStraightToOffered() {
        assertThat(AdmissionStatus.ENQUIRY.canTransitionTo(AdmissionStatus.OFFERED)).isFalse();
        assertThat(AdmissionStatus.ENQUIRY.canTransitionTo(AdmissionStatus.ENROLLED)).isFalse();
    }

    @Test
    void declinedAndAcceptedAreMutuallyExclusiveFromOffered() {
        assertThat(AdmissionStatus.OFFERED.canTransitionTo(AdmissionStatus.ACCEPTED)).isTrue();
        assertThat(AdmissionStatus.OFFERED.canTransitionTo(AdmissionStatus.DECLINED)).isTrue();
    }
}
