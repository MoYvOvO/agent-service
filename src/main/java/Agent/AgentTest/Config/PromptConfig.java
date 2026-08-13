package Agent.AgentTest.Config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "agent.prompts")
public class PromptConfig {
    private String structured;
    private String rag;
    private String orchestrate;

    // getter 和 setter（必须）
    public String getStructured() { return structured; }
    public void setStructured(String structured) { this.structured = structured; }

    public String getRag() { return rag; }
    public void setRag(String rag) { this.rag = rag; }

    public String getOrchestrate() { return orchestrate; }
    public void setOrchestrate(String orchestrate) { this.orchestrate = orchestrate; }
}