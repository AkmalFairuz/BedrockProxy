package org.akmalfairuz.bedrockproxy.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.GZIPInputStream;

public class FileManager {

    public static String getFileResource(String name) {
        try {
            ClassLoader classloader = Thread.currentThread().getContextClassLoader();
            InputStream in = classloader.getResourceAsStream(name);
            if(in == null) return null;
            return new String(in.readAllBytes());
        } catch (Exception ignored) {
        }

        return null;
    }

    public static InputStream getFileResourceAsInputStream(String name) {
        try {
            ClassLoader classloader = Thread.currentThread().getContextClassLoader();
            return classloader.getResourceAsStream(name);
        } catch (Exception ignored) {
        }

        return null;
    }

    public static String getFileContents(InputStream inputStream) throws Exception {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];

        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            byteArrayOutputStream.write(buffer, 0, length);
        }

        byteArrayOutputStream.close();
        inputStream.close();

        return byteArrayOutputStream.toString(StandardCharsets.UTF_8);
    }

    public static String decompressGZIP(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        GZIPInputStream gZIPInputStream = new GZIPInputStream(inputStream);
        byte[] buffer = new byte[1024];

        int length;
        while ((length = gZIPInputStream.read(buffer)) != -1) {
            byteArrayOutputStream.write(buffer, 0, length);
        }

        byteArrayOutputStream.close();
        inputStream.close();
        gZIPInputStream.close();
        return byteArrayOutputStream.toString(StandardCharsets.UTF_8);
    }
}
