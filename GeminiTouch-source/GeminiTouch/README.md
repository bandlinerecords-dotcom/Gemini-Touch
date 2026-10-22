# Gemini Touch — Android agent prototype

Aplicação Android nativa para Android 11+ (API 30+), com chave Gemini introduzida pelo utilizador, captura de ecrã via AccessibilityService, e ciclo observar → pedir uma ação ao Gemini → executar gesto → observar novamente.

## O que inclui
- Campo para chave API e modelo Gemini (predefinição `gemini-2.5-flash`).
- Tarefa em linguagem natural.
- Captura de ecrã com `AccessibilityService.takeScreenshot`.
- Toque, deslize, escrita em campo focado, voltar, início, espera e conclusão.
- Loop contínuo, estado/passos e botão para parar.
- Workflow GitHub Actions para compilar o APK sem instalar Android Studio.

## Compilar sem Android Studio
1. Cria um repositório no GitHub e carrega o conteúdo desta pasta.
2. Abre **Actions** → **Build Gemini Touch APK** → **Run workflow**.
3. Quando terminar, abre a execução e descarrega o artefacto `GeminiTouch-debug-apk`.
4. Extrai `app-debug.apk` e instala-o no Android. Se necessário, autoriza a instalação a partir da aplicação usada para abrir o APK.

Também podes executar o workflow fazendo push para `main` ou `master`.

## Primeira utilização
1. Instala e abre a aplicação.
2. Toca em **ATIVAR SERVIÇO DE ACESSIBILIDADE** e ativa **Gemini Touch control**.
3. Volta à aplicação, introduz a tua chave Gemini API, confirma o nome de um modelo disponível na tua conta e escreve a tarefa.
4. Toca em **INICIAR AGENTE**. Mantém a aplicação aberta durante a execução.
5. Toca em **PARAR AGORA** para interromper.

## Limitações importantes
- Isto é um protótipo inicial, não um substituto completo para o produto oficial Gemini Computer Use. O modelo devolve ações JSON a partir de uma imagem; não usa uma sessão Live nem a API oficial Computer Use especializada.
- A latência depende da rede, modelo, tamanho de imagem e cota. 1–2 segundos não é garantido.
- A escrita funciona em campos que aceitam `ACTION_SET_TEXT`; alguns apps/campos podem não aceitar.
- O serviço de acessibilidade deve ser ativado manualmente pelo utilizador e pode não controlar ecrãs protegidos, diálogos de sistema ou apps que bloqueiam automação.
- A chave API é usada apenas em chamadas HTTPS a Google e não é enviada para outro servidor por este código. Evita partilhar o APK com a tua chave; a chave não é guardada pela app.
- Confirma sempre ações importantes. Não uses automação para contornar segurança, autenticação ou proteções de aplicações.
