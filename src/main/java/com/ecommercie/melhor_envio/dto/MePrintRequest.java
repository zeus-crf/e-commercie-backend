package com.ecommercie.melhor_envio.dto;

import java.util.List;

/** mode "private": o link so abre para quem esta logado no ME (o dono da conta). */
public record MePrintRequest(String mode, List<String> orders) {
}
