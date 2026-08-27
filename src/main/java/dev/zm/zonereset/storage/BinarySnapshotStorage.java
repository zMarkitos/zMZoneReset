package dev.zm.zonereset.storage;

import dev.zm.zonereset.core.logging.ZoneLogger;
import dev.zm.zonereset.snapshot.BlockRecord;
import dev.zm.zonereset.snapshot.ChunkSnapshotData;
import dev.zm.zonereset.snapshot.ZoneSnapshot;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.zip.CRC32;

public final class BinarySnapshotStorage implements SnapshotStorage {

    private static final int MAGIC = 0x5A4D5A52;

    private static final short FORMAT_VERSION = 1;

    private static final short FLAG_COMPRESSED = 0x01;

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

            byte[] fullData = assembleFile(snapshot, payload, crc32);
            atomicWriter.write(finalFile, tempFile, fullData);

            logger.info("Snapshot for '{}' saved: {} chunks, {} blocks, {}.",
                    zoneId,
                    snapshot.getChunkCount(),
                    snapshot.getTotalBlocks(),
                    humanSize(fullData.length));

        } catch (IOException e) {
            throw new StorageException(
                    "Error saving snapshot for zone '" + zoneId + "'.", e);
        }
    }

    @Override
    public Optional<ZoneSnapshot> loadSnapshot(String zoneId) throws StorageException {
        Objects.requireNonNull(zoneId, "zoneId");

        File file = layout.getSnapshotFile(zoneId);
        if (!file.exists()) {
            return Optional.empty();
        }

        try {
            byte[] fullData = readFile(file);
            ZoneSnapshot snapshot = deserializeFile(zoneId, fullData);
            return Optional.of(snapshot);
        } catch (IOException e) {
            throw new StorageException(
                    "Error loading snapshot for zone '" + zoneId + "'. " +
                            "The file may be corrupted.",
                    e);
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
            if (remaining == null || remaining.length == 0) {
                dir.delete();
            }
        }

        logger.debug("Snapshot for '{}' deleted.", zoneId);
    }

    @Override
    public StorageInfo getInfo(String zoneId) {
        if (zoneId == null)
            return StorageInfo.notFound("");

        File file = layout.getSnapshotFile(zoneId);
        if (!file.exists()) {
            return StorageInfo.notFound(zoneId);
        }

        try {
            byte[] fullData = readFile(file);
            if (fullData.length < 40) {
                return StorageInfo.notFound(zoneId);
            }

            DataInputStream din = new DataInputStream(
                    new ByteArrayInputStream(fullData));
            int magic = din.readInt();
            if (magic != MAGIC) {
                return StorageInfo.notFound(zoneId);
            }

            din.readShort();
            din.readShort();
            int chunkCount = din.readInt();
            long totalBlocks = din.readLong();
            long epochSeconds = din.readLong();
            long crc32 = din.readInt() & 0xFFFFFFFFL;

            return new StorageInfo(
                    zoneId, true, file.length(),
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
        }

        return codec.compressBytes(rawOut.toByteArray());
    }

    private byte[] assembleFile(ZoneSnapshot snapshot, byte[] payload, long crc32)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(40 + payload.length);
        DataOutputStream dos = new DataOutputStream(out);

        dos.writeInt(MAGIC);
        dos.writeShort(FORMAT_VERSION);
        dos.writeShort(FLAG_COMPRESSED);
        dos.writeInt(snapshot.getChunkCount());
        dos.writeLong(snapshot.getTotalBlocks());
        dos.writeLong(snapshot.getCapturedAt().getEpochSecond());
        dos.writeInt((int) (crc32 & 0xFFFFFFFFL));
        dos.writeLong(0L);

        dos.write(payload);
        dos.flush();

        return out.toByteArray();
    }

    private ZoneSnapshot deserializeFile(String zoneId, byte[] fullData) throws IOException {
        DataInputStream din = new DataInputStream(new ByteArrayInputStream(fullData));

        int magic = din.readInt();
        if (magic != MAGIC) {
            throw new IOException("Invalid magic in snapshot for '" + zoneId + "': " +
                    Integer.toHexString(magic));
        }

        short version = din.readShort();
        if (version != FORMAT_VERSION) {
            throw new IOException("Unsupported format version: " + version +
                    " (expected: " + FORMAT_VERSION + ")");
        }

        short flags = din.readShort();
        int chunkCount = din.readInt();
        long totalBlocks = din.readLong();
        long epochSeconds = din.readLong();
        long storedCRC32 = din.readInt() & 0xFFFFFFFFL;
        din.readLong();

        int headerSize = 40;
        if (fullData.length < headerSize) {
            throw new IOException("Snapshot file is too small.");
        }

        byte[] payload = Arrays.copyOfRange(fullData, headerSize, fullData.length);

        long actualCRC32 = computeCRC32(payload);
        if (actualCRC32 != storedCRC32) {
            throw new IOException(
                    "Invalid CRC32 in snapshot for '" + zoneId + "'. " +
                            "Expected=" + storedCRC32 + ", actual=" + actualCRC32 +
                            ". The file is corrupted.");
        }

        byte[] rawData;
        if ((flags & FLAG_COMPRESSED) != 0) {
            rawData = codec.decompressBytes(payload);
        } else {
            rawData = payload;
        }

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

        return new ZoneSnapshot(
                zoneId,
                getWorldUIDFromMeta(zoneId),
                Instant.ofEpochSecond(epochSeconds),
                chunks);
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

    private static String humanSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1_048_576)
            return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / 1_048_576.0);
    }

    private UUID getWorldUIDFromMeta(String zoneId) {
        return new UUID(0L, 0L);
    }
}
