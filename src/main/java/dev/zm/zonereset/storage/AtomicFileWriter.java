package dev.zm.zonereset.storage;

import dev.zm.zonereset.core.logging.ZoneLogger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.zip.CRC32;

public final class AtomicFileWriter {

    private final ZoneLogger logger;

    public AtomicFileWriter(ZoneLogger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void write(File finalFile, File tempFile, byte[] data) throws IOException {
        Objects.requireNonNull(finalFile, "finalFile");
        Objects.requireNonNull(tempFile, "tempFile");
        Objects.requireNonNull(data, "data");

        File parent = finalFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create directory: " + parent.getAbsolutePath());
        }

        if (tempFile.exists()) {
            logger.debug("Deleting orphaned temporary file: {}", tempFile.getName());
            if (!tempFile.delete()) {
                throw new IOException("Could not delete previous temporary file: " + tempFile.getAbsolutePath());
            }
        }

        long writtenChecksum;
        try (FileOutputStream fos = new FileOutputStream(tempFile);
                BufferedOutputStream bos = new BufferedOutputStream(fos, 65_536)) {
            bos.write(data);
            bos.flush();
            fos.getFD().sync();
        }

        writtenChecksum = calculateCRC32(tempFile);

        long verifyChecksum = calculateCRC32(tempFile);
        if (writtenChecksum != verifyChecksum) {
            tempFile.delete();
            throw new IOException(
                    "CRC32 verification failed for " + tempFile.getName() +
                            " (written=" + writtenChecksum + ", read=" + verifyChecksum + ")");
        }

        try {
            Files.move(tempFile.toPath(), finalFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.debug("ATOMIC_MOVE not available, using REPLACE_EXISTING: {}", e.getMessage());
            Files.move(tempFile.toPath(), finalFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }

        logger.debug("File written atomically: {} ({} bytes, CRC32={})",
                finalFile.getName(), data.length, writtenChecksum);
    }

    public void writeStreaming(File finalFile, File tempFile,
            StreamWriter writer) throws IOException {
        Objects.requireNonNull(finalFile, "finalFile");
        Objects.requireNonNull(tempFile, "tempFile");
        Objects.requireNonNull(writer, "writer");

        File parent = finalFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create directory: " + parent.getAbsolutePath());
        }

        if (tempFile.exists() && !tempFile.delete()) {
            throw new IOException("Could not delete previous temporary file: " + tempFile.getAbsolutePath());
        }

        try (FileOutputStream fos = new FileOutputStream(tempFile);
                BufferedOutputStream bos = new BufferedOutputStream(fos, 65_536)) {
            writer.write(bos);
            bos.flush();
            fos.getFD().sync();
        } catch (IOException e) {
            tempFile.delete();
            throw new IOException("Error writing temporary file " + tempFile.getName(), e);
        }

        try {
            Files.move(tempFile.toPath(), finalFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tempFile.toPath(), finalFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }

        logger.debug("Streaming file written atomically: {} ({} bytes)",
                finalFile.getName(), finalFile.length());
    }

    public void cleanOrphanedTempFiles(File directory) {
        if (directory == null || !directory.isDirectory())
            return;

        File[] files = directory.listFiles();
        if (files == null)
            return;

        for (File f : files) {
            if (f.isDirectory()) {
                cleanOrphanedTempFiles(f);
            } else if (f.getName().endsWith(".tmp")) {
                logger.warn("Deleting orphaned temporary file from a previous crash: {}",
                        f.getAbsolutePath());
                if (!f.delete()) {
                    logger.error("Could not delete temporary file: {}",
                            f.getAbsolutePath());
                }
            }
        }
    }

    private static long calculateCRC32(File file) throws IOException {
        CRC32 crc = new CRC32();
        try (FileInputStream fis = new FileInputStream(file);
                BufferedInputStream bis = new BufferedInputStream(fis, 65_536)) {
            byte[] buf = new byte[65_536];
            int n;
            while ((n = bis.read(buf)) != -1) {
                crc.update(buf, 0, n);
            }
        }
        return crc.getValue();
    }

    @FunctionalInterface
    public interface StreamWriter {
        void write(OutputStream out) throws IOException;
    }
}
