---
slug: guia-do-iniciante
categoria: TRAINING
destaque: true
ordem: 1
atualizado: 2026-06-15
titulo: Guia do iniciante na academia
---

Primeiro parágrafo. Ele aparece inteiro no corpo do artigo e é o que a pessoa lê antes de decidir
se continua. Escreva a conclusão aqui, não no fim.

Parágrafo normal, com **negrito** quando a palavra é o ponto da frase. Sem travessão: o catálogo de
textos do app proíbe `—` e `–` desde a fatia G, e o gerador recusa o arquivo se achar um.

## Um subtítulo quando o assunto vira

Depois do `##` vem outro bloco de texto. Só existem quatro coisas no corpo: parágrafo, **negrito**,
subtítulo `##` e lista com `-`. Qualquer outra marcação de markdown é ignorada na tela.

- item de lista
- outro item

> Este arquivo começa com `_`, então o gerador PULA ele. É modelo, não artigo.

Para criar um artigo de verdade: copie este arquivo, renomeie para `<slug>.md` (o nome do arquivo e
o campo `slug` têm de bater), e escreva. A tradução em inglês é o mesmo `<slug>.md` dentro de
`conteudo/aprender/en/`, com apenas `titulo:` no frontmatter — o resto dos campos vem do pt-BR, que
é o piso. Artigo sem arquivo em inglês aparece em português para quem usa o app em inglês, e isso é
o fallback funcionando, não um defeito.
