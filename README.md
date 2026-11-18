# ChessSMP 2.1.0 — Paper 26.2

A chess-themed PvP roles plugin for Paper 26.2. This repository contains the complete Maven source project and a GitHub Actions workflow that builds the plugin automatically.

## Requirements

- Minecraft/Paper **26.2**
- **Java 25** for the server and build
- Maven 3.9+

## Features

- Six persistent roles: Pawn, Knight, Bishop, Rook, Queen and King.
- Two PvP abilities per role, each with its own cooldown.
- `/chessinfo` — shows your current piece, role ratings, both abilities, descriptions, cooldowns and exact activation commands.
- Emoji and role-coloured player-name prefixes through scoreboard teams.
- Chess War event: `/chess event start`, `/chess event status`, `/chess event stop`.
- White/Black teams, friendly-fire prevention during the event and a first-to-15 capture win condition.
- Piece Trader GUI: `/chess trader` gives the special trader item and opens the GUI. Right-click the item later to reopen it.
- Mid-game role swaps and supply trades.
- Chess Points and `/chess top` leaderboard.
- Player roles and Chess Points saved in `plugins/ChessSMP/players.yml`.

## Commands

| Command | Description |
|---|---|
| `/chess` | Show command help |
| `/chess choose <pawn\|knight\|bishop\|rook\|queen\|king>` | Choose a role outside an event |
| `/chess ability 1` | Activate ability one (during PvP combat) |
| `/chess ability 2` | Activate ability two (during PvP combat) |
| `/chess trader` | Get/open the Piece Trader |
| `/chess event start` | Start a server-wide Chess War (admin) |
| `/chess event status` | Show event score (admin) |
| `/chess event stop` | Stop the event (admin) |
| `/chess info` | Short role summary |
| `/chessinfo` | Full role, ability and stats guide |
| `/chess top` | Show the Chess Points leaderboard |

## Ability stats

The `/chessinfo` ratings are role-design ratings out of 10, not hidden Minecraft attributes:

- **Pawn:** Offense 5, Defense 5, Mobility 6, Support 3
- **Knight:** Offense 7, Defense 4, Mobility 10, Support 3
- **Bishop:** Offense 4, Defense 7, Mobility 5, Support 9
- **Rook:** Offense 5, Defense 10, Mobility 2, Support 4
- **Queen:** Offense 10, Defense 5, Mobility 7, Support 6
- **King:** Offense 6, Defense 9, Mobility 3, Support 10

Abilities require an active 10-second PvP combat tag. Their cooldowns are displayed in `/chessinfo`.

## Build locally

From the `chess-smp` project directory:

```bash
mvn clean package
```

The plugin JAR is created at `target/ChessSMP-2.1.0.jar`.

## Build on GitHub

1. Create a GitHub repository named `ChessSMP`.
2. Upload the **contents** of this project folder (including `.github/workflows/build.yml`) to the repository root.
3. Open the repository's **Actions** tab. The workflow builds the JAR on pushes to `main`/`master`, pull requests, or manual dispatch.
4. Open a successful workflow run and download the `ChessSMP-2.1.0` artifact.
5. Put the JAR in your Paper server's `plugins` folder and restart the server.

## Notes

- This project targets the Paper 26.2 API (`26.2.build.135-stable`) and Java 25. It is not intended for 1.21.x servers.
- This source has not been compiled or tested against a live Paper server in this environment. Use the GitHub Actions build as the first compile check, then test the features on a staging server before using it on your main world.
- Back up your server before installing or updating plugins.
