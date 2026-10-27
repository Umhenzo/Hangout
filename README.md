# Hangout

Protótipo Android nativo em Kotlin + Jetpack Compose, com tema escuro e interface de mensageiro.

## Funcionalidades demonstrativas
- Abas Chats, Global, Salas e Perfil
- Perfis fictícios com username, país, status e atividade
- Pesquisa e formulário de adicionar username
- Mensagens locais de demonstração
- Tela de salas de voz e botões de chamada
- Perfil com status e país editáveis

## Limitações
Este projeto é um protótipo de interface. Não implementa ainda autenticação real, mensagens entre dispositivos, armazenamento de imagens/áudios, chamadas WebRTC, presença online em tempo real ou backend. Os perfis e conversas iniciais são fictícios.

## Compilar
O workflow em `.github/workflows/build-apk.yml` compila o APK de debug no GitHub Actions a cada push para `main`. Após uma execução bem-sucedida, baixe o artefato `Hangout-debug-apk` na página Actions do repositório.
