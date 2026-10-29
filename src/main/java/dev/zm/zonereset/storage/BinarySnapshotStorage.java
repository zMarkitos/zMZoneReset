package dev.zm.zonereset.storage;

import dev.zm.zonereset.core.logging.ZoneLogger;
import dev.zm.zonereset.snapshot.BlockRecord;
import dev.zm.zonereset.snapshot.ChunkSnapshotData;
import dev.zm.zonereset.snapshot.SnapshotStreamWriter;
import dev.zm.zonereset.snapshot.ZoneSnapshot;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;

public final class BinarySnapshotStorage implements SnapshotStorage {

    private static final int MAGIC = 0x5A4D5A52;
    private static final short FORMAT_VERSION = 2;
    private static final short FLAG_COMPRESSED = 0x01;
    private static final int OFFSET_TOTAL_BLOCKS = 12;
    private static final int OFFSET_CRC32 = 28;
    static final int HEADER_SIZE = 40;

    private final StorageLayout layout;
    private final CompressionCodec codec;
    private final AtomicFileWriter atomicWriter;
    private final ZoneLogger logger;

    public BinarySnapshotStorage(StorageLayout layout,
            CompressionCodec codec,
            AtomicFileWriter atomicWriter,
            ZoneLogger logger) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.atomicWriter = Objects.requireNonNull(atomicWriter, "atomicWriter");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void saveSnapshot(String zoneId, ZoneSnapshot snapshot) throws StorageException {
        Objects.requireNonNull(zoneId, "zoneId");
        Objects.requireNonNull(snapshot, "snapshot");

        layout.ensureZoneSnapshotDir(zoneId);
        File finalFile = layout.getSnapshotFile(zoneId);
        File tempFile = layout.getSnapshotTempFile(zoneId);

        try {
            byte[] payload = serializePayload(snapshot);
            long crc32 = computeCRC32(payload);
            byte[] full = assembleFileBytes(snapshot.getChunkCount(),
                    snapshot.getTotalBlocks(),
                    snapshot.getCapturedAt(),
                    crc32, payload);

            atomicWriter.write(finalFile, tempFile, full);

            logger.info("Snapshot for '{}' saved: {} chunks, {} blocks, {}.",
                    zoneId, snapshot.getChunkCount(),
                    snapshot.getTotalBlocks(), humanSize(full.length));

        } catch (IOException e) {
            throw new StorageException("Error saving snapshot for zone '" + zoneId + "'.", e);
        }
    }

    @Override
    public SnapshotStreamWriter beginStreamingCapture(String zoneId,
            UUID worldUID,
            int chunkCount) throws IOException {
        Objects.requireNonNull(zoneId, "zoneId");
        Objects.requireNonNull(worldUID, "worldUID");

        layout.ensureZoneSnapshotDir(zoneId);
        File tempFile = layout.getSnapshotTempFile(zoneId);
        File finalFile = layout.getSnapshotFile(zoneId);

        if (tempFile.exists() && !tempFile.delete()) {
            throw new IOException("Could not delete orphaned temp file: " + tempFile);
        }

        FileOutputStream fos = new FileOutputStream(tempFile);
        BufferedOutputStream bos = new BufferedOutputStream(fos, 65_536);

        byte[] header = buildPlaceholderHeader(chunkCount);
        bos.write(header);

        // Checksum the compressed payload as it is written instead of reading
        // the whole file again in finish(). For large zones this removes an
        // entire extra pass over hundreds of MB of disk data.
        CRC32 payloadCrc = new CRC32();
        OutputStream compressedOut;
        try {
            compressedOut = codec.compress(new CheckedOutputStream(bos, payloadCrc));
        } catch (IOException e) {
            bos.close();
            tempFile.delete();
            throw e;
        }

        return new StreamWriterImpl(
                zoneId, chunkCount, Instant.now(),
                tempFile, finalFile, fos, bos, compressedOut, payloadCrc);
    }

