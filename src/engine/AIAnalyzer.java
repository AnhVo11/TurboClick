package engine;

import ui.SettingsPanel;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class AIAnalyzer {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL   = "claude-sonnet-4-20250514";
    private static final int MAX_SHOTS  = 8;

    public static String analyzeRecording(List<RecordingEngine.RecordedAction> actions) throws Exception {
        if (actions.isEmpty()) throw new Exception("No actions recorded.");

        StringBuilder userMsg = new StringBuilder();
        userMsg.append("I recorded the following actions. Generate a TurboClick automation task.\n\n");
        for (int i = 0; i < actions.size(); i++)
            userMsg.append((i + 1)).append(". ").append(actions.get(i).describe()).append("\n");
        userMsg.append("\nReturn ONLY valid JSON, no markdown:\n").append(getSchemaHint());

        StringBuilder contentJson = new StringBuilder("[");
        contentJson.append("{\"type\":\"text\",\"text\":").append(jsonStr(userMsg.toString())).append("}");

        int step = Math.max(1, actions.size() / MAX_SHOTS);
        for (int i = 0; i < actions.size(); i += step) {
            RecordingEngine.RecordedAction action = actions.get(i);
            String b64 = action.screenshotBase64;
            if (b64 == null) continue;
            contentJson.append(",{\"type\":\"image\",\"source\":{")
                .append("\"type\":\"base64\",")
                .append("\"media_type\":\"image/png\",")
                .append("\"data\":\"").append(b64).append("\"}}");
            contentJson.append(",{\"type\":\"text\",\"text\":")
                .append(jsonStr("Screenshot after action " + (i + 1) + ": " + action.describe()))
                .append("}");
        }
        contentJson.append("]");
        return callAPI(getSystemPrompt(), contentJson.toString());
    }

    public static String buildChatSystemPrompt(String currentTaskJson) {
        return "You are TurboClick AI helping users build screen automation tasks.\n"
            + "Current task JSON:\n" + currentTaskJson + "\n\n"
            + "When asked to change the task, respond with ONLY updated JSON.\n"
            + "When answering questions, respond with plain text.\n"
            + "Never use markdown code blocks.";
    }

    public static String callAPI(String system, String contentJson) throws Exception {
        String apiKey = SettingsPanel.getApiKey();
        if (apiKey.isEmpty())
            throw new Exception("No API key set. Go to Settings to add your Anthropic API key.");

        String body = "{\"model\":\"" + MODEL + "\",\"max_tokens\":4096,"
            + "\"system\":" + jsonStr(system) + ","
            + "\"messages\":[{\"role\":\"user\",\"content\":" + contentJson + "}]}";

        HttpURLConnection conn = (HttpURLConnection) new URL(API_URL).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("x-api-key", apiKey);
        conn.setRequestProperty("anthropic-version", "2023-06-01");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String response = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        if (code >= 400) throw new Exception("API error " + code + ": " + extractError(response));
        return extractText(response);
    }

    private static String extractText(String json) {
        String marker = "\"text\":\"";
        int i = json.indexOf(marker);
        if (i < 0) return "";
        int start = i + marker.length();
        StringBuilder sb = new StringBuilder();
        int j = start;
        while (j < json.length()) {
            char c = json.charAt(j);
            if (c == '"' && j > 0 && json.charAt(j - 1) != '\\') break;
            if (c == '\\' && j + 1 < json.length()) {
                char next = json.charAt(j + 1);
                switch (next) {
                    case 'n': sb.append('\n'); j += 2; continue;
                    case 't': sb.append('\t'); j += 2; continue;
                    case 'r':                  j += 2; continue;
                    case '"': sb.append('"');  j += 2; continue;
                    case '\\': sb.append('\\'); j += 2; continue;
                    default: sb.append(next);  j += 2; continue;
                }
            }
            sb.append(c);
            j++;
        }
        return sb.toString();
    }

    private static String extractError(String json) {
        String marker = "\"message\":\"";
        int i = json.indexOf(marker);
        if (i < 0) return json.length() > 200 ? json.substring(0, 200) : json;
        int start = i + marker.length();
        int end = json.indexOf("\"", start);
        return end > start ? json.substring(start, end) : json;
    }

    private static String jsonStr(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    private static String getSystemPrompt() {
        return "You are TurboClick AI. Analyze recorded screen actions and generate automation tasks as JSON.\n"
            + "Return ONLY valid JSON — no markdown, no explanation, no code blocks.\n"
            + "Use SIMPLE_CLICK for click sequences. Always end with a STOP node.";
    }

    private static String getSchemaHint() {
        return "{\"taskName\":\"Recorded Task\","
            + "\"nodes\":["
            + "{\"type\":\"SIMPLE_CLICK\",\"id\":\"n1\",\"label\":\"Clicks\","
            + "\"x\":100,\"y\":100,\"width\":180,\"height\":75,"
            + "\"branchEnabled\":true,\"entryDelayMs\":0,\"nodeLoopCount\":1,"
            + "\"points\":[[452,301,1,100,100,0]],"
            + "\"intervalMs\":100,\"waitToFinish\":true},"
            + "{\"type\":\"STOP\",\"id\":\"n2\",\"label\":\"Stop\","
            + "\"x\":100,\"y\":220,\"width\":120,\"height\":50,"
            + "\"branchEnabled\":true,\"entryDelayMs\":0,\"nodeLoopCount\":1}"
            + "],"
            + "\"arrows\":["
            + "{\"fromNodeId\":\"n1\",\"fromPort\":\"Done\",\"toNodeId\":\"n2\","
            + "\"label\":\"Done\",\"bendOffset\":40}"
            + "]}";
    }
}