/**
 * Liga a planilha ao app Controle Financeiro, nos dois sentidos:
 * - doPost: recebe um mês do app e preenche a aba desse mês (ex.: Out26) nas mesmas células
 *   que se preencheria à mão: renda (B6:D10), mesadas (F17:F18) e os lançamentos dos cinco
 *   blocos (linha 30 em diante). Fórmulas e resumo não são tocados.
 * - doGet: devolve as abas de mês e o conteúdo de uma delas, para o app baixar.
 * - onEdit: anota quando cada aba de mês foi editada na planilha. O app só sobrescreve uma
 *   aba se já tiver visto a última edição dela; senão ele baixa a planilha primeiro.
 * Os itens das subcategorias (cada compra do Mercado, da Gasolina...) ficam na mesma aba, à direita
 * do resumo, a partir da coluna AS; a linha da subcategoria nos blocos mostra a meta e a soma
 * deles nos dias pagos. K1 tem um link para essa área.
 * Cada aba de mês mostra em B3 a data da última atualização e de onde ela veio, com o mesmo
 * horário que o app mostra para o mês.
 *
 * Instalação: no app, em Ajustes > Planilha Google > Como instalar o script na planilha.
 */

const MESES = ['Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho', 'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'];
const ABREVIACOES = ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez'];
const NOME_DE_MES = /^(Jan|Fev|Mar|Abr|Mai|Jun|Jul|Ago|Set|Out|Nov|Dez)(\d{2})$/;

const ABA_MODELO = 'Modelo';
const LINHA_RENDA = 6;
const LINHAS_RENDA = 5; // 6 a 10; a linha 11 é o total
const LINHA_MESADAS = 17;
const LINHAS_MESADAS = 2;
const PRIMEIRA_LINHA = 30;
const FORMULA_DIZIMO = '=$D$11*Config!$C$6';
const CELULA_ATUALIZACAO = 'B3';
// Itens das subcategorias: título na linha 28, cabeçalho na 29 e itens da linha 30 em diante.
const COLUNA_ITENS = 45; // AS
const CAMPOS_ITENS = ['subcategory', 'description', 'date', 'amount', 'day'];
const CABECALHO_ITENS = ['Subcategoria', 'Descrição', 'Data', 'Valor', 'Dia pago (15 ou 30)'];
const CELULA_LINK_ITENS = 'K1';
const ORIGEM_APP = 'pelo app';
const ORIGEM_PLANILHA = 'na planilha';

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

/** Gatilho simples: guarda a hora da última edição feita à mão numa aba de mês. */
function onEdit(e) {
  const aba = e.range.getSheet();
  if (!NOME_DE_MES.test(aba.getName()) || e.range.getA1Notation() === CELULA_ATUALIZACAO) return;
  marcarEdicao(aba, Date.now(), ORIGEM_PLANILHA);
}

function doPost(e) {
  try {
    const dados = JSON.parse(e.postData.contents);
    if (!chaveValida(dados.key)) return resposta({ ok: false, erro: 'Chave inválida' });

    const trava = LockService.getScriptLock();
    trava.waitLock(30000);
    try {
      const planilha = SpreadsheetApp.getActive();
      const nome = nomeDaAba(dados.year, dados.month);
      const existente = planilha.getSheetByName(nome);
      // A planilha mudou depois do que o app conhece: o app precisa baixar antes de enviar.
      if (existente && mudouSemOApp(existente, dados.baseEditedAt)) {
        return resposta({ ok: false, conflito: true, aba: nome, editedAt: edicao(nome) });
      }
      const aba = preencherMes(planilha, dados);
      const agora = Date.now();
      marcarEdicao(aba, agora, ORIGEM_APP);
      return resposta({ ok: true, aba: aba.getName(), editedAt: agora });
    } finally {
      trava.releaseLock();
    }
  } catch (erro) {
    return resposta({ ok: false, erro: String(erro) });
  }
}

/**
 * Sem parâmetros: mensagem de teste para o navegador.
 * ?key=...: lista as abas de mês com a hora da última edição.
 * ?key=...&aba=Out26: devolve o conteúdo da aba.
 */
function doGet(e) {
  const p = (e && e.parameter) || {};
  if (!p.key) return resposta({ ok: true, mensagem: 'Controle Financeiro: script instalado.' });
  if (!chaveValida(p.key)) return resposta({ ok: false, erro: 'Chave inválida' });
  try {
    const planilha = SpreadsheetApp.getActive();
    if (p.aba) {
      const aba = planilha.getSheetByName(p.aba);
      if (!aba || !NOME_DE_MES.test(p.aba)) return resposta({ ok: false, erro: 'Aba não encontrada: ' + p.aba });
      return resposta(Object.assign({ ok: true, aba: p.aba, editedAt: edicao(p.aba), origem: origem(p.aba) }, lerMes(aba)));
    }
    const abas = planilha.getSheets().filter(function (aba) { return NOME_DE_MES.test(aba.getName()); }).map(function (aba) {
      datarSeFaltar(aba);
      const nome = aba.getName();
      const m = NOME_DE_MES.exec(nome);
      return { aba: nome, year: 2000 + Number(m[2]), month: ABREVIACOES.indexOf(m[1]) + 1, editedAt: edicao(nome), origem: origem(nome) };
    });
    return resposta({ ok: true, abas: abas });
  } catch (erro) {
    return resposta({ ok: false, erro: String(erro) });
  }
}

