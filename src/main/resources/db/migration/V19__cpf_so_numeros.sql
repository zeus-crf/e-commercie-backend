-- O cadastro passou a gravar o CPF/CNPJ so com numeros; normaliza os que ja existem.
-- Documentos invalidos continuam como estao: o checkout e a etiqueta barram com mensagem.
UPDATE usuarios SET cpf_cnpj = regexp_replace(cpf_cnpj, '\D', '', 'g') WHERE cpf_cnpj ~ '\D';