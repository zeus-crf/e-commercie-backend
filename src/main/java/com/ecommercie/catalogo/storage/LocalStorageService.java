package com.ecommercie.catalogo.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class LocalStorageService implements StorageService {

    private final Path diretorio;

    private final String baseUrl;

    public LocalStorageService(
            @Value("${storage.local.dir:uploads}") String dir,
            @Value("${storage.public-url:http://localhost:8080/files}") String baseUrl) {
        this.diretorio = Paths.get(dir).toAbsolutePath().normalize();
        this.baseUrl = baseUrl;
        try {
            Files.createDirectories(this.diretorio);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível criar a pasta de upload", e);
        }
    }

    @Override
    public String store(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new IllegalArgumentException("Arquivo vazio");
        }
        String nome = UUID.randomUUID() + extensao(arquivo.getOriginalFilename());
        try {
            arquivo.transferTo(this.diretorio.resolve(nome));
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao salvar o arquivo", e);
        }

        return baseUrl + "/" + nome;
    }

    @Override
    public void delete(String url) {
        if (url == null) return;
        String nome = url.substring(url.lastIndexOf('/') + 1);
        try {
            Files.deleteIfExists(this.diretorio.resolve(nome));
        } catch (IOException ignored) { /* apagar imagem não deve quebrar o fluxo */ }
    }

    private String extensao(String nome) {
        return (nome != null && nome.contains(".")) ? nome.substring(nome.lastIndexOf('.')) : "";
    }
}
