# Comentários e reações (fatia E.1)

> Índice: `docs/regras/README.md` · Marcadores: [REGRA] imutável · [INV] invariante · [PROPOSTA] lacuna não ratificada.

### Social — comentários e reações (E.1, V44)

- **[INV]** **Uma reação por pessoa por check-in** (8.2), garantida pela PK composta `(check_in_id, user_id)`. Trocar é `ON CONFLICT DO UPDATE` e tirar é `DELETE`; nenhum dos dois consulta antes. **Quando a regra cabe numa chave, ela para de depender de código correto.**
- **[REGRA]** O vocabulário de reações é **fechado no Kotlin e aberto no banco** — seis emojis, emendando a 8.2 que dizia "qualquer emoji". A lista é decisão de produto e vai mudar; um `CHECK` exigiria migration a cada ajuste de UI, e o custo de uma linha fora da lista é uma reação estranha no feed, não corrupção.
- **[REGRA]** **Comentário não se edita** (8.1). Não existe `updated_at` nem `PUT`, e a ausência é a regra: uma coluna de edição convidaria alguém a implementá-la, e o que 49 pessoas leram deixaria de ser o que está escrito.
- **[REGRA]** Apaga o comentário: **o autor, o dono do check-in ou o admin** *(emenda de 2026-09-07)*. O dono entrou depois — é a foto dele, e quem publica decide o que fica pendurado no próprio conteúdo. Sem prazo, ao contrário do check-in: comentário não vale ponto, então apagar não move o ranking, e comentário ofensivo antigo precisa poder sair.
- **[INV]** Toda operação social passa por **duas guardas**: sou membro do grupo (senão 404, nunca 403) **e** o alvo é deste grupo. A segunda não é redundante — sem ela, conhecer um `Uuid` de outro grupo daria acesso a comentar nele, e **segurança que depende de o atacante não descobrir um identificador é segurança emprestada**.
