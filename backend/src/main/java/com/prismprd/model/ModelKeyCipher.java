package com.prismprd.model;

import com.prismprd.common.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class ModelKeyCipher {
    private final Path keyPath;
    private final SecureRandom random = new SecureRandom();

    public ModelKeyCipher(@Value("${prism.models.key-path:./data/settings/model.key}") String path) {
        keyPath = Path.of(path).toAbsolutePath().normalize();
    }

    public synchronized String encrypt(String value) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(true), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv).put(encrypted).array());
        } catch (Exception e) {
            throw new BizException("无法保存模型密钥，请检查模型设置目录的写入权限");
        }
    }

    public synchronized String decrypt(String value) {
        try {
            byte[] data = Base64.getDecoder().decode(value);
            if (data.length < 28) throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(false), new GCMParameterSpec(128, data, 0, 12));
            return new String(cipher.doFinal(data, 12, data.length - 12), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BizException("模型密钥无法解密，请恢复配套的 model.key 文件或重新填写 API Key");
        }
    }

    private SecretKeySpec key(boolean create) throws java.io.IOException {
        if (!Files.exists(keyPath) && create) {
            Files.createDirectories(keyPath.getParent());
            byte[] generated = new byte[32];
            random.nextBytes(generated);
            try {
                if (Files.getFileStore(keyPath.getParent()).supportsFileAttributeView("posix")) {
                    Files.write(keyPath, new byte[0], StandardOpenOption.CREATE_NEW);
                    Files.setPosixFilePermissions(keyPath, PosixFilePermissions.fromString("rw-------"));
                    Files.write(keyPath, generated, StandardOpenOption.TRUNCATE_EXISTING);
                } else {
                    Files.write(keyPath, generated, StandardOpenOption.CREATE_NEW);
                }
            } catch (java.nio.file.FileAlreadyExistsException ignored) {
            }
        }
        byte[] bytes = Files.readAllBytes(keyPath);
        if (bytes.length != 32) throw new java.io.IOException("Invalid model key");
        return new SecretKeySpec(bytes, "AES");
    }
}

