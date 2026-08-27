package dev.zm.zonereset.storage;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class GzipCompressionCodec implements CompressionCodec {

    private static final int COMPRESSION_LEVEL = 6;

    private static final int BUFFER_SIZE = 65_536;

    @Override
    public String getName() {
        return "GZIP";
    }

    @Override
    public String getFileExtension() {
        return ".gz";
    }

    @Override
    public OutputStream compress(OutputStream out) throws IOException {
        return new GZIPOutputStream(new BufferedOutputStream(out, BUFFER_SIZE),
                COMPRESSION_LEVEL, false);
    }

    @Override
    public InputStream decompress(InputStream in) throws IOException {
        return new GZIPInputStream(new BufferedInputStream(in, BUFFER_SIZE));
    }

    @Override
    public byte[] compressBytes(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length / 4 + 64);
        try (OutputStream gzip = compress(baos)) {
            gzip.write(data);
        }
        return baos.toByteArray();
    }

    @Override
    public byte[] decompressBytes(byte[] compressed) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(compressed.length * 4);
        try (InputStream gzip = decompress(bais)) {
            byte[] buf = new byte[BUFFER_SIZE];
            int n;
            while ((n = gzip.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
        }
        return baos.toByteArray();
    }
}
