package br.edu.univille.poo.springai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SalvarHistoriaDTO(
        @Positive(message = "Identificador de história inválido.") Long id,
        @NotBlank(message = "Informe um título.")
        @Size(max = 200, message = "Use até 200 caracteres no título.") String titulo,
        @NotBlank(message = "Informe os personagens.")
        @Size(max = 500, message = "Use até 500 caracteres nos personagens.") String personagens,
        @NotBlank(message = "Descreva sua ideia para a história.")
        @Size(max = 8000, message = "Use até 8.000 caracteres na ideia.") String contexto,
        @NotBlank(message = "Gere ou escreva uma história antes de salvar.")
        @Size(max = 60000, message = "Use até 60.000 caracteres na história.") String texto) {
}