    @Override
    public Optional<ZoneSnapshot> loadSnapshot(String zoneId) throws StorageException {
        Objects.requireNonNull(zoneId, "zoneId");

        File file = layout.getSnapshotFile(zoneId);
        if (!file.exists())
            return Optional.empty();

        try {
            byte[] fullData = readFile(file);
            ZoneSnapshot snapshot = deserializeFile(zoneId, fullData);
            return Optional.of(snapshot);
        } catch (IOException e) {
            throw new StorageException(
                    "Error loading snapshot for zone '" + zoneId + "'. The file may be corrupted.", e);
        }
    }

    @Override
    public boolean hasSnapshot(String zoneId) {
        if (zoneId == null)
            return false;
        return layout.getSnapshotFile(zoneId).exists();
    }

    @Override
    public void deleteSnapshot(String zoneId) throws StorageException {
        Objects.requireNonNull(zoneId, "zoneId");
        File file = layout.getSnapshotFile(zoneId);
        if (file.exists() && !file.delete()) {
            throw new StorageException("Could not delete snapshot for '" + zoneId + "'.");
        }

        File dir = layout.getZoneSnapshotDir(zoneId);
        if (dir.exists() && dir.isDirectory()) {
            String[] remaining = dir.list();
            if (remaining == null || remaining.length == 0)
                dir.delete();
        }

        logger.debug("Snapshot for '{}' deleted.", zoneId);
    }

    @Override
    public StorageInfo getInfo(String zoneId) {
        if (zoneId == null)
            return StorageInfo.notFound("");

        File file = layout.getSnapshotFile(zoneId);
        if (!file.exists())
            return StorageInfo.notFound(zoneId);

        if (file.length() < HEADER_SIZE) {
            return StorageInfo.notFound(zoneId);
        }

        // Read only the fixed header. The previous implementation loaded the
        // entire snapshot (potentially hundreds of MB) just to display metadata.
        try (DataInputStream din = new DataInputStream(
                new BufferedInputStream(new FileInputStream(file), 4096))) {
            int magic = din.readInt();
            if (magic != MAGIC)
                return StorageInfo.notFound(zoneId);

            din.readShort();
            din.readShort();
            int chunkCount = din.readInt();
            long totalBlocks = din.readLong();
            long epochSeconds = din.readLong();
            long crc32 = din.readInt() & 0xFFFFFFFFL;

            return new StorageInfo(zoneId, true, file.length(),
                    chunkCount, totalBlocks,
                    Instant.ofEpochSecond(epochSeconds),
                    crc32, codec.getName());
        } catch (IOException e) {
            logger.warn("Error reading snapshot metadata '{}': {}", zoneId, e.getMessage());
            return StorageInfo.notFound(zoneId);
        }
    }

    private byte[] serializePayload(ZoneSnapshot snapshot) throws IOException {
        ByteArrayOutputStream rawOut = new ByteArrayOutputStream(1024 * 1024);
        try (DataOutputStream dos = new DataOutputStream(rawOut)) {
            for (ChunkSnapshotData chunk : snapshot.getChunks()) {
                writeChunkData(dos, chunk);
            }
        }
        return codec.compressBytes(rawOut.toByteArray());
    }

    private byte[] assembleFileBytes(int chunkCount, long totalBlocks,
            Instant capturedAt, long crc32, byte[] payload) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(HEADER_SIZE + payload.length);
        DataOutputStream dos = new DataOutputStream(out);

        dos.writeInt(MAGIC);
        dos.writeShort(FORMAT_VERSION);
        dos.writeShort(FLAG_COMPRESSED);
        dos.writeInt(chunkCount);
        dos.writeLong(totalBlocks);
        dos.writeLong(capturedAt.getEpochSecond());
        dos.writeInt((int) (crc32 & 0xFFFFFFFFL));
        dos.writeLong(0L); // reserved
        dos.write(payload);
        dos.flush();

