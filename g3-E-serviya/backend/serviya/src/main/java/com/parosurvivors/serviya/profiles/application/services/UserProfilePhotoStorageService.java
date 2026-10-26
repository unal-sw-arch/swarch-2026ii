package com.parosurvivors.serviya.profiles.application.services;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserProfilePhotoStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private final Path uploadRoot;

    public UserProfilePhotoStorageService(@Value("${app.upload.dir:uploads}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadRoot);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo preparar el directorio de archivos", e);
        }
    }

    public String storeProfilePhoto(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("profile");
        String extension = getExtension(originalName);
        if (!isValidImage(file, extension)) {
            throw new IllegalArgumentException("Solo se permiten archivos de imagen (jpg, jpeg, png, webp, gif)");
        }

        Path userDir = uploadRoot.resolve("profiles").resolve(String.valueOf(userId));
        try {
            Files.createDirectories(userDir);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear la carpeta para la foto del perfil", e);
        }

        String fileName = UUID.randomUUID() + (extension.isBlank() ? "" : "." + extension);
        Path target = userDir.resolve(fileName);
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar la imagen " + originalName, e);
        }

        return "/uploads/profiles/" + userId + "/" + fileName;
    }

    private boolean isValidImage(MultipartFile file, String extension) {
        String contentType = Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT);
        if (contentType.startsWith("image/")) {
            return true;
        }
        return extension != null && ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT));
    }

    private String getExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDot + 1);
    }
}
