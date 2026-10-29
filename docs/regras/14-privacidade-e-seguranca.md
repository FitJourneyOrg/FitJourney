# Privacidade e segurança

> Índice: `docs/regras/README.md` · Marcadores: [REGRA] imutável · [INV] invariante · [PROPOSTA] lacuna não ratificada.

## Privacidade e segurança

- **[REGRA]** Localização no check-in é opt-in (PII separada). Tokens/dados sensíveis nunca vão pro SQLDelight (Keychain/Keystore).
- **[REGRA]** A fronteira entre público e privado é o **DTO**, não uma condicional (#34). Dado privado **não existe** no contrato público: "desafios concluídos" vem de `/me/challenges`, não de um campo anulável em `PublicProfileDto` preenchido quando o id é o do próprio usuário. Isolamento que depende de alguém lembrar de escrever um `if` já falhou aqui (uid no cache local).
- **[INV]** O perfil público expõe: `display_name`, inicial colorida, nível, conquistas, total de treinos e tempo de app. **Nunca**: e-mail, XP bruto, streak, programas, peso, altura, idade ou dado de saúde (PAR-Q, limitações). XP e streak ficam de fora porque virariam placar paralelo do grupo, contra a [REGRA] do #18.
