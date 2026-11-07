# "Casa (em breve)" na tela de onboarding

**Fechado em 2026-11-07.** Quarto recorte do "resto da Fatia 12".

## O que era

A especificação decide lançar só com academia: "a lista de equipamentos é condição de
lançamento apenas para o treino em casa. Quando casa abrir, abre já com a lista, sem passar
pelo 'casa' genérico." Até lá, "casa" sai marcada "em breve" na primeira tela do onboarding.

Decisão já tomada antes desta fatia: isto é **só UI** -- não desliga o 1 usuário real que já
treina em casa hoje (medido em 2026-11-07).

## O que mudou

`EnvironmentStep` (`QuizSteps.kt`) ganhou `AMBIENTES_DISPONIVEIS = setOf(TrainingEnvironment.ACADEMIA)`.
Para `env !in AMBIENTES_DISPONIVEIS` (hoje, só CASA):

- `Row.clickable(enabled = false)` e `RadioButton(enabled = false)` -- não dá pra selecionar.
- Texto esmaecido (`alpha = 0.38f`, convenção Material de conteúdo desabilitado).
- Sufixo `"(em breve)"` / `"(coming soon)"` (`quiz_ambiente_em_breve`, nova).

Nenhuma linha de servidor, contrato ou persistência mudou -- `TrainingEnvironment.CASA`
continua existindo de ponta a ponta sem gate nenhum. Isto só restringe o que a tela OFERECE pra
quem está fazendo o onboarding agora.

## Achado ao editar o strings.xml

Um comentário novo usava `--` dentro de `<!-- -->` (travessão duplo) -- XML não aceita `--`
dentro de comentário (só no fechamento `-->`). `gen_strings.py` (que faz parse real do XML pra
gerar o PDF) falhou com `ExpatError: not well-formed`. Trocado por `;`. Vale lembrar pra
qualquer comentário futuro em `strings.xml`: travessão de prosa (`—`, dois hifens `--`) não é
seguro dentro de comentário XML, mesmo que seja comum na prosa dos comentários Kotlin deste
projeto.

## Testes

Nenhum teste novo -- `EnvironmentStep` é Composable de UI, sem teste no projeto (convenção:
Compose não é testado unitariamente aqui, só ViewModel/state). `CatalogoDeStringsTest`/
`CatalogoEmInglesTest` (já existentes) cobrem a string nova (comentário de contexto, chave nos
dois idiomas, sem travessão/caixa alta) -- a confirmar que continuam verdes.

`:app:androidApp:testDebugUnitTest` -- a confirmar pelo Rafael.
