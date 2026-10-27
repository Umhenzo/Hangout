# Hangout

App Android de conversas em Kotlin e Jetpack Compose, com visual escuro em tons de cinza.

## Firebase setup

1. Abra o projeto `hangout-1872a` no Firebase Console.
2. Em **Authentication → Sign-in method**, ative **Email/Password**. A interface do app pede nick e senha; internamente, o nick é convertido em um endereço de autenticação técnico que não corresponde a uma caixa de e-mail.
3. Crie o banco em **Firestore Database**.
4. Abra **Firestore Database → Rules**, cole o conteúdo de `firestore.rules` e publique. As regras desse arquivo agora incluem mensagens privadas.
5. Compile e instale o APK gerado pelo workflow **Build Hangout APK**.

**Importante:** depois de atualizar o arquivo `firestore.rules` no GitHub, as regras não são publicadas automaticamente no Firebase Console. Publique o conteúdo manualmente no Console.

## Recursos implementados

- Cadastro e login com nick e senha, usando Firebase Authentication Email/Password por baixo dos panos.
- Pesquisa de usuários pelo nick cadastrado.
- Conversas privadas entre duas contas autenticadas, com mensagens em tempo real e prévia da última mensagem.
- Chat global em tempo real.
- Lista inicial sem perfis fictícios.
- Tema cinza escuro.

## Ainda pendente

- Recuperação de senha para contas criadas sem e-mail real.
- Chamadas de voz, fotos e presença online.
- Validação de acessibilidade/usabilidade em aparelhos reais.

## Build APK

Cada push na branch `main` inicia o workflow. Abra a aba **Actions**, selecione a execução mais recente concluída e baixe o artefato `Hangout-debug-apk`.
