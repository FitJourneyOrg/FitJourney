-- Miniaturas de exercício: PNG -> WebP.
--
-- ⚠️ TOCA DADO EXISTENTE. Anda junto com a conversão dos arquivos
-- (`tools/converter_webp.py`): arquivo sem ref é lixo em disco, ref sem arquivo é 404
-- na tela. Rode a conversão ANTES de subir esta migration.
--
-- POR QUÊ
--   As 963 miniaturas eram PNG PALETIZADO de 1080x1080, sem transparência, exibidas
--   numa miniatura de 56dp e num quadrado de largura de tela. Errado em duas dimensões
--   ao mesmo tempo: o formato (PNG é sem perdas — ótimo para ícone, péssimo para
--   ilustração) e o tamanho (1080px para desenhar em 360dp).
--
--   ~184 MB -> ~16 MB. Ataca o cache de 231 MB no aparelho, o disco do servidor e a
--   banda de quem tem 4G limitado.
--
-- O QUE **NÃO** MUDA
--   `video_ref` continua .mp4. Os 963 vídeos são os outros 197 MB e pedem
--   re-encodificação, que é trabalho próprio — misturar as duas coisas numa migration
--   só tornaria a volta atrás mais difícil.
--
-- REVERSÃO
--   `UPDATE exercises SET thumb_ref = regexp_replace(thumb_ref, '\.webp$', '.png');`
--   e restaurar os PNG. Por isso o script de conversão só apaga os originais quando
--   se pede explicitamente.

-- `WHERE ... LIKE '%.png'` deixa a migration IDEMPOTENTE em espírito: rodar de novo
-- (num banco já convertido por outro caminho) não faz nada em vez de produzir
-- `.webp.webp`. Flyway já garante execução única; isto protege quem rodar na mão.
UPDATE exercises
   SET thumb_ref = regexp_replace(thumb_ref, '\.png$', '.webp')
 WHERE thumb_ref LIKE '%.png';

-- Rede de segurança: se sobrou qualquer .png, a migration falha e o deploy para.
-- Melhor abortar aqui do que servir 404 em metade da biblioteca.
DO $$
DECLARE
    restantes INT;
BEGIN
    SELECT count(*) INTO restantes FROM exercises WHERE thumb_ref LIKE '%.png';
    IF restantes > 0 THEN
        RAISE EXCEPTION 'V39: % miniaturas ainda apontam para .png', restantes;
    END IF;
END $$;