function chaveValida(chave) {
  const certa = PropertiesService.getScriptProperties().getProperty('CHAVE');
  return !!certa && chave === certa;
}

function resposta(objeto) {
  return ContentService.createTextOutput(JSON.stringify(objeto)).setMimeType(ContentService.MimeType.JSON);
}

function nomeDaAba(ano, mes) {
  return ABREVIACOES[mes - 1] + String(ano).slice(-2);
}

function edicao(nome) {
  return Number(PropertiesService.getDocumentProperties().getProperty('editado:' + nome) || 0);
}

function origem(nome) {
  return PropertiesService.getDocumentProperties().getProperty('origem:' + nome) || ORIGEM_PLANILHA;
}

/** Guarda quando e por onde a aba mudou e mostra isso em B3. */
function marcarEdicao(aba, quando, deOnde) {
  const props = PropertiesService.getDocumentProperties();
  props.setProperty('editado:' + aba.getName(), String(quando));
  props.setProperty('origem:' + aba.getName(), deOnde);
  carimbar(aba, quando, deOnde);
}

/**
 * Abas que ainda não têm data (nunca mudaram desde que o script foi instalado) ganham a hora de
 * agora; abas com data registrada mas B3 apagada têm a B3 escrita de novo.
 */
function datarSeFaltar(aba) {
  const nome = aba.getName();
  if (edicao(nome) === 0) {
    marcarEdicao(aba, Date.now(), ORIGEM_PLANILHA);
  } else if (String(aba.getRange(CELULA_ATUALIZACAO).getValue()).trim() === '') {
    carimbar(aba, edicao(nome), origem(nome));
  }
}

/** Escreve em B3 algo como "Última atualização: 28/10/2026 15:34 (pelo app)". */
function carimbar(aba, quando, deOnde) {
  const data = Utilities.formatDate(new Date(quando), Session.getScriptTimeZone(), 'dd/MM/yyyy HH:mm');
  aba.getRange(CELULA_ATUALIZACAO).setValue('Última atualização: ' + data + ' (' + deOnde + ')')
    .setFontStyle('italic').setFontColor('#6b7280');
}

/** Para rodar no editor: escreve a data em todas as abas de mês de uma vez. */
function atualizarDatas() {
  SpreadsheetApp.getActive().getSheets().filter(function (aba) { return NOME_DE_MES.test(aba.getName()); }).forEach(datarSeFaltar);
}

/** Sem base, o app nunca baixou o mês: só pode escrever se a aba estiver vazia. */
function mudouSemOApp(aba, base) {
  if (base === null || base === undefined) return temDados(aba);
  return edicao(aba.getName()) > Number(base);
}

function temDados(aba) {
  const mes = lerMes(aba);
  return mes.incomes.length > 0 || mes.expenses.length > 0;
}

// ---------- Escrita (app -> planilha) ----------

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
  preencherItens(aba, dados.items || []);
  return aba;
}

/** Escreve os itens das subcategorias à direita do resumo, com um link para eles em K1. */
function preencherItens(aba, itens) {
  const ultimaColuna = COLUNA_ITENS + CAMPOS_ITENS.length - 1;
  if (aba.getMaxColumns() < ultimaColuna) aba.insertColumnsAfter(aba.getMaxColumns(), ultimaColuna - aba.getMaxColumns());
  const faltam = PRIMEIRA_LINHA + itens.length - 1 - aba.getMaxRows();
  if (faltam > 0) aba.insertRowsAfter(aba.getMaxRows(), faltam);

  aba.getRange(PRIMEIRA_LINHA - 2, COLUNA_ITENS).setValue('Itens das subcategorias').setFontWeight('bold');
  aba.getRange(PRIMEIRA_LINHA - 1, COLUNA_ITENS, 1, CAMPOS_ITENS.length).setValues([CABECALHO_ITENS]).setFontWeight('bold');
  aba.getRange(PRIMEIRA_LINHA, COLUNA_ITENS, aba.getMaxRows() - PRIMEIRA_LINHA + 1, CAMPOS_ITENS.length).clearContent();
  const linhas = itens.map(function (item) {
    return [texto(item.subcategory), texto(item.description), item.date || '', item.amount, item.day === 30 ? 30 : 15];
  });
  if (linhas.length) aba.getRange(PRIMEIRA_LINHA, COLUNA_ITENS, linhas.length, CAMPOS_ITENS.length).setValues(linhas);

  const destino = '#gid=' + aba.getSheetId() + '&range=' + aba.getRange(PRIMEIRA_LINHA - 2, COLUNA_ITENS).getA1Notation();
  aba.getRange(CELULA_LINK_ITENS).setFormula('=HYPERLINK("' + destino + '", "Itens das subcategorias ›")');
}

