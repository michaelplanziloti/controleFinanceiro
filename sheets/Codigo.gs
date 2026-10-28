/**
 * Recebe um mês do app Controle Financeiro e preenche a aba desse mês (ex.: Out26)
 * nas mesmas células que se preencheria à mão. Só mexe nas células de entrada:
 * renda (B6:D10), mesadas (F17:F18) e os lançamentos dos cinco blocos (linha 30 em diante).
 *
 * Instalação: veja a seção "Planilha Google" do README do projeto.
 */

const MESES = ['Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho', 'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'];
const ABREVIACOES = ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez'];

const ABA_MODELO = 'Modelo';
const LINHA_RENDA = 6;
const LINHAS_RENDA = 5; // 6 a 10; a linha 11 é o total
const LINHA_MESADAS = 17;
const LINHAS_MESADAS = 2;
const PRIMEIRA_LINHA = 30;
const FORMULA_DIZIMO = '=$D$11*Config!$C$6';

// Coluna inicial e campos de cada bloco, na ordem das colunas da aba.
const BLOCOS = {
  FIXED: { coluna: 2, campos: ['description', 'category', 'payment', 'dueDay', 'planned', 'paid30', 'paid15', 'paid'] },
  COUPLE: { coluna: 11, campos: ['description', 'category', 'payment', 'planned', 'paid30', 'paid15', 'paid'] },
  PERSONAL: { coluna: 19, campos: ['description', 'person', 'category', 'payment', 'planned', 'paid30', 'paid15', 'paid'] },
  INVESTMENT: { coluna: 28, campos: ['description', 'category', 'payment', 'planned', 'paid30', 'paid15', 'paid'] },
  EXTRA: { coluna: 36, campos: ['description', 'category', 'payment', 'planned', 'paid30', 'paid15', 'paid'] },
};

/** Rode uma vez no editor: cria a chave que o app usa e mostra no registro de execução. */
function gerarChave() {
  const chave = Utilities.getUuid();
  PropertiesService.getScriptProperties().setProperty('CHAVE', chave);
  Logger.log('Chave para colar no app: ' + chave);
}

function doPost(e) {
  try {
    const dados = JSON.parse(e.postData.contents);
    const chave = PropertiesService.getScriptProperties().getProperty('CHAVE');
    if (!chave || dados.key !== chave) return resposta({ ok: false, erro: 'Chave inválida' });

    const trava = LockService.getScriptLock();
    trava.waitLock(30000);
    try {
      const aba = preencherMes(SpreadsheetApp.getActive(), dados);
      return resposta({ ok: true, aba: aba.getName() });
    } finally {
      trava.releaseLock();
    }
  } catch (erro) {
    return resposta({ ok: false, erro: String(erro) });
  }
}

/** Para testar no navegador: abrir o link do app da web deve mostrar esta mensagem. */
function doGet() {
  return resposta({ ok: true, mensagem: 'Controle Financeiro: script instalado.' });
}

function resposta(objeto) {
  return ContentService.createTextOutput(JSON.stringify(objeto)).setMimeType(ContentService.MimeType.JSON);
}

function preencherMes(planilha, dados) {
  const aba = abaDoMes(planilha, dados.year, dados.month);
  preencherRenda(aba, dados.incomes);
  preencherMesadas(aba, dados.allowances);

  const porBloco = { FIXED: [], COUPLE: [], PERSONAL: [], INVESTMENT: [], EXTRA: [] };
  // O dízimo fica no topo dos gastos fixos, como na planilha.
  dados.expenses.filter(function (d) { return d.block === 'TITHE'; }).forEach(function (d) { porBloco.FIXED.push(d); });
  dados.expenses.filter(function (d) { return d.block !== 'TITHE'; }).forEach(function (d) { porBloco[d.block].push(d); });

  const maiorBloco = Math.max.apply(null, Object.keys(porBloco).map(function (b) { return porBloco[b].length; }));
  const faltam = PRIMEIRA_LINHA + maiorBloco - 1 - aba.getMaxRows();
  if (faltam > 0) aba.insertRowsAfter(aba.getMaxRows(), faltam);
  const linhasDisponiveis = aba.getMaxRows() - PRIMEIRA_LINHA + 1;

  Object.keys(BLOCOS).forEach(function (nome) {
    const bloco = BLOCOS[nome];
    aba.getRange(PRIMEIRA_LINHA, bloco.coluna, linhasDisponiveis, bloco.campos.length).clearContent();
    const linhas = porBloco[nome].map(function (despesa) {
      return bloco.campos.map(function (campo) { return valorDoCampo(despesa, campo); });
    });
    if (linhas.length) aba.getRange(PRIMEIRA_LINHA, bloco.coluna, linhas.length, bloco.campos.length).setValues(linhas);
  });
  return aba;
}

function abaDoMes(planilha, ano, mes) {
  const nome = ABREVIACOES[mes - 1] + String(ano).slice(-2);
  const existente = planilha.getSheetByName(nome);
  if (existente) return existente;
  const nova = planilha.getSheetByName(ABA_MODELO).copyTo(planilha).setName(nome);
  nova.getRange('B1').setValue(MESES[mes - 1] + ' ' + ano);
  return nova;
}

function preencherRenda(aba, rendas) {
  let linhas = rendas.map(function (r) { return [texto(r.source), r.person || 'Casal', r.amount]; });
  if (linhas.length > LINHAS_RENDA) {
    // Só cabem cinco linhas: a quinta junta o restante.
    const resto = rendas.slice(LINHAS_RENDA - 1).reduce(function (soma, r) { return soma + r.amount; }, 0);
    linhas = linhas.slice(0, LINHAS_RENDA - 1).concat([['Outras rendas', 'Casal', Math.round(resto * 100) / 100]]);
  }
  aba.getRange(LINHA_RENDA, 2, LINHAS_RENDA, 3).clearContent();
  if (linhas.length) aba.getRange(LINHA_RENDA, 2, linhas.length, 3).setValues(linhas);
}

function preencherMesadas(aba, mesadas) {
  const pessoas = aba.getRange(LINHA_MESADAS, 2, LINHAS_MESADAS, 1).getValues();
  const valores = pessoas.map(function (linha) {
    const mesada = mesadas.filter(function (m) { return m.person === linha[0]; })[0];
    return [mesada ? mesada.amount : 0];
  });
  aba.getRange(LINHA_MESADAS, 6, LINHAS_MESADAS, 1).setValues(valores);
}

function valorDoCampo(despesa, campo) {
  switch (campo) {
    case 'planned': return despesa.block === 'TITHE' && despesa.autoPlanned ? FORMULA_DIZIMO : despesa.planned;
    case 'paid': return despesa.paid === true;
    case 'paid30':
    case 'paid15': return despesa[campo] || '';
    default: return texto(despesa[campo]);
  }
}

/** Texto como texto: evita que algo começando com "=" vire fórmula. */
function texto(valor) {
  if (valor === null || valor === undefined) return '';
  const s = String(valor);
  return /^[=+\-@]/.test(s) ? "'" + s : s;
}