        return out.toByteArray();
    }

    private ZoneSnapshot deserializeFile(String zoneId, byte[] fullData) throws IOException {
        DataInputStream din = new DataInputStream(new ByteArrayInputStream(fullData));

        int magic = din.readInt();
        if (magic != MAGIC) {
            throw new IOException("Invalid magic in snapshot for '" + zoneId + "': "
                    + Integer.toHexString(magic));
        }

        short version = din.readShort();
        if (version != 1 && version != 2) {
            throw new IOException("Unsupported format version: " + version
                    + " (supported: 1, 2)");
        }

        short flags = din.readShort();
        int chunkCount = din.readInt();
        long totalBlocks = din.readLong();
        long epochSeconds = din.readLong();
        long storedCRC32 = din.readInt() & 0xFFFFFFFFL;
        din.readLong();

        if (fullData.length < HEADER_SIZE) {
            throw new IOException("Snapshot file is too small.");
        }

        byte[] payload = Arrays.copyOfRange(fullData, HEADER_SIZE, fullData.length);

        long actualCRC32 = computeCRC32(payload);
        if (actualCRC32 != storedCRC32) {
            throw new IOException("Invalid CRC32 in snapshot for '" + zoneId + "'. "
                    + "Expected=" + storedCRC32 + ", actual=" + actualCRC32
                    + ". The file is corrupted.");
        }

        byte[] rawData = ((flags & FLAG_COMPRESSED) != 0)
                ? codec.decompressBytes(payload)
                : payload;

        List<ChunkSnapshotData> chunks = new ArrayList<>(chunkCount);
        DataInputStream raw = new DataInputStream(new ByteArrayInputStream(rawData));

        for (int c = 0; c < chunkCount; c++) {
            int cx = raw.readInt();
            int cz = raw.readInt();
            int blockCount = raw.readInt();

            List<BlockRecord> blocks = new ArrayList<>(blockCount);
            for (int b = 0; b < blockCount; b++) {
                long packedPos = raw.readLong();

                int bdLen = raw.readShort() & 0xFFFF;
                byte[] bdBytes = new byte[bdLen];
                raw.readFully(bdBytes);
                String blockDataStr = new String(bdBytes, StandardCharsets.UTF_8);

                byte hasEntity = raw.readByte();
                byte[] nbt = null;
                if (hasEntity == 1) {
                    int nbtLen = raw.readInt();
                    nbt = new byte[nbtLen];
                    raw.readFully(nbt);
                }
                blocks.add(new BlockRecord(packedPos, blockDataStr, nbt));
            }
            chunks.add(new ChunkSnapshotData(cx, cz, blocks));
        }

        logger.debug("Snapshot for '{}' loaded: {} chunks, {} blocks.",
                zoneId, chunks.size(), totalBlocks);

        return new ZoneSnapshot(zoneId, getWorldUIDFromMeta(zoneId),
                Instant.ofEpochSecond(epochSeconds), chunks);
    }

    static void writeChunkData(DataOutputStream dos, ChunkSnapshotData chunk) throws IOException {
        dos.writeInt(chunk.getChunkX());
        dos.writeInt(chunk.getChunkZ());
        dos.writeInt(chunk.getBlockCount());

        for (BlockRecord record : chunk.getBlocks()) {
            dos.writeLong(record.packedPos);

            byte[] bdBytes = record.blockDataString.getBytes(StandardCharsets.UTF_8);
            if (bdBytes.length > Short.MAX_VALUE) {
                throw new IOException("BlockData string is too long: " + bdBytes.length);
            }
            dos.writeShort((short) bdBytes.length);
            dos.write(bdBytes);

            if (record.hasBlockEntity()) {
                dos.writeByte(1);
                dos.writeInt(record.blockEntityNbt.length);
                dos.write(record.blockEntityNbt);
            } else {
                dos.writeByte(0);
            }
        }
    }

    private static byte[] buildPlaceholderHeader(int chunkCount) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(HEADER_SIZE);
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(MAGIC);
        dos.writeShort(FORMAT_VERSION);
        dos.writeShort(FLAG_COMPRESSED);
        dos.writeInt(chunkCount);
        dos.writeLong(0L);
        dos.writeLong(0L);
        dos.writeInt(0);
        dos.writeLong(0L);
        dos.flush();
        return baos.toByteArray();
    }

    private static byte[] readFile(File file) throws IOException {
        long size = file.length();
        if (size > 512L * 1024L * 1024L) {
            throw new IOException("Snapshot file is too large: " + humanSize(size));
        }
        try (FileInputStream fis = new FileInputStream(file);
                BufferedInputStream bis = new BufferedInputStream(fis, 65_536)) {
            return bis.readAllBytes();
        }
    }

    private static long computeCRC32(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return crc.getValue();
    }

    static String humanSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1_048_576)
            return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / 1_048_576.0);
    }

    private UUID getWorldUIDFromMeta(String zoneId) {
        return new UUID(0L, 0L);
    }

    @Override
    public Optional<SnapshotStreamReader> openStreamingReader(String zoneId) throws StorageException {
        Objects.requireNonNull(zoneId, "zoneId");

        File file = layout.getSnapshotFile(zoneId);
        if (!file.exists()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new StreamReaderImpl(zoneId, file, codec, logger));
        } catch (IOException e) {
            throw new StorageException(
                    "Error opening streaming reader for zone '" + zoneId + "'.", e);
        }
    }

    private static final class StreamReaderImpl implements SnapshotStreamReader {

        private final String zoneId;
        private final int totalChunks;
        private final long totalBlocks;

        private final FileInputStream fis;
        private final BufferedInputStream bis;
        private final InputStream decompressedIn;
        private final DataInputStream dataIn;

        private int chunksRead = 0;
        private boolean closed = false;

        StreamReaderImpl(String zoneId, File file, CompressionCodec codec, ZoneLogger logger)
                throws IOException {
            this.zoneId = zoneId;

            this.fis = new FileInputStream(file);
            this.bis = new BufferedInputStream(fis, 65_536);

            DataInputStream headerIn = new DataInputStream(bis);
            int magic = headerIn.readInt();
            if (magic != MAGIC) {
                fis.close();
                throw new IOException("Invalid magic in snapshot for '" + zoneId + "': "
                        + Integer.toHexString(magic));
            }

            short version = headerIn.readShort();
            if (version != 1 && version != 2) {
                fis.close();
                throw new IOException("Unsupported snapshot version: " + version + " (supported: 1, 2)");
            }

            short flags = headerIn.readShort();
            this.totalChunks = headerIn.readInt();
            this.totalBlocks = headerIn.readLong();
            headerIn.readLong();
            headerIn.readInt();
            headerIn.readLong();

            if ((flags & FLAG_COMPRESSED) != 0) {
                try {
                    this.decompressedIn = codec.decompress(bis);
                } catch (IOException e) {
                    fis.close();
                    throw e;
                }
            } else {
                this.decompressedIn = bis;
            }
            this.dataIn = new DataInputStream(decompressedIn);

            logger.debug("StreamReader opened for zone '{}': {} chunks expected.", zoneId, totalChunks);
        }

        @Override
        public int getTotalChunks() {
            return totalChunks;
        }

        @Override
        public long getTotalBlocks() {
            return totalBlocks;
        }

        @Override
        public int getChunksRead() {
            return chunksRead;
        }

        @Override
        public Optional<ChunkSnapshotData> readNextChunk() throws IOException {
            if (closed)
                throw new IOException("StreamReader is already closed.");
            if (chunksRead >= totalChunks)
                return Optional.empty();

            try {
                int cx = dataIn.readInt();
                int cz = dataIn.readInt();
                int blockCount = dataIn.readInt();

                List<BlockRecord> blocks = new ArrayList<>(blockCount);
                for (int b = 0; b < blockCount; b++) {
                    long packedPos = dataIn.readLong();

                    int bdLen = dataIn.readShort() & 0xFFFF;
                    byte[] bdBytes = new byte[bdLen];
                    dataIn.readFully(bdBytes);
                    String blockDataStr = new String(bdBytes, StandardCharsets.UTF_8);

                    byte hasEntity = dataIn.readByte();
                    byte[] nbt = null;
                    if (hasEntity == 1) {
                        int nbtLen = dataIn.readInt();
                        nbt = new byte[nbtLen];
                        dataIn.readFully(nbt);
                    }
                    blocks.add(new BlockRecord(packedPos, blockDataStr, nbt));
                }

                chunksRead++;
                return Optional.of(new ChunkSnapshotData(cx, cz, blocks));

            } catch (java.io.EOFException e) {
                throw new IOException(
                        "Snapshot for zone '" + zoneId + "' is truncated. "
                                + "Expected " + totalChunks + " chunks, but stream ended after " + chunksRead + ".",
                        e);
            }
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                closed = true;
                try {
                    dataIn.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private final class StreamWriterImpl implements SnapshotStreamWriter {

        private final String zoneId;
        private final int chunkCount;
        private final Instant capturedAt;
        private final File tempFile;
        private final File finalFile;

        private final FileOutputStream fos;
        private final BufferedOutputStream bos;
        private final OutputStream compressedOut;
        private final DataOutputStream dos;
        private final CRC32 payloadCrc;

        private long totalBlocks = 0L;
        private boolean finished = false;

        StreamWriterImpl(String zoneId, int chunkCount, Instant capturedAt,
                File tempFile, File finalFile,
                FileOutputStream fos, BufferedOutputStream bos,
                OutputStream compressedOut, CRC32 payloadCrc) {
            this.zoneId = zoneId;
            this.chunkCount = chunkCount;
            this.capturedAt = capturedAt;
            this.tempFile = tempFile;
            this.finalFile = finalFile;
            this.fos = fos;
            this.bos = bos;
            this.compressedOut = compressedOut;
            this.dos = new DataOutputStream(compressedOut);
            this.payloadCrc = payloadCrc;
        }

        @Override
        public void writeChunk(ChunkSnapshotData chunk) throws IOException {
            if (finished)
                throw new IOException("StreamWriter already finished.");
            writeChunkData(dos, chunk);
            totalBlocks += chunk.getBlockCount();
        }

        @Override
        public long finish() throws IOException {
            if (finished)
                throw new IOException("StreamWriter already finished.");
            finished = true;

            dos.flush();
            compressedOut.flush();
            // Closing the codec flushes it and the CheckedOutputStream, so the
            // CRC now covers the complete compressed payload.
            compressedOut.close();

            long crc32 = payloadCrc.getValue();
            backPatchHeader(crc32);

            try {
                Files.move(tempFile.toPath(), finalFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(tempFile.toPath(), finalFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }

            logger.info("Streaming snapshot for '{}' saved: {} chunks, {} blocks, {}.",
                    zoneId, chunkCount, totalBlocks, humanSize(finalFile.length()));

            return totalBlocks;
        }

        @Override
        public void abort() throws IOException {
            if (!finished) {
                finished = true;
                try {
                    dos.close();
                } catch (IOException ignored) {
                }
                try {
                    bos.close();
                } catch (IOException ignored) {
                }
                try {
                    fos.close();
                } catch (IOException ignored) {
                }
            }
            if (tempFile.exists())
                tempFile.delete();
        }

        private void backPatchHeader(long crc32) throws IOException {
            try (RandomAccessFile raf = new RandomAccessFile(tempFile, "rw")) {
                raf.seek(OFFSET_TOTAL_BLOCKS);
                raf.writeLong(totalBlocks);

                raf.seek(20);
                raf.writeLong(capturedAt.getEpochSecond());

                raf.seek(OFFSET_CRC32);
                raf.writeInt((int) (crc32 & 0xFFFFFFFFL));
            }
        }
    }
}