function abaDoMes(planilha, ano, mes) {
  const nome = nomeDaAba(ano, mes);
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
    // "julia" no app e "Julia" na planilha são a mesma pessoa.
    const mesada = mesadas.filter(function (m) { return mesmoNome(m.person, linha[0]); })[0];
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

// ---------- Leitura (planilha -> app) ----------

function lerMes(aba) {
  const rendas = aba.getRange(LINHA_RENDA, 2, LINHAS_RENDA, 3).getValues().filter(function (r) {
    return textoLido(r[0]) !== '' || numero(r[2]) !== 0;
  }).map(function (r) {
    const pessoa = textoLido(r[1]);
    return { source: textoLido(r[0]), person: pessoa === '' || mesmoNome(pessoa, 'Casal') ? null : pessoa, amount: numero(r[2]) };
  });

  const mesadas = aba.getRange(LINHA_MESADAS, 2, LINHAS_MESADAS, 5).getValues().filter(function (r) {
    return textoLido(r[0]) !== '';
  }).map(function (r) { return { person: textoLido(r[0]), amount: numero(r[4]) }; });

  const linhas = Math.max(0, aba.getLastRow() - PRIMEIRA_LINHA + 1);
  const despesas = [];
  if (linhas > 0) {
    Object.keys(BLOCOS).forEach(function (nome) {
      const bloco = BLOCOS[nome];
      const faixa = aba.getRange(PRIMEIRA_LINHA, bloco.coluna, linhas, bloco.campos.length);
      const valores = faixa.getValues();
      const formulas = faixa.getFormulas();
      const colunaMeta = bloco.campos.indexOf('planned');
      valores.forEach(function (linha, i) {
        if (textoLido(linha[0]) === '') return;
        const despesa = { block: nome };
        bloco.campos.forEach(function (campo, j) { despesa[campo] = lido(campo, linha[j]); });
        // A linha do dízimo usa a fórmula da renda: no app ela vira "calcular pela renda".
        const formula = String(formulas[i][colunaMeta] || '');
        if (nome === 'FIXED' && (formula.indexOf('Config!$C$6') >= 0 || mesmoNome(despesa.description, 'Dízimo'))) {
          despesa.block = 'TITHE';
          despesa.autoPlanned = formula.indexOf('Config!$C$6') >= 0;
        } else {
          despesa.autoPlanned = false;
        }
        despesas.push(despesa);
      });
    });
  }
  return { incomes: rendas, allowances: mesadas, expenses: despesas, items: lerItens(aba) };
}

// null quando a aba ainda não tem a área de itens: o app então mantém os itens que já tem.
function lerItens(aba) {
  if (aba.getMaxColumns() < COLUNA_ITENS + CAMPOS_ITENS.length - 1) return null;
  const linhas = aba.getLastRow() - PRIMEIRA_LINHA + 1;
  if (linhas <= 0) return [];
  return aba.getRange(PRIMEIRA_LINHA, COLUNA_ITENS, linhas, CAMPOS_ITENS.length).getValues().filter(function (r) {
    return textoLido(r[0]) !== '' && numero(r[3]) !== 0;
  }).map(function (r) {
    const data = r[2] instanceof Date ? Utilities.formatDate(r[2], Session.getScriptTimeZone(), 'dd/MM/yyyy') : textoLido(r[2]);
    return {
      subcategory: textoLido(r[0]),
      description: textoLido(r[1]) || textoLido(r[0]),
      date: data || null,
      amount: numero(r[3]),
      day: numero(r[4]) === 30 ? 30 : 15,
    };
  });
}

function lido(campo, valor) {
  switch (campo) {
    case 'planned':
    case 'paid30':
    case 'paid15': return numero(valor);
    case 'paid': return valor === true;
    case 'dueDay': return valor instanceof Date ? Utilities.formatDate(valor, 'GMT', 'dd/MM') : textoLido(valor) || null;
    default: return textoLido(valor) || null;
  }
}

function textoLido(valor) {
  return valor === null || valor === undefined ? '' : String(valor).trim();
}

function numero(valor) {
  if (typeof valor === 'number') return Math.round(valor * 100) / 100;
  const s = textoLido(valor).replace(/[R$\s]/g, '');
  if (s === '') return 0;
  const n = Number(s.indexOf(',') >= 0 ? s.replace(/\./g, '').replace(',', '.') : s);
  return isNaN(n) ? 0 : Math.round(n * 100) / 100;
}

function mesmoNome(a, b) {
  const chave = function (s) { return textoLido(s).normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase(); };
  return chave(a) === chave(b);
}
