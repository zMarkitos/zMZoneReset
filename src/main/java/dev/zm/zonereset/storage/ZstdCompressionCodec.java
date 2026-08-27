package dev.zm.zonereset.storage;

import dev.zm.zonereset.core.logging.ZoneLogger;

import java.io.*;

public final class ZstdCompressionCodec implements CompressionCodec {

    private static final int COMPRESSION_LEVEL = 3;
    private static final int BUFFER_SIZE = 65_536;

    private static final String ZSTD_OUTPUT_STREAM = "com.github.luben.zstd.ZstdOutputStream";
    private static final String ZSTD_INPUT_STREAM = "com.github.luben.zstd.ZstdInputStream";
    private static final String ZSTD_COMPRESS = "com.github.luben.zstd.Zstd";

    private final Class<?> zstdOutputStreamClass;
    private final Class<?> zstdInputStreamClass;
    private final java.lang.reflect.Constructor<?> outputCtor;
    private final java.lang.reflect.Constructor<?> inputCtor;

    private final Class<?> zstdClass;
    private final java.lang.reflect.Method compressMethod;
    private final java.lang.reflect.Method decompressMethod;

    private ZstdCompressionCodec() throws ReflectiveOperationException {
        this.zstdOutputStreamClass = Class.forName(ZSTD_OUTPUT_STREAM);
        this.zstdInputStreamClass = Class.forName(ZSTD_INPUT_STREAM);

        this.outputCtor = zstdOutputStreamClass.getConstructor(OutputStream.class, int.class);
        this.inputCtor = zstdInputStreamClass.getConstructor(InputStream.class);
        this.zstdClass = Class.forName(ZSTD_COMPRESS);

        this.compressMethod = zstdClass.getMethod("compress", byte[].class, int.class);
        this.decompressMethod = zstdClass.getMethod("decompress", byte[].class, int.class);
    }

    @Override
    public String getName() {
        return "ZSTD";
    }

    @Override
    public String getFileExtension() {
        return ".zst";
    }

    @Override
    public OutputStream compress(OutputStream out) throws IOException {
        try {
            OutputStream buffered = new BufferedOutputStream(out, BUFFER_SIZE);
            return (OutputStream) outputCtor.newInstance(buffered, COMPRESSION_LEVEL);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw new IOException("ZSTD compress init failed", cause);
        } catch (Exception e) {
            throw new IOException("ZSTD compress init failed", e);
        }
    }

    @Override
    public InputStream decompress(InputStream in) throws IOException {
        try {
            InputStream buffered = new BufferedInputStream(in, BUFFER_SIZE);
            return (InputStream) inputCtor.newInstance(buffered);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw new IOException("ZSTD decompress init failed", cause);
        } catch (Exception e) {
            throw new IOException("ZSTD decompress init failed", e);
        }
    }

    @Override
    public byte[] compressBytes(byte[] data) throws IOException {
        try {
            return (byte[]) compressMethod.invoke(null, data, COMPRESSION_LEVEL);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new IOException("ZSTD compressBytes failed", e.getCause());
        } catch (Exception e) {
            throw new IOException("ZSTD compressBytes failed", e);
        }
    }

    @Override
    public byte[] decompressBytes(byte[] compressed) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(compressed.length * 4);
        try (InputStream zstdIn = decompress(bais)) {
            byte[] buf = new byte[BUFFER_SIZE];
            int n;
            while ((n = zstdIn.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
        }
        return baos.toByteArray();
    }

    public static CompressionCodec createOrFallback(ZoneLogger logger) {
        try {
            ZstdCompressionCodec codec = new ZstdCompressionCodec();

            byte[] test = codec.compressBytes(new byte[] { 42 });
            byte[] result = codec.decompressBytes(test);
            if (result.length != 1 || result[0] != 42) {
                throw new IOException("ZSTD smoke test failed: data mismatch.");
            }

            logger.info("ZSTD compression initialized successfully (level {}).", COMPRESSION_LEVEL);
            return codec;

        } catch (Exception e) {
            logger.warn("ZSTD unavailable ({}). Using GZIP fallback. Snapshots will continue to work correctly.",
                    e.getMessage());
            return new GzipCompressionCodec();
        }
    }
}