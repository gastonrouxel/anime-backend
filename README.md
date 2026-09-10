# Anime Backend — Spring Boot

## Prérequis
- Java 21+
- Maven 3.9+
- MySQL 8+ (ta base existante)

## Lancer en développement

```bash
# Variables d'environnement (à adapter)
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=animelist
export DB_USER=ton_user
export DB_PASSWORD=ton_mdp

mvn spring-boot:run
```

L'API écoute sur `http://localhost:8080`

## Endpoints disponibles

| Méthode | URL                          | Description                        |
|---------|------------------------------|------------------------------------|
| GET     | /api/series                  | Toutes les séries                  |
| GET     | /api/series?search=naruto    | Recherche par nom                  |
| GET     | /api/series?sortBy=note&order=desc | Tri par note décroissante    |
| GET     | /api/series/{id}             | Une série par id                   |
| GET     | /api/series/{id}/seasons     | Saisons d'une série                |
| GET     | /api/series/{id}/seasons?search=x | Recherche dans les saisons    |
| POST    | /api/series                  | Ajouter une série (body JSON)      |
| DELETE  | /api/series/{id}             | Supprimer une série                |

## Exemple de body POST

```json
{
  "id": 12345,
  "nomFr": "Naruto",
  "nomOrig": "ナルト",
  "note": 7.8,
  "nbEpisodes": 220,
  "visible": true
}
```
