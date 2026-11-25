export class Historico {
    constructor(inicial, limite = 200) {
        this.estados = [{ ...inicial }];
        this.posicao = 0;
        this.limite = limite;
    }

    registrar(estado) {
        if (JSON.stringify(estado) === JSON.stringify(this.estados[this.posicao])) return false;
        this.estados = this.estados.slice(0, this.posicao + 1);
        this.estados.push({ ...estado });
        if (this.estados.length > this.limite) this.estados.shift();
        this.posicao = this.estados.length - 1;
        return true;
    }

    get podeDesfazer() { return this.posicao > 0; }
    get podeRefazer() { return this.posicao < this.estados.length - 1; }

    desfazer() {
        if (!this.podeDesfazer) return null;
        return { ...this.estados[--this.posicao] };
    }

    refazer() {
        if (!this.podeRefazer) return null;
        return { ...this.estados[++this.posicao] };
    }
}
