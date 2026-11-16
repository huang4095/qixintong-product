import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ApprovalServer {
    private static final String COLLECTION_PATH = "/api/approvals";
    private static final Pattern DECISION_PATH = Pattern.compile(COLLECTION_PATH + "/([1-9][0-9]*)/decision");
    private static final int MAX_FORM_BYTES = 4096;

    public static void main(String[] args) throws IOException {
        var store = new ApprovalStore();
        store.create("采购会议室白板", "演示员工");

        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8080), 0);
        server.createContext("/", exchange -> handle(exchange, store));
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.println("OA approval API listening on http://127.0.0.1:8080" + COLLECTION_PATH);
    }

    private static void handle(HttpExchange exchange, ApprovalStore store) throws IOException {
        try (exchange) {
            try {
                route(exchange, store);
            } catch (ApiException error) {
                writeJson(exchange, error.code, "{\"error\":" + quote(error.getMessage()) + "}");
            }
        }
    }

    private static void route(HttpExchange exchange, ApprovalStore store) throws IOException {
        var path = exchange.getRequestURI().getPath();
        var method = exchange.getRequestMethod();

        if (COLLECTION_PATH.equals(path)) {
            switch (method) {
                case "GET" -> writeJson(exchange, 200, store.list().stream()
                        .map(Approval::toJson)
                        .collect(Collectors.joining(",", "[", "]")));
                case "POST" -> {
                    var form = readForm(exchange);
                    var approval = store.create(required(form, "title", 120), required(form, "applicant", 40));
                    writeJson(exchange, 201, approval.toJson());
                }
                default -> throw invalidMethod(exchange, "GET, POST");
            }
            return;
        }

        var decisionPath = DECISION_PATH.matcher(path);
        if (!decisionPath.matches()) {
            throw new ApiException(404, "接口不存在");
        }
        if (!method.equals("POST")) {
            throw invalidMethod(exchange, "POST");
        }

        long id;
        try {
            id = Long.parseLong(decisionPath.group(1));
        } catch (NumberFormatException error) {
            throw new ApiException(404, "申请不存在");
        }
        var decision = switch (readForm(exchange).getOrDefault("decision", "")) {
            case "APPROVED" -> Status.APPROVED;
            case "REJECTED" -> Status.REJECTED;
            default -> throw new ApiException(400, "decision 必须是 APPROVED 或 REJECTED");
        };
        writeJson(exchange, 200, store.decide(id, decision).toJson());
    }

    private static ApiException invalidMethod(HttpExchange exchange, String allowed) {
        exchange.getResponseHeaders().set("Allow", allowed);
        return new ApiException(405, "接口不支持此请求方法");
    }

    private static Map<String, String> readForm(HttpExchange exchange) throws IOException {
        var contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null ||
                !contentType.split(";", 2)[0].strip().equalsIgnoreCase("application/x-www-form-urlencoded")) {
            throw new ApiException(415, "请求格式须为 application/x-www-form-urlencoded");
        }

        var bytes = exchange.getRequestBody().readNBytes(MAX_FORM_BYTES + 1);
        if (bytes.length > MAX_FORM_BYTES) {
            throw new ApiException(413, "表单超过 4096 字节");
        }

        var form = new HashMap<String, String>();
        var body = new String(bytes, StandardCharsets.UTF_8);
        if (body.isEmpty()) {
            return form;
        }
        for (var entry : body.split("&", -1)) {
            var field = entry.split("=", 2);
            if (field.length != 2) {
                throw new ApiException(400, "表单字段格式错误");
            }
            try {
                var key = URLDecoder.decode(field[0], StandardCharsets.UTF_8);
                var value = URLDecoder.decode(field[1], StandardCharsets.UTF_8);
                if (form.putIfAbsent(key, value) != null) {
                    throw new ApiException(400, "表单字段重复");
                }
            } catch (IllegalArgumentException error) {
                throw new ApiException(400, "表单编码错误");
            }
        }
        return form;
    }

    private static String required(Map<String, String> form, String field, int limit) {
        var value = form.getOrDefault(field, "").strip();
        if (value.isEmpty() || value.length() > limit) {
            throw new ApiException(400, field + " 必填且最长 " + limit + " 个字符");
        }
        return value;
    }

    private static void writeJson(HttpExchange exchange, int code, String json) throws IOException {
        var bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static String quote(String value) {
        var result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '"' || character == '\\') {
                result.append('\\').append(character);
            } else if (character < 0x20) {
                result.append(String.format("\\u%04x", (int) character));
            } else {
                result.append(character);
            }
        }
        return result.append('"').toString();
    }

    private enum Status {
        PENDING, APPROVED, REJECTED
    }

    private record Approval(long id, String title, String applicant, Status status) {
        String toJson() {
            return "{\"id\":" + id + ",\"title\":" + quote(title) +
                    ",\"applicant\":" + quote(applicant) + ",\"status\":" + quote(status.name()) + "}";
        }
    }

    private static final class ApprovalStore {
        private final Map<Long, Approval> approvals = new LinkedHashMap<>();
        private long nextId = 1;

        synchronized List<Approval> list() {
            return List.copyOf(approvals.values());
        }

        synchronized Approval create(String title, String applicant) {
            var approval = new Approval(nextId++, title, applicant, Status.PENDING);
            approvals.put(approval.id(), approval);
            return approval;
        }

        synchronized Approval decide(long id, Status decision) {
            var current = approvals.get(id);
            if (current == null) {
                throw new ApiException(404, "申请不存在");
            }
            if (current.status() != Status.PENDING) {
                throw new ApiException(409, "申请已处理，请刷新列表");
            }
            var updated = new Approval(id, current.title(), current.applicant(), decision);
            approvals.put(id, updated);
            return updated;
        }
    }

    private static final class ApiException extends RuntimeException {
        private final int code;

        ApiException(int code, String message) {
            super(message);
            this.code = code;
        }
    }
}
