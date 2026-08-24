package com.ecommercie.melhor_envio.service;

import com.ecommercie.melhor_envio.ShippingProvider;
import com.ecommercie.melhor_envio.dto.ShippingQuote;
import com.ecommercie.melhor_envio.dto.ShippingQuoteRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShippingService {

    private final ShippingProvider shippingProvider;

    public List<ShippingQuote> quote(ShippingQuoteRequest request){
        return shippingProvider.quote(request);
    }


}
