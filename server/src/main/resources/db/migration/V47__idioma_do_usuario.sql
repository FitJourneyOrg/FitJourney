-- Idioma do usuário (fatia G.1, ARCH #37).
--
-- ============================================================================
-- POR QUE O IDIOMA MORA NO BANCO, E NÃO SÓ NO APARELHO
-- ============================================================================
--
-- Guardar a escolha só no cliente seria bem mais barato: nenhuma migration, nenhuma rota, nenhum
-- catálogo de textos no Ktor. E está errado por um motivo decisivo: **o texto da notificação nasce
-- aqui**. O `Aviso` é montado no servidor e gravado em `notifications`, então quem escolhesse
-- inglês teria a interface em inglês e todo push em português.
--
-- > Metade do app traduzida é pior que nada. A pessoa não conclui "falta traduzir", conclui que
-- > está quebrado.
--
-- É também o que a [REGRA] de autoridade do servidor já manda: a verdade é do backend, o cliente
-- tem cópia otimista.


-- ============================================================================
-- O DEFAULT É 'pt-BR', E ISSO É DECISÃO DE PRODUTO
-- ============================================================================
--
-- Toda linha que já existe recebe 'pt-BR', inclusive a de quem tem o celular configurado em inglês.
--
-- Seguir o idioma do APARELHO no backfill seria o comportamento de um app internacional, e é
-- provavelmente o certo quando houver dez idiomas. Hoje seria um brasileiro abrindo o app amanhã em
-- inglês sem ter pedido nada, o que não parece recurso novo: parece defeito.
--
-- > Recurso que muda a experiência de quem não pediu nada precisa de um gesto da pessoa.
--
-- Por isso não há backfill nenhum aqui. O `DEFAULT` cobre as linhas existentes, e o inglês passa a
-- existir na tela de Conta, por escolha explícita (fatia G.4).
--
-- Sem `NOT NULL` separado em duas etapas como a V35 e a V40 fizeram: aquelas precisavam calcular um
-- valor por linha (nome derivado do e-mail, código sorteado) e por isso a coluna nascia nullable. Um
-- literal constante não precisa disso, e o Postgres preenche as linhas existentes na própria
-- instrução.
ALTER TABLE users
    ADD COLUMN locale VARCHAR(5) NOT NULL DEFAULT 'pt-BR';


-- ============================================================================
-- CHECK DE VOCABULÁRIO FECHADO
-- ============================================================================
--
-- ## Por que fechado, sabendo que cada idioma novo custa uma migration
--
-- É o padrão do projeto: emoji do check-in, papel de membro e ação de moderação são todos fechados
-- no banco. A razão é sempre a mesma, e não é o Kotlin: o `CHECK` vale para **escrita direta**, por
-- script, seed ou correção manual em produção, que é exatamente onde o código não alcança.
--
-- A alternativa era um CHECK de FORMATO (uma tag BCP-47 bem formada), que aceitaria idioma novo sem
-- migration. Recusada porque desloca a defesa para o lugar errado: o banco passaria a aceitar
-- 'xx-XX', e a correção viraria o fallback do cliente. Você descobriria o valor errado quando
-- alguém reclamasse do app em português, não quando ele foi gravado.
--
-- ## O custo real é de UMA linha
--
-- Adicionar espanhol é o mesmo movimento que a V46 já fez em `moderation_actions`: DROP CONSTRAINT
-- e ADD CONSTRAINT afrouxando o vocabulário. Migration que só ACRESCENTA valor permitido é segura
-- sobre dado existente, porque nenhuma linha atual pode violá-la.
--
-- ⚠️ Este CHECK espelha o enum `Idioma` do `shared-contract`. Se um idioma entrar lá e não aqui, o
-- `PATCH /me` passa na validação do Kotlin e estoura no INSERT. O teste de integração da V47 afirma
-- que os dois lados listam o mesmo conjunto, para a divergência aparecer no build e não em produção.
ALTER TABLE users
    ADD CONSTRAINT users_locale_suportado CHECK (locale IN ('pt-BR', 'en'));
