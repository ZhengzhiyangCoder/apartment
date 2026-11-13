package com.example.apartment.support.storage;

import com.example.apartment.common.ApiException;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/** Private files only: business authorization must precede every read. Never served as static resources. */
@Service
public class RepairImageStorage {
    private final Path root;
    public RepairImageStorage(@Value("${apartment.storage.root}") String root) { this.root = Path.of(root).toAbsolutePath().normalize(); }
    public record Prepared(byte[] png, String hash) {}
    public List<Prepared> prepare(List<MultipartFile> files) {
        if (files.size() > 5) throw invalid("每单最多上传 5 张图片");
        var result = new ArrayList<Prepared>();
        for (var file : files) {
            if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024) throw invalid("图片不能为空，每张不超过 5 MB");
            try {
                byte[] bytes = file.getBytes();
                try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                    var readers = ImageIO.getImageReaders(input);
                    if (!readers.hasNext()) throw invalid("图片内容无效，仅支持 JPG、PNG、WebP");
                    var reader = readers.next();
                    try {
                        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                        if (!Set.of("jpeg", "png", "webp").contains(format)) throw invalid("仅支持 JPG、PNG、WebP 图片");
                        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
                        boolean extension = name.endsWith("." + format) || (format.equals("jpeg") && name.endsWith(".jpg"));
                        if (!extension || !("image/" + format).equalsIgnoreCase(file.getContentType())) throw invalid("图片扩展名、类型与实际内容不一致");
                        reader.setInput(input, true, true);
                        int width = reader.getWidth(0), height = reader.getHeight(0);
                        if (width < 1 || height < 1 || (long) width * height > 16_000_000) throw invalid("图片像素过大，请压缩至 1600 万像素以内");
                        boolean[] warning = { false };
                        reader.addIIOReadWarningListener((source, message) -> warning[0] = true);
                        var decoded = reader.read(0);
                        if (decoded == null || warning[0]) throw invalid("图片已损坏，请重新选择");
                        var output = new ByteArrayOutputStream();
                        if (!ImageIO.write(decoded, "png", output)) throw new IOException("No PNG encoder");
                        result.add(new Prepared(output.toByteArray(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))));
                    } finally { reader.dispose(); }
                }
            } catch (ApiException error) { throw error; }
            catch (IOException | RuntimeException error) { throw invalid("图片无法完整解码，请选择有效的 JPG、PNG 或 WebP"); }
            catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
        }
        return result;
    }
    public String save(Prepared image) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Transaction required");
        String key = UUID.randomUUID() + ".png"; Path target = path(key);
        // Register before creating the file: even a partial disk write is removed on rollback.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try { Files.deleteIfExists(target); }
                    catch (IOException error) { LoggerFactory.getLogger(RepairImageStorage.class).error("Unable to remove inaccessible rolled-back image"); }
                }
            }
        });
        try {
            Files.createDirectories(root);
            Files.write(target, image.png(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return key;
        } catch (IOException error) { throw new IllegalStateException("Image storage unavailable", error); }
    }
    public byte[] read(String key) {
        try { return Files.readAllBytes(path(key)); }
        catch (IOException error) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "IMAGE_UNAVAILABLE", "图片暂时无法读取，请稍后重试"); }
    }
    private Path path(String key) {
        if (key == null || !key.matches("[a-f0-9-]{36}\\.png")) throw new IllegalArgumentException("Invalid storage key");
        return root.resolve(key);
    }
    private static ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", message); }
}
