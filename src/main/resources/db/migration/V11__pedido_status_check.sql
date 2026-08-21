ALTER TABLE pedido DROP CONSTRAINT IF EXISTS pedido_status_check;

ALTER TABLE pedido
    ADD CONSTRAINT pedido_status_check
        CHECK (status IN (
            'AGUARDANDO_PAGAMENTO',
            'PAGO',
            'EM_SEPARACAO',
            'ENVIADO',
            'ENTREGUE',
            'CANCELADO',
            'DEVOLUCAO_SOLICITADA',
            'DEVOLVIDO'
        ));
