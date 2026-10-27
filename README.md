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
