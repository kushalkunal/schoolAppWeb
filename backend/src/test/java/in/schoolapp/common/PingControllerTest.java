package in.schoolapp.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PingController.class)
@AutoConfigureMockMvc(addFilters = false)
class PingControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void ping_returnsStandardEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/ping"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.service").value("school-management"))
            .andExpect(jsonPath("$.data.status").value("ok"))
            .andExpect(jsonPath("$.data.timestamp").exists());
    }
}
