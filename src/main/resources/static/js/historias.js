import { Historico } from './historico.js';

const editor = document.getElementById('editor');
const campos = Object.fromEntries(['personagens', 'contexto', 'titulo', 'texto']
    .map(nome => [nome, document.getElementById(nome)]));
const botoes = Object.fromEntries(['gerar', 'melhorar', 'salvar', 'desfazer', 'refazer']
    .map(nome => [nome, document.getElementById(nome)]));
const mensagem = document.getElementById('mensagem');
const progresso = document.getElementById('progresso-ia');
const carregando = document.getElementById('carregando');
const baseUrl = editor.dataset.baseUrl;
let historiaId = editor.dataset.id ? Number(editor.dataset.id) : null;
let ocupado = false;

function lerCampos() {
    return Object.fromEntries(Object.entries(campos).map(([nome, campo]) => [nome, campo.value]));
}

const historico = new Historico(lerCampos());
let ultimoEstadoSalvo = JSON.stringify(lerCampos());

function foiAlterado() {
    return JSON.stringify(lerCampos()) !== ultimoEstadoSalvo;
}

function atualizarControles() {
    botoes.desfazer.disabled = ocupado || !historico.podeDesfazer;
    botoes.refazer.disabled = ocupado || !historico.podeRefazer;
    botoes.gerar.disabled = ocupado;
    botoes.melhorar.disabled = ocupado;
    botoes.salvar.disabled = ocupado || !Object.values(lerCampos()).every(valor => valor.trim());
    document.getElementById('contador-ideia').textContent = `${campos.contexto.value.length.toLocaleString('pt-BR')} / 8.000`;
    const palavras = campos.texto.value.trim().match(/\S+/g)?.length ?? 0;
    document.getElementById('contador-palavras').textContent = `${palavras.toLocaleString('pt-BR')} ${palavras === 1 ? 'palavra' : 'palavras'}`;
    document.getElementById('estado-salvo').textContent = foiAlterado()
        ? 'Alterações não salvas' : (historiaId ? 'História salva' : 'Rascunho');
}

function avisar(texto, erro = false) {
    mensagem.textContent = texto;
    mensagem.classList.toggle('erro', erro);
}

function aplicarEstado(estado) {
    if (!estado) return;
    for (const [nome, valor] of Object.entries(estado)) campos[nome].value = valor;
    atualizarControles();
}

function moverHistorico(refazer = false) {
    if (ocupado) return;
    aplicarEstado(refazer ? historico.refazer() : historico.desfazer());
    avisar('');
}

editor.addEventListener('input', event => {
    if (!Object.values(campos).includes(event.target)) return;
    historico.registrar(lerCampos());
    atualizarControles();
    avisar('');
});

editor.addEventListener('beforeinput', event => {
    if (event.inputType === 'historyUndo' || event.inputType === 'historyRedo') {
        event.preventDefault();
        moverHistorico(event.inputType === 'historyRedo');
    }
});

document.addEventListener('keydown', event => {
    if (!(event.ctrlKey || event.metaKey) || event.altKey || event.isComposing) return;
    if (!editor.contains(event.target) && event.target !== document.body) return;
    const tecla = event.key.toLowerCase();
    if (tecla === 'z' || tecla === 'y') {
        event.preventDefault();
        moverHistorico(tecla === 'y' || event.shiftKey);
    }
});

editor.addEventListener('submit', event => event.preventDefault());
botoes.desfazer.addEventListener('click', () => moverHistorico());
botoes.refazer.addEventListener('click', () => moverHistorico(true));

function validar(nomes) {
    for (const nome of nomes) {
        if (!campos[nome].value.trim()) {
            avisar(`Preencha o campo ${document.querySelector(`label[for="${nome}"]`).textContent.replace(/:\s*$/, '').toLowerCase()}.`, true);
            campos[nome].focus();
            return false;
        }
        if (!campos[nome].reportValidity()) return false;
    }
    return true;
}

