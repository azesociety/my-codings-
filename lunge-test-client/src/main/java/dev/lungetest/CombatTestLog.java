package dev.lungetest;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;

/** Bounded, buffered JSONL traces; server messages are observations, never invented verdicts. */
public final class CombatTestLog {
    private static final Gson JSON = new Gson();
    private static final ArrayList<String> buffer = new ArrayList<>();
    private static Path file;
    private static long started, tick;
    private static int entries;
    private static String status = "No session recorded";
    private CombatTestLog() {}
    public static String status() { return status; }
    public static Path file() { return file; }
    public static void begin(String source) {
        flush();
        started = System.nanoTime();
        entries = 0;
        file = FabricLoader.getInstance().getGameDir().resolve("logs/azeium-tests")
                .resolve("session-" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID() + ".jsonl");
        status = "Recording: " + file.getFileName();
        event(source, "session_started", "Client observations; server detection is unverified");
    }
    public static void event(String source, String action, String detail) {
        if (file == null || entries >= 12000) return;
        var row = new JsonObject();
        row.addProperty("time", Instant.now().toString());
        row.addProperty("elapsedNs", Math.max(0, System.nanoTime() - started));
        row.addProperty("clientTick", tick);
        row.addProperty("source", source);
        row.addProperty("action", action);
        row.addProperty("detail", detail == null ? "" : detail.substring(0, Math.min(2048, detail.length())));
        row.addProperty("serverVerdict", "unverified");
        buffer.add(JSON.toJson(row));
        entries++;
        if (buffer.size() >= 128) flush();
        if (entries == 12000) status = "Log limit reached; start a new session";
    }
    public static void tick() { if (++tick % 20 == 0) flush(); }
    public static void observation(String text) { event("server", "visible_message", text); }
    public static void mark(String result) {
        if (file == null) begin("Manual annotation");
        event("user", "detection_annotation", result); flush();
    }
    public static void flush() {
        if (file == null || buffer.isEmpty()) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, String.join("\n", buffer) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            buffer.clear();
        } catch (Exception exception) {
            buffer.clear();
            status = "Log write failed; see latest.log";
            LungeTestClient.LOG.warn("Could not write combat test trace", exception);
        }
    }
}
