package dev.zm.zonereset.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface CompressionCodec {

    String getName();

    OutputStream compress(OutputStream out) throws IOException;

    InputStream decompress(InputStream in) throws IOException;

    byte[] compressBytes(byte[] data) throws IOException;

    byte[] decompressBytes(byte[] compressed) throws IOException;

    default String getFileExtension() {
        return ".bin";
    }
}
