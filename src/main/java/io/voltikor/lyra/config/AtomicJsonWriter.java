package io.voltikor.lyra.config;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class AtomicJsonWriter {
   @FunctionalInterface
   interface Content { void write(Writer writer) throws IOException; }

   static void write(Path destination, Content content) throws IOException {
      Path target = destination.toAbsolutePath();
      Path parent = target.getParent() == null ? Path.of(".").toAbsolutePath() : target.getParent();
      Files.createDirectories(parent);
      Path temporary = Files.createTempFile(parent, target.getFileName() + ".", ".tmp");
      try {
         try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            content.write(writer);
         }
         try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
         } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
         }
      } finally {
         Files.deleteIfExists(temporary);
      }
   }
}
