package dev.chargescope.monitor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

final class InterfaceLog {
    private static final long MAX_BYTES = 64 * 1024;
    private static final int VISIBLE_CHARACTERS = 24_000;

    private InterfaceLog() {}

    static synchronized void append(File directory, String snapshot) throws IOException {
        Path current = new File(directory, "usb-interfaces.log").toPath();
        Path previous = new File(directory, "usb-interfaces.previous.log").toPath();
        byte[] entry = (Instant.now() + "\n" + snapshot + "\n\n").getBytes(StandardCharsets.UTF_8);
        if (Files.exists(current) && Files.size(current) + entry.length > MAX_BYTES) {
            Files.move(current, previous, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.write(current, entry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    static synchronized String recent(File directory) throws IOException {
        Path previous = new File(directory, "usb-interfaces.previous.log").toPath();
        Path current = new File(directory, "usb-interfaces.log").toPath();
        String text = read(previous) + read(current);
        if (text.length() > VISIBLE_CHARACTERS) {
            int boundary = text.indexOf("\n\n", text.length() - VISIBLE_CHARACTERS);
            if (boundary >= 0) text = text.substring(boundary + 2);
        }
        return text;
    }

    private static String read(Path path) throws IOException {
        return Files.exists(path) ? new String(Files.readAllBytes(path), StandardCharsets.UTF_8) : "";
    }
}
