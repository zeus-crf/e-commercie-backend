package com.ecommercie.catalogo.storage;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    /** Guarda o arquivo e devolve a URL pública dele. */
    String store(MultipartFile arquivo);

    /** Remove o arquivo pela URL/referência. */
    void delete(String url);
}
