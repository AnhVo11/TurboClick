package engine;

import ui.SettingsPanel;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class AIAnalyzer {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-sonnet-4-20250514";
    private static final int MAX_SHOTS = 8;

    public static String analyzeRecording(List<RecordingEngine.RecordedAction> actions) throws Exception {
        if (actions.isEmpty())
            throw new Exception("No actions recorded.");

        StringBuilder userMsg = new StringBuilder();
        userMsg.append("I recorded the following actions on my screen. Generate a TurboClick automation task.\n\n");
        userMsg.append(
                "IMPORTANT: Put ALL clicks into ONE SIMPLE_CLICK node. If I typed text, add a KEYBOARD node after.\n\n");
        for (int i = 0; i < actions.size(); i++)
            userMsg.append((i + 1)).append(". ").append(actions.get(i).describe()).append("\n");
        userMsg.append("\nReturn ONLY valid JSON, no markdown:\n").append(getSchemaHint());

        StringBuilder contentJson = new StringBuilder("[");
        contentJson.append("{\"type\":\"text\",\"text\":").append(jsonStr(userMsg.toString())).append("}");

        int step = Math.max(1, actions.size() / MAX_SHOTS);
        for (int i = 0; i < actions.size(); i += step) {
            RecordingEngine.RecordedAction action = actions.get(i);
            String b64 = action.screenshotBase64;
            if (b64 == null)
                continue;
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
        if (code >= 400)
            throw new Exception("API error " + code + ": " + extractError(response));
        return extractText(response);
    }

    private static String extractText(String json) {
        String marker = "\"text\":\"";
        int i = json.indexOf(marker);
        if (i < 0)
            return "";
        int start = i + marker.length();
        StringBuilder sb = new StringBuilder();
        int j = start;
        while (j < json.length()) {
            char c = json.charAt(j);
            if (c == '"' && j > 0 && json.charAt(j - 1) != '\\')
                break;
            if (c == '\\' && j + 1 < json.length()) {
                char next = json.charAt(j + 1);
                switch (next) {
                    case 'n':
                        sb.append('\n');
                        j += 2;
                        continue;
                    case 't':
                        sb.append('\t');
                        j += 2;
                        continue;
                    case 'r':
                        j += 2;
                        continue;
                    case '"':
                        sb.append('"');
                        j += 2;
                        continue;
                    case '\\':
                        sb.append('\\');
                        j += 2;
                        continue;
                    default:
                        sb.append(next);
                        j += 2;
                        continue;
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
        if (i < 0)
            return json.length() > 200 ? json.substring(0, 200) : json;
        int start = i + marker.length();
        int end = json.indexOf("\"", start);
        return end > start ? json.substring(start, end) : json;
    }

    private static String jsonStr(String s) {
        if (s == null)
            return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    private static String getSystemPrompt() {
        return "You are TurboClick AI. Analyze recorded screen actions and generate automation tasks as JSON.\n"
                + "Return ONLY valid JSON — no markdown, no explanation, no code blocks.\n\n"
                + "CRITICAL RULES:\n"
                + "1. COMBINE all clicks into ONE single SIMPLE_CLICK node with multiple points array entries. NEVER create one SIMPLE_CLICK per click.\n"
                + "2. If the user typed text after clicking, add a KEYBOARD node after the SIMPLE_CLICK.\n"
                + "3. For typing text use kbMode:0 and put the text in typeText field.\n"
                + "4. For keyboard shortcuts use kbMode:1 and hotkeyCombo field (e.g. 'meta+c', 'ctrl+v').\n"
                + "5. Use WAIT between actions only if there was a noticeable pause (>1 second).\n"
                + "6. Place nodes vertically, y increases by 120 per node starting at y:100.\n"
                + "7. Always connect every node with arrows — no orphan nodes.\n"
                + "8. The points array format is: [x, y, clickCount, subDelayMs, afterDelayMs, buttonType]\n"
                + "   buttonType: 0=left click, 1=right click, 2=middle click\n"
                + "9. Look at the screenshots to understand WHAT the user was doing, not just WHERE they clicked.\n"
                + "10. End the task with a MESSAGE node saying the task is complete.\n\n"
                + "Node types: SIMPLE_CLICK, KEYBOARD, WATCH_ZONE, WAIT, MESSAGE.\n"
                + "Do NOT use any other node types.";
    }

    private static String getSchemaHint() {
        return "{"
                + "\"taskName\":\"Recorded Task\","
                + "\"nodes\":["
                + "{\"type\":\"SIMPLE_CLICK\",\"id\":\"n1\",\"label\":\"Clicks\","
                + "\"x\":100,\"y\":100,\"width\":180,\"height\":75,"
                + "\"branchEnabled\":true,\"entryDelayMs\":0,\"nodeLoopCount\":1,"
                + "\"points\":[[452,301,1,100,100,0],[233,180,1,100,100,0]],"
                + "\"intervalMs\":100,\"waitToFinish\":true},"
                + "{\"type\":\"KEYBOARD\",\"id\":\"n2\",\"label\":\"Type text\","
                + "\"x\":100,\"y\":220,\"width\":180,\"height\":75,"
                + "\"branchEnabled\":true,\"entryDelayMs\":0,\"nodeLoopCount\":1,"
                + "\"kbMode\":0,\"typeText\":\"hello world\",\"charDelayMs\":50,"
                + "\"hotkeyCombo\":\"\",\"singleKey\":\"ENTER\",\"repeatCount\":1,\"repeatDelayMs\":100},"
                + "{\"type\":\"WATCH_ZONE\",\"id\":\"n3\",\"label\":\"Watch Zone\","
                + "\"x\":100,\"y\":340,\"width\":180,\"height\":75,"
                + "\"branchEnabled\":true,\"entryDelayMs\":0,\"nodeLoopCount\":1,"
                + "\"matchThreshold\":85,\"pollIntervalMs\":500,\"preTriggerDelayMs\":0,"
                + "\"timeoutMs\":5000,\"clickAtMatch\":true,\"clickX\":-1,\"clickY\":-1,\"retryCount\":0},"
                + "{\"type\":\"WAIT\",\"id\":\"n4\",\"label\":\"Wait\","
                + "\"x\":100,\"y\":460,\"width\":180,\"height\":75,"
                + "\"branchEnabled\":true,\"entryDelayMs\":0,\"nodeLoopCount\":1,"
                + "\"waitMode\":\"FIXED_DELAY\",\"delayMs\":2000},"
                + "{\"type\":\"MESSAGE\",\"id\":\"n5\",\"label\":\"Done\","
                + "\"x\":100,\"y\":580,\"width\":160,\"height\":60,"
                + "\"branchEnabled\":true,\"entryDelayMs\":0,\"nodeLoopCount\":1,"
                + "\"msgStyle\":0,\"msgTitle\":\"Done\",\"msgText\":\"Task complete\","
                + "\"msgDisplaySeconds\":3,\"msgWaitForDismiss\":false,\"msgPauseTask\":false,"
                + "\"msgPosition\":0,\"msgBoxWidth\":320,\"msgBoxHeight\":120}"
                + "],"
                + "\"arrows\":["
                + "{\"fromNodeId\":\"n1\",\"fromPort\":\"Done\",\"toNodeId\":\"n2\",\"label\":\"Done\",\"bendOffset\":40},"
                + "{\"fromNodeId\":\"n2\",\"fromPort\":\"Done\",\"toNodeId\":\"n3\",\"label\":\"Done\",\"bendOffset\":40},"
                + "{\"fromNodeId\":\"n3\",\"fromPort\":\"Found\",\"toNodeId\":\"n4\",\"label\":\"Found\",\"bendOffset\":40},"
                + "{\"fromNodeId\":\"n4\",\"fromPort\":\"Done\",\"toNodeId\":\"n5\",\"label\":\"Done\",\"bendOffset\":40}"
                + "]}";
    }
}