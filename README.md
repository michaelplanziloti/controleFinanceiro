# Controle Financeiro (Android)

App Android em Kotlin com a mesma ideia da planilha "Controle Financeiro 2026": um mês por vez, renda, resumo 50/30/20 depois do dízimo, mesadas de cada pessoa e os cinco blocos de lançamentos. Tudo fica salvo no aparelho.

## Como abrir

1. Instale o Android Studio (Ladybug ou mais novo).
2. Mova a pasta `ControleFinanceiro` para um caminho curto, por exemplo `C:\dev\ControleFinanceiro`. Caminhos muito longos quebram o build no Windows.
3. No Android Studio: File > Open e escolha a pasta. Ele baixa o Gradle 8.13 e as dependências.
4. Rode no emulador ou no celular (Android 8.0 ou mais novo).

Testes da lógica de orçamento: `./gradlew test` (ou clique em `BudgetCalculatorTest` e rode).

## Publicar uma versão

O GitHub Actions ([release.yml](.github/workflows/release.yml)) gera o APK e o AAB assinados e cria uma Release no GitHub quando você envia uma tag `v*`:

```bash
git tag v0.1.0
git push origin v0.1.0
```

O número da tag vira o `versionName`, e o número da execução do workflow vira o `versionCode`. O APK fica em *Releases* no GitHub para baixar e instalar; o AAB serve para a Play Store.

A assinatura usa quatro secrets do repositório: `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` e `RELEASE_KEY_PASSWORD`. Para gerar um release assinado na sua máquina, crie um `keystore.properties` na raiz (ele fica fora do Git) com `storeFile`, `storePassword`, `keyAlias` e `keyPassword`. Sem ele, o release sai sem assinatura.

Guarde o arquivo `.jks` e as senhas num lugar seguro: sem eles não dá para publicar atualizações do app.

## Planilha Google

O app pode mandar cada mês para a planilha "Controle Financeiro 2026". A cada alteração (lançamento, renda, mesada, baixa), alguns segundos depois o mês inteiro é enviado e a aba dele (ex.: `Out26`) é reescrita: renda, mesadas e os lançamentos dos cinco blocos. As fórmulas, o resumo e o Painel continuam funcionando. Se a aba do mês não existir, ela é criada a partir da aba `Modelo`. Sem internet, o envio espera a conexão voltar.

O envio é só do app para a planilha: o que for digitado direto na aba de um mês é substituído no próximo envio desse mês.

### Instalar o script (uma vez)

1. Abra a planilha e vá em **Extensões > Apps Script**.
2. Apague o que estiver em `Código.gs`, cole o conteúdo de [sheets/Codigo.gs](sheets/Codigo.gs) e salve.
3. No seletor de função, no topo, escolha **gerarChave** e clique em **Executar**. O Google pede autorização: escolha sua conta, clique em **Avançado** e em **Acessar** (o aviso de "app não verificado" aparece porque o script é seu). A chave aparece no **Registro de execução**. Copie.
4. Clique em **Implantar > Nova implantação**. Em tipo, escolha **App da Web**. Em **Executar como**, deixe **Eu**; em **Quem pode acessar**, escolha **Qualquer pessoa**. Clique em **Implantar** e copie o **URL do app da Web** (termina em `/exec`).
5. Abra esse link no navegador: deve aparecer `{"ok":true,...}`.
6. No app, em **Ajustes > Planilha Google**, cole o link e a chave e toque em **Salvar**. Para mandar os meses que já existem, toque em **Enviar todos os meses**.

"Qualquer pessoa" deixa o app chamar o script sem login; só quem tem a chave consegue gravar. Para trocar a chave, rode `gerarChave` de novo e cole a nova no app.

Se mudar o script depois, use **Implantar > Gerenciar implantações**, edite a implantação e escolha **Nova versão**: assim o link continua o mesmo.

Para testar antes na planilha de verdade, faça uma cópia dela (**Arquivo > Fazer uma cópia**) e instale o script na cópia.

## Tecnologia

- Kotlin 2.0, Jetpack Compose e Material 3, com tema claro, escuro ou do sistema.
- Room (SQLite) como banco relacional local.
- DataStore para a sessão e a escolha de tema.
- Senhas com PBKDF2 e salt por usuário. Nenhuma senha é salva em texto.
- Valores em centavos (`Long`) para não ter erro de arredondamento.

## Banco de dados

```
users ──1:1── budget_settings
  │
  ├──1:N── people ───────────────┐
  ├──1:N── categories ─────────┐ │
  ├──1:N── payment_methods ──┐ │ │
  └──1:N── months            │ │ │
             ├──1:N── incomes ────┼─┼─┤ (person_id opcional: renda do casal)
             ├──1:N── allowances ─┼─┼─┤ (mesada de cada pessoa por mês)
             └──1:N── expenses ───┴─┴─┘ (categoria, forma de pagamento e pessoa)
```

| Tabela | Para que serve |
|---|---|
| `users` | Conta de login (nome, e-mail único, hash e salt da senha) |
| `budget_settings` | Regra do orçamento: dízimo, fixo, variável, investimento |
| `people` | Quem tem mesada (ex.: Michael e Julia) |
| `categories`, `payment_methods` | Listas dos menus, editáveis em Ajustes |
| `months` | Um registro por mês e ano de cada usuário |
| `incomes` | Entradas de renda do mês, com a pessoa |
| `allowances` | Valor da mesada de cada pessoa no mês |
| `expenses` | Lançamentos: bloco, meta, pago dia 30, pago dia 15, pago (sim/não) |

Blocos de `expenses`: `TITHE` (dízimo), `FIXED`, `COUPLE` (variáveis do casal), `PERSONAL` (mesadas), `INVESTMENT`, `EXTRA` (fora do orçamento).

Apagar um mês apaga renda, mesadas e lançamentos dele (`ON DELETE CASCADE`). Apagar uma categoria ou forma de pagamento só limpa o campo nos lançamentos (`SET NULL`).

## Telas

- **Entrar / Criar conta**: cada usuário vê só os próprios dados.
- **Mês**: setas e menu para trocar de mês, botão + para criar mês (copia renda, mesadas e contas recorrentes do mês anterior). Tudo em uma coluna, só com rolagem vertical:
  - resumo (sobra, taxa de poupança, renda, previsto, realizado);
  - orçamento por grupo, com recomendado, previsto e realizado;
  - card das mesadas: mesada de cada um (toque para editar), gasto, saldo, total das mesadas e divisão das contas do casal pela renda;
  - seções Renda, Gastos fixos, Variáveis do casal, Mesadas (separadas por pessoa), Investimentos e Fora do orçamento. Toque no item para editar, marque a caixa quando pagar, deslize para a esquerda para excluir (com "Desfazer");
  - totais por forma de pagamento.
- **Ano**: totais do ano, cada mês (toque para abrir), mesadas acumuladas e gastos por categoria.
- **Ajustes**: tema, pessoas, categorias, formas de pagamento, regra do orçamento e sair.
