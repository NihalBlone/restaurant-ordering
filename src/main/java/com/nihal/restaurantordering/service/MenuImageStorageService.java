package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.config.StorageProperties;
import com.nihal.restaurantordering.exception.BadRequestException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MenuImageStorageService {

    private static final long MAX_IMAGE_BYTES = 5L * 1024L * 1024L;

    private final StorageProperties storageProperties;
    private Path imageDirectory;

    @PostConstruct
    void initialize() {
        imageDirectory = Path.of(storageProperties.getMenuImageDirectory())
                .toAbsolutePath()
                .normalize();
        try {
            Files.createDirectories(imageDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not initialize menu image storage", exception);
        }
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Select an image to upload");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BadRequestException("Menu images must be 5 MB or smaller");
        }

        try {
            byte[] bytes = file.getBytes();
            String extension = detectExtension(bytes);
            String filename = UUID.randomUUID() + "." + extension;
            Path destination = imageDirectory.resolve(filename).normalize();
            if (!destination.getParent().equals(imageDirectory)) {
                throw new BadRequestException("Invalid image filename");
            }
            Files.write(destination, bytes, StandardOpenOption.CREATE_NEW);
            return "/uploads/menu/" + filename;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not store menu image", exception);
        }
    }

    private String detectExtension(byte[] bytes) {
        if (isPng(bytes)) return "png";
        if (isJpeg(bytes)) return "jpg";
        if (isWebp(bytes)) return "webp";
        throw new BadRequestException("Only PNG, JPEG, and WebP menu images are supported");
    }

    private boolean isPng(byte[] bytes) {
        byte[] signature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        if (bytes.length < signature.length) return false;
        for (int index = 0; index < signature.length; index++) {
            if (bytes[index] != signature[index]) return false;
        }
        return true;
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    private boolean isWebp(byte[] bytes) {
        return bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }
}
