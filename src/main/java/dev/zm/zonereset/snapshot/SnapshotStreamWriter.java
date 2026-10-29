package dev.zm.zonereset.snapshot;

import java.io.IOException;

public interface SnapshotStreamWriter {
    void writeChunk(ChunkSnapshotData chunk) throws IOException;

    long finish() throws IOException;

    void abort() throws IOException;
}
