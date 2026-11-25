package br.edu.univille.poo.springai.controller;

import br.edu.univille.poo.springai.dto.HistoriaGeradaDTO;
import br.edu.univille.poo.springai.dto.IdeiaHistoriaDTO;
import br.edu.univille.poo.springai.dto.SalvarHistoriaDTO;
import jakarta.validation.Valid;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.stereotype.Controller;
import br.edu.univille.poo.springai.service.HistoriaService;

import java.util.Map;

@Controller
public class HistoriaController {
    private final HistoriaService service;

    public HistoriaController(HistoriaService service) {
        this.service = service;
    }

    @GetMapping("/")
    public ModelAndView index() {
        var mv = new ModelAndView("historias/index");
        mv.addObject("historias", service.listar());
        return mv;
    }

    @GetMapping("/historias")
    public String paginaInicial() {
        return "redirect:/";
    }

    @GetMapping("/historias/{id}")
    public ModelAndView abrir(@PathVariable Long id) {
        var mv = index();
        mv.addObject("historia", service.buscar(id));
        return mv;
    }

    @PostMapping("/historias/gerar")
    @ResponseBody
    public HistoriaGeradaDTO gerar(@Valid @RequestBody IdeiaHistoriaDTO ideia) {
        return service.gerar(ideia);
    }

    @PostMapping("/historias/melhorar")
    @ResponseBody
    public Map<String, String> melhorar(@Valid @RequestBody IdeiaHistoriaDTO ideia) {
        return Map.of("contexto", service.melhorar(ideia));
    }

    @PostMapping("/historias/salvar")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> salvar(@Valid @RequestBody SalvarHistoriaDTO dados) {
        var historia = service.salvar(dados);
        return ResponseEntity.status(dados.id() == null ? HttpStatus.CREATED : HttpStatus.OK)
                .body(Map.of("id", historia.getId(), "titulo", historia.getTitulo(),
                        "dataAtualizacao", historia.getDataAtualizacao()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> erroValidacao(MethodArgumentNotValidException exception) {
        var erro = exception.getBindingResult().getFieldError();
        return ResponseEntity.badRequest().body(Map.of("mensagem",
                erro == null ? "Confira os campos informados." : erro.getDefaultMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> erroRequisicao() {
        return ResponseEntity.badRequest().body(Map.of("mensagem", "Dados inválidos. Confira o formulário."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> erroOperacao(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("mensagem",
                exception.getReason() == null ? "Não foi possível concluir a operação." : exception.getReason()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> erroBanco() {
        return ResponseEntity.internalServerError().body(Map.of("mensagem",
                "Não foi possível acessar as histórias salvas. Seu texto permanece no editor."));
    }
}
