package br.edu.univille.poo.springai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IdeiaHistoriaDTO(
        @NotBlank(message = "Informe os personagens.")
        @Size(max = 500, message = "Use até 500 caracteres nos personagens.")
        String personagens,
        @NotBlank(message = "Descreva sua ideia para a história.")
        @Size(max = 8000, message = "Use até 8.000 caracteres na ideia.")
        String contexto) {
}
