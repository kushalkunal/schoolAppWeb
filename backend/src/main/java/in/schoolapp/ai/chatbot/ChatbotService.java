package in.schoolapp.ai.chatbot;

import in.schoolapp.ai.llm.LlmClient;
import in.schoolapp.school.SchoolService;
import in.schoolapp.school.dto.SchoolResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * General-purpose Q&amp;A surface for parents and staff. Each call resolves the active
 * {@link LlmClient} via Spring (only one is active at a time, picked by
 * {@code app.llm.provider}) and prepends a school-aware system prompt so the answer
 * stays on-topic.
 *
 * <p>Future enhancements (not in this slice):
 * <ul>
 *   <li>Retrieval-augmented generation against per-school documents (circulars, policies)</li>
 *   <li>Multi-turn conversation memory in Postgres</li>
 *   <li>Cost tracking per tenant via UsageCounter</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotService {

    private final LlmClient llmClient;
    private final SchoolService schoolService;

    public String answer(UUID schoolId, String userQuery) {
        SchoolResponse school = schoolService.getSchool(schoolId);
        String system = buildSystemPrompt(school);
        long t0 = System.currentTimeMillis();
        String reply = llmClient.chat(schoolId, system, userQuery);
        log.info("Chatbot reply tenant={} provider={} latencyMs={} promptLen={} replyLen={}",
            schoolId, llmClient.providerName(),
            System.currentTimeMillis() - t0,
            userQuery != null ? userQuery.length() : 0,
            reply != null ? reply.length() : 0);
        return reply;
    }

    private static String buildSystemPrompt(SchoolResponse school) {
        return """
            You are the friendly AI assistant for %s, a school based in %s, %s.
            Answer questions from parents, teachers and school staff helpfully and
            concisely. If you don't know the specific school detail asked about (a
            timetable, fee due date, holiday list), say you don't have that exact
            information and suggest who to contact at the school. Keep replies under
            120 words unless the user asks for more.
            """.formatted(
                school.name(),
                nullSafe(school.city()),
                nullSafe(school.state())
            );
    }

    private static String nullSafe(String s) { return s != null && !s.isBlank() ? s : "—"; }
}
