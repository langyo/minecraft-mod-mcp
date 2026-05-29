package xyz.langyo.minecraft.mcp.common;

import java.util.LinkedHashMap;
import java.util.Map;

public final class JsonHelper {

    private JsonHelper() {}

    public static String error(String message) {
        return kv("error", escape(message));
    }

    public static String error(String message, String key, String value) {
        return "{" + q("error") + ":" + q(escape(message)) + "," + q(key) + ":" + q(escape(value)) + "}";
    }

    public static String ok(String key, String value) {
        return kv(key, value);
    }

    public static String ok(String key, boolean value) {
        return kv(key, value);
    }

    public static String kv(String key, String value) {
        return "{" + q(key) + ":" + q(escape(value)) + "}";
    }

    public static String kv(String key, boolean value) {
        return "{" + q(key) + ":" + value + "}";
    }

    public static String kv(String key, int value) {
        return "{" + q(key) + ":" + value + "}";
    }

    public static String kv(String key, double value) {
        return "{" + q(key) + ":" + value + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static String escape(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String q(String s) {
        return "\"" + s + "\"";
    }

    public static class Builder {
        private final Map<String, String> entries = new LinkedHashMap<>();

        public Builder put(String key, String value) {
            entries.put(key, q(escape(value)));
            return this;
        }

        public Builder putRaw(String key, String rawJsonValue) {
            entries.put(key, rawJsonValue);
            return this;
        }

        public Builder put(String key, boolean value) {
            entries.put(key, String.valueOf(value));
            return this;
        }

        public Builder put(String key, int value) {
            entries.put(key, String.valueOf(value));
            return this;
        }

        public Builder put(String key, long value) {
            entries.put(key, String.valueOf(value));
            return this;
        }

        public Builder put(String key, double value) {
            entries.put(key, String.valueOf(value));
            return this;
        }

        public Builder put(String key, double value, String format) {
            entries.put(key, String.format(format, value));
            return this;
        }

        public Builder error(String message) {
            entries.put("error", q(escape(message)));
            return this;
        }

        public String build() {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, String> e : entries.entrySet()) {
                if (!first) sb.append(",");
                sb.append(q(e.getKey())).append(":").append(e.getValue());
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
    }
}
