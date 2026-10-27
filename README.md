# Controle Financeiro (Android)

App Android em Kotlin com a mesma ideia da planilha "Controle Financeiro 2026": um mês por vez, renda, resumo 50/30/20 depois do dízimo, mesadas de cada pessoa e os cinco blocos de lançamentos. Tudo fica salvo no aparelho.

## Como abrir

1. Instale o Android Studio (Ladybug ou mais novo).
2. Mova a pasta `ControleFinanceiro` para um caminho curto, por exemplo `C:\dev\ControleFinanceiro`. Caminhos muito longos quebram o build no Windows.
3. No Android Studio: File > Open e escolha a pasta. Ele baixa o Gradle 8.7 e as dependências.
4. Rode no emulador ou no celular (Android 8.0 ou mais novo).

Testes da lógica de orçamento: `./gradlew test` (ou clique em `BudgetCalculatorTest` e rode).

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
