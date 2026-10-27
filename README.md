# Hangout

App Android de conversas criado com Kotlin e Jetpack Compose.

## Firebase setup

O app usa Firebase Authentication (e-mail/senha) e Cloud Firestore para o chat global em tempo real.

1. No Firebase Console, abra o projeto `hangout-1872a`.
2. Em **Authentication → Sign-in method**, ative **Email/Password**.
3. Em **Firestore Database**, crie um banco de dados.
4. Abra a seção **Rules** do Firestore e publique o conteúdo do arquivo `firestore.rules` deste repositório.
5. Compile e instale o APK criado pelo workflow GitHub Actions.

As regras permitem ler o chat global apenas para usuários autenticados e limitam a criação de mensagens ao UID do usuário conectado. Não substitua as regras por regras públicas.

## Build APK

Cada push na branch `main` inicia o workflow **Build Hangout APK**. Abra a aba **Actions**, escolha a execução concluída e baixe o artefato `Hangout-debug-apk`.

## Estado atual

- Cadastro e login com e-mail/senha.
- Perfil básico criado no Firestore ao cadastrar.
- Chat global com sincronização em tempo real.
- Chats privados, fotos, presença e chamadas de voz ainda precisam de implementação adicional.
