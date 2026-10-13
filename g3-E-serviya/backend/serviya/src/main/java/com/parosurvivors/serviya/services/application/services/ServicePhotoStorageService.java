package com.parosurvivors.serviya.services.application.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ServicePhotoStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private final Path uploadRoot;

    public ServicePhotoStorageService(@Value("${app.upload.dir:uploads}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadRoot);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo preparar el directorio de archivos", e);
        }
    }

    public List<String> storePhotos(Long serviceId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        List<MultipartFile> validFiles = files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();

        if (validFiles.size() > 15) {
            throw new IllegalArgumentException("Máximo 15 fotos por servicio");
        }

        Path serviceDir = uploadRoot.resolve("services").resolve(String.valueOf(serviceId));
        try {
            Files.createDirectories(serviceDir);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear la carpeta para las fotos del servicio", e);
        }

        List<String> storedPaths = new ArrayList<>();
        for (MultipartFile file : validFiles) {
            String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("photo");
            String extension = getExtension(originalName);
            if (!isValidImage(file, extension)) {
                throw new IllegalArgumentException("Solo se permiten archivos de imagen (jpg, jpeg, png, webp, gif)");
            }

            String fileName = UUID.randomUUID() + (extension.isBlank() ? "" : "." + extension);
            Path target = serviceDir.resolve(fileName);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new IllegalStateException("No se pudo guardar la imagen " + originalName, e);
            }

            storedPaths.add("/uploads/services/" + serviceId + "/" + fileName);
        }

        return storedPaths;
    }

    public void deletePhotos(List<String> photoPaths) {
        if (photoPaths == null || photoPaths.isEmpty()) {
            return;
        }

        for (String photoPath : photoPaths) {
            if (photoPath == null || photoPath.isBlank()) {
                continue;
            }
            String normalizedPath = photoPath.startsWith("/uploads/")
                    ? photoPath.substring("/uploads/".length())
                    : photoPath.replaceFirst("^/", "");
            Path target = uploadRoot.resolve(normalizedPath);
            try {
                Files.deleteIfExists(target);
            } catch (IOException e) {
                throw new IllegalStateException("No se pudo eliminar la imagen " + photoPath, e);
            }
        }
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
