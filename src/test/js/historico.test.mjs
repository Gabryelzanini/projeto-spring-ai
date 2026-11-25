import test from 'node:test';
import assert from 'node:assert/strict';
import { Historico } from '../../main/resources/static/js/historico.js';

const inicial = { personagens: 'Luna', contexto: 'Uma cidade mágica.', titulo: '', texto: '' };

test('melhoria da IA é desfeita e refeita inteira, preservando a edição manual anterior', () => {
    const historico = new Historico(inicial);
    const manual = { ...inicial, contexto: 'Uma cidade onde as ruas mudam à noite.' };
    const melhorado = { ...manual, contexto: 'Luna precisa mapear as ruas que mudam antes do amanhecer.' };
    historico.registrar(manual);
    historico.registrar(melhorado);
    assert.deepEqual(historico.desfazer(), manual);
    assert.deepEqual(historico.refazer(), melhorado);
    assert.deepEqual(historico.desfazer(), manual);
    assert.deepEqual(historico.desfazer(), inicial);
    assert.equal(historico.desfazer(), null);
});

test('uma nova edição após desfazer descarta o ramo antigo de refazer', () => {
    const historico = new Historico(inicial);
    historico.registrar({ ...inicial, contexto: 'Melhoria anterior.' });
    historico.desfazer();
    const novaIdeia = { ...inicial, contexto: 'Outra direção para a aventura.' };
    historico.registrar(novaIdeia);
    assert.equal(historico.podeRefazer, false);
    assert.equal(historico.refazer(), null);
    assert.deepEqual(historico.desfazer(), inicial);
    assert.deepEqual(historico.refazer(), novaIdeia);
});

test('uma geração pode ser desfeita sem perder personagens e contexto', () => {
    const historico = new Historico(inicial);
    const gerada = { ...inicial, titulo: 'O mapa', texto: 'Era uma vez…' };
    historico.registrar(gerada);
    historico.registrar({ ...gerada, texto: 'Era uma vez uma cartógrafa.' });
    assert.deepEqual(historico.desfazer(), gerada);
    assert.deepEqual(historico.desfazer(), inicial);
    assert.deepEqual(historico.refazer(), gerada);
});

test('estados repetidos não criam passos extras nem apagam o refazer', () => {
    const historico = new Historico(inicial);
    historico.registrar({ ...inicial, contexto: 'Nova ideia.' });
    historico.desfazer();
    assert.equal(historico.registrar({ ...inicial }), false);
    assert.equal(historico.podeRefazer, true);
});

test('o histórico tem limite e continua navegável ao descartar estados antigos', () => {
    const historico = new Historico(inicial, 3);
    for (let numero = 1; numero <= 5; numero++) {
        historico.registrar({ ...inicial, contexto: `Versão ${numero}` });
    }
    assert.equal(historico.desfazer().contexto, 'Versão 4');
    assert.equal(historico.desfazer().contexto, 'Versão 3');
    assert.equal(historico.desfazer(), null);
    assert.equal(historico.refazer().contexto, 'Versão 4');
    assert.equal(historico.refazer().contexto, 'Versão 5');
});

test('mutar um estado devolvido não altera a versão guardada', () => {
    const historico = new Historico(inicial);
    const gerada = { ...inicial, titulo: 'Original', texto: 'Texto original.' };
    historico.registrar(gerada);
    gerada.texto = 'Mutação externa.';
    historico.desfazer();
    const restaurada = historico.refazer();
    assert.equal(restaurada.texto, 'Texto original.');
    restaurada.titulo = 'Outra mutação externa.';
    historico.desfazer();
    assert.equal(historico.refazer().titulo, 'Original');
});
