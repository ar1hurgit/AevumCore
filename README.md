# AevumCore

> Plugin Paper modulaire pour centraliser les outils staff, le chat RP/HRP et plusieurs fonctionnalités de qualité de vie sur un serveur Minecraft 1.21.x.

[![GitHub](https://img.shields.io/badge/GitHub-Repository-181717?logo=github&logoColor=white)](https://github.com/ar1hurgit/AevumCore)
![Paper](https://img.shields.io/badge/Paper-1.21.1-1B9AE5)
![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-Build-02303A?logo=gradle&logoColor=white)

![Commits](https://img.shields.io/github/commit-activity/m/ar1hurgit/AevumCore?label=Commits&color=1B9AE5)
![Issues](https://img.shields.io/github/issues/ar1hurgit/AevumCore?label=Issues&color=D73A49)
![Last Commit](https://img.shields.io/github/last-commit/ar1hurgit/AevumCore?label=Last%20Commit&color=F59E0B)
![Repo Size](https://img.shields.io/github/repo-size/ar1hurgit/AevumCore?label=Size&color=9CA3AF)

![Contributors](https://img.shields.io/github/contributors/ar1hurgit/AevumCore?label=Contributors&color=0F766E)
![Stars](https://img.shields.io/github/stars/ar1hurgit/AevumCore?label=Stars&color=EAB308)

## Fonctionnalités

- Chat RP/HRP avec annonces, messages privés, staff chat, espionnage des MP et mode HRP mask.
- Outils staff intégrés avec maintenance, vanish, god mode, vision permanente et gestion des reports.
- Fonctions joueur avec anti-AFK, temps de jeu, dernière connexion, pseudonymes et lancer de dés.
- Récompenses et progression avec premier join configurable et système de salaires.
- Protection et confort serveur avec blocage des dégâts d'explosion sur les blocs et formats de messages personnalisables.
- Stockage persistant via SQLite ou MySQL selon la configuration du serveur.

## Compatibilité

| Plateforme | Minecraft | Java | Statut |
| --- | --- | --- | --- |
| Paper | 1.21.1 | 21 | Supporté |

## Dépendances

- `Vault` : intégration optionnelle, notamment pour les récompenses configurées en mode `VAULT`.
- `ProtocolLib` : dépendance souple déclarée dans le plugin.
- `HikariCP` : inclus pour la gestion des connexions base de données.

## Installation

1. Compilez le plugin avec `./gradlew build`.
2. Récupérez le jar généré dans `build/libs/`.
3. Placez le fichier dans le dossier `plugins/` de votre serveur Paper.
4. Démarrez le serveur une première fois pour générer les fichiers de configuration.

## Configuration

Le plugin génère plusieurs fichiers dans son dossier de données, avec un point d'entrée principal dans `config.yml`.

- Base de données : choix entre `SQLITE` et `MYSQL`.
- Chat : formats RP/HRP, messages privés, staff chat, annonces et mentions.
- Maintenance : état persistant, message de refus et groupes autorisés.
- Anti-AFK : délai d'inactivité et action (`message` ou `kick`).
- Nickname : longueur maximale, caractères autorisés et cooldown.
- Reports, salaires, vision, vanish, explosions et premier join : comportements activables individuellement.

Fichiers de ressources présents dans le dépôt :

- `src/main/resources/config.yml`
- `src/main/resources/messages.yml`
- `src/main/resources/salaries.yml`

## Commandes

| Commande | Alias | Permission |
| --- | --- | --- |
| `/des` | - | `aevumcore.player.dice` |
| `/maintenance` | - | `aevumcore.admin.maintenance` |
| `/lastconnexion` | `lastco`, `last` | `aevumcore.player.lastconnexion` |
| `/salary` | - | `aevumcore.player.salary` |
| `/playtime` | - | `aevumcore.player.playtime` |
| `/antiafk` | - | `aevumcore.player.antiafk` |
| `/vanish` | `v` | `aevumcore.vanish.use` |
| `/godmode` | `god` | `aevumcore.godmode.use` |
| `/vision` | - | `aevumcore.vision.use` |
| `/nom` | `pseudo` | `aevumcore.nickname.use` |
| `/realname` | - | `aevumcore.nickname.realname` |
| `/report` | - | `aevumcore.player.report` |
| `/reports` | - | `aevumcore.report.view` |
| `/msg` | `tell`, `w` | `aevumcore.chat.msg` |
| `/staffchat` | `sc` | `aevumcore.chat.staffchat` |
| `/annonce` | - | `aevumcore.chat.announce` |
| `/anrp` | - | `aevumcore.chat.announce.rp` |
| `/anhrp` | - | `aevumcore.chat.announce.hrp` |
| `/spy` | - | `aevumcore.chat.spy` |
| `/hrp` | - | `aevumcore.chat.hrp` |

## Permissions clés

- `aevumcore.player.*` : ensemble des permissions joueur de base.
- `aevumcore.admin.*` : ensemble des permissions administrateur.
- `aevumcore.chat.announce.*` : sous-permissions pour les annonces RP, HRP et royales.
- `aevumcore.vanish.*` : options avancées liées au vanish.
- `aevumcore.report.*` : consultation et gestion des reports côté staff.

La liste complète et les valeurs par défaut sont définies dans `src/main/resources/plugin.yml`.

## Développement

Prérequis :

- Java 21
- Gradle Wrapper inclus dans le dépôt

Commandes utiles :

```bash
./gradlew build
./gradlew runServer
./gradlew runArclight
```

- `runServer` démarre un environnement Paper local via le plugin `xyz.jpenilla.run-paper`.
- `runArclight` copie le jar du plugin dans `runArclight/plugins/` puis lance `server.jar` renommé en `arclight.jar`.

## Contribution

Les issues et pull requests sont bienvenues via le dépôt GitHub :

- [github.com/ar1hurgit/AevumCore](https://github.com/ar1hurgit/AevumCore)