async function executar(caminho, dados, aviso, aplicarResposta) {
    if (ocupado) return;
    ocupado = true;
    Object.values(campos).forEach(campo => { campo.readOnly = true; });
    editor.setAttribute('aria-busy', 'true');
    const usandoIA = caminho === 'melhorar' || caminho === 'gerar';
    progresso.setAttribute('aria-label', caminho === 'melhorar' ? 'Melhorando ideia' : 'Gerando história');
    progresso.hidden = !usandoIA;
    carregando.hidden = usandoIA;
    atualizarControles();
    avisar(usandoIA ? '' : aviso);

    try {
        const resposta = await fetch(`${baseUrl}/${caminho}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
            body: JSON.stringify(dados),
            signal: AbortSignal.timeout(120000)
        });
        const resultado = await resposta.json().catch(() => null);
        if (!resposta.ok) throw new Error(resultado?.mensagem || 'Não foi possível concluir a operação. Tente novamente.');
        if (!resultado) throw new Error('O servidor retornou uma resposta inválida. Seu texto foi mantido.');
        aplicarResposta(resultado);
    } catch (erro) {
        const texto = erro.name === 'TimeoutError' || erro.name === 'AbortError'
            ? 'A resposta demorou mais que o esperado. Seu texto foi mantido. Tente novamente.'
            : (erro instanceof TypeError ? 'Não foi possível conectar ao servidor. Seu texto foi mantido.' : erro.message);
        avisar(texto, true);
    } finally {
        ocupado = false;
        Object.values(campos).forEach(campo => { campo.readOnly = false; });
        editor.setAttribute('aria-busy', 'false');
        progresso.hidden = true;
        carregando.hidden = true;
        atualizarControles();
    }
}

botoes.melhorar.addEventListener('click', () => {
    if (!validar(['personagens', 'contexto'])) return;
    const { personagens, contexto } = lerCampos();
    executar('melhorar', { personagens, contexto }, 'Enriquecendo sua ideia…', resultado => {
        if (typeof resultado.contexto !== 'string' || !resultado.contexto.trim() || resultado.contexto.length > 8000) {
            throw new Error('A melhoria retornou um texto inválido. Sua ideia foi mantida.');
        }
        campos.contexto.value = resultado.contexto;
        historico.registrar(lerCampos());
        avisar('Ideia melhorada.');
    });
});

botoes.gerar.addEventListener('click', () => {
    if (!validar(['personagens', 'contexto'])) return;
    const { personagens, contexto } = lerCampos();
    executar('gerar', { personagens, contexto }, 'Escrevendo sua história…', resultado => {
        if (typeof resultado.titulo !== 'string' || !resultado.titulo.trim() || resultado.titulo.length > 200
            || typeof resultado.texto !== 'string' || !resultado.texto.trim() || resultado.texto.length > 60000) {
            throw new Error('A geração retornou uma história inválida. Seu texto foi mantido.');
        }
        campos.titulo.value = resultado.titulo;
        campos.texto.value = resultado.texto;
        historico.registrar(lerCampos());
        avisar('História pronta!');
    });
});

botoes.salvar.addEventListener('click', () => {
    if (!validar(Object.keys(campos))) return;
    executar('salvar', { id: historiaId, ...lerCampos() }, 'Salvando sua história…', resultado => {
        if (!Number.isSafeInteger(resultado.id) || resultado.id < 1 || typeof resultado.titulo !== 'string') {
            throw new Error('Não foi possível confirmar o salvamento. Seu texto foi mantido.');
        }
        historiaId = resultado.id;
        editor.dataset.id = String(historiaId);
        ultimoEstadoSalvo = JSON.stringify(lerCampos());
        window.history.replaceState(null, '', `${baseUrl}/${historiaId}`);
        atualizarBiblioteca(resultado);
        avisar('História salva.');
    });
});

function atualizarBiblioteca(historia) {
    const lista = document.getElementById('historias-salvas');
    lista.querySelector(`[data-historia-id="${historia.id}"]`)?.remove();
    lista.querySelectorAll('.atual').forEach(item => item.classList.remove('atual'));
    document.getElementById('biblioteca-vazia')?.remove();
    const item = document.createElement('li');
    item.dataset.historiaId = String(historia.id);
    const link = document.createElement('a');
    link.href = `${baseUrl}/${historia.id}`;
    link.className = 'atual';
    link.textContent = historia.titulo;
    const data = document.createElement('span');
    data.className = 'ajuda';
    const momento = new Date(historia.dataAtualizacao);
    data.textContent = Number.isNaN(momento.getTime()) ? ' — Salva agora' : ` — Salva em ${momento.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })}`;
    item.append(link, data);
    lista.prepend(item);
}

window.addEventListener('beforeunload', event => {
    if (!foiAlterado()) return;
    event.preventDefault();
    event.returnValue = '';
});

atualizarControles();
