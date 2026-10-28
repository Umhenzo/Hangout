# Hangout

App Android de conversas em Kotlin/Jetpack Compose, com visual escuro em tons de cinza.

## Recursos do app

- Cadastro e login por nick e senha (sem pedir e-mail real na interface).
- Pesquisa de usuário por nick e conversas privadas em tempo real.
- Chat global em tempo real.
- Perfil com foto, status e presença aproximada online/offline.
- Código de recuperação que pode redefinir a senha sem e-mail real (requer Firebase Functions publicadas).
- Salas de voz LiveKit com controles de microfone (requer LiveKit Cloud/self-hosted e Firebase Functions publicadas).

## Firebase setup

Projeto: `hangout-1872a`.

1. Em **Authentication → Sign-in method**, ative **Email/Password**. O app usa um endereço interno do tipo `nick@users.hangout.invalid`; as pessoas não precisam digitar um e-mail real.
2. Crie o Firestore Database, se ainda não existir.
3. Em **Firestore Database → Rules**, publique o conteúdo de `firestore.rules`.
4. Em **Storage**, crie/habilite o bucket e publique `storage.rules`.
5. Atualize o app com o APK do workflow GitHub Actions.

A configuração no GitHub não publica as regras automaticamente no Console. Para publicar regras a partir da raiz do repositório, use Firebase CLI e o arquivo `firebase.json`.

## Fotos de perfil

O app envia fotos para `profilePhotos/{uid}/avatar.jpg`. O bucket Cloud Storage precisa estar criado e acessível. O Firebase exige o plano de faturamento Blaze para criar/usar Cloud Storage em projetos atuais; é um plano pago por uso, então não o ative sem o titular da conta revisar custos e alertas de orçamento.

## Recuperação de senha

Na aba **Perfil**, use **Gerar código de recuperação** e salve o código exibido. Ele só é mostrado uma vez. Se esquecer a senha, na tela de login toque em **Esqueci a senha**, informe nick, código e nova senha. Depois da recuperação, o código antigo é invalidado e um novo código é exibido.

Isso exige implantar a função `recoverAccount`. Sem a função publicada e sem um código salvo antes de perder a senha, a recuperação não estará disponível.

## Salas de voz (LiveKit)

A tela de salas está conectada à SDK Android do LiveKit. Para chamadas reais:

1. Crie um projeto no LiveKit Cloud ou hospede um servidor LiveKit.
2. Na raiz deste repositório, instale Firebase CLI e selecione o projeto `hangout-1872a`.
3. Configure os segredos no servidor Firebase Functions:
   `firebase functions:secrets:set LIVEKIT_API_KEY`
   `firebase functions:secrets:set LIVEKIT_API_SECRET`
4. Crie `functions/.env.hangout-1872a` com uma linha `LIVEKIT_URL=wss://SEU-SERVIDOR-LIVEKIT`. Use o URL real do seu projeto; não publique chaves secretas no GitHub.
5. Implante as funções e regras:
   `firebase deploy --project hangout-1872a --only functions,firestore:rules,storage`

A função `createVoiceToken` cria tokens temporários para as quatro salas permitidas. A chave secreta do LiveKit fica no servidor, nunca dentro do APK.

**Atenção sobre custos:** Cloud Functions e Cloud Storage podem exigir o plano Blaze de faturamento por uso. Configure alertas de orçamento e confira os preços antes de habilitar o faturamento.

## Compilar

Cada push na branch `main` inicia o workflow **Build Hangout APK**. Abra a aba **Actions**, selecione a execução mais recente concluída e baixe o artefato `Hangout-debug-apk`.

## Limitações atuais

- Não há chamada de vídeo.
- Presença online é aproximada pelo ciclo de vida do app e pode demorar a refletir fechamento forçado ou falta de rede.
- O chat não tem criptografia ponta a ponta; as regras do Firestore restringem o acesso às conversas, mas isso não é o mesmo que E2EE.
- Teste em dois aparelhos/contas antes de compartilhar o APK com outras pessoas.
