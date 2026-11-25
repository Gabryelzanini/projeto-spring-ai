package br.edu.univille.poo.springai;

import br.edu.univille.poo.springai.model.Historia;
import br.edu.univille.poo.springai.repository.HistoriaRepository;
import com.openai.core.http.Headers;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-placeholder",
        "spring.datasource.url=jdbc:h2:mem:historias-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.h2.console.enabled=false"
})
class SpringAiApplicationTests {
    @Autowired
    private WebApplicationContext context;

    @Autowired
    private HistoriaRepository repository;

    @MockitoBean
    private ChatModel chatModel;

    private MockMvc mvc;

    private static final String IDEIA = """
            {"personagens":"Luna e Bento","contexto":"Uma cidade onde as ruas mudam à noite."}
            """;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        repository.deleteAll();
        reset(chatModel);
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
    }

    @Test
    void abreEditorNaPaginaInicialSemOsExemplos() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("historias/index"))
                .andExpect(content().string(containsString("Melhorar ideia")))
                .andExpect(content().string(not(containsString("Voltar aos exemplos"))));
        mvc.perform(get("/historias"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
        for (var caminho : List.of("/chat", "/cidades", "/jogodavelha")) {
            mvc.perform(get(caminho)).andExpect(status().isNotFound());
        }
        verifyNoInteractions(chatModel);
    }

    @Test
    void geraHistoriaSemSalvarAutomaticamente() throws Exception {
        responderCom("""
                {"titulo":"O mapa de amanhã","texto":"Luna abriu o mapa.\\n\\nBento encontrou o caminho de volta."}
                """);

        mvc.perform(post("/historias/gerar").contentType(MediaType.APPLICATION_JSON).content(IDEIA))
                .andDo(resultado -> {
                    if (resultado.getResponse().getStatus() != 200 && resultado.getResolvedException() != null) {
                        throw new AssertionError("Falha ao gerar história", resultado.getResolvedException());
                    }
                })
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("O mapa de amanhã"))
                .andExpect(jsonPath("$.texto").value(containsString("Bento")));

        assertThat(repository.count()).isZero();
        verify(chatModel).call(argThat((Prompt prompt) -> prompt.getInstructions().stream()
                .anyMatch(message -> message.getText().contains("Luna e Bento")
                        && message.getText().contains("ruas mudam"))));
    }

    @Test
    void melhoraSomenteAIdeiaSemSalvar() throws Exception {
        responderCom("Luna e Bento precisam mapear uma cidade que muda de lugar antes do amanhecer.");
        mvc.perform(post("/historias/melhorar").contentType(MediaType.APPLICATION_JSON).content(IDEIA))
                .andDo(resultado -> {
                    if (resultado.getResponse().getStatus() != 200 && resultado.getResolvedException() != null) {
                        throw new AssertionError("Falha ao melhorar ideia", resultado.getResolvedException());
                    }
                })
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contexto").value(containsString("amanhecer")));
        assertThat(repository.count()).isZero();
    }

    @Test
    void recuperaMelhoriaVaziaOuGrandeSemNovoClique() throws Exception {
        for (String texto : List.of("   ", "a".repeat(8001))) {
            reset(chatModel);
            when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
            when(chatModel.call(any(Prompt.class)))
                    .thenReturn(resposta(texto), resposta("Luna e Bento exploram uma cidade viva."));
            mvc.perform(post("/historias/melhorar").contentType(MediaType.APPLICATION_JSON).content(IDEIA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contexto").value("Luna e Bento exploram uma cidade viva."));
            verify(chatModel, times(2)).call(any(Prompt.class));
            assertThat(repository.count()).isZero();
        }
    }

    @Test
    void descartaMelhoriaTruncadaERepeteComAIdeiaOriginal() throws Exception {
        var incompleta = new ChatResponse(List.of(new Generation(new AssistantMessage("Texto incompleto"),
                ChatGenerationMetadata.builder().finishReason("length").build())));
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(incompleta, resposta("Luna e Bento mapeiam ruas que mudam à noite."));
        mvc.perform(post("/historias/melhorar").contentType(MediaType.APPLICATION_JSON).content(IDEIA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contexto").value(containsString("mapeiam")));
        verify(chatModel, times(2)).call(argThat((Prompt prompt) -> prompt.getInstructions().stream()
                .anyMatch(message -> message.getText().contains("Luna e Bento")
                        && message.getText().contains("ruas mudam")
                        && !message.getText().contains("Texto incompleto"))));
    }

    @Test
    void recuperaFalhaTemporariaDoProvedor() throws Exception {
        var falha = falhaHttp(503, null);
        when(chatModel.call(any(Prompt.class))).thenThrow(falha)
                .thenReturn(resposta("Uma cidade em transformação."));
        mvc.perform(post("/historias/melhorar").contentType(MediaType.APPLICATION_JSON).content(IDEIA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contexto").value("Uma cidade em transformação."));
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void recuperaFalhaDeConexao() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenThrow(new OpenAIIoException("Connection interrupted"))
                .thenReturn(resposta("Luna e Bento enfrentam o amanhecer."));
        mvc.perform(post("/historias/melhorar").contentType(MediaType.APPLICATION_JSON).content(IDEIA))
                .andExpect(status().isOk());
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void encerraAposDuasFalhasSemSalvar() throws Exception {
        var falha = falhaHttp(503, null);
        when(chatModel.call(any(Prompt.class))).thenThrow(falha);
        mvc.perform(post("/historias/melhorar").contentType(MediaType.APPLICATION_JSON).content(IDEIA))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.mensagem").value(containsString("Seu texto foi mantido")));
        verify(chatModel, times(2)).call(any(Prompt.class));
        assertThat(repository.count()).isZero();
    }

    @Test
    void naoRepeteErrosDeChaveCreditoOuRequisicao() throws Exception {
        for (int status : List.of(400, 401, 402, 403)) {
            reset(chatModel);
            when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
            var falha = falhaHttp(status, null);
            when(chatModel.call(any(Prompt.class))).thenThrow(falha);
            mvc.perform(post("/historias/melhorar").contentType(MediaType.APPLICATION_JSON).content(IDEIA))
                    .andExpect(status().isBadGateway());
            verify(chatModel).call(any(Prompt.class));
        }
    }

    @Test
    void respeitaEsperaLongaDoProvedorSemRepetirAntesDaHora() throws Exception {
        var falha = falhaHttp(429, "60");
        when(chatModel.call(any(Prompt.class))).thenThrow(falha);
        mvc.perform(post("/historias/melhorar").contentType(MediaType.APPLICATION_JSON).content(IDEIA))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.mensagem").value(containsString("limite de uso")));
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void recuperaLimiteTemporarioComEsperaCurta() throws Exception {
        var falha = falhaHttp(429, "0");
        when(chatModel.call(any(Prompt.class))).thenThrow(falha)
                .thenReturn(resposta("Luna e Bento exploram a cidade."));
        mvc.perform(post("/historias/melhorar").contentType(MediaType.APPLICATION_JSON).content(IDEIA))
                .andExpect(status().isOk());
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void rejeitaIdeiaVaziaSemConsultarIA() throws Exception {
        for (var caminho : List.of("/historias/gerar", "/historias/melhorar")) {
            mvc.perform(post(caminho).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"personagens\":\"Luna\",\"contexto\":\"   \"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensagem").isNotEmpty());
        }
        verifyNoInteractions(chatModel);
    }

    @Test
    void rejeitaIdeiaGrandeEDadosMalformados() throws Exception {
        mvc.perform(post("/historias/gerar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"personagens\":\"Luna\",\"contexto\":\"" + "a".repeat(8001) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/historias/salvar").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").isNotEmpty());
        verifyNoInteractions(chatModel);
        assertThat(repository.count()).isZero();
    }

    @Test
    void salvaTextoLongoReabreEEscapaHtml() throws Exception {
        String texto = "Uma longa aventura. ".repeat(200);
        mvc.perform(post("/historias/salvar").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo":"<script>alert(1)</script>","personagens":"Luna e Bento",
                                 "contexto":"Uma cidade mágica.","texto":"%s"}
                                """.formatted(texto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.dataAtualizacao").isNotEmpty());

        Historia salva = repository.findAll().get(0);
        assertThat(salva.getTexto()).isEqualTo(texto.trim());
        assertThat(salva.getDataCriacao()).isNotNull();
        mvc.perform(get("/historias/" + salva.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
                .andExpect(content().string(containsString(texto.trim())));
        verifyNoInteractions(chatModel);
    }

    @Test
    void atualizaHistoriaExistenteSemDuplicar() throws Exception {
        var historia = new Historia();
        historia.setTitulo("Antes");
        historia.setPersonagens("Luna");
        historia.setContexto("Uma cidade mágica.");
        historia.setTexto("Primeira versão.");
        var salva = repository.saveAndFlush(historia);
        var criacao = repository.findById(salva.getId()).orElseThrow().getDataCriacao();

        mvc.perform(post("/historias/salvar").contentType(MediaType.APPLICATION_JSON).content("""
                        {"id":%d,"titulo":"Depois","personagens":"Luna e Bento",
                         "contexto":"Uma cidade mágica.","texto":"Versão revisada."}
                        """.formatted(salva.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(salva.getId()));

        assertThat(repository.count()).isEqualTo(1);
        var atualizada = repository.findById(salva.getId()).orElseThrow();
        assertThat(atualizada.getTexto()).isEqualTo("Versão revisada.");
        assertThat(atualizada.getDataCriacao()).isEqualTo(criacao);
        assertThat(atualizada.getDataAtualizacao()).isAfterOrEqualTo(criacao);
    }

    @Test
    void historiaInexistenteRetorna404() throws Exception {
        mvc.perform(get("/historias/999999"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/historias/salvar").contentType(MediaType.APPLICATION_JSON).content("""
                        {"id":999999,"titulo":"Título","personagens":"Luna",
                         "contexto":"Uma cidade mágica.","texto":"Uma aventura."}
                        """))
                .andExpect(status().isNotFound());
        assertThat(repository.count()).isZero();
    }

    @Test
    void rejeitaHistoriaIncompleta() throws Exception {
        mvc.perform(post("/historias/salvar").contentType(MediaType.APPLICATION_JSON).content("""
                        {"titulo":"Título","personagens":"Luna","contexto":"Uma aventura.","texto":""}
                        """))
                .andExpect(status().isBadRequest());
        assertThat(repository.count()).isZero();
    }

    @Test
    void falhaDaIANaoExpoeDetalhesNemSalvaHistoria() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("sensitive-api-response"));
        for (var caminho : List.of("/historias/gerar", "/historias/melhorar")) {
            mvc.perform(post(caminho).contentType(MediaType.APPLICATION_JSON).content(IDEIA))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.mensagem").isNotEmpty())
                    .andExpect(content().string(not(containsString("sensitive-api-response"))));
        }
        assertThat(repository.count()).isZero();
    }

    @Test
    void respostaInvalidaDaIANaoETratadaComoSucesso() throws Exception {
        responderCom("{\"titulo\":\"Título\",\"texto\":\"\"}");
        mvc.perform(post("/historias/gerar").contentType(MediaType.APPLICATION_JSON).content(IDEIA))
                .andExpect(status().isBadGateway());
        responderCom("   ");
        mvc.perform(post("/historias/melhorar").contentType(MediaType.APPLICATION_JSON).content(IDEIA))
                .andExpect(status().isBadGateway());
        assertThat(repository.count()).isZero();
    }

    private void responderCom(String texto) {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(resposta(texto));
    }

    private ChatResponse resposta(String texto) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(texto))));
    }

    private OpenAIServiceException falhaHttp(int status, String espera) {
        var falha = mock(OpenAIServiceException.class);
        when(falha.statusCode()).thenReturn(status);
        var headers = Headers.builder();
        if (espera != null) headers.put("Retry-After", espera);
        when(falha.headers()).thenReturn(headers.build());
        return falha;
    }
}
